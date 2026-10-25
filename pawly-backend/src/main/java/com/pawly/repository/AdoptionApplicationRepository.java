package com.pawly.repository;

import com.pawly.entity.AdoptionApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdoptionApplicationRepository extends JpaRepository<AdoptionApplication, Long> {

    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    List<AdoptionApplication> findByPostIdOrderByCreatedAtDesc(Long postId);

    List<AdoptionApplication> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    Optional<AdoptionApplication> findByIdAndApplicantId(Long id, Long applicantId);
}
