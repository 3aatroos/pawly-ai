package com.pawly.dto.store;

import com.pawly.entity.Payment;
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
public class PaymentDTO {

    private Long id;
    private Payment.PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String stripePaymentIntentId;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static PaymentDTO from(Payment p) {
        return PaymentDTO.builder()
                .id(p.getId())
                .status(p.getStatus())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .stripePaymentIntentId(p.getStripePaymentIntentId())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
