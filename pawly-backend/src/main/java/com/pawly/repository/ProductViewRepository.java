package com.pawly.repository;

import com.pawly.entity.ProductView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductViewRepository extends JpaRepository<ProductView, Long> {

    long countByProductId(Long productId);

    long countByUserId(Long userId);

    List<ProductView> findTop20ByUserIdOrderByViewedAtDesc(Long userId);

    @Query("SELECT COUNT(pv) FROM ProductView pv WHERE pv.product.id = :productId AND pv.viewedAt >= :since")
    long countByProductIdSince(@Param("productId") Long productId, @Param("since") LocalDateTime since);
}
