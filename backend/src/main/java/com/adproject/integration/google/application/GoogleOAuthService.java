package com.adproject.integration.google.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.integration.google.api.GoogleOAuthDtos;
import com.adproject.integration.google.domain.GoogleConnectionStatus;
import com.adproject.integration.google.infrastructure.GoogleOAuthStateEntity;
import com.adproject.integration.google.infrastructure.GoogleOAuthStateRepository;
import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionEntity;
import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionRepository;
import com.adproject.user.domain.UserRole;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Secure recruiter Google account connection: begin authorization, handle the
 * OAuth callback, read connection status, and disconnect. The callback is
 * unauthenticated by design (it is a browser redirect from Google) and is
 * authenticated instead by a single-use, short-lived, recruiter-bound state.
 */
@Service
public class GoogleOAuthService {
    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private static final long STATE_TTL_SECONDS = 600;

    private final GoogleOAuthProperties properties;
    private final GoogleRecruiterConnectionRepository connections;
    private final GoogleOAuthStateRepository oauthStates;
    private final GoogleOAuthStateConsumer stateConsumer;
    private final GoogleOAuthClient client;
    private final SecretCipher cipher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public GoogleOAuthService(GoogleOAuthProperties properties, GoogleRecruiterConnectionRepository connections,
                              GoogleOAuthStateRepository oauthStates, GoogleOAuthStateConsumer stateConsumer,
                              GoogleOAuthClient client, SecretCipher cipher, Clock clock,
                              PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.connections = connections;
        this.oauthStates = oauthStates;
        this.stateConsumer = stateConsumer;
        this.client = client;
        this.cipher = cipher;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public GoogleOAuthDtos.AuthorizeResponse beginAuthorization(AuthenticatedUser principal) {
        String recruiterId = requireRecruiter(principal);
        requireConfigured();
        String state = OAuthUtil.generateState();
        String verifier = OAuthUtil.generateCodeVerifier();
        Instant now = DatabaseTimePrecision.micros(clock.instant());
        oauthStates.save(new GoogleOAuthStateEntity(UUID.randomUUID().toString(), OAuthUtil.sha256Hex(state),
                recruiterId, cipher.encrypt(verifier), now, now.plusSeconds(STATE_TTL_SECONDS)));
        return new GoogleOAuthDtos.AuthorizeResponse(buildAuthorizationUrl(state, verifier));
    }

    public GoogleOAuthDtos.ConnectionStatus status(AuthenticatedUser principal) {
        String recruiterId = requireRecruiter(principal);
        return connections.findByRecruiterId(recruiterId)
                .map(connection -> {
                    boolean connected = connection.getStatus() == GoogleConnectionStatus.CONNECTED;
                    return new GoogleOAuthDtos.ConnectionStatus(connected, connection.getStatus().name(),
                            connection.getCreatedAt());
                })
                .orElseGet(() -> new GoogleOAuthDtos.ConnectionStatus(false, "DISCONNECTED", null));
    }

    @Transactional
    public void disconnect(AuthenticatedUser principal) {
        String recruiterId = requireRecruiter(principal);
        connections.deleteByRecruiterId(recruiterId);
        oauthStates.deleteByRecruiterId(recruiterId);
    }

    public GoogleOAuthCallbackOutcome handleCallback(String code, String state, String error) {
        // Fail closed before anything else: without a validated web return URI the
        // callback must not redirect anywhere.
        requireConfigured();
        if (state == null || state.isBlank()) {
            return GoogleOAuthCallbackOutcome.FAILED;
        }
        // Consume the state in its own committed transaction first, so that every
        // subsequent failure (denied, decryption, token exchange, connection write)
        // leaves the state consumed and unreplayable. Invalid, expired, or replayed
        // state collapses to a generic failed handoff with no detail leaked.
        ConsumedGoogleOAuthState consumed;
        try {
            consumed = stateConsumer.consume(state);
        } catch (ApiException e) {
            return GoogleOAuthCallbackOutcome.FAILED;
        }
        if (error != null && !error.isBlank()) {
            return "access_denied".equals(error)
                    ? GoogleOAuthCallbackOutcome.DENIED : GoogleOAuthCallbackOutcome.FAILED;
        }
        if (code == null || code.isBlank()) {
            return GoogleOAuthCallbackOutcome.FAILED;
        }
        try {
            Instant now = DatabaseTimePrecision.micros(clock.instant());
            String verifier = cipher.decrypt(consumed.pkceVerifierEncrypted());
            TokenExchangeResult tokens = client.exchangeAuthorizationCode(code, properties.redirectUri(), verifier);
            persistConnection(consumed.recruiterId(), tokens, now);
            return GoogleOAuthCallbackOutcome.CONNECTED;
        } catch (RuntimeException e) {
            // Any post-consumption failure (decryption, token exchange, connection
            // write) is a generic failed handoff; the state stays consumed.
            log.warn("Google OAuth callback failed after state consumption: {}", e.getClass().getSimpleName());
            return GoogleOAuthCallbackOutcome.FAILED;
        }
    }

    private void persistConnection(String recruiterId, TokenExchangeResult tokens, Instant now) {
        Instant expiresAt = now.plusSeconds(tokens.expiresInSeconds());
        String accessTokenEncrypted = cipher.encrypt(tokens.accessToken());
        String refreshTokenEncrypted = cipher.encrypt(tokens.refreshToken());
        transactionTemplate.executeWithoutResult(status -> {
            GoogleRecruiterConnectionEntity connection = connections.findByRecruiterId(recruiterId)
                    .map(existing -> {
                        // A successful re-authorization restores a previously revoked
                        // connection to CONNECTED, alongside fresh tokens and timestamps.
                        existing.reconnect(accessTokenEncrypted, refreshTokenEncrypted, expiresAt, now);
                        return existing;
                    })
                    .orElseGet(() -> new GoogleRecruiterConnectionEntity(UUID.randomUUID().toString(),
                            recruiterId, accessTokenEncrypted, refreshTokenEncrypted, expiresAt,
                            GoogleConnectionStatus.CONNECTED, now));
            connections.save(connection);
        });
    }

    private String buildAuthorizationUrl(String state, String verifier) {
        return AUTHORIZATION_ENDPOINT
                + "?client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode(SCOPE)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&code_challenge=" + encode(OAuthUtil.codeChallenge(verifier))
                + "&code_challenge_method=S256"
                + "&state=" + encode(state);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String requireRecruiter(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
        return principal.userId();
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_OAUTH_NOT_CONFIGURED",
                    "Google OAuth is not configured");
        }
    }
}
