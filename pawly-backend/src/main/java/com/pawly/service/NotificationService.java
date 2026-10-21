package com.pawly.service;

import com.pawly.dto.NotificationDTO;
import com.pawly.entity.Notification;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    public List<NotificationDTO> findByUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationDTO::from).toList();
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationDTO create(Long userId, String title, String message, Notification.NotificationType type) {
        Notification n = Notification.builder()
                .user(userService.getUser(userId))
                .title(title)
                .message(message)
                .type(type)
                .build();
        return NotificationDTO.from(notificationRepository.save(n));
    }

    @Transactional
    public NotificationDTO markRead(Long notifId, Long userId) {
        Notification n = notificationRepository.findById(notifId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notifId));
        if (!n.getUser().getId().equals(userId)) {
            throw new SecurityException("Forbidden");
        }
        n.setIsRead(true);
        return NotificationDTO.from(notificationRepository.save(n));
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllReadForUser(userId);
    }
}
