package com.pawly.service.store;

import com.pawly.dto.store.PaymentIntentDTO;
import com.pawly.entity.Order;
import com.pawly.entity.Payment;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.OrderRepository;
import com.pawly.repository.PaymentRepository;
import com.pawly.service.UserService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final OrderRepository   orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserService       userService;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    /**
     * Create (or reuse) a Stripe PaymentIntent for the given order.
     *
     * Idempotency: if a PaymentIntent already exists for this order, return it.
     * This prevents duplicate charges when the user retries the checkout page.
     */
    @Transactional
    public PaymentIntentDTO createPaymentIntent(Long orderId, String callerEmail) {
        Order order = resolveOrderForCaller(orderId, callerEmail);

        if (order.getPaymentMethod() != Order.PaymentMethod.STRIPE) {
            throw new IllegalStateException("Order #" + orderId + " is not a Stripe order");
        }

        Payment payment = order.getPayment();
        if (payment == null) {
            throw new IllegalStateException("Payment record missing for order #" + orderId);
        }

        // Idempotency: reuse existing PaymentIntent if already created
        if (payment.getStripePaymentIntentId() != null) {
            log.info("Reusing existing PaymentIntent {} for order {}", payment.getStripePaymentIntentId(), orderId);
            return PaymentIntentDTO.builder()
                    .orderId(orderId)
                    .clientSecret(payment.getStripeClientSecret())
                    .paymentIntentId(payment.getStripePaymentIntentId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .build();
        }

        if (payment.getStatus() == Payment.PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Order #" + orderId + " is already paid");
        }

        // Convert EUR to cents — Stripe requires integer amount in smallest currency unit
        long amountCents = payment.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency("eur")
                    .setDescription("Pawly Pet Store — Order #" + orderId)
                    .putMetadata("order_id",   String.valueOf(orderId))
                    .putMetadata("buyer_email", callerEmail)
                    .addPaymentMethodType("card")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            payment.setStripePaymentIntentId(intent.getId());
            payment.setStripeClientSecret(intent.getClientSecret());
            paymentRepository.save(payment);

            log.info("Created PaymentIntent {} for order {}", intent.getId(), orderId);

            return PaymentIntentDTO.builder()
                    .orderId(orderId)
                    .clientSecret(intent.getClientSecret())
                    .paymentIntentId(intent.getId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .build();

        } catch (StripeException e) {
            log.error("Stripe API error creating PaymentIntent for order {}: {}", orderId, e.getMessage());
            throw new IllegalStateException("Stripe payment initialisation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handle incoming Stripe webhook events.
     * Verifies the signature before processing — any unverified event is rejected.
     * Idempotent: processing an already-processed event is a no-op.
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }

        com.stripe.model.Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid Stripe webhook signature");
        }

        log.info("Received Stripe event: {}", event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded"             -> handleSucceeded(event);
            case "payment_intent.payment_failed"        -> handleFailed(event);
            case "payment_intent.canceled"              -> handleCancelled(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    // ── Private handlers ──────────────────────────────────────

    private void handleSucceeded(com.stripe.model.Event event) {
        PaymentIntent intent = extractIntent(event);
        paymentRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(payment -> {
            if (payment.getStatus() == Payment.PaymentStatus.SUCCEEDED) {
                log.debug("PaymentIntent {} already marked succeeded — skipping", intent.getId());
                return;
            }
            payment.setStatus(Payment.PaymentStatus.SUCCEEDED);
            payment.setStripeChargeId(intent.getLatestCharge());
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);
            log.info("Payment {} marked SUCCEEDED for order {}",
                    intent.getId(), payment.getOrder().getId());
        });
    }

    private void handleFailed(com.stripe.model.Event event) {
        PaymentIntent intent = extractIntent(event);
        paymentRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(payment -> {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            String reason = intent.getLastPaymentError() != null
                    ? intent.getLastPaymentError().getMessage()
                    : "Payment failed";
            payment.setFailureReason(reason);
            paymentRepository.save(payment);
            log.info("Payment {} marked FAILED for order {}: {}",
                    intent.getId(), payment.getOrder().getId(), reason);
        });
    }

    private void handleCancelled(com.stripe.model.Event event) {
        PaymentIntent intent = extractIntent(event);
        paymentRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(payment -> {
            payment.setStatus(Payment.PaymentStatus.CANCELLED);
            paymentRepository.save(payment);
            log.info("Payment {} marked CANCELLED for order {}",
                    intent.getId(), payment.getOrder().getId());
        });
    }

    private PaymentIntent extractIntent(com.stripe.model.Event event) {
        return (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Could not deserialize PaymentIntent from event " + event.getId()));
    }

    private Order resolveOrderForCaller(Long orderId, String callerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!order.getBuyer().getEmail().equals(callerEmail)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not own order #" + orderId);
        }
        return order;
    }
}
