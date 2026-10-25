package com.pawly.dto;

import com.pawly.entity.AdoptionApplication;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DecideApplicationRequest {

    @NotNull(message = "Status is required")
    private AdoptionApplication.Status status;

    @Size(max = 1000, message = "Decision note is too long")
    private String decisionNote;
}
