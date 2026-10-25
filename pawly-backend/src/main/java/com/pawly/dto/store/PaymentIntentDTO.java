package com.pawly.dto.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Returned to the Angular client so it can mount the Stripe.js Element.
 * Never contains the secret key — only the client_secret of the PaymentIntent,
 * which Stripe.js needs to confirm the payment client-side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentDTO {

    private Long orderId;
    private String clientSecret;
    private String paymentIntentId;
    private BigDecimal amount;
    private String currency;
}
