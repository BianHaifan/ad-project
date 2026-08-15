package com.adproject.integration.google;

/**
 * Signals that a meeting cannot be provisioned right now, carrying a safe,
 * provider-neutral error code. The code is one of the documented
 * {@code GOOGLE_MEET_*} strings and never a provider response body or a token.
 *
 * <p>Thrown by {@link MeetingProvisioningPort#ensureConnectionUsable} to the
 * interview service (which maps it to an HTTP error), and used internally by the
 * provisioning implementation to funnel the same safe codes out of the package.
 */
public class MeetingProvisioningException extends RuntimeException {
    private final String code;

    public MeetingProvisioningException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
