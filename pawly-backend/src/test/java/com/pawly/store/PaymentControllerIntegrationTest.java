package com.pawly.store;

import com.pawly.entity.*;
import com.pawly.repository.*;
import com.pawly.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Integration tests for PaymentController.
 *
 * NOTE: These tests do NOT call the real Stripe API.
 * The intent endpoint is tested at the security level only:
 *   - 401 without token
 *   - 404 for unknown order
 *   - 400 for non-Stripe orders
 *   - 403 when caller does not own the order
 *
 * Real PaymentIntent creation (which requires a live sk_test_ key) is covered
 * in StripeServiceTest via unit tests with mocked behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User buyer;
    private User other;
    private Order stripeOrder;
    private Order codOrder;
    private String buyerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(User.builder()
                .email("buyer@pay.test").passwordHash(passwordEncoder.encode("p"))
                .firstName("Buyer").lastName("Pay")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        other = userRepository.save(User.builder()
                .email("other@pay.test").passwordHash(passwordEncoder.encode("p"))
                .firstName("Other").lastName("Pay")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        buyerToken = "Bearer " + jwtUtil.generateToken(buyer.getEmail(), buyer.getRole().name());
        otherToken = "Bearer " + jwtUtil.generateToken(other.getEmail(),  other.getRole().name());

        stripeOrder = orderRepository.save(Order.builder()
                .buyer(buyer).paymentMethod(Order.PaymentMethod.STRIPE)
                .status(Order.OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("45.00")).shippingFee(new BigDecimal("5.00"))
                .shippingAddress("Test St").build());

        paymentRepository.save(Payment.builder()
                .order(stripeOrder).amount(new BigDecimal("45.00"))
                .status(Payment.PaymentStatus.PENDING).build());

        codOrder = orderRepository.save(Order.builder()
                .buyer(buyer).paymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY)
                .status(Order.OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("30.00")).shippingFee(BigDecimal.ZERO)
                .shippingAddress("Test St").build());

        paymentRepository.save(Payment.builder()
                .order(codOrder).amount(new BigDecimal("30.00"))
                .status(Payment.PaymentStatus.PENDING).build());
    }

    @Test
    void POST_intent_requires_authentication() throws Exception {
        mockMvc.perform(post("/api/store/payments/{id}/intent", stripeOrder.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void POST_intent_returns_403_when_caller_does_not_own_order() throws Exception {
        mockMvc.perform(post("/api/store/payments/{id}/intent", stripeOrder.getId())
                        .header("Authorization", otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void POST_intent_returns_404_for_unknown_order() throws Exception {
        mockMvc.perform(post("/api/store/payments/99999/intent")
                        .header("Authorization", buyerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void POST_intent_returns_400_for_COD_order() throws Exception {
        mockMvc.perform(post("/api/store/payments/{id}/intent", codOrder.getId())
                        .header("Authorization", buyerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_intent_returns_400_when_payment_already_succeeded() throws Exception {
        paymentRepository.findByOrderId(stripeOrder.getId()).ifPresent(p -> {
            p.setStatus(Payment.PaymentStatus.SUCCEEDED);
            paymentRepository.save(p);
        });

        mockMvc.perform(post("/api/store/payments/{id}/intent", stripeOrder.getId())
                        .header("Authorization", buyerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_webhook_without_signature_returns_400() throws Exception {
        mockMvc.perform(post("/api/store/payments/webhook")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_webhook_with_invalid_signature_returns_400() throws Exception {
        mockMvc.perform(post("/api/store/payments/webhook")
                        .content("{}")
                        .header("Stripe-Signature", "t=1,v1=invalid_signature"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_webhook_is_accessible_without_jwt() throws Exception {
        // Webhook must be public — Stripe has no JWT.
        // With an invalid signature it returns 400, not 401.
        mockMvc.perform(post("/api/store/payments/webhook")
                        .content("{}")
                        .header("Stripe-Signature", "t=1,v1=bad"))
                .andExpect(status().isBadRequest());
    }
}
