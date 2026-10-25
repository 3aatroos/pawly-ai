package com.pawly.repository;

import com.pawly.entity.AdoptionFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdoptionFavoriteRepository extends JpaRepository<AdoptionFavorite, Long> {

    Optional<AdoptionFavorite> findByPostIdAndUserId(Long postId, Long userId);

    List<AdoptionFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);
}
