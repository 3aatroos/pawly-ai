package com.pawly.controller;

import com.pawly.dto.PetDTO;
import com.pawly.dto.UserDTO;
import com.pawly.dto.VetProfileDTO;
import com.pawly.entity.VetProfile;
import com.pawly.service.UserService;
import com.pawly.service.VetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vets")
@RequiredArgsConstructor
public class VetController {

    private final VetService vetService;
    private final UserService userService;

    /** List all vets (public browsing) */
    @GetMapping
    public ResponseEntity<Page<VetProfileDTO>> list(
            @RequestParam(required = false) VetProfile.VerificationStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(vetService.findAll(status, search, pageable));
    }

    /** Get specific vet profile */
    @GetMapping("/{id}")
    public ResponseEntity<VetProfileDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vetService.findById(id));
    }

    /** Current vet's own profile */
    @GetMapping("/me")
    public ResponseEntity<VetProfileDTO> myProfile(@AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(vetService.findByUserId(me.getId()));
    }

    /** Update current vet's profile */
    @PutMapping("/me")
    public ResponseEntity<VetProfileDTO> updateMyProfile(@RequestBody VetProfileDTO dto,
                                                          @AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(vetService.update(me.getId(), dto));
    }

    /** Vet retrieves their own assigned pets */
    @GetMapping("/me/pets")
    public ResponseEntity<List<PetDTO>> myAssignedPets(@AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(vetService.findAssignedPets(me.getId()));
    }
}
