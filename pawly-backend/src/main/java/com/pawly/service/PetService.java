package com.pawly.service;

import com.pawly.dto.PetDTO;
import com.pawly.entity.Pet;
import com.pawly.entity.User;
import com.pawly.entity.VetProfile;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.PetRepository;
import com.pawly.repository.VetProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final UserService userService;
    private final VetProfileRepository vetProfileRepository;

    public List<PetDTO> findByOwner(Long ownerId) {
        return petRepository.findByOwnerId(ownerId).stream().map(PetDTO::from).toList();
    }

    public PetDTO findById(Long petId, Long ownerId) {
        Pet pet = petRepository.findByIdAndOwnerId(petId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet", petId));
        return PetDTO.from(pet);
    }

    @Transactional
    public PetDTO create(Long ownerId, PetDTO dto) {
        User owner = userService.getUser(ownerId);
        Pet pet = Pet.builder()
                .owner(owner)
                .name(dto.getName())
                .species(dto.getSpecies())
                .breed(dto.getBreed())
                .dateOfBirth(dto.getDateOfBirth())
                .gender(dto.getGender() != null ? dto.getGender() : Pet.PetGender.UNKNOWN)
                .photoUrl(dto.getPhotoUrl())
                .healthInfo(dto.getHealthInfo())
                .weightKg(dto.getWeightKg())
                .color(dto.getColor())
                .microchipId(dto.getMicrochipId())
                .isVaccinated(Boolean.TRUE.equals(dto.getIsVaccinated()))
                .build();
        return PetDTO.from(petRepository.save(pet));
    }

    @Transactional
    public PetDTO update(Long petId, Long ownerId, PetDTO dto) {
        Pet pet = petRepository.findByIdAndOwnerId(petId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet", petId));
        if (dto.getName()        != null) pet.setName(dto.getName());
        if (dto.getSpecies()     != null) pet.setSpecies(dto.getSpecies());
        if (dto.getBreed()       != null) pet.setBreed(dto.getBreed());
        if (dto.getDateOfBirth() != null) pet.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender()      != null) pet.setGender(dto.getGender());
        if (dto.getPhotoUrl()    != null) pet.setPhotoUrl(dto.getPhotoUrl());
        if (dto.getHealthInfo()  != null) pet.setHealthInfo(dto.getHealthInfo());
        if (dto.getWeightKg()    != null) pet.setWeightKg(dto.getWeightKg());
        if (dto.getColor()       != null) pet.setColor(dto.getColor());
        if (dto.getMicrochipId() != null) pet.setMicrochipId(dto.getMicrochipId());
        if (dto.getIsVaccinated() != null) pet.setIsVaccinated(dto.getIsVaccinated());
        return PetDTO.from(petRepository.save(pet));
    }

    @Transactional
    public void delete(Long petId, Long ownerId) {
        Pet pet = petRepository.findByIdAndOwnerId(petId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet", petId));
        petRepository.delete(pet);
    }

    public long countByOwner(Long ownerId) {
        return petRepository.countByOwnerId(ownerId);
    }

    @Transactional
    public PetDTO assignVet(Long petId, Long ownerId, Long vetProfileId) {
        Pet pet = petRepository.findByIdAndOwnerId(petId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet", petId));
        VetProfile vetProfile = vetProfileRepository.findById(vetProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("VetProfile", vetProfileId));
        if (vetProfile.getVerificationStatus() != VetProfile.VerificationStatus.VERIFIED
                || !Boolean.TRUE.equals(vetProfile.getIsAvailable())) {
            throw new IllegalStateException("Vet is not available for assignment");
        }
        pet.setAssignedVet(vetProfile);
        return PetDTO.from(petRepository.save(pet));
    }

    @Transactional
    public PetDTO removeVet(Long petId, Long ownerId) {
        Pet pet = petRepository.findByIdAndOwnerId(petId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet", petId));
        pet.setAssignedVet(null);
        return PetDTO.from(petRepository.save(pet));
    }
}
