package com.adproject.integration.google;

import java.time.Instant;

/**
 * Provider-neutral request to update (reschedule) an existing meeting. The
 * caller passes the already-known external event id, so no new event or
 * conference is ever created.
 */
public record MeetingUpdateRequest(String recruiterId, String eventId, Instant scheduledAt,
                                   int durationMinutes, String timezone) {}
