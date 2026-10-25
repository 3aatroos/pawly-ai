package com.pawly.store;

import com.pawly.dto.store.CreateOrderRequest;
import com.pawly.dto.store.OrderDTO;
import com.pawly.dto.store.UpdateOrderStatusRequest;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.*;
import com.pawly.service.UserService;
import com.pawly.service.store.CartService;
import com.pawly.service.store.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProductPurchaseHistoryRepository purchaseHistoryRepository;
    @Mock private UserService userService;
    @Mock private CartService cartService;

    @InjectMocks private OrderService orderService;

    private User buyer;
    private Product product;
    private CartItem cartItem;
    private Order savedOrder;

    @BeforeEach
    void setUp() {
        buyer = User.builder()
                .email("buyer@test.com").passwordHash("hash")
                .firstName("Jane").lastName("Doe")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build();
        setField(buyer, "id", 1L);

        ProductCategory cat = ProductCategory.builder().name("Food").isActive(true).build();
        setField(cat, "id", 1L);

        User admin = User.builder().email("a@a.com").passwordHash("h")
                .firstName("A").lastName("B").role(User.Role.ADMINISTRATOR)
                .status(User.UserStatus.ACTIVE).build();
        setField(admin, "id", 2L);

        product = Product.builder()
                .category(cat).createdBy(admin)
                .name("Dog Food").price(new BigDecimal("20.00"))
                .stockQuantity(10).isAvailable(true).build();
        setField(product, "id", 10L);
        setField(product, "version", 0L);

        cartItem = CartItem.builder().user(buyer).product(product).quantity(2).build();
        setField(cartItem, "id", 100L);

        savedOrder = Order.builder()
                .buyer(buyer)
                .status(Order.OrderStatus.CONFIRMED)
                .paymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY)
                .totalAmount(new BigDecimal("45.00"))
                .shippingFee(new BigDecimal("5.00"))
                .shippingAddress("123 Test St")
                .build();
        setField(savedOrder, "id", 1000L);
    }

    // ── create ────────────────────────────────────────────────

    @Test
    void create_places_order_and_clears_cart() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPaymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY);
        req.setShippingAddress("123 Test St");

        given(userService.getUserByEmail("buyer@test.com")).willReturn(buyer);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));
        given(productRepository.decrementStock(10L, 2)).willReturn(1);
        given(orderRepository.save(any())).willReturn(savedOrder);
        given(paymentRepository.save(any())).willReturn(Payment.builder()
                .order(savedOrder).amount(savedOrder.getTotalAmount()).build());
        given(orderRepository.findById(1000L)).willReturn(Optional.of(savedOrder));

        OrderDTO result = orderService.create(req, "buyer@test.com");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        then(cartItemRepository).should().deleteAllByUserId(1L);
        then(purchaseHistoryRepository).should().save(any(ProductPurchaseHistory.class));
    }

    @Test
    void create_throws_when_cart_is_empty() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPaymentMethod(Order.PaymentMethod.STRIPE);
        req.setShippingAddress("addr");

        given(userService.getUserByEmail("buyer@test.com")).willReturn(buyer);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of());

        assertThatThrownBy(() -> orderService.create(req, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty cart");
    }

    @Test
    void create_throws_when_product_unavailable() {
        product.setIsAvailable(false);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPaymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY);
        req.setShippingAddress("addr");

        given(userService.getUserByEmail("buyer@test.com")).willReturn(buyer);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));

        assertThatThrownBy(() -> orderService.create(req, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void create_throws_when_stock_insufficient() {
        product.setStockQuantity(1);
        cartItem.setQuantity(5);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPaymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY);
        req.setShippingAddress("addr");

        given(userService.getUserByEmail("buyer@test.com")).willReturn(buyer);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));

        assertThatThrownBy(() -> orderService.create(req, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void create_throws_on_concurrent_stock_change() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPaymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY);
        req.setShippingAddress("addr");

        given(userService.getUserByEmail("buyer@test.com")).willReturn(buyer);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));
        given(productRepository.decrementStock(10L, 2)).willReturn(0); // concurrent update won

        assertThatThrownBy(() -> orderService.create(req, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stock changed concurrently");
    }

    // ── findMyOrders ──────────────────────────────────────────

    @Test
    void findMyOrders_returns_paged_orders_for_buyer() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(buyer);
        given(orderRepository.findByBuyerIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(savedOrder)));

        var page = orderService.findMyOrders("buyer@test.com", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
    }

    // ── findMyOrderById ───────────────────────────────────────

    @Test
    void findMyOrderById_returns_order_for_owner() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(buyer);
        given(orderRepository.findByIdAndBuyerId(1000L, 1L)).willReturn(Optional.of(savedOrder));

        OrderDTO result = orderService.findMyOrderById(1000L, "buyer@test.com");

        assertThat(result.getId()).isEqualTo(1000L);
    }

    @Test
    void findMyOrderById_throws_not_found_for_other_buyers_order() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(buyer);
        given(orderRepository.findByIdAndBuyerId(1000L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findMyOrderById(1000L, "buyer@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateStatus ──────────────────────────────────────────

    @Test
    void updateStatus_transitions_CONFIRMED_to_PREPARING() {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(Order.OrderStatus.PREPARING);

        given(orderRepository.findById(1000L)).willReturn(Optional.of(savedOrder));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        OrderDTO result = orderService.updateStatus(1000L, req);

        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PREPARING);
    }

    @Test
    void updateStatus_marks_COD_payment_succeeded_on_DELIVERED() {
        Payment payment = Payment.builder()
                .order(savedOrder).amount(savedOrder.getTotalAmount())
                .status(Payment.PaymentStatus.PENDING).build();
        setField(payment, "id", 50L);
        savedOrder.setPayment(payment);

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(Order.OrderStatus.DELIVERED);

        given(orderRepository.findById(1000L)).willReturn(Optional.of(savedOrder));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        orderService.updateStatus(1000L, req);

        assertThat(savedOrder.getPayment().getStatus()).isEqualTo(Payment.PaymentStatus.SUCCEEDED);
        assertThat(savedOrder.getPayment().getPaidAt()).isNotNull();
    }

    @Test
    void updateStatus_throws_for_terminal_DELIVERED_order() {
        savedOrder.setStatus(Order.OrderStatus.DELIVERED);
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(Order.OrderStatus.SHIPPED);

        given(orderRepository.findById(1000L)).willReturn(Optional.of(savedOrder));

        assertThatThrownBy(() -> orderService.updateStatus(1000L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void updateStatus_throws_for_terminal_CANCELLED_order() {
        savedOrder.setStatus(Order.OrderStatus.CANCELLED);
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(Order.OrderStatus.CONFIRMED);

        given(orderRepository.findById(1000L)).willReturn(Optional.of(savedOrder));

        assertThatThrownBy(() -> orderService.updateStatus(1000L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
