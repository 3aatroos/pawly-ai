package com.pawly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAdoptionPostRequest {

    private Long petId;

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title is too long")
    private String title;

    @Size(max = 80, message = "Animal name is too long")
    private String animalName;

    @NotBlank(message = "Notes are required")
    @Size(max = 4000, message = "Notes are too long")
    private String shelterNotes;

    @Size(max = 1500, message = "Description is too long")
    private String description;

    @Size(max = 50, message = "Species is too long")
    private String species;

    @Size(max = 100, message = "Breed is too long")
    private String breed;

    @Size(max = 80, message = "Age is too long")
    private String ageLabel;

    @Size(max = 20, message = "Gender is too long")
    private String gender;

    @Size(max = 500, message = "Personality is too long")
    private String personality;

    @Size(max = 500, message = "Special needs are too long")
    private String specialNeeds;

    @Size(max = 80, message = "Area is too long")
    private String areaLabel;

    @Size(max = 500, message = "Photo URL is too long")
    private String photoUrl;

    @Size(max = 2000, message = "Photo description is too long")
    private String photoDescription;
}
