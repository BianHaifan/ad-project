package com.adproject.integration.google;

import java.time.Instant;

/**
 * Provider-neutral request to provision an online meeting for one interview.
 * The correlation id is minted by the caller, persisted with the interview, and
 * reused as the external event/conference id so retries stay idempotent. The
 * {@code attendeeEmail} is the candidate's application contact email, sourced
 * server-side only — never a browser-supplied value — and is the sole Calendar
 * event attendee so Google sends the invitation.
 */
public record ProvisionRequest(String recruiterId, String correlationId, Instant scheduledAt,
                               String timezone, int durationMinutes, String attendeeEmail) {}
