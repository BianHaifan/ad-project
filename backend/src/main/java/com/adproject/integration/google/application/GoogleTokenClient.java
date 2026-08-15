package com.adproject.integration.google.application;

/**
 * Port for the refresh-token grant against the fixed Google token endpoint.
 * Tests substitute a fake so no test contacts Google.
 */
public interface GoogleTokenClient {

    /**
     * Refreshes an access token from a refresh token.
     *
     * @throws GoogleTokenRefreshException with {@code INVALID_GRANT} when the
     *         refresh token is no longer usable, or {@code TRANSIENT} for a
     *         temporary failure.
     */
    RefreshedToken refreshAccessToken(String refreshToken);
}
