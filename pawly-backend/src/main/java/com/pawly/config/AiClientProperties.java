package com.pawly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python recommendation client. The internal token is read from the environment
 * and is empty unless AI_SERVICE_TOKEN is set outside the repository.
 */
@Data
@Component
@ConfigurationProperties(prefix = "pawly.ai")
public class AiClientProperties {

    private String baseUrl = "http://localhost:8000";
    private String scorePath = "/recommendations/score";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 8000;
    /** Extra attempts after the first call. Hard-capped at 2. */
    private int maxRetries = 1;
    private int maxCandidates = 8;
    private String internalToken = "";
}
