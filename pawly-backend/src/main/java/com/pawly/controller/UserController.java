package com.pawly.controller;

import com.pawly.dto.NotificationDTO;
import com.pawly.dto.UpdateProfileRequest;
import com.pawly.dto.UserDTO;
import com.pawly.entity.User;
import com.pawly.service.NotificationService;
import com.pawly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final NotificationService notificationService;

    /** Current user profile */
    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(userService.findByEmail(principal.getUsername()));
    }

    /** Update current user profile */
    @PutMapping("/me")
    public ResponseEntity<UserDTO> updateMe(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody UpdateProfileRequest req) {
        UserDTO current = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(userService.updateProfile(current.getId(), req));
    }

    /** Get any user by id (self or admin) */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getById(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails principal) {
        UserDTO current = userService.findByEmail(principal.getUsername());
        if (!current.getId().equals(id) && current.getRole() != User.Role.ADMINISTRATOR) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(userService.findById(id));
    }

    /** Notifications for current user */
    @GetMapping("/me/notifications")
    public ResponseEntity<List<NotificationDTO>> myNotifications(@AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(notificationService.findByUser(me.getId()));
    }

    @GetMapping("/me/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(me.getId())));
    }

    @PatchMapping("/me/notifications/{notifId}/read")
    public ResponseEntity<NotificationDTO> markRead(@PathVariable Long notifId,
                                                     @AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(notificationService.markRead(notifId, me.getId()));
    }

    @PatchMapping("/me/notifications/mark-all-read")
    public ResponseEntity<Map<String, Integer>> markAllRead(@AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        int updated = notificationService.markAllRead(me.getId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
