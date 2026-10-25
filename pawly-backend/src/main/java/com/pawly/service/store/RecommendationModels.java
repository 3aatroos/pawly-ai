package com.pawly.service.store;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JSON exchanged with the Python scorer. Field names are the contract in
 * ai-pawly/recommendations/API.md. There is no user, pet, or contact field.
 */
public final class RecommendationModels {

    private RecommendationModels() {
    }

    public record ProfileView(
            @JsonProperty("pet_species") List<String> petSpecies,
            @JsonProperty("pet_ages") List<String> petAges,
            @JsonProperty("budget_tier") String budgetTier
    ) {
    }

    public record HistoryView(
            @JsonProperty("product_id") String productId,
            @JsonProperty("product_name") String productName,
            String signal,
            double weight,
            String category,
            String species
    ) {
    }

    public record CandidateView(
            @JsonProperty("product_id") String productId,
            String name,
            String category,
            String species,
            @JsonProperty("price_eur") double priceEur,
            String description,
            @JsonProperty("avg_rating") double avgRating,
            @JsonProperty("review_count") int reviewCount
    ) {
    }

    public record ScoreRequestView(
            ProfileView profile,
            List<HistoryView> history,
            List<CandidateView> candidates,
            int limit
    ) {
    }

    public record ScoredView(
            String productId,
            double score,
            String relevanceClass,
            String justification,
            String source
    ) {
    }
}
