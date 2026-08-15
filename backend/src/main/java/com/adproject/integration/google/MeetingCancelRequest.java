package com.adproject.integration.google;

/**
 * Provider-neutral request to cancel (delete) an existing meeting. The caller
 * passes the already-known external event id.
 */
public record MeetingCancelRequest(String recruiterId, String eventId) {}
