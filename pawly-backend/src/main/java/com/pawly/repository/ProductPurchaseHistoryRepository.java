package com.pawly.repository;

import com.pawly.entity.ProductPurchaseHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductPurchaseHistoryRepository extends JpaRepository<ProductPurchaseHistory, Long> {

    List<ProductPurchaseHistory> findByUserIdOrderByPurchasedAtDesc(Long userId);

    Page<ProductPurchaseHistory> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            SELECT pph.product.id FROM ProductPurchaseHistory pph
            WHERE pph.user.id = :userId
            GROUP BY pph.product.id
            ORDER BY COUNT(pph.id) DESC
            """)
    List<Long> findMostPurchasedProductIdsByUser(@Param("userId") Long userId, Pageable pageable);
}
