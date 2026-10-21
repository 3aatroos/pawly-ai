package com.pawly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls the ai-pawly Python microservice for AI triage.
 * The Python service handles RAG retrieval and Mistral/Ollama internally.
 */
@Service
@Slf4j
public class MistralService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ai-pawly Python microservice endpoint
    private static final String AI_SERVICE_URL = "http://localhost:8000/triage";

    public MistralService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Delegate triage to the Python ai-pawly service.
     * Returns the full JSON response as a string; caller parses as needed.
     */
    public String generateTriageResponse(String symptoms, String species, String petName) {
        try {
            log.info("Calling ai-pawly Python service for triage...");

            Map<String, Object> body = new HashMap<>();
            body.put("symptoms", symptoms);
            if (species  != null) body.put("species",  species);
            if (petName  != null) body.put("pet_name", petName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String raw = restTemplate.postForObject(AI_SERVICE_URL, request, String.class);
            log.debug("ai-pawly response: {}", raw);

            if (raw != null) {
                JsonNode node = objectMapper.readTree(raw);
                String assessment = node.path("assessment").asText("").trim();
                log.info("Triage generation successful ({} chars)", assessment.length());
                return assessment;
            }

            return "Unable to generate triage response at this time.";

        } catch (Exception e) {
            log.error("Error calling ai-pawly service", e);
            return "Error generating triage response: " + e.getMessage()
                   + " — Make sure the ai-pawly Python service is running on port 8000.";
        }
    }

    /** Convenience overload for backward compatibility (prompt-based). */
    public String generateTriageResponse(String prompt) {
        return generateTriageResponse(prompt, null, null);
    }
}