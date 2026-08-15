package com.adproject.integration.google.application;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A valid Google Calendar event id, stably derived from the interview
 * correlation id. Google restricts event ids to lowercase {@code a-v} and
 * digits {@code 0-9} (no hyphens), so the UUID correlation id cannot be used
 * verbatim. This value object strips the UUID hyphens and prefixes a short tag,
 * producing a deterministic, idempotent id while the raw correlation id stays
 * the database key and the conference {@code requestId}.
 *
 * <p>The mapping is a pure function: the same correlation id always yields the
 * same event id, which is what makes the "lost response -> GET recovery, no
 * duplicate insert" path safe.
 */
public final class GoogleCalendarEventId {
    private static final String PREFIX = "gmeet";
    private static final Pattern VALID = Pattern.compile("^[a-v0-9]{5,1024}$");

    private final String value;

    private GoogleCalendarEventId(String value) {
        this.value = value;
    }

    /**
     * Maps a correlation id (a UUID) to a stable, valid Calendar event id.
     *
     * @throws IllegalArgumentException if the correlation id is blank or its
     *         derived form is not a legal Google Calendar id.
     */
    public static GoogleCalendarEventId fromCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlation id must not be blank");
        }
        String compact = correlationId.replace("-", "").toLowerCase(Locale.ROOT);
        GoogleCalendarEventId id = new GoogleCalendarEventId(PREFIX + compact);
        if (!VALID.matcher(id.value).matches()) {
            throw new IllegalArgumentException("derived event id is not a valid Google Calendar id");
        }
        return id;
    }

    /** The valid event id used for insert, recovery GET, and polling GET. */
    public String value() {
        return value;
    }
}
