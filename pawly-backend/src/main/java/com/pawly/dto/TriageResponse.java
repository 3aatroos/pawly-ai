package com.pawly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageResponse {

    private String urgencyLevel;

    private String assessment;

    private List<String> knowledgeSources;

    private LocalDateTime timestamp;

    private boolean aiGenerated;
}