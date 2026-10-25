package com.pawly.dto;

import com.pawly.entity.AdoptionPost;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdoptionPostDTO {

    private Long id;
    private Long petId;
    private Long authorId;
    private String authorFirstName;
    private AdoptionPost.Status status;
    private String title;
    private String animalName;
    private String shelterNotes;
    private String description;
    private String species;
    private String breed;
    private String ageLabel;
    private String gender;
    private String personality;
    private String specialNeeds;
    private String areaLabel;
    private String photoUrl;
    private String photoDescription;
    private String aiDraft;
    private LocalDateTime createdAt;
    private LocalDateTime adoptedAt;
}
