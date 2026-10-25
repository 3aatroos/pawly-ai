package com.pawly.dto.store;

import com.pawly.entity.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    private Long id;

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    private String name;

    private String description;
    private String imageUrl;
    private Boolean isActive;
    private Integer productCount;
    private LocalDateTime createdAt;

    public static CategoryDTO from(ProductCategory c) {
        return CategoryDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .imageUrl(c.getImageUrl())
                .isActive(c.getIsActive())
                .productCount(c.getProducts() != null ? c.getProducts().size() : 0)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
