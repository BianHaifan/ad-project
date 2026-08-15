package com.adproject.integration.google;

/**
 * Outcome of mutating an existing external meeting (reschedule or cancel), in
 * provider-neutral terms so the interview service never depends on Google
 * specifics.
 */
public enum MeetingSyncOutcome {
    /** The external meeting now matches the requested local change. */
    SYNCED,
    /** The provider could not be reached or rejected the request; a safe error code is set. */
    FAILED
}
