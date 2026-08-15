package com.adproject.integration.google;

/**
 * Result of a provision attempt. For {@link ProvisionOutcome#READY} and
 * {@link ProvisionOutcome#PENDING} the event id is the stable external
 * identifier; {@code meetingUrl} is only ever a server-verified HTTPS
 * {@code meet.google.com} link, never a client-supplied value. On
 * {@link ProvisionOutcome#FAILED} only a safe, provider-neutral error code is
 * returned; raw provider responses never cross this boundary.
 */
public record ProvisionResult(ProvisionOutcome outcome, String eventId, String meetingUrl, String syncErrorCode) {}
