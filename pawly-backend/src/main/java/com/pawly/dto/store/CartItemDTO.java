package com.pawly.dto.store;

import com.pawly.entity.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO {

    private Long id;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
    private Boolean productAvailable;
    private Integer productStockQuantity;
    private LocalDateTime addedAt;

    public static CartItemDTO from(CartItem item) {
        BigDecimal lineTotal = item.getProduct().getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        String imageUrl = item.getProduct().getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .map(img -> img.getImageUrl())
                .orElse(item.getProduct().getImages().isEmpty()
                        ? null : item.getProduct().getImages().get(0).getImageUrl());

        return CartItemDTO.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImageUrl(imageUrl)
                .unitPrice(item.getProduct().getPrice())
                .quantity(item.getQuantity())
                .lineTotal(lineTotal)
                .productAvailable(item.getProduct().getIsAvailable())
                .productStockQuantity(item.getProduct().getStockQuantity())
                .addedAt(item.getAddedAt())
                .build();
    }
}
