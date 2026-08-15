package com.adproject.integration.google.application;

/**
 * A parsed Calendar event. {@code hangoutLink} is null until the conference is
 * ready; the caller validates it is an HTTPS {@code meet.google.com} link
 * before persisting.
 */
public record CalendarEvent(String eventId, String hangoutLink) {}
