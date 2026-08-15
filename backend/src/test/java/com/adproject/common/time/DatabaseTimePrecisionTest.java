package com.adproject.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DatabaseTimePrecisionTest {

    @Test
    void truncatesNanosecondPrecisionToMicroseconds() {
        Instant source = Instant.parse("2026-08-14T12:34:56.123456789Z");
        assertThat(DatabaseTimePrecision.micros(source))
                .isEqualTo(Instant.parse("2026-08-14T12:34:56.123456Z"));
    }

    @Test
    void leavesAnAlreadyMicrosecondValueUnchanged() {
        Instant source = Instant.parse("2026-08-14T12:34:56.123456Z");
        assertThat(DatabaseTimePrecision.micros(source)).isEqualTo(source);
    }

    @Test
    void leavesAWholeSecondUnchanged() {
        Instant source = Instant.parse("2026-08-14T12:34:56Z");
        assertThat(DatabaseTimePrecision.micros(source)).isEqualTo(source);
    }

    @Test
    void preservesTheSameUtcInstant() {
        Instant source = Instant.parse("2026-08-14T12:34:56.123456789Z");
        Instant truncated = DatabaseTimePrecision.micros(source);
        // Truncation only zeroes the sub-microsecond digits; it never shifts the instant.
        assertThat(truncated.getEpochSecond()).isEqualTo(source.getEpochSecond());
        assertThat(truncated.getNano()).isEqualTo(source.getNano() / 1000 * 1000);
        assertThat(truncated).isBeforeOrEqualTo(source);
        assertThat(truncated.plusNanos(1000)).isAfter(source);
    }

    @Test
    void passesNullThroughUnchanged() {
        assertThat(DatabaseTimePrecision.micros(null)).isNull();
    }
}
