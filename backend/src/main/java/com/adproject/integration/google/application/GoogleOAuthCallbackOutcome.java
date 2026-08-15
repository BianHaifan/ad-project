package com.adproject.integration.google.application;

/**
 * Classification of a completed Google OAuth callback, safe to map directly to a
 * browser redirect. The {@link #value()} strings are the only information
 * exposed to the browser and never carry an OAuth code, state, token, error
 * detail, or recruiter identity.
 */
public enum GoogleOAuthCallbackOutcome {
    CONNECTED("connected"),
    DENIED("denied"),
    FAILED("failed");

    private final String value;

    GoogleOAuthCallbackOutcome(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
