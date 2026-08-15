package com.adproject.common.time;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Canonical persistence-time precision. Business times are stored in MySQL as
 * {@code DATETIME(6)} (microsecond precision), while {@code Instant} carries up to
 * nanosecond precision. Any {@code Instant} written to an entity must be normalized
 * through {@link #micros(Instant)} first so the in-memory value matches what the
 * database reads back.
 */
public final class DatabaseTimePrecision {

    private DatabaseTimePrecision() {
    }

    /**
     * Truncates an instant to microsecond precision without rounding, so the fractional
     * seconds never exceed six digits. Null passes through unchanged.
     */
    public static Instant micros(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }
}
