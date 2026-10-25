package com.pawly.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "adoption_applications", uniqueConstraints = {
        @UniqueConstraint(name = "uq_adoption_application_post_user", columnNames = {"post_id", "applicant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdoptionApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private AdoptionPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.SUBMITTED;

    @Column(length = 80)
    private String housing;

    @Column(name = "has_garden")
    private Boolean hasGarden;

    @Column(name = "hours_away_per_day")
    private Integer hoursAwayPerDay;

    @Column(name = "other_pets")
    private Boolean otherPets;

    @Column(name = "children_at_home")
    private Boolean childrenAtHome;

    @Column(length = 80)
    private String experience;

    @Column(columnDefinition = "TEXT")
    private String motivation;

    @Column(name = "reviewer_summary", columnDefinition = "TEXT")
    private String reviewerSummary;

    @Column(name = "points_to_verify", columnDefinition = "TEXT")
    private String pointsToVerify;

    @Column(name = "suggested_attention", length = 10)
    private String suggestedAttention;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        SUBMITTED, UNDER_REVIEW, ACCEPTED, REJECTED
    }
}
