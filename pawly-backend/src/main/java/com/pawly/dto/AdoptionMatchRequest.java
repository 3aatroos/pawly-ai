package com.pawly.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdoptionMatchRequest {

    @Size(max = 80, message = "Housing is too long")
    private String housing;

    private Boolean hasGarden;

    @Min(value = 0, message = "Hours away must be between 0 and 24")
    @Max(value = 24, message = "Hours away must be between 0 and 24")
    private Integer hoursAwayPerDay;

    private Boolean otherPets;

    private Boolean children;

    @Size(max = 80, message = "Experience is too long")
    private String experience;

    @Size(max = 80, message = "Area is too long")
    private String sector;
}
