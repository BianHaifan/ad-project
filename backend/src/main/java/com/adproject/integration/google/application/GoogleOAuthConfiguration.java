package com.adproject.integration.google.application;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Validates the Google OAuth web return URI at startup. A present-but-invalid
 * value fails the application fast rather than allowing the callback to ever
 * redirect to an unsafe address. A blank value is allowed: it simply leaves the
 * OAuth feature unconfigured, so the callback fails closed instead of
 * redirecting.
 */
@Configuration
public class GoogleOAuthConfiguration {
    private final GoogleOAuthProperties properties;

    public GoogleOAuthConfiguration(GoogleOAuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateWebReturnUri() {
        properties.resolvedWebReturnUri();
    }
}
