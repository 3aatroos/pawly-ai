package com.pawly.controller;

import com.pawly.dto.*;
import com.pawly.entity.AdoptionPost;
import com.pawly.entity.User;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.UserRepository;
import com.pawly.service.AdoptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Adoption feed. The Angular app calls this API on port 8081.
 * Gemini is reached only by AdoptionAiClient, through FastAPI.
 */
@RestController
@RequestMapping("/api/adoption")
@RequiredArgsConstructor
public class AdoptionController {

    private final AdoptionService adoptionService;
    private final UserRepository userRepository;

    @GetMapping("/posts")
    public List<AdoptionPostDTO> list(@RequestParam(defaultValue = "AVAILABLE") AdoptionPost.Status status,
                                      @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.list(current(principal), status);
    }

    @GetMapping("/posts/{id}")
    public AdoptionPostDTO get(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.get(current(principal), id);
    }

    @PostMapping("/posts")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public ResponseEntity<AdoptionPostDTO> create(@Valid @RequestBody CreateAdoptionPostRequest request,
                                                  @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adoptionService.create(current(principal), request));
    }

    @PatchMapping("/posts/{id}")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public AdoptionPostDTO update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateAdoptionPostRequest request,
                                  @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.update(current(principal), id, request);
    }

    @PatchMapping("/posts/{id}/status")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public AdoptionPostDTO changeStatus(@PathVariable Long id,
                                        @Valid @RequestBody UpdateAdoptionStatusRequest request,
                                        @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.changeStatus(current(principal), id, request);
    }

    @PostMapping("/draft-preview")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public AdoptionDraftDTO previewDraft(@Valid @RequestBody DraftAdoptionRequest request,
                                         @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.previewDraft(current(principal), request);
    }

    @PostMapping("/posts/{id}/draft")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public AdoptionDraftDTO draft(@PathVariable Long id,
                                  @Valid @RequestBody DraftAdoptionRequest request,
                                  @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.draft(current(principal), id, request);
    }

    @GetMapping("/posts/{id}/comments")
    public List<AdoptionCommentDTO> comments(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.comments(current(principal), id);
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<AdoptionCommentDTO> comment(@PathVariable Long id,
                                                      @Valid @RequestBody CreateAdoptionCommentRequest request,
                                                      @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adoptionService.comment(current(principal), id, request));
    }

    @DeleteMapping("/posts/{id}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id,
                                              @PathVariable Long commentId,
                                              @AuthenticationPrincipal UserDetails principal) {
        adoptionService.deleteComment(current(principal), id, commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/favorite")
    public AdoptionPostDTO favorite(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.favorite(current(principal), id);
    }

    @DeleteMapping("/posts/{id}/favorite")
    public ResponseEntity<Void> unfavorite(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        adoptionService.unfavorite(current(principal), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/favorites")
    public List<AdoptionPostDTO> favorites(@AuthenticationPrincipal UserDetails principal) {
        return adoptionService.favorites(current(principal));
    }

    @PostMapping("/posts/{id}/applications")
    public ResponseEntity<AdoptionApplicationDTO> apply(@PathVariable Long id,
                                                        @Valid @RequestBody CreateAdoptionApplicationRequest request,
                                                        @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adoptionService.apply(current(principal), id, request));
    }

    @GetMapping("/posts/{id}/applications")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public List<AdoptionApplicationDTO> applications(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.applications(current(principal), id);
    }

    @GetMapping("/applications/mine")
    public List<AdoptionApplicationDTO> mine(@AuthenticationPrincipal UserDetails principal) {
        return adoptionService.myApplications(current(principal));
    }

    @PatchMapping("/applications/{id}/decision")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public AdoptionApplicationDTO decide(@PathVariable Long id,
                                         @Valid @RequestBody DecideApplicationRequest request,
                                         @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.decide(current(principal), id, request);
    }

    @PostMapping("/match")
    public AdoptionMatchResultDTO match(@Valid @RequestBody AdoptionMatchRequest request,
                                        @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.match(current(principal), request);
    }

    @PostMapping("/applications/{id}/review-preview")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public AdoptionReviewDTO previewReview(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.previewReview(current(principal), id);
    }

    @PostMapping("/applications/{id}/review")
    @PreAuthorize("hasAnyRole('VETERINARIAN','ADMINISTRATOR')")
    public AdoptionReviewDTO review(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.review(current(principal), id);
    }

    private User current(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
