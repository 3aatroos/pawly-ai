package com.pawly.controller.store;

import com.pawly.dto.store.PaymentIntentDTO;
import com.pawly.service.store.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/store/payments/{orderId}/intent  — create / retrieve PaymentIntent (authenticated)
 * POST /api/store/payments/webhook           — Stripe webhook (public, signature-verified)
 */
@RestController
@RequestMapping("/api/store/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final StripeService stripeService;

    /**
     * Creates or returns the existing PaymentIntent for a Stripe order.
     * The clientSecret is safe to expose to the browser — it is not the secret key.
     */
    @PostMapping("/{orderId}/intent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentIntentDTO> createIntent(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails principal) {

        return ResponseEntity.ok(
                stripeService.createPaymentIntent(orderId, principal.getUsername()));
    }

    /**
     * Stripe webhook endpoint.
     *
     * Security:
     *  - Must be permitted in SecurityConfig (Stripe cannot send a JWT).
     *  - Authenticity is verified inside StripeService via Webhook.constructEvent().
     *  - The raw request body is required for signature verification — do NOT parse it
     *    with @RequestBody before passing to the service.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        stripeService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }
}
