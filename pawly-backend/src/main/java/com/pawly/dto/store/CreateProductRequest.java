package com.pawly.dto.store;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Product name must not exceed 200 characters")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    private BigDecimal price;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity = 0;

    private Boolean isAvailable = true;

    @Size(max = 50, message = "Target species must not exceed 50 characters")
    private String targetSpecies;

    private List<ProductImageDTO> images = new ArrayList<>();
}
