package com.pawly.dto.store;

import com.pawly.entity.ProductImage;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageDTO {

    private Long id;

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private Boolean isPrimary;
    private Integer sortOrder;

    public static ProductImageDTO from(ProductImage img) {
        return ProductImageDTO.builder()
                .id(img.getId())
                .imageUrl(img.getImageUrl())
                .isPrimary(img.getIsPrimary())
                .sortOrder(img.getSortOrder())
                .build();
    }
}
