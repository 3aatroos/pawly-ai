package com.pawly.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.pawly.config.AiClientProperties;
import com.pawly.dto.AdoptionDraftDTO;
import com.pawly.dto.AdoptionMatchItemDTO;
import com.pawly.dto.AdoptionMatchRequest;
import com.pawly.dto.AdoptionMatchResultDTO;
import com.pawly.dto.AdoptionReviewDTO;
import com.pawly.entity.AdoptionApplication;
import com.pawly.entity.AdoptionPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server-side calls to ai-pawly. The browser never uses port 8000.
 * Payloads contain animal facts and lifestyle answers only.
 */
@Service
@Slf4j
public class AdoptionAiClient {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");
    private static final Pattern VERDICT = Pattern.compile(
            "(candidature|dossier)\\s+(est\\s+)?(refus|accept)"
                    + "|refus automatique|accepter la candidature|refuser la candidature",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<String> ATTENTION = Set.of("low", "medium", "high");

    private final RestTemplate restTemplate;
    private final ObjectMapper aiMapper;
    private final String baseUrl;
    private final String internalToken;

    @Autowired
    public AdoptionAiClient(AiClientProperties properties) {
        this(buildTemplate(), properties.getBaseUrl(), properties.getInternalToken());
    }

    public AdoptionAiClient(RestTemplate restTemplate, String baseUrl, String internalToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "http://localhost:8000" : baseUrl.replaceAll("/$", "");
        this.internalToken = internalToken == null ? "" : internalToken;
        this.aiMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public AdoptionDraftDTO draft(String notes, String photoDescription) {
        DraftWire body = new DraftWire(requireOutbound(notes, 4000), optionalOutbound(photoDescription, 2000));
        DraftWireResult result = post("/adoption/draft", body, DraftWireResult.class);
        String description = optionalOutbound(result.description, 2000);
        return AdoptionDraftDTO.builder()
                .name(optionalOutbound(result.name, 80))
                .species(optionalOutbound(result.species, 80))
                .breed(optionalOutbound(result.breed, 80))
                .age(optionalOutbound(result.age, 80))
                .gender(normalizeGender(result.gender))
                .personalityTags(cleanList(result.personalityTags, 12))
                .description(description == null ? "" : description)
                .specialNeeds(optionalOutbound(result.specialNeeds, 500))
                .uncertainties(cleanList(result.uncertainties, 12))
                .build();
    }

    public AdoptionMatchResultDTO match(AdoptionMatchRequest lifestyle, List<AdoptionPost> posts) {
        List<CardWire> cards = new ArrayList<>();
        for (AdoptionPost post : posts) {
            if (post.getStatus() != AdoptionPost.Status.AVAILABLE || post.getId() == null) {
                continue;
            }
            if (cards.size() == 20) {
                break;
            }
            cards.add(new CardWire(
                    post.getId(),
                    optionalOutbound(post.getSpecies(), 80),
                    optionalOutbound(post.getBreed(), 80),
                    optionalOutbound(post.getAgeLabel(), 80),
                    optionalOutbound(post.getPersonality(), 500),
                    optionalOutbound(post.getSpecialNeeds(), 500),
                    optionalOutbound(post.getAreaLabel(), 80)
            ));
        }
        LifestyleWire life = new LifestyleWire(
                optionalOutbound(lifestyle.getHousing(), 80),
                lifestyle.getHasGarden(),
                lifestyle.getHoursAwayPerDay(),
                lifestyle.getOtherPets(),
                lifestyle.getChildren(),
                optionalOutbound(lifestyle.getExperience(), 80),
                optionalOutbound(lifestyle.getSector(), 80)
        );
        MatchWireResult result = post("/adoption/match", new MatchWire(life, cards), MatchWireResult.class);
        Set<Long> allowed = cards.stream().map(CardWire::id).collect(java.util.stream.Collectors.toSet());
        List<AdoptionMatchItemDTO> matches = new ArrayList<>();
        if (result.matches != null) {
            for (MatchItemWire item : result.matches) {
                if (item.postId == null || item.score == null || !allowed.contains(item.postId)) {
                    continue;
                }
                if (item.score < 0 || item.score > 100) {
                    continue;
                }
                String reason = optionalOutbound(item.reason, 400);
                if (reason == null) {
                    continue;
                }
                matches.add(AdoptionMatchItemDTO.builder()
                        .postId(item.postId)
                        .score(item.score)
                        .reason(reason)
                        .warning(optionalOutbound(item.warning, 400))
                        .build());
            }
        }
        matches.sort(Comparator
                .comparingInt(AdoptionMatchItemDTO::getScore).reversed()
                .thenComparing(AdoptionMatchItemDTO::getPostId));
        if (matches.size() > 5) {
            matches = new ArrayList<>(matches.subList(0, 5));
        }
        return AdoptionMatchResultDTO.builder().matches(List.copyOf(matches)).build();
    }

    public AdoptionReviewDTO review(AdoptionPost post, AdoptionApplication application) {
        AnimalWire animal = new AnimalWire(
                optionalOutbound(post.getAnimalName(), 80),
                optionalOutbound(post.getSpecies(), 80),
                optionalOutbound(post.getBreed(), 80),
                optionalOutbound(post.getAgeLabel(), 80),
                optionalOutbound(post.getGender(), 80),
                optionalOutbound(post.getPersonality(), 500),
                optionalOutbound(post.getDescription(), 1500),
                optionalOutbound(post.getSpecialNeeds(), 500),
                optionalOutbound(post.getAreaLabel(), 80)
        );
        FormWire form = new FormWire(
                optionalOutbound(application.getHousing(), 80),
                application.getHasGarden(),
                application.getHoursAwayPerDay(),
                application.getOtherPets(),
                application.getChildrenAtHome(),
                optionalOutbound(application.getExperience(), 80),
                optionalOutbound(application.getMotivation(), 2000)
        );
        ReviewWireResult result = post("/adoption/review-application", new ReviewWire(animal, form), ReviewWireResult.class);
        String summary = requireOutbound(result.summary, 2000);
        if (VERDICT.matcher(summary).find()) {
            throw new IllegalStateException("Adoption AI returned a decision. It was discarded.");
        }
        List<String> points = cleanList(result.pointsToVerify, 12);
        for (String point : points) {
            if (VERDICT.matcher(point).find()) {
                throw new IllegalStateException("Adoption AI returned a decision. It was discarded.");
            }
        }
        String attention = result.suggestedAttention == null
                ? ""
                : result.suggestedAttention.trim().toLowerCase(Locale.ROOT);
        if (!ATTENTION.contains(attention)) {
            throw new IllegalStateException("Adoption AI returned an invalid attention level.");
        }
        return AdoptionReviewDTO.builder()
                .summary(summary)
                .pointsToVerify(points)
                .suggestedAttention(attention)
                .build();
    }

    private <T> T post(String path, Object body, Class<T> type) {
        String url = baseUrl + path;
        final String payload;
        try {
            payload = aiMapper.writeValueAsString(body);
        } catch (Exception ex) {
            log.warn("adoption ai payload unreadable error_type={}", ex.getClass().getSimpleName());
            throw new IllegalStateException("Adoption AI request failed.");
        }
        try {
            log.info("adoption ai call path={}", path);
            String raw = restTemplate.execute(url, HttpMethod.POST, request -> {
                request.getHeaders().addAll(headers());
                StreamUtils.copy(payload, StandardCharsets.UTF_8, request.getBody());
            }, response -> response.getBody() == null
                    ? ""
                    : StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8));
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Adoption AI returned an empty response.");
            }
            return aiMapper.readValue(raw, type);
        } catch (HttpStatusCodeException ex) {
            log.warn("adoption ai status={} path={}", ex.getStatusCode().value(), path);
            throw new IllegalStateException(safeDetail(ex));
        } catch (ResourceAccessException ex) {
            log.warn("adoption ai unreachable path={} error_type={}", path, ex.getClass().getSimpleName());
            throw new IllegalStateException("Adoption AI timed out. Please try again.");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("adoption ai unreadable path={} error_type={}", path, ex.getClass().getSimpleName());
            throw new IllegalStateException("Adoption AI returned invalid JSON.");
        } catch (Exception ex) {
            log.warn("adoption ai failed path={} error_type={}", path, ex.getClass().getSimpleName());
            throw new IllegalStateException("Adoption AI returned invalid JSON.");
        }
    }

    private String safeDetail(HttpStatusCodeException ex) {
        try {
            JsonNode detail = aiMapper.readTree(ex.getResponseBodyAsString()).get("detail");
            if (detail != null && detail.isTextual()) {
                String text = detail.asText().trim();
                if (!text.isEmpty() && text.length() <= 180 && !hasContact(text)) {
                    return text;
                }
            }
        } catch (Exception ignored) {
            // The provider body can echo a note. It stays out of the log and the response.
        }
        return "Adoption AI request failed.";
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!internalToken.isBlank()) {
            headers.set("X-Pawly-Internal-Token", internalToken);
        }
        return headers;
    }

    private static RestTemplate buildTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(60000);
        return new RestTemplate(factory);
    }

    private static String requireOutbound(String value, int max) {
        String text = optionalOutbound(value, max);
        if (text == null) {
            throw new IllegalStateException("Adoption AI returned an empty field.");
        }
        return text;
    }

    private static String optionalOutbound(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max || hasContact(text)) {
            throw new IllegalStateException("Adoption AI returned a field that cannot be stored.");
        }
        return text;
    }

    private static List<String> cleanList(List<String> values, int maxItems) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            String text = optionalOutbound(value, 300);
            if (text != null) {
                cleaned.add(text);
            }
            if (cleaned.size() == maxItems) {
                break;
            }
        }
        return List.copyOf(cleaned);
    }

    private static String normalizeGender(String value) {
        String text = optionalOutbound(value, 20);
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("mâle") || lower.equals("male")) {
            return "mâle";
        }
        if (lower.equals("femelle") || lower.equals("female")) {
            return "femelle";
        }
        throw new IllegalStateException("Adoption AI returned an invalid gender.");
    }

    private static boolean hasContact(String value) {
        return EMAIL.matcher(value).find() || LONG_DIGITS.matcher(value).find();
    }

    private record DraftWire(String notes, String photoDescription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class DraftWireResult {
        public String name;
        public String species;
        public String breed;
        public String age;
        public String gender;
        public List<String> personalityTags;
        public String description;
        public String specialNeeds;
        public List<String> uncertainties;
    }

    private record LifestyleWire(
            String housing,
            Boolean hasGarden,
            Integer hoursAwayPerDay,
            Boolean otherPets,
            Boolean children,
            String experience,
            String sector
    ) {
    }

    private record CardWire(
            Long id,
            String species,
            String breed,
            String age,
            String personality,
            String specialNeeds,
            String location
    ) {
    }

    private record MatchWire(LifestyleWire lifestyle, List<CardWire> posts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class MatchWireResult {
        public List<MatchItemWire> matches;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class MatchItemWire {
        public Long postId;
        public Integer score;
        public String reason;
        public String warning;
    }

    private record AnimalWire(
            String name,
            String species,
            String breed,
            String age,
            String gender,
            String personality,
            String description,
            String specialNeeds,
            String location
    ) {
    }

    private record FormWire(
            String housing,
            Boolean hasGarden,
            Integer hoursAwayPerDay,
            Boolean otherPets,
            Boolean children,
            String experience,
            String motivation
    ) {
    }

    private record ReviewWire(AnimalWire animal, FormWire form) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ReviewWireResult {
        public String summary;
        public List<String> pointsToVerify;
        public String suggestedAttention;
    }
}
