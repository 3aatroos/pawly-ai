package com.pawly.dto.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartDTO {

    private List<CartItemDTO> items;
    private int itemCount;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal total;
    private boolean hasUnavailableItems;

    /** Standard shipping fee in EUR. Free above FREE_THRESHOLD. */
    public static final BigDecimal SHIPPING_FEE      = new BigDecimal("5.00");
    public static final BigDecimal FREE_THRESHOLD    = new BigDecimal("50.00");

    public static CartDTO from(List<CartItemDTO> items) {
        BigDecimal subtotal = items.stream()
                .map(CartItemDTO::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingFee = subtotal.signum() == 0 || subtotal.compareTo(FREE_THRESHOLD) >= 0
                ? BigDecimal.ZERO
                : SHIPPING_FEE;

        boolean hasUnavailable = items.stream()
                .anyMatch(i -> Boolean.FALSE.equals(i.getProductAvailable())
                            || i.getProductStockQuantity() < i.getQuantity());

        return CartDTO.builder()
                .items(items)
                .itemCount(items.stream().mapToInt(CartItemDTO::getQuantity).sum())
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .total(subtotal.add(shippingFee))
                .hasUnavailableItems(hasUnavailable)
                .build();
    }
}
