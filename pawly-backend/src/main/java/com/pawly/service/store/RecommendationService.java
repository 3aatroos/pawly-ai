package com.pawly.service.store;

import com.pawly.dto.store.ProductRecommendationDTO;
import com.pawly.entity.CartItem;
import com.pawly.entity.Pet;
import com.pawly.entity.Product;
import com.pawly.entity.ProductPurchaseHistory;
import com.pawly.entity.ProductReview;
import com.pawly.entity.ProductView;
import com.pawly.entity.Recommendation;
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
import com.pawly.config.AiClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.pawly.service.store.RecommendationModels.HistoryView;
import static com.pawly.service.store.RecommendationModels.ProfileView;
import static com.pawly.service.store.RecommendationModels.ScoreRequestView;
import static com.pawly.service.store.RecommendationModels.ScoredView;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final ProductRepository productRepository;
    private final ProductViewRepository productViewRepository;
    private final ProductPurchaseHistoryRepository purchaseHistoryRepository;
    private final ProductReviewRepository reviewRepository;
    private final CartItemRepository cartItemRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationContextBuilder contextBuilder;
    private final AiRecommendationClient aiClient;
    private final ContentBasedRanker contentBasedRanker;
    private final AiClientProperties aiClientProperties;

    @Transactional
    public List<ProductRecommendationDTO> recommend(String email, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 12));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Pet> pets = petRepository.findByOwnerId(user.getId());
        List<ProductPurchaseHistory> purchases = purchaseHistoryRepository.findByUserIdOrderByPurchasedAtDesc(user.getId());
        List<HistoryView> history = history(user.getId());

        // Without pets or any interaction history the engine has nothing to personalise on.
        // Return empty so the frontend can prompt the user to add pets / browse the store.
        if (pets.isEmpty() && history.isEmpty()) {
            return List.of();
        }

        ProfileView profile = contextBuilder.profile(pets, RecommendationContextBuilder.pricesOf(purchases));
        Set<String> purchasedIds = purchasedIds(purchases);
        List<Product> candidates = selectCandidates(profile.petSpecies(), purchasedIds);
        if (candidates.isEmpty()) {
            return List.of();
        }

        int window = Math.max(1, Math.min(aiClientProperties.getMaxCandidates(), 12));
        if (candidates.size() > window) {
            candidates = new ArrayList<>(candidates.subList(0, window));
        }
        ScoreRequestView request = contextBuilder.request(profile, history, candidates, limit);
        Set<String> allowed = new HashSet<>();
        for (Product product : candidates) {
            allowed.add(String.valueOf(product.getId()));
        }

        List<ScoredView> scored = aiClient.score(request, allowed)
                .orElseGet(() -> contentBasedRanker.rank(request));
        if (scored.size() > limit) {
            scored = scored.subList(0, limit);
        }
        scored = scored.stream()
                .map(item -> new ScoredView(
                        item.productId(),
                        item.score(),
                        item.relevanceClass(),
                        RecommendationValidator.cleanReason(item.justification()),
                        item.source()))
                .toList();
        persist(user, scored, candidates);
        return toDto(scored, candidates);
    }

    private List<HistoryView> history(Long userId) {
        List<EventHolder> events = new ArrayList<>();
        for (ProductPurchaseHistory purchase : purchaseHistoryRepository.findByUserIdOrderByPurchasedAtDesc(userId)) {
            if (purchase.getProduct() == null) {
                continue;
            }
            events.add(event(purchase.getProduct(), "purchase", 1.0, purchase.getPurchasedAt()));
        }
        for (CartItem item : cartItemRepository.findByUserIdOrderByAddedAtDesc(userId)) {
            events.add(event(item.getProduct(), "cart", 0.7, item.getAddedAt()));
        }
        for (ProductReview review : reviewRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)) {
            if (review.getRating() != null && review.getRating() >= 4) {
                events.add(event(review.getProduct(), "review_pos", 0.9, review.getCreatedAt()));
            } else if (review.getRating() != null && review.getRating() <= 2) {
                events.add(event(review.getProduct(), "review_neg", -0.5, review.getCreatedAt()));
            }
        }
        for (ProductView view : productViewRepository.findTop20ByUserIdOrderByViewedAtDesc(userId)) {
            if (view.getProduct() != null) {
                events.add(event(view.getProduct(), "view", 0.3, view.getViewedAt()));
            }
        }
        events.sort(Comparator.comparing(EventHolder::at, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        Map<Long, EventHolder> latest = new LinkedHashMap<>();
        for (EventHolder item : events) {
            latest.putIfAbsent(item.productId(), item);
            if (latest.size() == 10) {
                break;
            }
        }
        List<HistoryView> history = new ArrayList<>();
        for (EventHolder item : latest.values()) {
            history.add(new HistoryView(
                    String.valueOf(item.productId()),
                    item.name(),
                    item.signal(),
                    item.weight(),
                    item.category(),
                    item.species()
            ));
        }
        return history;
    }

    private EventHolder event(Product product, String signal, double weight, LocalDateTime at) {
        return new EventHolder(
                product.getId(),
                product.getName(),
                signal,
                weight,
                product.getCategory() == null ? "" : product.getCategory().getName(),
                product.getTargetSpecies() == null ? "" : product.getTargetSpecies(),
                at
        );
    }

    private record EventHolder(Long productId, String name, String signal, double weight, String category,
                               String species, LocalDateTime at) {
    }

    private Set<String> purchasedIds(List<ProductPurchaseHistory> purchases) {
        Set<String> ids = new HashSet<>();
        for (ProductPurchaseHistory purchase : purchases) {
            if (purchase.getProduct() != null && purchase.getProduct().getId() != null) {
                ids.add(String.valueOf(purchase.getProduct().getId()));
            }
        }
        return ids;
    }

    private List<Product> selectCandidates(List<String> species, Set<String> purchasedIds) {
        Set<String> wanted = new HashSet<>();
        if (species != null) {
            for (String value : species) {
                if (value != null && !value.isBlank()) {
                    wanted.add(value.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        wanted.add("all");
        List<Product> preferred = new ArrayList<>();
        List<Product> others = new ArrayList<>();
        for (Product product : productRepository.findByIsAvailableTrue()) {
            if (product.getId() != null && purchasedIds.contains(String.valueOf(product.getId()))) {
                continue;
            }
            String target = product.getTargetSpecies() == null ? "" : product.getTargetSpecies().trim().toLowerCase(Locale.ROOT);
            if (wanted.contains(target)) {
                preferred.add(product);
            } else {
                others.add(product);
            }
        }
        Comparator<Product> byRating = Comparator
                .comparing((Product product) -> product.getAverageRating() == null ? BigDecimal.ZERO : product.getAverageRating())
                .reversed();
        preferred.sort(byRating);
        others.sort(byRating);
        List<Product> ordered = new ArrayList<>(preferred);
        ordered.addAll(others);
        return ordered;
    }

    private void persist(User user, List<ScoredView> scored, List<Product> candidates) {
        Map<String, Product> byId = new LinkedHashMap<>();
        for (Product product : candidates) {
            byId.put(String.valueOf(product.getId()), product);
        }
        recommendationRepository.deleteAllByUserId(user.getId());
        recommendationRepository.flush();
        for (ScoredView item : scored) {
            Product product = byId.get(item.productId());
            if (product == null) {
                continue;
            }
            recommendationRepository.save(Recommendation.builder()
                    .user(user)
                    .product(product)
                    .score(BigDecimal.valueOf(item.score()).setScale(4, RoundingMode.HALF_UP))
                    .reason(item.justification())
                    .build());
        }
    }

    private List<ProductRecommendationDTO> toDto(List<ScoredView> scored, List<Product> candidates) {
        Map<String, Product> byId = new LinkedHashMap<>();
        for (Product product : candidates) {
            byId.put(String.valueOf(product.getId()), product);
        }
        List<ProductRecommendationDTO> result = new ArrayList<>();
        for (ScoredView item : scored) {
            Product product = byId.get(item.productId());
            if (product == null) {
                continue;
            }
            result.add(ProductRecommendationDTO.from(
                    product,
                    BigDecimal.valueOf(item.score()).setScale(4, RoundingMode.HALF_UP),
                    item.justification(),
                    item.source()
            ));
        }
        return result;
    }
}
