package com.pawly.dto.store;

import com.pawly.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full product detail, including images and creator info.
 * stockQuantity is always present here — the controller guards who can call GET /products/{id}.
 * For public detail view, stock is shown as a boolean availability indicator via isAvailable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private Long createdById;
    private String createdByName;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private Boolean isAvailable;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private String targetSpecies;
    private List<ProductImageDTO> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductDTO from(Product p) {
        return ProductDTO.builder()
                .id(p.getId())
                .categoryId(p.getCategory().getId())
                .categoryName(p.getCategory().getName())
                .createdById(p.getCreatedBy().getId())
                .createdByName(p.getCreatedBy().getFullName())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                .isAvailable(p.getIsAvailable())
                .averageRating(p.getAverageRating())
                .reviewCount(p.getReviewCount())
                .targetSpecies(p.getTargetSpecies())
                .images(p.getImages().stream().map(ProductImageDTO::from).toList())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
