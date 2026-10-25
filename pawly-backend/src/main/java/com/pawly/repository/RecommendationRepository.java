package com.pawly.repository;

import com.pawly.entity.Recommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByUserIdOrderByScoreDesc(Long userId, Pageable pageable);

    Optional<Recommendation> findByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Recommendation r WHERE r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
