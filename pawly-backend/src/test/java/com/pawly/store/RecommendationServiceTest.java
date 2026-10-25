package com.pawly.store;

import com.pawly.config.AiClientProperties;
import com.pawly.dto.store.ProductRecommendationDTO;
import com.pawly.entity.Pet;
import com.pawly.entity.Product;
import com.pawly.entity.ProductCategory;
import com.pawly.entity.ProductPurchaseHistory;
import com.pawly.entity.User;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.CartItemRepository;
import com.pawly.repository.PetRepository;
import com.pawly.repository.ProductPurchaseHistoryRepository;
import com.pawly.repository.ProductRepository;
import com.pawly.repository.ProductReviewRepository;
import com.pawly.repository.ProductViewRepository;
import com.pawly.repository.RecommendationRepository;
import com.pawly.repository.UserRepository;
import com.pawly.service.store.AiRecommendationClient;
import com.pawly.service.store.ContentBasedRanker;
import com.pawly.service.store.RecommendationContextBuilder;
import com.pawly.service.store.RecommendationModels.ScoredView;
import com.pawly.service.store.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Recommendation scoring with the AI client mocked. H2 is not involved:
 * the content-based fallback is the real Java ranker.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PetRepository petRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductViewRepository productViewRepository;
    @Mock private ProductPurchaseHistoryRepository purchaseHistoryRepository;
    @Mock private ProductReviewRepository reviewRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private AiRecommendationClient aiClient;

    private RecommendationService service;
    private User owner;
    private Product dogProduct;
    private Product catProduct;

    @BeforeEach
    void setUp() {
        AiClientProperties properties = new AiClientProperties();
        properties.setMaxCandidates(8);
        service = new RecommendationService(
                userRepository,
                petRepository,
                productRepository,
                productViewRepository,
                purchaseHistoryRepository,
                reviewRepository,
                cartItemRepository,
                recommendationRepository,
                new RecommendationContextBuilder(),
                aiClient,
                new ContentBasedRanker(),
                properties);

        owner = User.builder()
                .email("owner@store.test")
                .passwordHash("hash")
                .firstName("Owner")
                .lastName("User")
                .role(User.Role.PET_OWNER)
                .status(User.UserStatus.ACTIVE)
                .build();
        setField(owner, "id", 3L);

        ProductCategory category = ProductCategory.builder().name("Food").isActive(true).build();
        setField(category, "id", 1L);
        dogProduct = product(5L, "Dog Kibble", "dog", new BigDecimal("5.00"), category);
        catProduct = product(7L, "Cat Litter", "cat", BigDecimal.ZERO, category);
    }

    @Test
    void recommend_uses_the_mocked_ai_score_and_ignores_unknown_products() {
        stubCatalogue(List.of(dogProduct, catProduct));
        given(aiClient.score(any(), anySet())).willReturn(Optional.of(List.of(
                new ScoredView("5", 0.91, "highly_relevant", "Species match.", "qlora"),
                new ScoredView("999", 0.99, "highly_relevant", "Outside the catalogue", "qlora"))));

        List<ProductRecommendationDTO> result = service.recommend("owner@store.test", 8);

        assertThat(result).extracting(ProductRecommendationDTO::getProductId).containsExactly(5L);
        assertThat(result.get(0).getSource()).isEqualTo("qlora");
        assertThat(result.get(0).getName()).isEqualTo("Dog Kibble");
        verify(recommendationRepository).deleteAllByUserId(3L);
    }

    @Test
    void recommend_falls_back_to_content_ranker_when_ai_returns_nothing() {
        stubCatalogue(List.of(dogProduct, catProduct));
        given(aiClient.score(any(), anySet())).willReturn(Optional.empty());

        List<ProductRecommendationDTO> result = service.recommend("owner@store.test", 8);

        assertThat(result.get(0).getProductId()).isEqualTo(5L);
        assertThat(result.get(0).getSource()).isEqualTo("content_based");
        assertThat(result.get(0).getScore()).isGreaterThan(result.get(1).getScore());
    }

    @Test
    void recommend_does_not_call_ai_when_no_candidate_remains() {
        ProductPurchaseHistory purchase = ProductPurchaseHistory.builder()
                .user(owner)
                .product(dogProduct)
                .productName(dogProduct.getName())
                .quantity(1)
                .unitPrice(dogProduct.getPrice())
                .build();
        given(userRepository.findByEmail("owner@store.test")).willReturn(Optional.of(owner));
        given(petRepository.findByOwnerId(3L)).willReturn(List.of(dog()));
        given(purchaseHistoryRepository.findByUserIdOrderByPurchasedAtDesc(3L)).willReturn(List.of(purchase));
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(3L)).willReturn(List.of());
        given(reviewRepository.findTop20ByUserIdOrderByCreatedAtDesc(3L)).willReturn(List.of());
        given(productViewRepository.findTop20ByUserIdOrderByViewedAtDesc(3L)).willReturn(List.of());
        given(productRepository.findByIsAvailableTrue()).willReturn(List.of(dogProduct));

        assertThat(service.recommend("owner@store.test", 8)).isEmpty();
        verify(aiClient, never()).score(any(), anySet());
    }

    @Test
    void recommend_throws_when_the_caller_does_not_exist() {
        given(userRepository.findByEmail("missing@store.test")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.recommend("missing@store.test", 8))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(aiClient, never()).score(any(), any());
    }

    private void stubCatalogue(List<Product> products) {
        given(userRepository.findByEmail("owner@store.test")).willReturn(Optional.of(owner));
        given(petRepository.findByOwnerId(3L)).willReturn(List.of(dog()));
        given(purchaseHistoryRepository.findByUserIdOrderByPurchasedAtDesc(3L)).willReturn(List.of());
        given(cartItemRepository.findByUserIdOrderByAddedAtDesc(3L)).willReturn(List.of());
        given(reviewRepository.findTop20ByUserIdOrderByCreatedAtDesc(3L)).willReturn(List.of());
        given(productViewRepository.findTop20ByUserIdOrderByViewedAtDesc(3L)).willReturn(List.of());
        given(productRepository.findByIsAvailableTrue()).willReturn(products);
    }

    private Pet dog() {
        return Pet.builder()
                .owner(owner)
                .name("Rex")
                .species("dog")
                .gender(Pet.PetGender.UNKNOWN)
                .build();
    }

    private Product product(Long id, String name, String species, BigDecimal rating, ProductCategory category) {
        Product product = Product.builder()
                .category(category)
                .createdBy(owner)
                .name(name)
                .description(name)
                .price(new BigDecimal("19.99"))
                .stockQuantity(4)
                .isAvailable(true)
                .targetSpecies(species)
                .averageRating(rating)
                .reviewCount(rating.signum() > 0 ? 4 : 0)
                .build();
        setField(product, "id", id);
        return product;
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
