package com.pawly.store;

import com.pawly.entity.*;
import com.pawly.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CartItemRepositoryTest {

    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCategoryRepository categoryRepository;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("buyer@test.com").passwordHash("hash")
                .firstName("Buyer").lastName("Test")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE)
                .build());

        User admin = userRepository.save(User.builder()
                .email("admin2@test.com").passwordHash("hash")
                .firstName("Admin").lastName("Two")
                .role(User.Role.ADMINISTRATOR).status(User.UserStatus.ACTIVE)
                .build());

        ProductCategory category = categoryRepository.save(
                ProductCategory.builder().name("Food").isActive(true).build());

        product = productRepository.save(Product.builder()
                .category(category).createdBy(admin)
                .name("Cat Food").price(new BigDecimal("15.00"))
                .stockQuantity(20).isAvailable(true).build());
    }

    @Test
    void save_cart_item_persists_correctly() {
        CartItem item = cartItemRepository.save(CartItem.builder()
                .user(user).product(product).quantity(2).build());

        assertThat(item.getId()).isNotNull();
        assertThat(item.getAddedAt()).isNotNull();
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    void findByUserIdOrderByAddedAtDesc_returns_user_cart() {
        cartItemRepository.save(CartItem.builder().user(user).product(product).quantity(1).build());

        List<CartItem> cart = cartItemRepository.findByUserIdOrderByAddedAtDesc(user.getId());

        assertThat(cart).hasSize(1);
        assertThat(cart.get(0).getProduct().getName()).isEqualTo("Cat Food");
    }

    @Test
    void findByUserIdAndProductId_returns_existing_item() {
        cartItemRepository.save(CartItem.builder().user(user).product(product).quantity(3).build());

        Optional<CartItem> found = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(3);
    }

    @Test
    void deleteAllByUserId_clears_all_cart_items() {
        cartItemRepository.save(CartItem.builder().user(user).product(product).quantity(1).build());

        cartItemRepository.deleteAllByUserId(user.getId());

        assertThat(cartItemRepository.findByUserIdOrderByAddedAtDesc(user.getId())).isEmpty();
    }

    @Test
    void unique_constraint_prevents_duplicate_product_in_cart() {
        cartItemRepository.save(CartItem.builder().user(user).product(product).quantity(1).build());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            cartItemRepository.saveAndFlush(
                    CartItem.builder().user(user).product(product).quantity(2).build());
        });
    }
}
