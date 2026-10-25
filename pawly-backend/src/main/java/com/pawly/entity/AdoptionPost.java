package com.pawly.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "adoption_posts", indexes = {
        @Index(name = "idx_adoption_posts_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdoptionPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.AVAILABLE;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "animal_name", length = 80)
    private String animalName;

    @Column(name = "shelter_notes", nullable = false, columnDefinition = "TEXT")
    private String shelterNotes;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String species;

    @Column(length = 100)
    private String breed;

    @Column(name = "age_label", length = 80)
    private String ageLabel;

    @Column(length = 20)
    private String gender;

    @Column(length = 500)
    private String personality;

    @Column(name = "special_needs", length = 500)
    private String specialNeeds;

    @Column(name = "area_label", length = 80)
    private String areaLabel;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "photo_description", length = 2000)
    private String photoDescription;

    @Column(name = "ai_draft", columnDefinition = "TEXT")
    private String aiDraft;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_changed_by_id")
    private User statusChangedBy;

    @Column(name = "adopted_at")
    private LocalDateTime adoptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        AVAILABLE, PENDING, ADOPTED
    }
}
