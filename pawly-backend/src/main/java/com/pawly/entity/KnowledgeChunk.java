package com.pawly.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_chunks", indexes = {
    @Index(name = "idx_topic", columnList = "topic"),
    @Index(name = "idx_species", columnList = "species"),
    @Index(name = "idx_urgency", columnList = "urgency_level")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(length = 100)
    private String species;

    @Column(name = "urgency_level", length = 50)
    private String urgencyLevel;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "content_text", nullable = false, columnDefinition = "LONGTEXT")
    private String contentText;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(name = "reviewed_date")
    private LocalDateTime reviewedDate;

    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}