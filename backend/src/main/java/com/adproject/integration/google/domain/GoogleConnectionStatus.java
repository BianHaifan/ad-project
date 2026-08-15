package com.adproject.integration.google.domain;

/**
 * Lifecycle state of a recruiter's Google account connection.
 *
 * <p>{@link #CONNECTED} is a usable connection. {@link #REVOKED} is set when a
 * token refresh proves the refresh token is no longer valid (the user revoked
 * access or the grant expired), which makes the recruiter reconnect instead of
 * receiving a generic server error. No new table is required: the status column
 * already exists and is free-form.
 */
public enum GoogleConnectionStatus {
    CONNECTED,
    REVOKED
}
