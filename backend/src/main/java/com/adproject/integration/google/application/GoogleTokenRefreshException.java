package com.adproject.integration.google.application;

/**
 * Thrown when a refresh-token grant fails. The message never carries the
 * refresh token or a raw provider response body.
 */
public class GoogleTokenRefreshException extends RuntimeException {

    public enum Category {
        /** The refresh token is invalid or revoked; the user must reconnect. */
        INVALID_GRANT,
        /** A temporary failure (network, 429, 5xx); safe to surface as a sync error. */
        TRANSIENT
    }

    private final Category category;

    public GoogleTokenRefreshException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public Category category() { return category; }
}
