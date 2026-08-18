package com.adproject.candidate.feature.messages

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTimeFormattingTest {
    @Test
    fun `utc message time is converted to device timezone`() {
        assertEquals(
            "18:30",
            formatLocalTimestamp("2026-08-18T10:30:00Z", "HH:mm", ZoneId.of("Asia/Shanghai")),
        )
    }

    @Test
    fun `offset timestamp is converted as an instant and can cross date boundary`() {
        assertEquals(
            "Aug 17, 22:30",
            formatLocalTimestamp(
                "2026-08-18T10:30:00+08:00",
                "MMM d, HH:mm",
                ZoneId.of("America/New_York"),
            ),
        )
    }

    @Test
    fun `invalid server timestamp remains visible instead of crashing`() {
        assertEquals(
            "unknown-time",
            formatLocalTimestamp("unknown-time", "HH:mm", ZoneId.of("Asia/Shanghai")),
        )
    }
}
