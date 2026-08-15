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
 * JDK-{@link HttpClient} implementation of the token exchange. Uses a fixed
 * Google token endpoint and an explicit timeout, and never logs tokens, the
 * client secret, or the response body (which may carry tokens or error hints).
 */
@Component
public class HttpGoogleOAuthClient implements GoogleOAuthClient {
    private static final Logger log = LoggerFactory.getLogger(HttpGoogleOAuthClient.class);
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final GoogleOAuthProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public HttpGoogleOAuthClient(GoogleOAuthProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public TokenExchangeResult exchangeAuthorizationCode(String code, String redirectUri, String codeVerifier) {
        String form = "grant_type=" + url("authorization_code")
                + "&code=" + url(code)
                + "&client_id=" + url(properties.clientId())
                + "&client_secret=" + url(properties.clientSecret())
                + "&redirect_uri=" + url(redirectUri)
                + "&code_verifier=" + url(codeVerifier);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Google token endpoint returned HTTP {}", response.statusCode());
                throw new GoogleOAuthTokenExchangeException(
                        "Google token endpoint returned HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (IOException e) {
            throw new GoogleOAuthTokenExchangeException("Google token exchange failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleOAuthTokenExchangeException("Google token exchange interrupted", e);
        }
    }

    private TokenExchangeResult parse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            String accessToken = root.path("access_token").asText(null);
            String refreshToken = root.path("refresh_token").asText(null);
            long expiresIn = root.path("expires_in").asLong(3600);
            String tokenType = root.path("token_type").asText("Bearer");
            if (accessToken == null || accessToken.isBlank()) {
                throw new GoogleOAuthTokenExchangeException("Google token response is missing an access token");
            }
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new GoogleOAuthTokenExchangeException("Google token response is missing a refresh token");
            }
            return new TokenExchangeResult(accessToken, refreshToken, expiresIn, tokenType);
        } catch (IOException e) {
            throw new GoogleOAuthTokenExchangeException("Unable to parse Google token response", e);
        }
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
