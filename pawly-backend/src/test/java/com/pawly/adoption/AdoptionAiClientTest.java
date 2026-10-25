package com.pawly.adoption;

import com.pawly.dto.AdoptionMatchRequest;
import com.pawly.entity.AdoptionApplication;
import com.pawly.entity.AdoptionPost;
import com.pawly.entity.User;
import com.pawly.service.AdoptionAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AdoptionAiClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private AdoptionAiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new AdoptionAiClient(restTemplate, "http://localhost:8000", "");
    }

    @Test
    void draftPayloadUsesSnakeCaseAndDropsContactFromTheError() {
        server.expect(requestTo("http://localhost:8000/adoption/draft"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"notes\":\"Chatte rousse à Tunis\""),
                        org.hamcrest.Matchers.containsString("\"photo_description\":\"Chat roux dans un box\""),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ada@example.com"))
                )))
                .andRespond(withSuccess("""
                        {"name":"Louna","species":"chat","breed":null,"age":"environ 2 ans","gender":"femelle",
                         "personality_tags":["calme"],"description":"Chatte au refuge de Tunis.",
                         "special_needs":null,"uncertainties":["statut vaccinal non indiqué"]}
                        """, MediaType.APPLICATION_JSON));

        var draft = client.draft("Chatte rousse à Tunis", "Chat roux dans un box");
        assertEquals("Louna", draft.getName());
        assertEquals("femelle", draft.getGender());
        server.verify();

        server.reset();
        server.expect(requestTo("http://localhost:8000/adoption/draft"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"write ada@example.com\"}"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.draft("Chatte rousse à Tunis", null));
        assertEquals("Adoption AI request failed.", error.getMessage());
        assertFalse(error.getMessage().contains("ada@example.com"));
    }

    @Test
    void matchAndReviewLeaveIdentityInTheDatabase() {
        User person = User.builder()
                .id(4L)
                .email("ada@example.com")
                .phone("0612345678")
                .address("12 rue des Oliviers, Tunis")
                .firstName("Ada")
                .lastName("Ben Ali")
                .role(User.Role.PET_OWNER)
                .status(User.UserStatus.ACTIVE)
                .passwordHash("hash")
                .build();
        AdoptionPost post = AdoptionPost.builder()
                .id(12L)
                .author(person)
                .status(AdoptionPost.Status.AVAILABLE)
                .title("Louna")
                .animalName("Louna")
                .shelterNotes("SECRET-NOTES ada@example.com")
                .description("Chatte du refuge à Tunis.")
                .species("chat")
                .areaLabel("Tunis")
                .photoUrl("http://files.local/secret-photo")
                .build();
        AdoptionMatchRequest lifestyle = new AdoptionMatchRequest();
        lifestyle.setHousing("appartement");
        lifestyle.setSector("Tunis");

        server.expect(requestTo("http://localhost:8000/adoption/match"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"location\":\"Tunis\""),
                        org.hamcrest.Matchers.containsString("\"housing\":\"appartement\""),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ada@example.com")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("0612345678")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("rue des Oliviers")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET-NOTES")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-photo")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Ada"))
                )))
                .andRespond(withSuccess("""
                        {"matches":[{"post_id":12,"score":74,"reason":"Chat calme à Tunis.","warning":null}]}
                        """, MediaType.APPLICATION_JSON));

        var matched = client.match(lifestyle, List.of(post));
        assertEquals(12L, matched.getMatches().get(0).getPostId());
        assertEquals(74, matched.getMatches().get(0).getScore());
        server.verify();
        server.reset();

        AdoptionApplication application = AdoptionApplication.builder()
                .id(5L)
                .post(post)
                .applicant(person)
                .status(AdoptionApplication.Status.SUBMITTED)
                .housing("appartement")
                .motivation("Je rentre le midi.")
                .build();
        server.expect(requestTo("http://localhost:8000/adoption/review-application"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Je rentre le midi."),
                        org.hamcrest.Matchers.containsString("\"name\":\"Louna\""),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ada@example.com")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("0612345678")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("rue des Oliviers")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET-NOTES"))
                )))
                .andRespond(withSuccess("""
                        {"summary":"Louna est une chatte à Tunis.","points_to_verify":["Confirmer le midi."],
                         "suggested_attention":"medium"}
                        """, MediaType.APPLICATION_JSON));

        var review = client.review(post, application);
        assertEquals("medium", review.getSuggestedAttention());
        assertEquals(AdoptionPost.Status.AVAILABLE, post.getStatus());
        assertEquals(AdoptionApplication.Status.SUBMITTED, application.getStatus());
        server.verify();
    }
}
