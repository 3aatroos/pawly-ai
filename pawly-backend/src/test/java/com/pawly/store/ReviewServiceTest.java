package com.pawly.store;

import com.pawly.dto.store.ReviewDTO;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.*;
import com.pawly.service.UserService;
import com.pawly.service.store.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ProductReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserService userService;

    @InjectMocks private ReviewService reviewService;

    private User reviewer;
    private Product product;
    private ProductReview existingReview;

    @BeforeEach
    void setUp() {
        reviewer = User.builder()
                .email("user@test.com").passwordHash("hash")
                .firstName("Jane").lastName("Doe")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build();
        setField(reviewer, "id", 1L);

        ProductCategory cat = ProductCategory.builder().name("Food").isActive(true).build();
        setField(cat, "id", 1L);

        User admin = User.builder().email("a@a.com").passwordHash("h")
                .firstName("A").lastName("B").role(User.Role.ADMINISTRATOR)
                .status(User.UserStatus.ACTIVE).build();
        setField(admin, "id", 2L);

        product = Product.builder()
                .category(cat).createdBy(admin)
                .name("Dog Food").price(new BigDecimal("10.00"))
                .stockQuantity(5).isAvailable(true).build();
        setField(product, "id", 10L);
        setField(product, "version", 0L);

        existingReview = ProductReview.builder()
                .product(product).user(reviewer).rating(4).comment("Good").build();
        setField(existingReview, "id", 100L);
    }

    @Test
    void create_saves_review_and_refreshes_rating() {
        ReviewDTO dto = ReviewDTO.builder().rating(5).comment("Excellent").build();

        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        given(userService.getUserByEmail("user@test.com")).willReturn(reviewer);
        given(reviewRepository.existsByProductIdAndUserId(10L, 1L)).willReturn(false);
        given(reviewRepository.save(any())).willReturn(existingReview);

        ReviewDTO result = reviewService.create(10L, dto, "user@test.com");

        assertThat(result).isNotNull();
        then(productRepository).should().refreshRating(10L);
    }

    @Test
    void create_throws_when_user_already_reviewed() {
        ReviewDTO dto = ReviewDTO.builder().rating(3).build();

        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        given(userService.getUserByEmail("user@test.com")).willReturn(reviewer);
        given(reviewRepository.existsByProductIdAndUserId(10L, 1L)).willReturn(true);

        assertThatThrownBy(() -> reviewService.create(10L, dto, "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void update_succeeds_for_review_owner() {
        ReviewDTO dto = ReviewDTO.builder().rating(5).comment("Changed mind").build();

        given(reviewRepository.findById(100L)).willReturn(Optional.of(existingReview));
        given(userService.getUserByEmail("user@test.com")).willReturn(reviewer);
        given(reviewRepository.save(any())).willReturn(existingReview);

        ReviewDTO result = reviewService.update(10L, 100L, dto, "user@test.com", false);

        assertThat(result).isNotNull();
        then(productRepository).should().refreshRating(10L);
    }

    @Test
    void update_throws_access_denied_for_other_user() {
        User other = User.builder().email("x@x.com").passwordHash("h")
                .firstName("X").lastName("Y").role(User.Role.PET_OWNER)
                .status(User.UserStatus.ACTIVE).build();
        setField(other, "id", 99L);

        ReviewDTO dto = ReviewDTO.builder().rating(1).build();

        given(reviewRepository.findById(100L)).willReturn(Optional.of(existingReview));
        given(userService.getUserByEmail("x@x.com")).willReturn(other);

        assertThatThrownBy(() -> reviewService.update(10L, 100L, dto, "x@x.com", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_succeeds_for_admin_on_any_review() {
        given(reviewRepository.findById(100L)).willReturn(Optional.of(existingReview));

        reviewService.delete(10L, 100L, "admin@test.com", true);

        then(reviewRepository).should().delete(existingReview);
        then(productRepository).should().refreshRating(10L);
    }

    @Test
    void delete_throws_when_review_belongs_to_different_product() {
        Product otherProduct = Product.builder()
                .category(product.getCategory()).createdBy(product.getCreatedBy())
                .name("Other").price(BigDecimal.ONE).stockQuantity(0).isAvailable(true).build();
        setField(otherProduct, "id", 999L);
        setField(otherProduct, "version", 0L);

        ProductReview wrongReview = ProductReview.builder()
                .product(otherProduct).user(reviewer).rating(3).build();
        setField(wrongReview, "id", 200L);

        given(reviewRepository.findById(200L)).willReturn(Optional.of(wrongReview));

        assertThatThrownBy(() -> reviewService.delete(10L, 200L, "admin@test.com", true))
                .isInstanceOf(ResourceNotFoundException.class);
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
