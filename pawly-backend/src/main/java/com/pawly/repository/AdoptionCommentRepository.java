package com.pawly.repository;

import com.pawly.entity.AdoptionComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdoptionCommentRepository extends JpaRepository<AdoptionComment, Long> {

    List<AdoptionComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    Optional<AdoptionComment> findByIdAndPostId(Long id, Long postId);
}
