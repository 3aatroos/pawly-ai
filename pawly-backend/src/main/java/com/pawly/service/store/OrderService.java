package com.pawly.service.store;

import com.pawly.dto.store.CartItemDTO;
import com.pawly.dto.store.CreateOrderRequest;
import com.pawly.dto.store.OrderDTO;
import com.pawly.dto.store.UpdateOrderStatusRequest;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.*;
import com.pawly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository               orderRepository;
    private final CartItemRepository            cartItemRepository;
    private final ProductRepository             productRepository;
    private final PaymentRepository             paymentRepository;
    private final ProductPurchaseHistoryRepository purchaseHistoryRepository;
    private final UserService                   userService;
    private final CartService                   cartService;

    /** Statuses that cannot be transitioned away from once reached. */
    private static final Set<Order.OrderStatus> TERMINAL_STATUSES =
            Set.of(Order.OrderStatus.DELIVERED, Order.OrderStatus.CANCELLED);

    // ── Create ────────────────────────────────────────────────

    @Transactional
    public OrderDTO create(CreateOrderRequest req, String callerEmail) {
        User buyer = userService.getUserByEmail(callerEmail);

        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByAddedAtDesc(buyer.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot place an order with an empty cart");
        }

        // Pre-flight stock and availability check
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            if (Boolean.FALSE.equals(product.getIsAvailable())) {
                throw new IllegalStateException(
                        "Product is no longer available: " + product.getName());
            }
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new IllegalStateException(
                        "Insufficient stock for '" + product.getName()
                        + "'. Available: " + product.getStockQuantity()
                        + ", in cart: " + item.getQuantity());
            }
        }

        // Compute totals using CartDTO logic for consistency
        List<CartItemDTO> cartItemDTOs = cartItems.stream().map(CartItemDTO::from).toList();
        com.pawly.dto.store.CartDTO cartSnapshot =
                com.pawly.dto.store.CartDTO.from(cartItemDTOs);

        // Build Order
        Order order = Order.builder()
                .buyer(buyer)
                .status(Order.OrderStatus.CONFIRMED)
                .paymentMethod(req.getPaymentMethod())
                .totalAmount(cartSnapshot.getTotal())
                .shippingFee(cartSnapshot.getShippingFee())
                .shippingAddress(req.getShippingAddress())
                .shippingCity(req.getShippingCity() != null ? req.getShippingCity() : "")
                .shippingCountry(req.getShippingCountry() != null ? req.getShippingCountry() : "TN")
                .notes(req.getNotes())
                .build();

        // Snapshot items and decrement stock atomically
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();

            int updated = productRepository.decrementStock(product.getId(), cartItem.getQuantity());
            if (updated == 0) {
                throw new IllegalStateException(
                        "Stock changed concurrently for: " + product.getName());
            }

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(product.getPrice())
                    .build());
        }

        Order saved = orderRepository.save(order);

        Payment pendingPayment = paymentRepository.save(Payment.builder()
                .order(saved)
                .amount(saved.getTotalAmount())
                .status(Payment.PaymentStatus.PENDING)
                .build());
        saved.setPayment(pendingPayment);

        // Persist purchase history for recommendation engine
        for (CartItem cartItem : cartItems) {
            purchaseHistoryRepository.save(ProductPurchaseHistory.builder()
                    .user(buyer)
                    .product(cartItem.getProduct())
                    .order(saved)
                    .productName(cartItem.getProduct().getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getProduct().getPrice())
                    .build());
        }

        // Clear cart
        cartItemRepository.deleteAllByUserId(buyer.getId());

        return OrderDTO.from(orderRepository.findById(saved.getId()).orElseThrow());
    }

    // ── Read — buyer ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<OrderDTO> findMyOrders(String callerEmail, Pageable pageable) {
        User buyer = userService.getUserByEmail(callerEmail);
        return orderRepository
                .findByBuyerIdOrderByCreatedAtDesc(buyer.getId(), pageable)
                .map(OrderDTO::from);
    }

    @Transactional(readOnly = true)
    public OrderDTO findMyOrderById(Long orderId, String callerEmail) {
        User buyer = userService.getUserByEmail(callerEmail);
        Order order = orderRepository.findByIdAndBuyerId(orderId, buyer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return OrderDTO.from(order);
    }

    // ── Read — admin ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<OrderDTO> findAll(Order.OrderStatus status, Long orderId, Pageable pageable) {
        return orderRepository
                .findAllFiltered(status, null, orderId, pageable)
                .map(OrderDTO::from);
    }

    @Transactional(readOnly = true)
    public OrderDTO findById(Long orderId) {
        return OrderDTO.from(orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId)));
    }

    // ── Update status — admin ─────────────────────────────────

    @Transactional
    public OrderDTO updateStatus(Long orderId, UpdateOrderStatusRequest req) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (TERMINAL_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException(
                    "Cannot update status of an order that is already " + order.getStatus());
        }

        order.setStatus(req.getStatus());

        // When delivered and payment was COD, mark payment as succeeded
        if (req.getStatus() == Order.OrderStatus.DELIVERED
                && order.getPaymentMethod() == Order.PaymentMethod.CASH_ON_DELIVERY
                && order.getPayment() != null
                && order.getPayment().getStatus() == Payment.PaymentStatus.PENDING) {
            order.getPayment().setStatus(Payment.PaymentStatus.SUCCEEDED);
            order.getPayment().setPaidAt(java.time.LocalDateTime.now());
        }

        return OrderDTO.from(orderRepository.save(order));
    }
}
