package com.pawly.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "vet_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VetProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 200)
    private String specialization;

    @Column(name = "experience_years", nullable = false)
    @Builder.Default
    private Integer experienceYears = 0;

    @Column(name = "clinic_name", length = 200)
    private String clinicName;

    @Column(name = "clinic_address", length = 500)
    private String clinicAddress;

    @Column(length = 100)
    private String city;

    @Column(name = "working_hours", length = 200)
    private String workingHours;

    @Column(columnDefinition = "TEXT")
    private String services;

    @Column(name = "license_number", length = 100)
    private String licenseNumber;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enum ──────────────────────────────────────────────────

    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED
    }
}
