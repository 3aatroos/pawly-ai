package com.pawly.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdoptionDraftDTO {

    private String name;
    private String species;
    private String breed;
    private String age;
    private String gender;
    private List<String> personalityTags;
    private String description;
    private String specialNeeds;
    private List<String> uncertainties;
}
