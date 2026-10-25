package com.pawly.service.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawly.config.AiClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.pawly.service.store.RecommendationModels.ScoreRequestView;
import static com.pawly.service.store.RecommendationModels.ScoredView;

/**
 * Calls FastAPI /recommendations/score. Retries are limited to transient
 * failures. Timeouts and 4xx responses stop immediately so the caller can
 * use the content-based fallback.
 */
@Service
@Slf4j
public class AiRecommendationClient {

    private final AiClientProperties properties;
    private final RestTemplate restTemplate;
    private final RecommendationValidator validator;

    @Autowired
    public AiRecommendationClient(AiClientProperties properties,
                                  RecommendationValidator validator) {
        this(properties, validator, buildTemplate(properties));
    }

    public AiRecommendationClient(AiClientProperties properties,
                                  RecommendationValidator validator,
                                  RestTemplate restTemplate) {
        this.properties = properties;
        this.validator = validator;
        this.restTemplate = restTemplate;
    }

    public Optional<List<ScoredView>> score(ScoreRequestView request, Set<String> allowedIds) {
        int retries = Math.max(0, Math.min(properties.getMaxRetries(), 2));
        int attempts = retries + 1;
        String url = properties.getBaseUrl().replaceAll("/$", "") + properties.getScorePath();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                ResponseEntity<AiScoreBody> response = restTemplate.postForEntity(
                        url,
                        new HttpEntity<>(request, headers()),
                        AiScoreBody.class
                );
                AiScoreBody body = response.getBody();
                if (body == null) {
                    log.warn("Recommendation service returned an empty body attempt={}/{}", attempt, attempts);
                    return Optional.empty();
                }
                List<ScoredView> incoming = body.recommendations() == null
                        ? List.of()
                        : body.recommendations().stream()
                        .map(item -> new ScoredView(
                                item.productId(),
                                item.score(),
                                item.relevanceClass(),
                                item.justification(),
                                body.source()
                        ))
                        .toList();
                List<ScoredView> valid = validator.sanitize(incoming, allowedIds, body.source());
                if (valid.isEmpty()) {
                    log.warn("Recommendation service response failed validation attempt={}/{}", attempt, attempts);
                    return Optional.empty();
                }
                log.info("Recommendation service responded source={} count={}", valid.get(0).source(), valid.size());
                return Optional.of(valid);
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                if (ex.getStatusCode().is4xxClientError()) {
                    log.warn("Recommendation service rejected the request status={}", status);
                    return Optional.empty();
                }
                log.warn("Recommendation service transient status={} attempt={}/{}", status, attempt, attempts);
            } catch (ResourceAccessException ex) {
                log.warn("Recommendation service unreachable attempt={}/{} error_type={}",
                        attempt, attempts, ex.getClass().getSimpleName());
            } catch (RuntimeException ex) {
                log.warn("Recommendation service response unreadable error_type={}", ex.getClass().getSimpleName());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = properties.getInternalToken();
        if (token != null && !token.isBlank()) {
            headers.set("X-Pawly-Internal-Token", token);
        }
        return headers;
    }

    private static RestTemplate buildTemplate(AiClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(200, properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(200, properties.getReadTimeoutMs()));
        return new RestTemplate(factory);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiScoreBody(
            String source,
            List<AiScoreItem> recommendations
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiScoreItem(
            @JsonProperty("product_id") String productId,
            double score,
            @JsonProperty("relevance_class") String relevanceClass,
            String justification
    ) {
    }
}
