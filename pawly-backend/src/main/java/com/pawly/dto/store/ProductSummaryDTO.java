package com.pawly.dto.store;

import com.pawly.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight projection used in list/search results.
 * Exposes stock only to callers with appropriate role — the controller decides.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryDTO {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private BigDecimal price;
    private Boolean isAvailable;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private String targetSpecies;
    private String primaryImageUrl;
    private LocalDateTime createdAt;

    // Exposed only to VETERINARIAN and ADMINISTRATOR
    private Integer stockQuantity;

    public static ProductSummaryDTO from(Product p, boolean includeStock) {
        String primaryImageUrl = p.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .map(img -> img.getImageUrl())
                .orElse(p.getImages().isEmpty() ? null : p.getImages().get(0).getImageUrl());

        return ProductSummaryDTO.builder()
                .id(p.getId())
                .categoryId(p.getCategory().getId())
                .categoryName(p.getCategory().getName())
                .name(p.getName())
                .price(p.getPrice())
                .isAvailable(p.getIsAvailable())
                .averageRating(p.getAverageRating())
                .reviewCount(p.getReviewCount())
                .targetSpecies(p.getTargetSpecies())
                .primaryImageUrl(primaryImageUrl)
                .createdAt(p.getCreatedAt())
                .stockQuantity(includeStock ? p.getStockQuantity() : null)
                .build();
    }
}
