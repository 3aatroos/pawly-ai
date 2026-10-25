package com.pawly.controller.store;

import com.pawly.dto.store.ProductRecommendationDTO;
import com.pawly.service.store.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authenticated recommendations.
 * History is loaded here, scoring is delegated to ai-pawly, and the response
 * is a ProductRecommendationDTO list with no identity fields.
 */
@RestController
@RequestMapping("/api/store/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<List<ProductRecommendationDTO>> mine(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(recommendationService.recommend(principal.getUsername(), limit));
    }
}
