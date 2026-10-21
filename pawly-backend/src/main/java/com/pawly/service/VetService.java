package com.pawly.service;

import com.pawly.dto.PetDTO;
import com.pawly.dto.VetProfileDTO;
import com.pawly.entity.VetProfile;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.PetRepository;
import com.pawly.repository.VetProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VetService {

    private final VetProfileRepository vetProfileRepository;
    private final PetRepository petRepository;

    public VetProfileDTO findByUserId(Long userId) {
        return VetProfileDTO.from(getByUserId(userId));
    }

    public VetProfileDTO findById(Long id) {
        return VetProfileDTO.from(vetProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VetProfile", id)));
    }

    public Page<VetProfileDTO> findAll(VetProfile.VerificationStatus status, String search, Pageable pageable) {
        return vetProfileRepository.findAllFiltered(status, search, pageable).map(VetProfileDTO::from);
    }

    @Transactional
    public VetProfileDTO update(Long userId, VetProfileDTO dto) {
        VetProfile vp = getByUserId(userId);
        if (dto.getSpecialization()  != null) vp.setSpecialization(dto.getSpecialization());
        if (dto.getExperienceYears() != null) vp.setExperienceYears(dto.getExperienceYears());
        if (dto.getClinicName()      != null) vp.setClinicName(dto.getClinicName());
        if (dto.getClinicAddress()   != null) vp.setClinicAddress(dto.getClinicAddress());
        if (dto.getCity()            != null) vp.setCity(dto.getCity());
        if (dto.getWorkingHours()    != null) vp.setWorkingHours(dto.getWorkingHours());
        if (dto.getServices()        != null) vp.setServices(dto.getServices());
        if (dto.getLicenseNumber()   != null) vp.setLicenseNumber(dto.getLicenseNumber());
        if (dto.getBio()             != null) vp.setBio(dto.getBio());
        if (dto.getIsAvailable()     != null) vp.setIsAvailable(dto.getIsAvailable());
        return VetProfileDTO.from(vetProfileRepository.save(vp));
    }

    @Transactional
    public VetProfileDTO verify(Long vetProfileId) {
        VetProfile vp = vetProfileRepository.findById(vetProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("VetProfile", vetProfileId));
        vp.setVerificationStatus(VetProfile.VerificationStatus.VERIFIED);
        vp.setVerifiedAt(LocalDateTime.now());
        vp.setRejectionReason(null);

        // Also activate the user account
        vp.getUser().setStatus(com.pawly.entity.User.UserStatus.ACTIVE);

        return VetProfileDTO.from(vetProfileRepository.save(vp));
    }

    @Transactional
    public VetProfileDTO reject(Long vetProfileId, String reason) {
        VetProfile vp = vetProfileRepository.findById(vetProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("VetProfile", vetProfileId));
        vp.setVerificationStatus(VetProfile.VerificationStatus.REJECTED);
        vp.setRejectionReason(reason);
        return VetProfileDTO.from(vetProfileRepository.save(vp));
    }

    public long countByStatus(VetProfile.VerificationStatus status) {
        return vetProfileRepository.countByVerificationStatus(status);
    }

    private VetProfile getByUserId(Long userId) {
        return vetProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("VetProfile not found for user: " + userId));
    }

    public List<PetDTO> findAssignedPets(Long vetUserId) {
        VetProfile vetProfile = getByUserId(vetUserId);
        return petRepository.findByAssignedVetId(vetProfile.getId())
                .stream()
                .map(PetDTO::from)
                .toList();
    }
}
