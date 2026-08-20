package com.adproject.agent.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.deepseek")
public record DeepSeekProperties(String apiKey, String model, String baseUrl,
                                 Duration connectTimeout, Duration readTimeout) {
    public DeepSeekProperties {
        model = model == null || model.isBlank() ? "deepseek-v4-pro" : model;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(60) : readTimeout;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
