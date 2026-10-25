package com.pawly.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DraftAdoptionRequest {

    @Size(max = 4000, message = "Notes are too long")
    private String notes;

    @Size(max = 2000, message = "Photo description is too long")
    private String photoDescription;
}
