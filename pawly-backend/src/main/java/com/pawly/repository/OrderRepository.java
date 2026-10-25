package com.pawly.repository;

import com.pawly.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);

    Optional<Order> findByIdAndBuyerId(Long id, Long buyerId);

    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:buyerId IS NULL OR o.buyer.id = :buyerId)
              AND (:orderId IS NULL OR o.id = :orderId)
            ORDER BY o.createdAt DESC
            """)
    Page<Order> findAllFiltered(
            @Param("status")  Order.OrderStatus status,
            @Param("buyerId") Long buyerId,
            @Param("orderId") Long orderId,
            Pageable pageable
    );

    long countByBuyerId(Long buyerId);

    long countByStatus(Order.OrderStatus status);
}
