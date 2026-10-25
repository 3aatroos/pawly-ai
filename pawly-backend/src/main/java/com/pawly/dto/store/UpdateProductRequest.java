package com.pawly.dto.store;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateProductRequest {

    @Size(max = 200, message = "Product name must not exceed 200 characters")
    private String name;

    private String description;

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    private BigDecimal price;

    private Long categoryId;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    private Boolean isAvailable;

    @Size(max = 50, message = "Target species must not exceed 50 characters")
    private String targetSpecies;

    private List<ProductImageDTO> images;
}
