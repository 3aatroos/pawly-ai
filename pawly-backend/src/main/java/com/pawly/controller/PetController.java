package com.pawly.controller;

import com.pawly.dto.AssignVetRequest;
import com.pawly.dto.PetDTO;
import com.pawly.dto.UserDTO;
import com.pawly.service.PetService;
import com.pawly.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<PetDTO>> myPets(@AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(petService.findByOwner(me.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PetDTO> getById(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(petService.findById(id, me.getId()));
    }

    @PostMapping
    public ResponseEntity<PetDTO> create(@Valid @RequestBody PetDTO dto,
                                          @AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(petService.create(me.getId(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PetDTO> update(@PathVariable Long id,
                                          @RequestBody PetDTO dto,
                                          @AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(petService.update(id, me.getId(), dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        petService.delete(id, me.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/assign-vet")
    public ResponseEntity<PetDTO> assignVet(@PathVariable Long id,
                                             @Valid @RequestBody AssignVetRequest body,
                                             @AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(petService.assignVet(id, me.getId(), body.getVetProfileId()));
    }

    @DeleteMapping("/{id}/assign-vet")
    public ResponseEntity<PetDTO> removeVet(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails principal) {
        UserDTO me = userService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(petService.removeVet(id, me.getId()));
    }
}
