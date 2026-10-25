package com.pawly.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdoptionMatchResultDTO {

    private List<AdoptionMatchItemDTO> matches;
}
