package com.pawly.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdoptionReviewDTO {

    private String summary;
    private List<String> pointsToVerify;
    private String suggestedAttention;
}
