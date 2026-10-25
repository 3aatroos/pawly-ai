package com.pawly.service.store;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static com.pawly.service.store.RecommendationModels.ScoredView;

/**
 * Drops scores that do not belong to the candidate set or leave the 0..1 range.
 * Justifications are shortened and stripped of contact-shaped text.
 */
@Component
public class RecommendationValidator {

    static final int MAX_REASON = 200;
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");
    private static final Set<String> SOURCES = Set.of("qlora", "content_based");

    public List<ScoredView> sanitize(List<ScoredView> incoming, Set<String> allowedIds, String source) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        String safeSource = SOURCES.contains(source) ? source : "content_based";
        List<ScoredView> kept = new ArrayList<>();
        for (ScoredView item : incoming) {
            if (item == null || item.productId() == null || !allowedIds.contains(item.productId())) {
                continue;
            }
            if (Double.isNaN(item.score()) || item.score() < 0.0 || item.score() > 1.0) {
                continue;
            }
            String reason = cleanReason(item.justification());
            kept.add(new ScoredView(
                    item.productId(),
                    item.score(),
                    item.relevanceClass() == null ? "" : item.relevanceClass(),
                    reason,
                    safeSource
            ));
        }
        kept.sort(Comparator.comparingDouble(ScoredView::score).reversed().thenComparing(ScoredView::productId));
        return kept;
    }

    static String cleanReason(String justification) {
        String text = justification == null ? "" : justification.trim();
        text = EMAIL.matcher(text).replaceAll("");
        text = LONG_DIGITS.matcher(text).replaceAll("");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            text = "Relevant to the catalogue profile.";
        }
        if (text.length() > MAX_REASON) {
            text = text.substring(0, MAX_REASON).trim();
        }
        return text;
    }
}
