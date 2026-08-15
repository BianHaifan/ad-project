package com.adproject.integration.google.application;

import java.time.Instant;

/**
 * The fields updated on an existing Calendar event during a reschedule. This
 * deliberately carries no conference data or request id: a reschedule must
 * never create a second Meet conference.
 */
public record CalendarEventPatch(String summary, Instant startUtc, Instant endUtc, String timezone) {}
