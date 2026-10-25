package com.pawly.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdoptionMatchItemDTO {

    private Long postId;
    private Integer score;
    private String reason;
    private String warning;
}
