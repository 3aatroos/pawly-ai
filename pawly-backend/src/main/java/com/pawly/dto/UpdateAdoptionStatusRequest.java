package com.pawly.dto;

import com.pawly.entity.AdoptionPost;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAdoptionStatusRequest {

    @NotNull(message = "Status is required")
    private AdoptionPost.Status status;
}
