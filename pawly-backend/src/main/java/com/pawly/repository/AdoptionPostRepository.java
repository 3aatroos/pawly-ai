package com.pawly.repository;

import com.pawly.entity.AdoptionPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdoptionPostRepository extends JpaRepository<AdoptionPost, Long> {

    List<AdoptionPost> findByStatusOrderByCreatedAtDesc(AdoptionPost.Status status);

    List<AdoptionPost> findTop20ByStatusOrderByCreatedAtDesc(AdoptionPost.Status status);
}
