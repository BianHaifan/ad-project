package com.adproject.agent.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(String plannerBaseUrl, long previewTtlSeconds,
                              Duration connectTimeout, Duration readTimeout) {
    public AgentProperties {
        if (plannerBaseUrl == null || plannerBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Agent planner base URL is required");
        }
        if (previewTtlSeconds < 60 || previewTtlSeconds > 3600) {
            throw new IllegalArgumentException("Agent preview TTL must be between 60 and 3600 seconds");
        }
    }
}
