package com.pawly.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawly.dto.TriageRequest;
import com.pawly.dto.TriageResponse;
import com.pawly.service.MistralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triage controller — delegates AI work to the ai-pawly Python microservice.
 */
@RestController
@RequestMapping("/api/triage")
@RequiredArgsConstructor
@Slf4j
public class TriageController {

    private final RestTemplate  restTemplate;
    private final ObjectMapper  objectMapper;

    private static final String AI_SERVICE_TRIAGE = "http://localhost:8000/triage";
    private static final String AI_SERVICE_SEED   = "http://localhost:8000/ingest/seed";

    // ─── POST /api/triage ─────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TriageResponse> performTriage(@Valid @RequestBody TriageRequest request) {
        log.info("Triage request - species={}, symptoms={}", request.getSpecies(), request.getSymptoms());

        try {
            // Forward to ai-pawly Python service
            Map<String, Object> body = new HashMap<>();
            body.put("symptoms", request.getSymptoms());
            if (request.getSpecies()  != null) body.put("species",  request.getSpecies());
            if (request.getPetName()  != null) body.put("pet_name", request.getPetName());
            if (request.getPetId()    != null) body.put("pet_id",   request.getPetId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String raw = restTemplate.postForObject(
                    AI_SERVICE_TRIAGE, new HttpEntity<>(body, headers), String.class);

            if (raw == null) throw new RuntimeException("Empty response from ai-pawly");

            JsonNode node     = objectMapper.readTree(raw);
            String assessment = node.path("assessment").asText("");
            String urgency    = node.path("urgency_level").asText("UNKNOWN");

            List<String> sources = new ArrayList<>();
            node.path("knowledge_sources").forEach(n -> sources.add(n.asText()));

            return ResponseEntity.ok(TriageResponse.builder()
                    .urgencyLevel(urgency)
                    .assessment(assessment)
                    .knowledgeSources(sources)
                    .timestamp(LocalDateTime.now())
                    .aiGenerated(true)
                    .build());

        } catch (Exception e) {
            log.error("Triage failed", e);
            return ResponseEntity.ok(TriageResponse.builder()
                    .urgencyLevel("UNKNOWN")
                    .assessment("AI triage service unavailable: " + e.getMessage()
                                + " — Ensure the ai-pawly service is running: cd ai-pawly && uvicorn main:app --reload")
                    .knowledgeSources(List.of())
                    .timestamp(LocalDateTime.now())
                    .aiGenerated(false)
                    .build());
        }
    }

    // ─── POST /api/triage/seed ────────────────────────────────────────────────
    @PostMapping("/seed")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<String> seedKnowledgeBase() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String result = restTemplate.postForObject(
                    AI_SERVICE_SEED, new HttpEntity<>(null, headers), String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok("Seed failed: " + e.getMessage());
        }
    }

    // ─── GET /api/triage/status ───────────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok(
            "Triage service ready. POST /api/triage with {symptoms, species, petName}. " +
            "Ensure ai-pawly Python service is running on port 8000.");
    }
}