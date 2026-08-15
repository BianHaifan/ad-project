package com.adproject.integration.google.api;

import com.adproject.integration.google.application.GoogleOAuthCallbackOutcome;
import com.adproject.integration.google.application.GoogleOAuthProperties;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Turns a classified callback outcome into a browser handoff: an HTTP 303 to the
 * single server-configured web return URI carrying only a safe {@code
 * googleOAuth} result value. No token, code, state, error, or identity ever
 * reaches the redirect URL.
 */
@Component
public class GoogleOAuthCallbackPresenter {
    private final GoogleOAuthProperties properties;

    public GoogleOAuthCallbackPresenter(GoogleOAuthProperties properties) {
        this.properties = properties;
    }

    public ResponseEntity<Void> redirect(GoogleOAuthCallbackOutcome outcome) {
        URI base = properties.resolvedWebReturnUri();
        String separator = base.toString().contains("?") ? "&" : "?";
        URI location = URI.create(base + separator + "googleOAuth=" + outcome.value());
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
