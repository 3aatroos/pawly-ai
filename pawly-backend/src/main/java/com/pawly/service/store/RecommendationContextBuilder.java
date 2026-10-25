package com.pawly.service.store;

import com.pawly.entity.Pet;
import com.pawly.entity.Product;
import com.pawly.entity.ProductPurchaseHistory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.pawly.service.store.RecommendationModels.CandidateView;
import static com.pawly.service.store.RecommendationModels.HistoryView;
import static com.pawly.service.store.RecommendationModels.ProfileView;
import static com.pawly.service.store.RecommendationModels.ScoreRequestView;

/**
 * Builds the minimal context sent to Python. Pet names, health notes,
 * microchips, emails, and phone numbers are never read into the payload.
 */
@Component
public class RecommendationContextBuilder {

    public ProfileView profile(List<Pet> pets, List<BigDecimal> purchasePrices) {
        List<String> species = new ArrayList<>();
        List<String> ages = new ArrayList<>();
        if (pets != null) {
            for (Pet pet : pets) {
                if (pet.getSpecies() != null && !pet.getSpecies().isBlank()) {
                    String normalized = pet.getSpecies().trim().toLowerCase(Locale.ROOT);
                    if (!species.contains(normalized)) {
                        species.add(normalized);
                    }
                }
                String age = ageBand(pet.getDateOfBirth());
                if (age != null && !ages.contains(age)) {
                    ages.add(age);
                }
            }
        }
        return new ProfileView(species, ages, budgetTier(purchasePrices));
    }

    public ScoreRequestView request(ProfileView profile, List<HistoryView> history, List<Product> products, int limit) {
        List<CandidateView> candidates = new ArrayList<>();
        for (Product product : products) {
            candidates.add(new CandidateView(
                    String.valueOf(product.getId()),
                    product.getName(),
                    product.getCategory() == null ? "" : product.getCategory().getName(),
                    product.getTargetSpecies() == null ? "" : product.getTargetSpecies(),
                    product.getPrice() == null ? 0.0 : product.getPrice().doubleValue(),
                    product.getDescription() == null ? "" : product.getDescription(),
                    product.getAverageRating() == null ? 0.0 : product.getAverageRating().doubleValue(),
                    product.getReviewCount() == null ? 0 : product.getReviewCount()
            ));
        }
        return new ScoreRequestView(profile, history, candidates, limit);
    }

    public static String budgetTier(List<BigDecimal> prices) {
        if (prices == null || prices.isEmpty()) {
            return "unknown";
        }
        List<BigDecimal> sorted = new ArrayList<>(prices);
        sorted.sort(Comparator.naturalOrder());
        BigDecimal median = sorted.get(sorted.size() / 2);
        if (median.compareTo(new BigDecimal("15")) < 0) {
            return "budget";
        }
        if (median.compareTo(new BigDecimal("40")) < 0) {
            return "mid";
        }
        return "premium";
    }

    static String ageBand(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        int years = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (years < 1) {
            return "puppy";
        }
        if (years < 8) {
            return "adult";
        }
        return "senior";
    }

    public static List<BigDecimal> pricesOf(List<ProductPurchaseHistory> purchases) {
        List<BigDecimal> prices = new ArrayList<>();
        if (purchases == null) {
            return prices;
        }
        for (ProductPurchaseHistory purchase : purchases) {
            if (purchase.getUnitPrice() != null) {
                prices.add(purchase.getUnitPrice());
            }
        }
        return prices;
    }
}
