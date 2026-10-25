package com.pawly.adoption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawly.dto.AdoptionDraftDTO;
import com.pawly.dto.AdoptionMatchRequest;
import com.pawly.dto.AdoptionMatchResultDTO;
import com.pawly.dto.AdoptionReviewDTO;
import com.pawly.dto.CreateAdoptionPostRequest;
import com.pawly.dto.DraftAdoptionRequest;
import com.pawly.entity.AdoptionApplication;
import com.pawly.entity.AdoptionPost;
import com.pawly.entity.Pet;
import com.pawly.entity.User;
import com.pawly.repository.AdoptionApplicationRepository;
import com.pawly.repository.AdoptionCommentRepository;
import com.pawly.repository.AdoptionFavoriteRepository;
import com.pawly.repository.AdoptionPostRepository;
import com.pawly.repository.PetRepository;
import com.pawly.service.AdoptionAiClient;
import com.pawly.service.AdoptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdoptionServiceTest {

    @Mock private AdoptionPostRepository postRepository;
    @Mock private AdoptionCommentRepository commentRepository;
    @Mock private AdoptionFavoriteRepository favoriteRepository;
    @Mock private AdoptionApplicationRepository applicationRepository;
    @Mock private PetRepository petRepository;
    @Mock private AdoptionAiClient aiClient;

    private AdoptionService service;
    private User owner;
    private User vet;

    @BeforeEach
    void setUp() {
        service = new AdoptionService(
                postRepository, commentRepository, favoriteRepository, applicationRepository,
                petRepository, aiClient, new ObjectMapper());
        owner = user(1L, User.Role.PET_OWNER, "Nadia");
        vet = user(2L, User.Role.VETERINARIAN, "Sam");
    }

    @Test
    void availableFeedExcludesAdoptedPosts() {
        AdoptionPost available = post(10L, AdoptionPost.Status.AVAILABLE);
        AdoptionPost adopted = post(11L, AdoptionPost.Status.ADOPTED);
        given(postRepository.findByStatusOrderByCreatedAtDesc(AdoptionPost.Status.AVAILABLE))
                .willReturn(List.of(available, adopted));

        var feed = service.list(owner, AdoptionPost.Status.AVAILABLE);

        assertThat(feed).extracting("id").containsExactly(10L);
        assertThat(feed).extracting("status").containsExactly(AdoptionPost.Status.AVAILABLE);
        assertThat(feed.get(0).getShelterNotes()).isNull();
        verify(postRepository).findByStatusOrderByCreatedAtDesc(AdoptionPost.Status.AVAILABLE);
    }

    @Test
    void petOwnerCannotListAdoptedOrPublish() {
        assertThatThrownBy(() -> service.list(owner, AdoptionPost.Status.ADOPTED))
                .isInstanceOf(AccessDeniedException.class);
        verify(postRepository, never()).findByStatusOrderByCreatedAtDesc(any());

        CreateAdoptionPostRequest request = new CreateAdoptionPostRequest();
        request.setTitle("Louna");
        request.setShelterNotes("Chatte rousse à Tunis.");
        assertThatThrownBy(() -> service.create(owner, request))
                .isInstanceOf(AccessDeniedException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void publishCopiesAnimalFactsAndLeavesMedicalIdentityBehind() {
        Pet pet = Pet.builder()
                .id(3L)
                .name("Louna")
                .species("chat")
                .healthInfo("vaccin secret")
                .microchipId("123456789012345")
                .owner(owner)
                .build();
        given(petRepository.findById(3L)).willReturn(Optional.of(pet));
        given(postRepository.save(any())).willAnswer(invocation -> {
            AdoptionPost saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        CreateAdoptionPostRequest request = new CreateAdoptionPostRequest();
        request.setPetId(3L);
        request.setTitle("Louna à Tunis");
        request.setShelterNotes("Chatte rousse, environ 2 ans.");

        var dto = service.create(vet, request);

        assertThat(dto.getStatus()).isEqualTo(AdoptionPost.Status.AVAILABLE);
        assertThat(dto.getAnimalName()).isEqualTo("Louna");
        assertThat(dto.getSpecies()).isEqualTo("chat");
        assertThat(dto.getShelterNotes()).doesNotContain("123456789012345");
        assertThat(dto.getShelterNotes()).doesNotContain("vaccin secret");
    }

    @Test
    void draftAndReviewDoNotChangeStatus() {
        AdoptionPost post = post(10L, AdoptionPost.Status.AVAILABLE);
        post.setDescription("Fiche relue");
        post.setShelterNotes("Chatte rousse à Tunis.");
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(postRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(aiClient.draft(any(), any())).willReturn(AdoptionDraftDTO.builder()
                .name("Louna")
                .description("Brouillon")
                .personalityTags(List.of())
                .uncertainties(List.of("statut vaccinal non indiqué"))
                .build());

        service.draft(vet, 10L, new DraftAdoptionRequest());

        assertThat(post.getStatus()).isEqualTo(AdoptionPost.Status.AVAILABLE);
        assertThat(post.getDescription()).isEqualTo("Fiche relue");
        assertThat(post.getAiDraft()).contains("Louna");

        AdoptionApplication application = AdoptionApplication.builder()
                .id(5L)
                .post(post)
                .applicant(owner)
                .status(AdoptionApplication.Status.SUBMITTED)
                .housing("appartement")
                .build();
        given(applicationRepository.findById(5L)).willReturn(Optional.of(application));
        given(aiClient.review(post, application)).willThrow(
                new IllegalStateException("Adoption AI returned a decision. It was discarded."));

        assertThatThrownBy(() -> service.review(vet, 5L)).isInstanceOf(IllegalStateException.class);
        assertThat(post.getStatus()).isEqualTo(AdoptionPost.Status.AVAILABLE);
        assertThat(application.getStatus()).isEqualTo(AdoptionApplication.Status.SUBMITTED);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void previewDraftAndReviewDoNotSave() {
        given(aiClient.draft(any(), any())).willReturn(AdoptionDraftDTO.builder()
                .name("Louna").description("Brouillon").personalityTags(List.of()).uncertainties(List.of()).build());
        DraftAdoptionRequest request = new DraftAdoptionRequest();
        request.setNotes("Chatte rousse à Tunis.");
        assertThat(service.previewDraft(vet, request).getDescription()).isEqualTo("Brouillon");
        verify(postRepository, never()).save(any());

        AdoptionPost post = post(10L, AdoptionPost.Status.AVAILABLE);
        AdoptionApplication application = AdoptionApplication.builder()
                .id(5L).post(post).applicant(owner).status(AdoptionApplication.Status.SUBMITTED).housing("appartement").build();
        given(applicationRepository.findById(5L)).willReturn(Optional.of(application));
        given(aiClient.review(post, application)).willReturn(AdoptionReviewDTO.builder()
                .summary("À lire").pointsToVerify(List.of()).suggestedAttention("low").build());
        assertThat(service.previewReview(vet, 5L).getSummary()).isEqualTo("À lire");
        assertThat(application.getStatus()).isEqualTo(AdoptionApplication.Status.SUBMITTED);
        assertThat(application.getReviewerSummary()).isNull();
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void matchSendsOnlyAvailablePosts() {
        AdoptionPost available = post(12L, AdoptionPost.Status.AVAILABLE);
        AdoptionPost adopted = post(13L, AdoptionPost.Status.ADOPTED);
        given(postRepository.findTop20ByStatusOrderByCreatedAtDesc(AdoptionPost.Status.AVAILABLE))
                .willReturn(List.of(available, adopted));
        given(aiClient.match(any(), any())).willReturn(AdoptionMatchResultDTO.builder().matches(List.of()).build());
        AdoptionMatchRequest request = new AdoptionMatchRequest();
        request.setHousing("appartement");

        service.match(owner, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdoptionPost>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).match(eq(request), captor.capture());
        assertThat(captor.getValue()).extracting(AdoptionPost::getId).containsExactly(12L);
        verify(postRepository).findTop20ByStatusOrderByCreatedAtDesc(AdoptionPost.Status.AVAILABLE);
    }

    private static User user(Long id, User.Role role, String firstName) {
        return User.builder()
                .id(id)
                .email(firstName.toLowerCase() + "@example.com")
                .firstName(firstName)
                .lastName("Test")
                .role(role)
                .status(User.UserStatus.ACTIVE)
                .passwordHash("hash")
                .build();
    }

    private AdoptionPost post(Long id, AdoptionPost.Status status) {
        return AdoptionPost.builder()
                .id(id)
                .author(vet)
                .status(status)
                .title("Louna")
                .shelterNotes("Notes du refuge")
                .species("chat")
                .areaLabel("Tunis")
                .build();
    }
}
