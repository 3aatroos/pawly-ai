package com.pawly.service.store;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.pawly.service.store.RecommendationModels.CandidateView;
import static com.pawly.service.store.RecommendationModels.HistoryView;
import static com.pawly.service.store.RecommendationModels.ScoreRequestView;
import static com.pawly.service.store.RecommendationModels.ScoredView;

/**
 * Same weights as ai-pawly/recommendations/content_based.py.
 * Used when the Python service cannot be reached after the configured retries.
 */
@Component
public class ContentBasedRanker {

    static final double BASE = 0.15;
    static final double SPECIES_MATCH = 0.35;
    static final double SPECIES_MISMATCH = -0.25;
    static final double CATEGORY_MATCH = 0.20;
    static final double CONTENT_MATCH = 0.25;
    static final double RATING_BONUS = 0.10;
    static final double PRIOR_POSITIVE = 0.10;
    static final double PRIOR_NEGATIVE = -0.20;

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final Set<String> POSITIVE = Set.of("view", "cart", "purchase", "review_pos");

    public List<ScoredView> rank(ScoreRequestView request) {
        Set<String> species = new HashSet<>();
        if (request.profile() != null && request.profile().petSpecies() != null) {
            for (String value : request.profile().petSpecies()) {
                if (value != null && !value.isBlank()) {
                    species.add(value.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        List<HistoryView> positive = new ArrayList<>();
        Set<String> negativeIds = new HashSet<>();
        Set<String> positiveIds = new HashSet<>();
        Set<String> categories = new HashSet<>();
        StringBuilder historyText = new StringBuilder();
        for (HistoryView item : request.history()) {
            if ("review_neg".equals(item.signal())) {
                negativeIds.add(item.productId());
            }
            if (POSITIVE.contains(item.signal()) && item.weight() > 0) {
                positive.add(item);
                positiveIds.add(item.productId());
                if (item.category() != null && !item.category().isBlank()) {
                    categories.add(item.category().trim().toLowerCase(Locale.ROOT));
                }
                historyText.append(' ').append(nullToEmpty(item.productName()))
                        .append(' ').append(nullToEmpty(item.category()))
                        .append(' ').append(nullToEmpty(item.species()));
            }
        }
        Map<String, Double> historyVector = tokens(historyText.toString());
        List<ScoredView> ranked = new ArrayList<>();
        for (CandidateView candidate : request.candidates()) {
            List<String> notes = new ArrayList<>();
            double score = BASE;
            Boolean matched = speciesMatch(candidate, species);
            if (Boolean.TRUE.equals(matched)) {
                score += SPECIES_MATCH;
                notes.add("Species match (" + candidate.species().trim().toLowerCase(Locale.ROOT) + ").");
            } else if (Boolean.FALSE.equals(matched)) {
                score += SPECIES_MISMATCH;
                notes.add("Species differs from the profile (" + candidate.species().trim().toLowerCase(Locale.ROOT) + ").");
            }
            String category = nullToEmpty(candidate.category()).trim().toLowerCase(Locale.ROOT);
            if (!category.isEmpty() && categories.contains(category)) {
                score += CATEGORY_MATCH;
                notes.add("Category matches recent interest.");
            }
            double content = cosine(
                    tokens(nullToEmpty(candidate.name()) + " " + nullToEmpty(candidate.description()) + " " + nullToEmpty(candidate.category())),
                    historyVector
            );
            if (content > 0) {
                score += CONTENT_MATCH * content;
                notes.add("Similar to recently considered products.");
            }
            if (candidate.avgRating() > 0) {
                score += RATING_BONUS * (candidate.avgRating() / 5.0);
                notes.add(String.format(Locale.ROOT, "Rating %.1f/5.", candidate.avgRating()));
            }
            if (positiveIds.contains(candidate.productId())) {
                score += PRIOR_POSITIVE;
                notes.add("Already considered by the shopper.");
            }
            if (negativeIds.contains(candidate.productId())) {
                score += PRIOR_NEGATIVE;
                notes.add("Previous feedback was negative.");
            }
            if (notes.isEmpty()) {
                notes.add("Popular catalogue fallback.");
            }
            String justification = String.join(" ", notes);
            String[] words = justification.split("\\s+");
            if (words.length > 40) {
                justification = String.join(" ", java.util.Arrays.copyOf(words, 40));
            }
            double value = clamp(score);
            ranked.add(new ScoredView(
                    candidate.productId(),
                    value,
                    relevanceClass(value),
                    justification,
                    "content_based"
            ));
        }
        if (ranked.isEmpty()) {
            return List.of();
        }
        ranked.sort(Comparator.comparingDouble(ScoredView::score).reversed().thenComparing(ScoredView::productId));
        int limit = Math.min(Math.max(request.limit(), 1), ranked.size());
        return List.copyOf(ranked.subList(0, limit));
    }

    static String relevanceClass(double score) {
        if (score <= 0.25) return "not_relevant";
        if (score <= 0.50) return "somewhat";
        if (score <= 0.75) return "relevant";
        return "highly_relevant";
    }

    private static Boolean speciesMatch(CandidateView candidate, Set<String> species) {
        String target = nullToEmpty(candidate.species()).trim().toLowerCase(Locale.ROOT);
        if (target.isEmpty() || "all".equals(target) || species.isEmpty()) {
            return null;
        }
        return species.contains(target);
    }

    private static double clamp(double value) {
        double bounded = Math.min(1.0, Math.max(0.0, value));
        return Math.round(bounded * 10000.0) / 10000.0;
    }

    private static Map<String, Double> tokens(String text) {
        Map<String, Double> counts = new HashMap<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            counts.merge(matcher.group(), 1.0, Double::sum);
        }
        return counts;
    }

    private static double cosine(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (Map.Entry<String, Double> entry : left.entrySet()) {
            dot += entry.getValue() * right.getOrDefault(entry.getKey(), 0.0);
            leftNorm += entry.getValue() * entry.getValue();
        }
        for (double weight : right.values()) {
            rightNorm += weight * weight;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
