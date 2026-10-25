package com.pawly.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdoptionCommentDTO {

    private Long id;
    private Long postId;
    private Long authorId;
    private String authorFirstName;
    private String body;
    private LocalDateTime createdAt;
}
