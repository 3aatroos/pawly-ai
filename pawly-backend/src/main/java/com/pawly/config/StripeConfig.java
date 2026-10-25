package com.pawly.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.secret.key:}")
    private String secretKey;

    @PostConstruct
    public void init() {
        // Stripe is optional — the store runs on cash on delivery by default.
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("Stripe secret key not configured — card payments disabled, cash on delivery only.");
            return;
        }
        if (!secretKey.startsWith("sk_test_")) {
            throw new IllegalStateException(
                    "Only Stripe TEST keys are allowed (must start with sk_test_).");
        }
        Stripe.apiKey = secretKey;
    }
}
