package com.adproject.integration.google.application;

/**
 * Thrown when a Calendar API call fails. The message never carries the access
 * token or a raw provider response body.
 */
public class GoogleCalendarException extends RuntimeException {

    public enum Category {
        /** HTTP 401: the access token is not accepted (caller may refresh and retry once). */
        UNAUTHORIZED,
        /** HTTP 409: an event with the requested id already exists (recover via GET). */
        CONFLICT,
        /** Network timeout, 429, or 5xx: a temporary failure. */
        TRANSIENT
    }

    private final Category category;

    public GoogleCalendarException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public Category category() { return category; }
}
