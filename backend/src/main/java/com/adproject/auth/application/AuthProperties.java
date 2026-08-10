package com.adproject.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(long accessTokenSeconds, long refreshTokenSeconds, String jwtSecret) {
    public AuthProperties {
        if (accessTokenSeconds != 7200) {
            throw new IllegalArgumentException("Access token lifetime must match the OpenAPI contract (7200 seconds)");
        }
        if (refreshTokenSeconds != 2592000) {
            throw new IllegalArgumentException("Refresh token lifetime must match the OpenAPI contract (2592000 seconds)");
        }
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
    }
}
