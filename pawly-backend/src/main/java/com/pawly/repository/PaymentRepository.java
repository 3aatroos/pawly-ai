package com.pawly.repository;

import com.pawly.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByStripePaymentIntentId(String paymentIntentId);

    boolean existsByStripePaymentIntentId(String paymentIntentId);
}
