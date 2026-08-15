package com.adproject.integration.google;

/**
 * Outcome of a meeting-provisioning attempt, in provider-neutral terms so the
 * interview service never depends on Google specifics.
 */
public enum ProvisionOutcome {
    /** A verified meet link was obtained and can be stored. */
    READY,
    /** The event exists but the link is not yet available; no URL is fabricated. */
    PENDING,
    /** The provider could not be reached or rejected the request; a safe error code is set. */
    FAILED
}
