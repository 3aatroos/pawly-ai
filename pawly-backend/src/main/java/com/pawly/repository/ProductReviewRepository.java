package com.pawly.repository;

import com.pawly.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    Page<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    Optional<ProductReview> findByProductIdAndUserId(Long productId, Long userId);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    long countByProductId(Long productId);

    List<ProductReview> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM ProductReview r WHERE r.product.id = :productId")
    Double averageRatingByProductId(@Param("productId") Long productId);
}
