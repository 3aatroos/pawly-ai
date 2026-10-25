package com.pawly.repository;

import com.pawly.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByAddedAtDesc(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CartItem c WHERE c.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    boolean existsByProductId(Long productId);
}
