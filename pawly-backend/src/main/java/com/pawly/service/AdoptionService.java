package com.pawly.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawly.dto.*;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.AdoptionApplicationRepository;
import com.pawly.repository.AdoptionCommentRepository;
import com.pawly.repository.AdoptionFavoriteRepository;
import com.pawly.repository.AdoptionPostRepository;
import com.pawly.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdoptionService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AdoptionPostRepository postRepository;
    private final AdoptionCommentRepository commentRepository;
    private final AdoptionFavoriteRepository favoriteRepository;
    private final AdoptionApplicationRepository applicationRepository;
    private final PetRepository petRepository;
    private final AdoptionAiClient aiClient;
    private final ObjectMapper objectMapper;

    public List<AdoptionPostDTO> list(User user, AdoptionPost.Status status) {
        requireActive(user);
        AdoptionPost.Status requested = status == null ? AdoptionPost.Status.AVAILABLE : status;
        if (requested != AdoptionPost.Status.AVAILABLE && !isStaff(user)) {
            throw new AccessDeniedException("Access denied");
        }
        boolean staff = isStaff(user);
        return postRepository.findByStatusOrderByCreatedAtDesc(requested).stream()
                .filter(post -> post.getStatus() == requested)
                .filter(post -> requested != AdoptionPost.Status.AVAILABLE
                        || post.getStatus() == AdoptionPost.Status.AVAILABLE)
                .map(post -> toDto(post, staff))
                .toList();
    }

    public AdoptionPostDTO get(User user, Long postId) {
        requireActive(user);
        return toDto(loadPost(postId), isStaff(user));
    }

    @Transactional
    public AdoptionPostDTO create(User staff, CreateAdoptionPostRequest request) {
        requireStaff(staff);
        AdoptionPost post = new AdoptionPost();
        post.setAuthor(staff);
        post.setStatus(AdoptionPost.Status.AVAILABLE);
        post.setTitle(AdoptionTexts.requireClean(request.getTitle(), 120));
        post.setShelterNotes(AdoptionTexts.requireClean(request.getShelterNotes(), 4000));
        applyAnimalFields(post, request.getPetId(), request.getAnimalName(), request.getDescription(),
                request.getSpecies(), request.getBreed(), request.getAgeLabel(), request.getGender(),
                request.getPersonality(), request.getSpecialNeeds(), request.getAreaLabel(),
                request.getPhotoUrl(), request.getPhotoDescription());
        AdoptionPost saved = postRepository.save(post);
        log.info("adoption post created id={} authorId={}", saved.getId(), staff.getId());
        return toDto(saved, true);
    }

    @Transactional
    public AdoptionPostDTO update(User staff, Long postId, UpdateAdoptionPostRequest request) {
        requireStaff(staff);
        AdoptionPost post = loadPost(postId);
        AdoptionPost.Status status = post.getStatus();
        if (request.getTitle() != null) {
            post.setTitle(AdoptionTexts.requireClean(request.getTitle(), 120));
        }
        if (request.getShelterNotes() != null) {
            post.setShelterNotes(AdoptionTexts.requireClean(request.getShelterNotes(), 4000));
        }
        applyAnimalFields(
                post,
                null,
                request.getAnimalName(),
                request.getDescription(),
                request.getSpecies(),
                request.getBreed(),
                request.getAgeLabel(),
                request.getGender(),
                request.getPersonality(),
                request.getSpecialNeeds(),
                request.getAreaLabel(),
                request.getPhotoUrl(),
                request.getPhotoDescription()
        );
        post.setStatus(status);
        log.info("adoption post updated id={} authorId={}", post.getId(), staff.getId());
        return toDto(postRepository.save(post), true);
    }

    @Transactional
    public AdoptionPostDTO changeStatus(User staff, Long postId, UpdateAdoptionStatusRequest request) {
        requireStaff(staff);
        if (request.getStatus() == null) {
            throw new IllegalArgumentException("Status is empty");
        }
        AdoptionPost post = loadPost(postId);
        post.setStatus(request.getStatus());
        post.setStatusChangedBy(staff);
        post.setAdoptedAt(request.getStatus() == AdoptionPost.Status.ADOPTED ? LocalDateTime.now() : null);
        log.info("adoption status changed id={} status={} userId={}", post.getId(), post.getStatus(), staff.getId());
        return toDto(postRepository.save(post), true);
    }

    @Transactional
    public AdoptionDraftDTO draft(User staff, Long postId, DraftAdoptionRequest request) {
        requireStaff(staff);
        AdoptionPost post = loadPost(postId);
        AdoptionPost.Status statusBefore = post.getStatus();
        String previousDescription = post.getDescription();
        String notes = request.getNotes() == null
                ? AdoptionTexts.requireClean(post.getShelterNotes(), 4000)
                : AdoptionTexts.requireClean(request.getNotes(), 4000);
        String photo = request.getPhotoDescription() == null
                ? AdoptionTexts.optional(post.getPhotoDescription(), 2000)
                : AdoptionTexts.optional(request.getPhotoDescription(), 2000);
        AdoptionDraftDTO draft = aiClient.draft(notes, photo);
        post.setShelterNotes(notes);
        post.setPhotoDescription(photo);
        post.setAiDraft(writeJson(draft));
        post.setDescription(previousDescription);
        post.setStatus(statusBefore);
        postRepository.save(post);
        log.info("adoption draft stored id={} authorId={}", post.getId(), staff.getId());
        return draft;
    }

    public AdoptionDraftDTO previewDraft(User staff, DraftAdoptionRequest request) {
        requireStaff(staff);
        String notes = AdoptionTexts.requireClean(request.getNotes(), 4000);
        String photo = AdoptionTexts.optional(request.getPhotoDescription(), 2000);
        log.info("adoption draft preview authorId={}", staff.getId());
        return aiClient.draft(notes, photo);
    }

    public AdoptionReviewDTO previewReview(User staff, Long applicationId) {
        requireStaff(staff);
        AdoptionApplication application = loadApplication(applicationId);
        log.info("adoption review preview applicationId={} userId={}", applicationId, staff.getId());
        return aiClient.review(application.getPost(), application);
    }

    public List<AdoptionCommentDTO> comments(User user, Long postId) {
        requireActive(user);
        loadPost(postId);
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(this::toCommentDto)
                .toList();
    }

    @Transactional
    public AdoptionCommentDTO comment(User user, Long postId, CreateAdoptionCommentRequest request) {
        requireActive(user);
        AdoptionPost post = loadOpenPost(postId);
        AdoptionComment comment = AdoptionComment.builder()
                .post(post)
                .author(user)
                .body(AdoptionTexts.requireClean(request.getBody(), 1000))
                .build();
        log.info("adoption comment created postId={} userId={}", postId, user.getId());
        return toCommentDto(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(User user, Long postId, Long commentId) {
        requireActive(user);
        AdoptionComment comment = commentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
        boolean owner = comment.getAuthor().getId().equals(user.getId());
        if (!owner && !isStaff(user)) {
            throw new AccessDeniedException("Access denied");
        }
        commentRepository.delete(comment);
        log.info("adoption comment deleted id={} userId={}", commentId, user.getId());
    }

    @Transactional
    public AdoptionPostDTO favorite(User user, Long postId) {
        requireActive(user);
        AdoptionPost post = loadOpenPost(postId);
        favoriteRepository.findByPostIdAndUserId(postId, user.getId())
                .orElseGet(() -> favoriteRepository.save(AdoptionFavorite.builder().post(post).user(user).build()));
        log.info("adoption favorite postId={} userId={}", postId, user.getId());
        return toDto(post, false);
    }

    @Transactional
    public void unfavorite(User user, Long postId) {
        requireActive(user);
        favoriteRepository.findByPostIdAndUserId(postId, user.getId())
                .ifPresent(favoriteRepository::delete);
        log.info("adoption unfavorite postId={} userId={}", postId, user.getId());
    }

    public List<AdoptionPostDTO> favorites(User user) {
        requireActive(user);
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(AdoptionFavorite::getPost)
                .filter(post -> post.getStatus() == AdoptionPost.Status.AVAILABLE)
                .map(post -> toDto(post, false))
                .toList();
    }

    @Transactional
    public AdoptionApplicationDTO apply(User user, Long postId, CreateAdoptionApplicationRequest request) {
        requireActive(user);
        AdoptionPost post = loadPost(postId);
        if (post.getStatus() != AdoptionPost.Status.AVAILABLE) {
            throw new IllegalStateException("This animal is no longer available");
        }
        if (post.getAuthor().getId().equals(user.getId())) {
            throw new IllegalStateException("You cannot apply to your own post");
        }
        if (applicationRepository.existsByPostIdAndApplicantId(postId, user.getId())) {
            throw new IllegalStateException("An application already exists for this post");
        }
        AdoptionApplication application = AdoptionApplication.builder()
                .post(post)
                .applicant(user)
                .status(AdoptionApplication.Status.SUBMITTED)
                .housing(AdoptionTexts.requireClean(request.getHousing(), 80))
                .hasGarden(request.getHasGarden())
                .hoursAwayPerDay(hours(request.getHoursAwayPerDay()))
                .otherPets(request.getOtherPets())
                .childrenAtHome(request.getChildrenAtHome())
                .experience(AdoptionTexts.optional(request.getExperience(), 80))
                .motivation(AdoptionTexts.optional(request.getMotivation(), 2000))
                .build();
        log.info("adoption application created postId={} userId={}", postId, user.getId());
        return toApplicationDto(applicationRepository.save(application));
    }

    public List<AdoptionApplicationDTO> applications(User staff, Long postId) {
        requireStaff(staff);
        loadPost(postId);
        return applicationRepository.findByPostIdOrderByCreatedAtDesc(postId).stream()
                .map(this::toApplicationDto)
                .toList();
    }

    public List<AdoptionApplicationDTO> myApplications(User user) {
        requireActive(user);
        return applicationRepository.findByApplicantIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toApplicationDto)
                .toList();
    }

    @Transactional
    public AdoptionApplicationDTO decide(User staff, Long applicationId, DecideApplicationRequest request) {
        requireStaff(staff);
        if (request.getStatus() == null || request.getStatus() == AdoptionApplication.Status.SUBMITTED) {
            throw new IllegalArgumentException("Status is empty");
        }
        AdoptionApplication application = loadApplication(applicationId);
        AdoptionPost.Status postStatus = application.getPost().getStatus();
        application.setStatus(request.getStatus());
        application.setDecisionNote(AdoptionTexts.optional(request.getDecisionNote(), 1000));
        application.setReviewedBy(staff);
        application.getPost().setStatus(postStatus);
        log.info("adoption application decided id={} status={} userId={}",
                applicationId, application.getStatus(), staff.getId());
        return toApplicationDto(applicationRepository.save(application));
    }

    public AdoptionMatchResultDTO match(User user, AdoptionMatchRequest request) {
        requireActive(user);
        if (request.getHousing() == null && request.getExperience() == null && request.getSector() == null
                && request.getHoursAwayPerDay() == null && request.getHasGarden() == null
                && request.getOtherPets() == null && request.getChildren() == null) {
            throw new IllegalArgumentException("Lifestyle is empty");
        }
        request.setHousing(AdoptionTexts.optional(request.getHousing(), 80));
        request.setExperience(AdoptionTexts.optional(request.getExperience(), 80));
        request.setSector(AdoptionTexts.optional(request.getSector(), 80));
        request.setHoursAwayPerDay(hours(request.getHoursAwayPerDay()));
        List<AdoptionPost> posts = postRepository
                .findTop20ByStatusOrderByCreatedAtDesc(AdoptionPost.Status.AVAILABLE).stream()
                .filter(post -> post.getStatus() == AdoptionPost.Status.AVAILABLE)
                .toList();
        log.info("adoption match userId={} posts={}", user.getId(), posts.size());
        return aiClient.match(request, posts);
    }

    @Transactional
    public AdoptionReviewDTO review(User staff, Long applicationId) {
        requireStaff(staff);
        AdoptionApplication application = loadApplication(applicationId);
        AdoptionPost post = application.getPost();
        AdoptionPost.Status postStatus = post.getStatus();
        AdoptionApplication.Status applicationStatus = application.getStatus();
        AdoptionReviewDTO review = aiClient.review(post, application);
        application.setReviewerSummary(review.getSummary());
        application.setPointsToVerify(writeJson(review.getPointsToVerify()));
        application.setSuggestedAttention(review.getSuggestedAttention());
        application.setStatus(applicationStatus);
        post.setStatus(postStatus);
        applicationRepository.save(application);
        log.info("adoption review stored applicationId={} userId={}", applicationId, staff.getId());
        return review;
    }

    private void applyAnimalFields(AdoptionPost post, Long petId, String animalName, String description,
                                   String species, String breed, String ageLabel, String gender,
                                   String personality, String specialNeeds, String areaLabel,
                                   String photoUrl, String photoDescription) {
        if (petId != null) {
            Pet pet = petRepository.findById(petId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pet", petId));
            post.setPet(pet);
            if (isBlank(animalName) && !isBlank(pet.getName())) {
                animalName = pet.getName();
            }
            if (isBlank(species) && !isBlank(pet.getSpecies())) {
                species = pet.getSpecies();
            }
            if (isBlank(breed) && !isBlank(pet.getBreed())) {
                breed = pet.getBreed();
            }
            if (isBlank(gender) && pet.getGender() != null) {
                gender = pet.getGender().name().toLowerCase();
            }
            if (isBlank(ageLabel) && pet.getDateOfBirth() != null) {
                ageLabel = pet.getDateOfBirth().toString();
            }
            if (isBlank(photoUrl) && !isBlank(pet.getPhotoUrl())) {
                photoUrl = pet.getPhotoUrl();
            }
        }
        if (animalName != null) {
            post.setAnimalName(AdoptionTexts.optional(animalName, 80));
        }
        if (description != null) {
            post.setDescription(AdoptionTexts.optional(description, 1500));
        }
        if (species != null) {
            post.setSpecies(AdoptionTexts.optional(species, 50));
        }
        if (breed != null) {
            post.setBreed(AdoptionTexts.optional(breed, 100));
        }
        if (ageLabel != null) {
            post.setAgeLabel(AdoptionTexts.optional(ageLabel, 80));
        }
        if (gender != null) {
            post.setGender(AdoptionTexts.optional(gender, 20));
        }
        if (personality != null) {
            post.setPersonality(AdoptionTexts.optional(personality, 500));
        }
        if (specialNeeds != null) {
            post.setSpecialNeeds(AdoptionTexts.optional(specialNeeds, 500));
        }
        if (areaLabel != null) {
            post.setAreaLabel(AdoptionTexts.optional(areaLabel, 80));
        }
        if (photoUrl != null) {
            String url = photoUrl.trim();
            if (url.isEmpty()) {
                post.setPhotoUrl(null);
            } else if (url.length() > 500) {
                throw new IllegalArgumentException("Text is too long");
            } else {
                post.setPhotoUrl(url);
            }
        }
        if (photoDescription != null) {
            post.setPhotoDescription(AdoptionTexts.optional(photoDescription, 2000));
        }
    }

    private AdoptionPost loadOpenPost(Long postId) {
        AdoptionPost post = loadPost(postId);
        if (post.getStatus() == AdoptionPost.Status.ADOPTED) {
            throw new IllegalStateException("This animal is already adopted");
        }
        return post;
    }

    private AdoptionPost loadPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Adoption post", postId));
    }

    private AdoptionApplication loadApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Adoption application", applicationId));
    }

    private AdoptionPostDTO toDto(AdoptionPost post, boolean staff) {
        return AdoptionPostDTO.builder()
                .id(post.getId())
                .petId(post.getPet() == null ? null : post.getPet().getId())
                .authorId(post.getAuthor().getId())
                .authorFirstName(firstName(post.getAuthor()))
                .status(post.getStatus())
                .title(post.getTitle())
                .animalName(post.getAnimalName())
                .shelterNotes(staff ? post.getShelterNotes() : null)
                .description(post.getDescription())
                .species(post.getSpecies())
                .breed(post.getBreed())
                .ageLabel(post.getAgeLabel())
                .gender(post.getGender())
                .personality(post.getPersonality())
                .specialNeeds(post.getSpecialNeeds())
                .areaLabel(post.getAreaLabel())
                .photoUrl(post.getPhotoUrl())
                .photoDescription(staff ? post.getPhotoDescription() : null)
                .aiDraft(staff ? post.getAiDraft() : null)
                .createdAt(post.getCreatedAt())
                .adoptedAt(post.getAdoptedAt())
                .build();
    }

    private AdoptionCommentDTO toCommentDto(AdoptionComment comment) {
        return AdoptionCommentDTO.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .authorId(comment.getAuthor().getId())
                .authorFirstName(firstName(comment.getAuthor()))
                .body(comment.getBody())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private AdoptionApplicationDTO toApplicationDto(AdoptionApplication application) {
        return AdoptionApplicationDTO.builder()
                .id(application.getId())
                .postId(application.getPost().getId())
                .applicantId(application.getApplicant().getId())
                .applicantFirstName(firstName(application.getApplicant()))
                .status(application.getStatus())
                .housing(application.getHousing())
                .hasGarden(application.getHasGarden())
                .hoursAwayPerDay(application.getHoursAwayPerDay())
                .otherPets(application.getOtherPets())
                .childrenAtHome(application.getChildrenAtHome())
                .experience(application.getExperience())
                .motivation(application.getMotivation())
                .reviewerSummary(application.getReviewerSummary())
                .pointsToVerify(readPoints(application.getPointsToVerify()))
                .suggestedAttention(application.getSuggestedAttention())
                .decisionNote(application.getDecisionNote())
                .createdAt(application.getCreatedAt())
                .build();
    }

    private List<String> readPoints(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            log.warn("adoption points unreadable error_type={}", ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Adoption AI returned invalid JSON.");
        }
    }

    private static Integer hours(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > 24) {
            throw new IllegalArgumentException("Hours away must be between 0 and 24");
        }
        return value;
    }

    private static String firstName(User user) {
        if (user == null || user.getFirstName() == null || user.getFirstName().isBlank()) {
            return null;
        }
        return user.getFirstName().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isStaff(User user) {
        return user.getRole() == User.Role.VETERINARIAN || user.getRole() == User.Role.ADMINISTRATOR;
    }

    private static void requireStaff(User user) {
        requireActive(user);
        if (!isStaff(user)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private static void requireActive(User user) {
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
