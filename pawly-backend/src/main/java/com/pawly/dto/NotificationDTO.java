package com.pawly.dto;

import com.pawly.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private Long id;
    private Long userId;
    private String title;
    private String message;
    private Notification.NotificationType type;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationDTO from(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .userId(n.getUser().getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
