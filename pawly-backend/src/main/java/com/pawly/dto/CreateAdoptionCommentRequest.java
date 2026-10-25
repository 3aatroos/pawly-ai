package com.pawly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAdoptionCommentRequest {

    @NotBlank(message = "Comment is required")
    @Size(max = 1000, message = "Comment is too long")
    private String body;
}
