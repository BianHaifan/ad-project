package com.adproject.integration.google.application;

import java.time.Instant;

/**
 * The fields needed to create a Calendar event. {@code eventId} is the stable,
 * caller-supplied identifier (derived from the interview correlation id) so a
 * lost response can be recovered with a GET instead of a duplicate insert.
 * {@code attendeeEmail} is the single server-sourced attendee, added so Google
 * sends the invitation; it is never read from the browser.
 */
public record CalendarEventSpec(String eventId, String summary, Instant startUtc, Instant endUtc,
                                String timezone, String requestId, String attendeeEmail) {}
