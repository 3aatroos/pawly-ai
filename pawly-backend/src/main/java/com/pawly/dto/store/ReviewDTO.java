package com.pawly.dto.store;

import com.pawly.entity.ProductReview;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {

    private Long id;
    private Long productId;
    private Long userId;
    private String userFirstName;
    private String userLastName;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    private String comment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReviewDTO from(ProductReview r) {
        return ReviewDTO.builder()
                .id(r.getId())
                .productId(r.getProduct().getId())
                .userId(r.getUser().getId())
                .userFirstName(r.getUser().getFirstName())
                .userLastName(r.getUser().getLastName())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
