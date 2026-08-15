package com.adproject.integration.google;

/**
 * Result of mutating an existing meeting. On {@link MeetingSyncOutcome#FAILED}
 * only a safe, provider-neutral error code is returned; raw provider responses
 * never cross this boundary.
 */
public record MeetingSyncResult(MeetingSyncOutcome outcome, String syncErrorCode) {}
