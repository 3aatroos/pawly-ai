package com.pawly.store;

import com.pawly.config.AiClientProperties;
import com.pawly.service.store.AiRecommendationClient;
import com.pawly.service.store.ContentBasedRanker;
import com.pawly.service.store.RecommendationContextBuilder;
import com.pawly.service.store.RecommendationModels.CandidateView;
import com.pawly.service.store.RecommendationModels.HistoryView;
import com.pawly.service.store.RecommendationModels.ProfileView;
import com.pawly.service.store.RecommendationModels.ScoreRequestView;
import com.pawly.service.store.RecommendationValidator;
import com.pawly.entity.Pet;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

class RecommendationClientTest {

    private final ContentBasedRanker ranker = new ContentBasedRanker();
    private final RecommendationValidator validator = new RecommendationValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void content_ranker_prefers_the_matching_species() {
        ScoreRequestView request = sampleRequest();
        var ranked = ranker.rank(request);
        assertEquals("5", ranked.get(0).productId());
        assertTrue(ranked.get(0).score() > ranked.get(1).score());
        assertEquals("content_based", ranked.get(0).source());
        assertFalse(ranked.get(0).justification().contains("@"));
    }

    @Test
    void context_json_keeps_species_and_drops_identity() throws Exception {
        Pet pet = Pet.builder()
                .name("Rex")
                .species("Dog")
                .dateOfBirth(LocalDate.of(2010, 3, 15))
                .healthInfo("call 0600000000")
                .microchipId("123456789012345")
                .build();
        var profile = new RecommendationContextBuilder().profile(List.of(pet), List.of(new BigDecimal("12.00")));
        String json = mapper.writeValueAsString(profile);
        assertTrue(json.contains("dog"));
        assertTrue(json.contains("senior"));
        assertTrue(json.contains("budget"));
        assertFalse(json.contains("Rex"));
        assertFalse(json.contains("2010-03-15"));
        assertFalse(json.contains("0600000000"));
        assertFalse(json.contains("@"));
    }

    @Test
    void validator_drops_unknown_products_and_strips_contact_text() {
        var cleaned = validator.sanitize(List.of(
                new com.pawly.service.store.RecommendationModels.ScoredView(
                        "5", 0.8, "highly_relevant", "See secret.user@example.com now", "qlora"),
                new com.pawly.service.store.RecommendationModels.ScoredView(
                        "999", 0.9, "highly_relevant", "Outside the candidate set", "qlora"),
                new com.pawly.service.store.RecommendationModels.ScoredView(
                        "5", 1.4, "highly_relevant", "Out of range", "qlora")
        ), Set.of("5"), "qlora");
        assertEquals(1, cleaned.size());
        assertEquals("5", cleaned.get(0).productId());
        assertFalse(cleaned.get(0).justification().contains("@"));
        assertEquals("qlora", cleaned.get(0).source());
    }

    @Test
    void client_retries_a_transient_error_then_accepts_the_score() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        AiClientProperties properties = new AiClientProperties();
        properties.setBaseUrl("http://ai.test");
        properties.setScorePath("/recommendations/score");
        properties.setMaxRetries(1);
        properties.setInternalToken("local-test-token");
        AiRecommendationClient client = new AiRecommendationClient(properties, validator, template);
        String url = "http://ai.test/recommendations/score";

        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Pawly-Internal-Token", "local-test-token"))
                .andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo(url))
                .andExpect(content().string(containsString("\"pet_species\"")))
                .andRespond(withSuccess("""
                        {"source":"qlora","recommendations":[
                          {"product_id":"5","score":0.82,"relevance_class":"highly_relevant","justification":"Species match."},
                          {"product_id":"999","score":0.99,"relevance_class":"highly_relevant","justification":"Ignored."}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var result = client.score(sampleRequest(), Set.of("5", "7"));
        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("5", result.get().get(0).productId());
        assertEquals("qlora", result.get().get(0).source());
        server.verify();
    }

    @Test
    void client_does_not_retry_a_rejected_payload() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        AiClientProperties properties = new AiClientProperties();
        properties.setBaseUrl("http://ai.test");
        properties.setMaxRetries(1);
        AiRecommendationClient client = new AiRecommendationClient(properties, validator, template);
        server.expect(requestTo("http://ai.test/recommendations/score"))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY));

        assertTrue(client.score(sampleRequest(), Set.of("5")).isEmpty());
        server.verify();
    }

    private ScoreRequestView sampleRequest() {
        return new ScoreRequestView(
                new ProfileView(List.of("dog"), List.of("senior"), "budget"),
                List.<HistoryView>of(),
                List.of(
                        new CandidateView("5", "Dog Kibble", "Food", "dog", 19.99, "Food for dogs", 5.0, 4),
                        new CandidateView("7", "Cat Litter", "Hygiene", "cat", 12.0, "Litter for cats", 0.0, 0)
                ),
                8
        );
    }
}
