package com.pawly.controller;

import com.pawly.dto.UserDTO;
import com.pawly.dto.VetProfileDTO;
import com.pawly.entity.User;
import com.pawly.entity.VetProfile;
import com.pawly.service.UserService;
import com.pawly.service.VetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final VetService vetService;

    // ── Platform Stats ─────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers",        userService.countTotal(),
                "petOwners",         userService.countByRole(User.Role.PET_OWNER),
                "veterinarians",     userService.countByRole(User.Role.VETERINARIAN),
                "activeAccounts",    userService.countByStatus(User.UserStatus.ACTIVE),
                "inactiveAccounts",  userService.countByStatus(User.UserStatus.INACTIVE),
                "pendingVets",       vetService.countByStatus(VetProfile.VerificationStatus.PENDING),
                "verifiedVets",      vetService.countByStatus(VetProfile.VerificationStatus.VERIFIED)
        ));
    }

    // ── User Management ────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<Page<UserDTO>> listUsers(
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) User.UserStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(userService.findAll(role, status, search, pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<UserDTO> activateUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.setStatus(id, User.UserStatus.ACTIVE));
    }

    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<UserDTO> deactivateUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.setStatus(id, User.UserStatus.INACTIVE));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ── Vet Verification ───────────────────────────────────────

    @GetMapping("/vets")
    public ResponseEntity<Page<VetProfileDTO>> listVets(
            @RequestParam(required = false) VetProfile.VerificationStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(vetService.findAll(status, search, pageable));
    }

    @PatchMapping("/vets/{id}/verify")
    public ResponseEntity<VetProfileDTO> verifyVet(@PathVariable Long id) {
        return ResponseEntity.ok(vetService.verify(id));
    }

    @PatchMapping("/vets/{id}/reject")
    public ResponseEntity<VetProfileDTO> rejectVet(@PathVariable Long id,
                                                     @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(vetService.reject(id, body.get("reason")));
    }
}
