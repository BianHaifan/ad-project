package com.adproject.integration.google.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.integration.google.infrastructure.GoogleOAuthStateEntity;
import com.adproject.integration.google.infrastructure.GoogleOAuthStateRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes an OAuth state exactly once, in its own committed transaction, so
 * that a later callback failure (provider denied, token exchange failure,
 * verifier decryption failure, connection write failure) can never roll the
 * consumption back and make the state replayable.
 */
@Component
public class GoogleOAuthStateConsumer {
    private final GoogleOAuthStateRepository oauthStates;
    private final Clock clock;

    public GoogleOAuthStateConsumer(GoogleOAuthStateRepository oauthStates, Clock clock) {
        this.oauthStates = oauthStates;
        this.clock = clock;
    }

    /**
     * Locks, validates, consumes, and commits the state in a transaction
     * independent of the caller's, then returns the bound recruiter and the
     * still-encrypted verifier. The pessimistic lock is released at commit, so
     * no database lock is held during the subsequent token exchange.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConsumedGoogleOAuthState consume(String state) {
        Instant now = DatabaseTimePrecision.micros(clock.instant());
        GoogleOAuthStateEntity oauthState = oauthStates.findByStateHashForUpdate(OAuthUtil.sha256Hex(state))
                .orElseThrow(GoogleOAuthStateConsumer::invalidState);
        if (oauthState.getExpiresAt().isBefore(now) || oauthState.getConsumedAt() != null) {
            throw invalidState();
        }
        oauthState.consume(now);
        oauthStates.flush();
        return new ConsumedGoogleOAuthState(oauthState.getRecruiterId(), oauthState.getPkceVerifierEncrypted());
    }

    private static ApiException invalidState() {
        return new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_OAUTH_STATE_INVALID", "OAuth state is invalid");
    }
}
