package com.adproject.candidate.feature.messages

/**
 * Polling cadence for the Messages list and chat detail screens, mirroring the Web
 * reference implementation (`web/src/api/polling.ts`). The list refreshes every 3s and
 * the chat detail every 1s while the app is in the foreground and the screen is visible.
 *
 * Consecutive failures escalate the interval to 3s, 10s, then 30s; a success resets it
 * back to the base cadence. This object is pure so the mapping is unit-testable.
 */
object PollSchedule {
    const val LIST_INTERVAL_MS = 3_000L
    const val DETAIL_INTERVAL_MS = 1_000L

    /**
     * Delay before the next poll. A healthy poll (zero consecutive failures) uses the
     * base cadence; failures escalate through 3s / 10s / 30s and cap at 30s.
     */
    fun delayAfter(consecutiveFailures: Int, baseIntervalMs: Long): Long = when {
        consecutiveFailures <= 0 -> baseIntervalMs
        consecutiveFailures == 1 -> 3_000L
        consecutiveFailures == 2 -> 10_000L
        else -> 30_000L
    }
}
