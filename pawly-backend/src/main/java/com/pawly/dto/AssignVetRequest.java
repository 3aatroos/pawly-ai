package com.pawly.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignVetRequest {

    @NotNull(message = "Vet profile ID is required")
    private Long vetProfileId;
}
