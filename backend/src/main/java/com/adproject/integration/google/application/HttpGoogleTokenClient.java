package com.adproject.integration.google.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JDK-{@link HttpClient} implementation of the refresh-token grant. Uses the
 * fixed Google token endpoint and an explicit timeout, and never logs the
 * refresh token or the response body (which may carry tokens or error hints).
 */
@Component
public class HttpGoogleTokenClient implements GoogleTokenClient {
    private static final Logger log = LoggerFactory.getLogger(HttpGoogleTokenClient.class);
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final GoogleOAuthProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public HttpGoogleTokenClient(GoogleOAuthProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public RefreshedToken refreshAccessToken(String refreshToken) {
        String form = "grant_type=" + url("refresh_token")
                + "&refresh_token=" + url(refreshToken)
                + "&client_id=" + url(properties.clientId())
                + "&client_secret=" + url(properties.clientSecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.TRANSIENT,
                    "Google token refresh failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.TRANSIENT,
                    "Google token refresh interrupted");
        }

        int status = response.statusCode();
        if (status == 400 || status == 401) {
            // invalid_grant is the one body detail worth classifying; nothing else is surfaced.
            if (isInvalidGrant(response.body())) {
                throw new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.INVALID_GRANT,
                        "Google refresh token is invalid or revoked");
            }
            throw new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.TRANSIENT,
                    "Google token refresh rejected");
        }
        if (status / 100 != 2) {
            log.warn("Google token endpoint returned HTTP {} for refresh", status);
            throw new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.TRANSIENT,
                    "Google token refresh failed");
        }
        return parse(response.body());
    }

    private boolean isInvalidGrant(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            return "invalid_grant".equals(root.path("error").asText(null));
        } catch (IOException e) {
            return false;
        }
    }

    private RefreshedToken parse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            String accessToken = root.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.TRANSIENT,
                        "Google token response is missing an access token");
            }
            long expiresIn = root.path("expires_in").asLong(3600);
            String refreshToken = root.path("refresh_token").asText(null);
            return new RefreshedToken(accessToken, expiresIn, refreshToken);
        } catch (IOException e) {
            throw new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.TRANSIENT,
                    "Unable to parse Google token response");
        }
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
