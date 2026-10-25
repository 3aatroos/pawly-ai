package com.pawly.dto.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pawly.entity.Product;
import com.pawly.entity.ProductImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Recommendation returned to Angular. Stock, owner identity, and pet identity
 * are intentionally absent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductRecommendationDTO {

    private Long productId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private BigDecimal price;
    private Boolean isAvailable;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private String targetSpecies;
    private String primaryImageUrl;
    private BigDecimal score;
    private String reason;
    private String source;

    public static ProductRecommendationDTO from(Product product, BigDecimal score, String reason, String source) {
        return ProductRecommendationDTO.builder()
                .productId(product.getId())
                .categoryId(product.getCategory() == null ? null : product.getCategory().getId())
                .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
                .name(product.getName())
                .price(product.getPrice())
                .isAvailable(product.getIsAvailable())
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .targetSpecies(product.getTargetSpecies())
                .primaryImageUrl(primaryImage(product))
                .score(score)
                .reason(reason)
                .source(source)
                .build();
    }

    private static String primaryImage(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }
        return product.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(product.getImages().get(0).getImageUrl());
    }
}
