package com.adproject.integration.google.application;

/**
 * Port for the Calendar API calls this package needs: create an event with
 * Meet conference data, fetch a specific event by id for recovery/polling, and
 * patch/delete an existing event for reschedule/cancel. Tests substitute a
 * fake so no test contacts Google.
 */
public interface GoogleCalendarClient {

    CalendarEvent createEvent(String accessToken, CalendarEventSpec spec);

    CalendarEvent getEvent(String accessToken, String eventId);

    /**
     * Patches only the summary/start/end/timezone of an existing event. Never
     * sends conference data, so it can never mint a second Meet link.
     */
    CalendarEvent patchEvent(String accessToken, String eventId, CalendarEventPatch patch);

    /**
     * Deletes an existing event. An HTTP 404 (the event is already gone) is
     * treated as success and returns normally.
     */
    void deleteEvent(String accessToken, String eventId);
}
