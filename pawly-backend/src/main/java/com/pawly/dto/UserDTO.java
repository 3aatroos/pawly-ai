package com.pawly.dto;

import com.pawly.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private String photoUrl;
    private User.Role role;
    private User.UserStatus status;
    private LocalDateTime createdAt;
    private int petCount;

    public static UserDTO from(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .photoUrl(user.getPhotoUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .petCount(user.getPets() != null ? user.getPets().size() : 0)
                .build();
    }
}
