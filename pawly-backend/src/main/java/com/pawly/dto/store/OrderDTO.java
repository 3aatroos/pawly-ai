package com.pawly.dto.store;

import com.pawly.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long id;

    // Buyer info — always visible to the buyer, and to admins
    private Long buyerId;
    private String buyerFirstName;
    private String buyerLastName;
    private String buyerEmail;

    private Order.OrderStatus status;
    private Order.PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private String shippingAddress;
    private String shippingCity;
    private String shippingCountry;
    private String notes;

    private List<OrderItemDTO> items;
    private PaymentDTO payment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderDTO from(Order o) {
        return OrderDTO.builder()
                .id(o.getId())
                .buyerId(o.getBuyer().getId())
                .buyerFirstName(o.getBuyer().getFirstName())
                .buyerLastName(o.getBuyer().getLastName())
                .buyerEmail(o.getBuyer().getEmail())
                .status(o.getStatus())
                .paymentMethod(o.getPaymentMethod())
                .totalAmount(o.getTotalAmount())
                .shippingFee(o.getShippingFee())
                .shippingAddress(o.getShippingAddress())
                .shippingCity(o.getShippingCity())
                .shippingCountry(o.getShippingCountry())
                .notes(o.getNotes())
                .items(o.getItems().stream().map(OrderItemDTO::from).toList())
                .payment(o.getPayment() != null ? PaymentDTO.from(o.getPayment()) : null)
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }
}
