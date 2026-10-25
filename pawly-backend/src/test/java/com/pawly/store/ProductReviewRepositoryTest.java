package com.pawly.store;

import com.pawly.entity.*;
import com.pawly.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@ActiveProfiles("test")
class ProductReviewRepositoryTest {

    @Autowired private ProductReviewRepository reviewRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductCategoryRepository categoryRepository;

    private Product product;
    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.builder()
                .email("admin@test.com").passwordHash("hash")
                .firstName("Admin").lastName("Test")
                .role(User.Role.ADMINISTRATOR).status(User.UserStatus.ACTIVE).build());

        user1 = userRepository.save(User.builder()
                .email("user1@test.com").passwordHash("hash")
                .firstName("User").lastName("One")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        user2 = userRepository.save(User.builder()
                .email("user2@test.com").passwordHash("hash")
                .firstName("User").lastName("Two")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        ProductCategory cat = categoryRepository.save(
                ProductCategory.builder().name("Food").isActive(true).build());

        product = productRepository.save(Product.builder()
                .category(cat).createdBy(admin)
                .name("Dog Kibble").price(new BigDecimal("25.00"))
                .stockQuantity(50).isAvailable(true).build());
    }

    @Test
    void save_review_persists_correctly() {
        ProductReview review = reviewRepository.save(ProductReview.builder()
                .product(product).user(user1).rating(5).comment("Excellent!").build());

        assertThat(review.getId()).isNotNull();
        assertThat(review.getCreatedAt()).isNotNull();
    }

    @Test
    void findByProductIdOrderByCreatedAtDesc_returns_reviews_for_product() {
        reviewRepository.save(ProductReview.builder().product(product).user(user1).rating(4).build());
        reviewRepository.save(ProductReview.builder().product(product).user(user2).rating(5).build());

        Page<ProductReview> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(
                product.getId(), PageRequest.of(0, 10));

        assertThat(reviews.getTotalElements()).isEqualTo(2);
    }

    @Test
    void existsByProductIdAndUserId_returns_true_after_review() {
        reviewRepository.save(ProductReview.builder().product(product).user(user1).rating(3).build());

        assertThat(reviewRepository.existsByProductIdAndUserId(product.getId(), user1.getId())).isTrue();
        assertThat(reviewRepository.existsByProductIdAndUserId(product.getId(), user2.getId())).isFalse();
    }

    @Test
    void averageRatingByProductId_computes_correct_average() {
        reviewRepository.save(ProductReview.builder().product(product).user(user1).rating(4).build());
        reviewRepository.save(ProductReview.builder().product(product).user(user2).rating(2).build());

        Double avg = reviewRepository.averageRatingByProductId(product.getId());

        assertThat(avg).isCloseTo(3.0, within(0.001));
    }

    @Test
    void unique_constraint_prevents_duplicate_review() {
        reviewRepository.save(ProductReview.builder().product(product).user(user1).rating(5).build());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            reviewRepository.saveAndFlush(
                    ProductReview.builder().product(product).user(user1).rating(3).build());
        });
    }

    @Test
    void findByProductIdAndUserId_returns_existing_review() {
        reviewRepository.save(ProductReview.builder().product(product).user(user1).rating(4).comment("Good").build());

        Optional<ProductReview> found = reviewRepository.findByProductIdAndUserId(product.getId(), user1.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getComment()).isEqualTo("Good");
    }
}
