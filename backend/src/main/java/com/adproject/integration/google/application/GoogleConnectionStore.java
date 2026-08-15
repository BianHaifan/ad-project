package com.adproject.integration.google.application;

import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes token and status changes to the stored connection in their own short
 * transactions. Kept separate from the provisioning orchestration so a refresh
 * or revocation write never happens inside a long-lived external call or a
 * caller's business transaction. Mutations are idempotent: when the connection
 * was concurrently removed, the write is simply skipped.
 */
@Component
public class GoogleConnectionStore {
    private final GoogleRecruiterConnectionRepository connections;

    public GoogleConnectionStore(GoogleRecruiterConnectionRepository connections) {
        this.connections = connections;
    }

    @Transactional
    public void updateTokens(String recruiterId, String accessTokenEncrypted, String refreshTokenEncrypted,
                             Instant accessTokenExpiresAt, Instant now) {
        connections.findByRecruiterId(recruiterId)
                .ifPresent(connection -> connection.replaceTokens(accessTokenEncrypted, refreshTokenEncrypted,
                        accessTokenExpiresAt, now));
    }

    @Transactional
    public void markRevoked(String recruiterId, Instant now) {
        connections.findByRecruiterId(recruiterId)
                .ifPresent(connection -> connection.markRevoked(now));
    }
}
