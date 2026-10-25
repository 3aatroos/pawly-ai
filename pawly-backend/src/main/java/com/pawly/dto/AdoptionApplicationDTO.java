package com.pawly.dto;

import com.pawly.entity.AdoptionApplication;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdoptionApplicationDTO {

    private Long id;
    private Long postId;
    private Long applicantId;
    private String applicantFirstName;
    private AdoptionApplication.Status status;
    private String housing;
    private Boolean hasGarden;
    private Integer hoursAwayPerDay;
    private Boolean otherPets;
    private Boolean childrenAtHome;
    private String experience;
    private String motivation;
    private String reviewerSummary;
    private List<String> pointsToVerify;
    private String suggestedAttention;
    private String decisionNote;
    private LocalDateTime createdAt;
}
