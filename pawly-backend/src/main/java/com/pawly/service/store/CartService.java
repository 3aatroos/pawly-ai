package com.pawly.service.store;

import com.pawly.dto.store.AddToCartRequest;
import com.pawly.dto.store.CartDTO;
import com.pawly.dto.store.CartItemDTO;
import com.pawly.entity.CartItem;
import com.pawly.entity.Product;
import com.pawly.entity.User;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.CartItemRepository;
import com.pawly.repository.ProductRepository;
import com.pawly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserService userService;

    // ── Read ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CartDTO getCart(String callerEmail) {
        User user = userService.getUserByEmail(callerEmail);
        List<CartItemDTO> items = cartItemRepository
                .findByUserIdOrderByAddedAtDesc(user.getId())
                .stream()
                .map(CartItemDTO::from)
                .toList();
        return CartDTO.from(items);
    }

    // ── Add ───────────────────────────────────────────────────

    @Transactional
    public CartDTO addItem(AddToCartRequest req, String callerEmail) {
        User user     = userService.getUserByEmail(callerEmail);
        Product product = resolveAvailableProduct(req.getProductId());

        validateStock(product, req.getQuantity());

        cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .ifPresentOrElse(
                        existing -> {
                            int newQty = existing.getQuantity() + req.getQuantity();
                            validateStock(product, newQty);
                            existing.setQuantity(newQty);
                            cartItemRepository.save(existing);
                        },
                        () -> cartItemRepository.save(CartItem.builder()
                                .user(user)
                                .product(product)
                                .quantity(req.getQuantity())
                                .build())
                );

        return getCart(callerEmail);
    }

    // ── Update quantity ───────────────────────────────────────

    @Transactional
    public CartDTO updateItem(Long itemId, int newQuantity, String callerEmail) {
        CartItem item = getItemForCaller(itemId, callerEmail);
        validateStock(item.getProduct(), newQuantity);
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
        return getCart(callerEmail);
    }

    // ── Remove ────────────────────────────────────────────────

    @Transactional
    public CartDTO removeItem(Long itemId, String callerEmail) {
        CartItem item = getItemForCaller(itemId, callerEmail);
        cartItemRepository.delete(item);
        return getCart(callerEmail);
    }

    // ── Clear ─────────────────────────────────────────────────

    @Transactional
    public void clearCart(String callerEmail) {
        User user = userService.getUserByEmail(callerEmail);
        cartItemRepository.deleteAllByUserId(user.getId());
    }

    // ── Internal helpers ──────────────────────────────────────

    /**
     * Ensures the item belongs to the caller — an isolated ownership check.
     * Uses findByIdAndUserId so no information about other users' carts leaks.
     */
    private CartItem getItemForCaller(Long itemId, String callerEmail) {
        User user = userService.getUserByEmail(callerEmail);
        return cartItemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
    }

    private Product resolveAvailableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (Boolean.FALSE.equals(product.getIsAvailable())) {
            throw new IllegalStateException("Product is not available: " + product.getName());
        }
        return product;
    }

    private void validateStock(Product product, int requestedQty) {
        if (requestedQty < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (product.getStockQuantity() < requestedQty) {
            throw new IllegalStateException(
                    "Insufficient stock for '" + product.getName()
                    + "'. Available: " + product.getStockQuantity()
                    + ", requested: " + requestedQty);
        }
    }
}
