package com.pawly.repository;

import com.pawly.entity.VetProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VetProfileRepository extends JpaRepository<VetProfile, Long> {

    Optional<VetProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    long countByVerificationStatus(VetProfile.VerificationStatus status);

    @Query("""
            SELECT vp FROM VetProfile vp
            WHERE (:status IS NULL OR vp.verificationStatus = :status)
              AND (:search IS NULL OR LOWER(vp.specialization) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(vp.clinicName)     LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(vp.city)           LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<VetProfile> findAllFiltered(
            @Param("status") VetProfile.VerificationStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
