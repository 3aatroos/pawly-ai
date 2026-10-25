package com.pawly.dto;

import com.pawly.entity.Pet;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetDTO {

    private Long id;
    private Long ownerId;
    private String ownerName;

    @NotBlank(message = "Pet name is required")
    private String name;

    @NotBlank(message = "Species is required")
    private String species;

    private String breed;
    private LocalDate dateOfBirth;
    private Pet.PetGender gender;
    private String photoUrl;
    private String healthInfo;
    private BigDecimal weightKg;
    private String color;
    private String microchipId;
    private Boolean isVaccinated;
    private LocalDateTime createdAt;
    private Long assignedVetId;
    private String assignedVetName; // "Dr. FirstName LastName"

    public static PetDTO from(Pet pet) {
        return PetDTO.builder()
                .id(pet.getId())
                .ownerId(pet.getOwner().getId())
                .ownerName(pet.getOwner().getFullName())
                .name(pet.getName())
                .species(pet.getSpecies())
                .breed(pet.getBreed())
                .dateOfBirth(pet.getDateOfBirth())
                .gender(pet.getGender())
                .photoUrl(pet.getPhotoUrl())
                .healthInfo(pet.getHealthInfo())
                .weightKg(pet.getWeightKg())
                .color(pet.getColor())
                .microchipId(pet.getMicrochipId())
                .isVaccinated(pet.getIsVaccinated())
                .createdAt(pet.getCreatedAt())
                .assignedVetId(pet.getAssignedVet() != null ? pet.getAssignedVet().getId() : null)
                .assignedVetName(pet.getAssignedVet() != null
                        ? "Dr. " + pet.getAssignedVet().getUser().getFullName() : null)
                .build();
    }
}
