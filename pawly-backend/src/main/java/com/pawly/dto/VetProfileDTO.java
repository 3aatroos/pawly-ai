package com.pawly.dto;

import com.pawly.entity.VetProfile;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VetProfileDTO {

    private Long id;
    private Long userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private String userPhone;
    private String userPhotoUrl;

    @NotBlank(message = "Specialization is required")
    private String specialization;

    private Integer experienceYears;
    private String clinicName;
    private String clinicAddress;
    private String city;
    private String workingHours;
    private String services;
    private String licenseNumber;
    private String bio;
    private Boolean isAvailable;
    private VetProfile.VerificationStatus verificationStatus;
    private LocalDateTime verifiedAt;
    private String rejectionReason;
    private LocalDateTime createdAt;

    public static VetProfileDTO from(VetProfile vp) {
        return VetProfileDTO.builder()
                .id(vp.getId())
                .userId(vp.getUser().getId())
                .userEmail(vp.getUser().getEmail())
                .userFirstName(vp.getUser().getFirstName())
                .userLastName(vp.getUser().getLastName())
                .userPhone(vp.getUser().getPhone())
                .userPhotoUrl(vp.getUser().getPhotoUrl())
                .specialization(vp.getSpecialization())
                .experienceYears(vp.getExperienceYears())
                .clinicName(vp.getClinicName())
                .clinicAddress(vp.getClinicAddress())
                .city(vp.getCity())
                .workingHours(vp.getWorkingHours())
                .services(vp.getServices())
                .licenseNumber(vp.getLicenseNumber())
                .bio(vp.getBio())
                .isAvailable(vp.getIsAvailable())
                .verificationStatus(vp.getVerificationStatus())
                .verifiedAt(vp.getVerifiedAt())
                .rejectionReason(vp.getRejectionReason())
                .createdAt(vp.getCreatedAt())
                .build();
    }
}
