package com.pawly.store;

import com.pawly.entity.*;
import com.pawly.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;

    private Order order;

    @BeforeEach
    void setUp() {
        User buyer = userRepository.save(User.builder()
                .email("buyer@test.com").passwordHash("hash")
                .firstName("Buyer").lastName("Test")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE).build());

        order = orderRepository.save(Order.builder()
                .buyer(buyer)
                .status(Order.OrderStatus.CONFIRMED)
                .paymentMethod(Order.PaymentMethod.STRIPE)
                .totalAmount(new BigDecimal("59.99"))
                .shippingFee(new BigDecimal("5.00"))
                .shippingAddress("456 Avenue Habib Bourguiba, Tunis")
                .build());
    }

    @Test
    void save_payment_persists_with_EUR_currency_by_default() {
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .amount(new BigDecimal("59.99"))
                .stripePaymentIntentId("pi_test_123abc")
                .stripeClientSecret("pi_test_123abc_secret_xyz")
                .build());

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getCurrency()).isEqualTo("EUR");
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(payment.getCreatedAt()).isNotNull();
    }

    @Test
    void findByOrderId_returns_associated_payment() {
        paymentRepository.save(Payment.builder()
                .order(order).amount(new BigDecimal("59.99"))
                .stripePaymentIntentId("pi_test_abc").build());

        Optional<Payment> found = paymentRepository.findByOrderId(order.getId());

        assertThat(found).isPresent();
    }

    @Test
    void findByStripePaymentIntentId_returns_correct_payment() {
        String intentId = "pi_test_unique_456";
        paymentRepository.save(Payment.builder()
                .order(order).amount(new BigDecimal("59.99"))
                .stripePaymentIntentId(intentId).build());

        Optional<Payment> found = paymentRepository.findByStripePaymentIntentId(intentId);

        assertThat(found).isPresent();
        assertThat(found.get().getStripePaymentIntentId()).isEqualTo(intentId);
    }

    @Test
    void existsByStripePaymentIntentId_idempotency_check() {
        paymentRepository.save(Payment.builder()
                .order(order).amount(new BigDecimal("59.99"))
                .stripePaymentIntentId("pi_test_789").build());

        assertThat(paymentRepository.existsByStripePaymentIntentId("pi_test_789")).isTrue();
        assertThat(paymentRepository.existsByStripePaymentIntentId("pi_test_nonexistent")).isFalse();
    }

    @Test
    void payment_cascade_deletes_with_order() {
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order).amount(new BigDecimal("59.99"))
                .stripePaymentIntentId("pi_test_cascade").build());
        order.setPayment(payment);
        entityManager.flush();

        orderRepository.delete(order);
        entityManager.flush();

        assertThat(paymentRepository.findByOrderId(order.getId())).isEmpty();
    }
}
