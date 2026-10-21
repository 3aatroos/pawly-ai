package com.pawly.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class TriageRequest {

    @NotBlank(message = "Symptom description is required")
    @Size(min = 10, max = 2000, message = "Symptom description must be between 10 and 2000 characters")
    private String symptoms;

    private String species;

    private String petName;

    private Long petId;
}