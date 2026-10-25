package com.pawly.store;

import com.pawly.entity.*;
import com.pawly.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;

    private User buyer;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(User.builder()
                .email("buyer@test.com").passwordHash("hash")
                .firstName("Buyer").lastName("Test")
                .role(User.Role.PET_OWNER).status(User.UserStatus.ACTIVE)
                .build());
    }

    private Order buildOrder(Order.OrderStatus status) {
        return Order.builder()
                .buyer(buyer)
                .status(status)
                .paymentMethod(Order.PaymentMethod.CASH_ON_DELIVERY)
                .totalAmount(new BigDecimal("49.99"))
                .shippingFee(new BigDecimal("5.00"))
                .shippingAddress("123 Rue de la Paix, Tunis")
                .shippingCity("Tunis")
                .shippingCountry("TN")
                .build();
    }

    @Test
    void save_order_persists_with_default_status() {
        Order order = orderRepository.save(buildOrder(Order.OrderStatus.CONFIRMED));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getShippingCountry()).isEqualTo("TN");
    }

    @Test
    void findByBuyerIdOrderByCreatedAtDesc_returns_buyer_orders() {
        orderRepository.save(buildOrder(Order.OrderStatus.CONFIRMED));
        orderRepository.save(buildOrder(Order.OrderStatus.CONFIRMED));

        Page<Order> orders = orderRepository.findByBuyerIdOrderByCreatedAtDesc(
                buyer.getId(), PageRequest.of(0, 10));

        assertThat(orders.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByIdAndBuyerId_returns_empty_for_other_buyer() {
        Order order = orderRepository.save(buildOrder(Order.OrderStatus.CONFIRMED));

        Optional<Order> result = orderRepository.findByIdAndBuyerId(order.getId(), 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllFiltered_filters_by_status() {
        orderRepository.save(buildOrder(Order.OrderStatus.CONFIRMED));
        orderRepository.save(buildOrder(Order.OrderStatus.DELIVERED));

        Page<Order> pending = orderRepository.findAllFiltered(
                Order.OrderStatus.CONFIRMED, null, null, PageRequest.of(0, 10));

        assertThat(pending.getContent()).hasSize(1);
        assertThat(pending.getContent().get(0).getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
    }

    @Test
    void order_items_cascade_delete_with_order() {
        Order order = orderRepository.save(buildOrder(Order.OrderStatus.CONFIRMED));
        order.getItems().add(OrderItem.builder()
                .order(order).productName("Cat Food")
                .quantity(2).unitPrice(new BigDecimal("15.00")).build());
        orderRepository.save(order);

        orderRepository.delete(order);

        assertThat(orderRepository.findById(order.getId())).isEmpty();
    }
}
