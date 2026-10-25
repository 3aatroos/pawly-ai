package com.pawly.store;

import com.pawly.dto.store.PaymentIntentDTO;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.OrderRepository;
import com.pawly.repository.PaymentRepository;
import com.pawly.service.UserService;
import com.pawly.service.store.StripeService;
import com.stripe.exception.ApiException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserService userService;

    @InjectMocks private StripeService stripeService;

    private User buyer;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stripeService, "webhookSecret", "whsec_test_placeholder");

        buyer = User.builder()
                .email("buyer@test.com").passwordHash("hash")
                .firstName("Jane").lastName("Doe")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build();
        setField(buyer, "id", 1L);

        order = Order.builder()
                .buyer(buyer)
                .paymentMethod(Order.PaymentMethod.STRIPE)
                .totalAmount(new BigDecimal("45.00"))
                .shippingFee(new BigDecimal("5.00"))
                .shippingAddress("123 Test St")
                .status(Order.OrderStatus.CONFIRMED)
                .build();
        setField(order, "id", 100L);

        payment = Payment.builder()
                .order(order)
                .amount(new BigDecimal("45.00"))
                .status(Payment.PaymentStatus.PENDING)
                .build();
        setField(payment, "id", 50L);
        order.setPayment(payment);
    }

    @Test
    void createPaymentIntent_throws_access_denied_for_different_user() {
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() ->
                stripeService.createPaymentIntent(100L, "other@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createPaymentIntent_throws_for_non_stripe_order() {
        order.setPaymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY);
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() ->
                stripeService.createPaymentIntent(100L, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a Stripe order");
    }

    @Test
    void createPaymentIntent_throws_when_already_paid() {
        payment.setStatus(Payment.PaymentStatus.SUCCEEDED);
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() ->
                stripeService.createPaymentIntent(100L, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already paid");
    }

    @Test
    void createPaymentIntent_reuses_existing_intent_without_calling_stripe_api() {
        payment.setStripePaymentIntentId("pi_test_existing");
        payment.setStripeClientSecret("pi_test_existing_secret_xyz");
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        PaymentIntentDTO result = stripeService.createPaymentIntent(100L, "buyer@test.com");

        assertThat(result.getPaymentIntentId()).isEqualTo("pi_test_existing");
        assertThat(result.getClientSecret()).isEqualTo("pi_test_existing_secret_xyz");
        assertThat(result.getAmount()).isEqualByComparingTo("45.00");
        // PaymentRepository.save should NOT be called — intent was reused
        then(paymentRepository).should(never()).save(any());
    }

    @Test
    void createPaymentIntent_throws_not_found_for_unknown_order() {
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                stripeService.createPaymentIntent(999L, "buyer@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createPaymentIntent_throws_when_payment_record_missing() {
        order.setPayment(null);
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() ->
                stripeService.createPaymentIntent(100L, "buyer@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment record missing");
    }

    @Test
    void createPaymentIntent_wraps_a_refused_stripe_charge() {
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenThrow(new ApiException(
                            "Your card was declined", "req_test", "card_declined", 402, null));

            assertThatThrownBy(() ->
                    stripeService.createPaymentIntent(100L, "buyer@test.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Stripe payment initialisation failed")
                    .hasMessageContaining("card was declined");
        }

        then(paymentRepository).should(never()).save(any());
    }

    @Test
    void handleWebhook_throws_on_missing_webhook_secret() {
        ReflectionTestUtils.setField(stripeService, "webhookSecret", "");

        assertThatThrownBy(() ->
                stripeService.handleWebhook("{}", "t=123,v1=abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook secret is not configured");
    }

    @Test
    void handleWebhook_throws_on_invalid_signature() {
        // webhookSecret is set to a placeholder — any payload/sig will fail verification
        assertThatThrownBy(() ->
                stripeService.handleWebhook("{}", "t=1,v1=invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Stripe webhook signature");
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
