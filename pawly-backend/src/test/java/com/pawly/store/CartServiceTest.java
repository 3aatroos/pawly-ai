package com.pawly.store;

import com.pawly.dto.store.AddToCartRequest;
import com.pawly.dto.store.CartDTO;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.CartItemRepository;
import com.pawly.repository.ProductRepository;
import com.pawly.service.UserService;
import com.pawly.service.store.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserService userService;

    @InjectMocks private CartService cartService;

    private User user;
    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("buyer@test.com").passwordHash("hash")
                .firstName("Buyer").lastName("Test")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build();
        setField(user, "id", 1L);

        ProductCategory category = ProductCategory.builder().name("Food").isActive(true).build();
        setField(category, "id", 1L);

        User admin = User.builder().email("a@a.com").passwordHash("h")
                .firstName("A").lastName("B").role(User.Role.ADMINISTRATOR)
                .status(User.UserStatus.ACTIVE).build();
        setField(admin, "id", 2L);

        product = Product.builder()
                .category(category).createdBy(admin)
                .name("Dog Food").price(new BigDecimal("20.00"))
                .stockQuantity(10).isAvailable(true).build();
        setField(product, "id", 10L);
        setField(product, "version", 0L);

        cartItem = CartItem.builder().user(user).product(product).quantity(2).build();
        setField(cartItem, "id", 100L);
    }

    // ── getCart ───────────────────────────────────────────────

    @Test
    void getCart_returns_empty_cart_for_new_user() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of());

        CartDTO cart = cartService.getCart("buyer@test.com");

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cart.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cart.getItemCount()).isZero();
    }

    @Test
    void getCart_calculates_subtotal_and_shipping() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));

        CartDTO cart = cartService.getCart("buyer@test.com");

        // 2 × 20.00 = 40.00 < 50.00 threshold → shipping = 5.00
        assertThat(cart.getSubtotal()).isEqualByComparingTo("40.00");
        assertThat(cart.getShippingFee()).isEqualByComparingTo("5.00");
        assertThat(cart.getTotal()).isEqualByComparingTo("45.00");
    }

    @Test
    void getCart_applies_free_shipping_above_threshold() {
        product.setStockQuantity(100);
        CartItem bigItem = CartItem.builder().user(user).product(product).quantity(3).build();
        setField(bigItem, "id", 101L);
        // 3 × 20.00 = 60.00 ≥ 50.00 → free shipping

        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(bigItem));

        CartDTO cart = cartService.getCart("buyer@test.com");

        assertThat(cart.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cart.getTotal()).isEqualByComparingTo("60.00");
    }

    // ── addItem ───────────────────────────────────────────────

    @Test
    void addItem_creates_new_cart_item() {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(10L);
        req.setQuantity(1);

        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        given(cartItemRepository.findByUserIdAndProductId(1L, 10L)).willReturn(Optional.empty());
        given(cartItemRepository.save(any())).willReturn(cartItem);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));

        CartDTO cart = cartService.addItem(req, "buyer@test.com");

        assertThat(cart.getItems()).hasSize(1);
        then(cartItemRepository).should().save(any(CartItem.class));
    }

    @Test
    void addItem_increments_quantity_when_product_already_in_cart() {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(10L);
        req.setQuantity(2);

        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        given(cartItemRepository.findByUserIdAndProductId(1L, 10L)).willReturn(Optional.of(cartItem));
        given(cartItemRepository.save(any())).willReturn(cartItem);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));

        cartService.addItem(req, "buyer@test.com");

        // existing quantity was 2, added 2 → should update to 4
        assertThat(cartItem.getQuantity()).isEqualTo(4);
    }

    @Test
    void addItem_throws_when_product_unavailable() {
        product.setIsAvailable(false);
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(10L);
        req.setQuantity(1);

        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(req, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void addItem_throws_when_stock_insufficient() {
        product.setStockQuantity(1);
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(10L);
        req.setQuantity(5);

        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(req, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void addItem_throws_for_unknown_product() {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(999L);
        req.setQuantity(1);

        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(req, "buyer@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateItem ────────────────────────────────────────────

    @Test
    void updateItem_changes_quantity() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByIdAndUserId(100L, 1L)).willReturn(Optional.of(cartItem));
        given(cartItemRepository.save(any())).willReturn(cartItem);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));

        cartService.updateItem(100L, 5, "buyer@test.com");

        assertThat(cartItem.getQuantity()).isEqualTo(5);
    }

    @Test
    void updateItem_throws_when_stock_insufficient() {
        product.setStockQuantity(3);
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByIdAndUserId(100L, 1L)).willReturn(Optional.of(cartItem));

        assertThatThrownBy(() -> cartService.updateItem(100L, 10, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void updateItem_throws_not_found_for_other_users_item() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByIdAndUserId(100L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(100L, 1, "buyer@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── removeItem ────────────────────────────────────────────

    @Test
    void removeItem_deletes_the_item() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByIdAndUserId(100L, 1L)).willReturn(Optional.of(cartItem));
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of());

        CartDTO cart = cartService.removeItem(100L, "buyer@test.com");

        then(cartItemRepository).should().delete(cartItem);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void removeItem_throws_not_found_for_other_users_item() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByIdAndUserId(99L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(99L, "buyer@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── clearCart ─────────────────────────────────────────────

    @Test
    void clearCart_deletes_all_items_for_user() {
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);

        cartService.clearCart("buyer@test.com");

        then(cartItemRepository).should().deleteAllByUserId(1L);
    }

    // ── CartDTO.hasUnavailableItems ───────────────────────────

    @Test
    void cart_flags_unavailable_items() {
        product.setIsAvailable(false);
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));

        CartDTO cart = cartService.getCart("buyer@test.com");

        assertThat(cart.isHasUnavailableItems()).isTrue();
    }

    @Test
    void cart_flags_items_with_insufficient_stock() {
        product.setStockQuantity(1);
        cartItem.setQuantity(3);
        given(userService.getUserByEmail("buyer@test.com")).willReturn(user);
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(cartItem));

        CartDTO cart = cartService.getCart("buyer@test.com");

        assertThat(cart.isHasUnavailableItems()).isTrue();
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
