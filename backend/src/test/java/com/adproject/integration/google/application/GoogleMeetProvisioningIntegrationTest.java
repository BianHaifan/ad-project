package com.adproject.integration.google.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.adproject.integration.google.MeetingCancelRequest;
import com.adproject.integration.google.MeetingProvisioningPort;
import com.adproject.integration.google.MeetingSyncOutcome;
import com.adproject.integration.google.MeetingSyncResult;
import com.adproject.integration.google.MeetingUpdateRequest;
import com.adproject.integration.google.ProvisionOutcome;
import com.adproject.integration.google.ProvisionRequest;
import com.adproject.integration.google.ProvisionResult;
import com.adproject.integration.google.domain.GoogleConnectionStatus;
import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionEntity;
import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Exercises the real {@link MeetingProvisioningService} against mocked Google
 * Calendar / token transports, so no test ever contacts Google.
 */
@SpringBootTest @ActiveProfiles("test")
class GoogleMeetProvisioningIntegrationTest {
    @Autowired MeetingProvisioningPort meetingProvisioning;
    @Autowired GoogleRecruiterConnectionRepository connections;
    @Autowired SecretCipher cipher;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GoogleCalendarClient calendarClient;
    @MockitoBean GoogleTokenClient tokenClient;

    private static final String CORRELATION_ID = "corr-1";
    private static final String EVENT_ID = GoogleCalendarEventId.fromCorrelationId(CORRELATION_ID).value();
    private static final Instant SCHEDULED_AT = Instant.parse("2026-08-20T09:00:00Z");
    private static final String MEET_LINK = "https://meet.google.com/abc-defg-hij";
    private static final String ATTENDEE_EMAIL = "candidate@example.com";

    @Test void provisionReturnsReadyWhenCreateReturnsHttpsMeetLink() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.READY);
        assertThat(result.eventId()).isEqualTo("evt-1");
        assertThat(result.meetingUrl()).isEqualTo(MEET_LINK);
        assertThat(result.syncErrorCode()).isNull();
        verify(calendarClient, times(1)).createEvent(eq("access-token"), any());
    }

    @Test void provisionPollsAndReturnsReadyWhenLinkAppearsLater() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-1", null));
        when(calendarClient.getEvent(anyString(), eq(EVENT_ID)))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.READY);
        assertThat(result.meetingUrl()).isEqualTo(MEET_LINK);
    }

    @Test void provisionReturnsPendingWhenNoLinkAfterPolls() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-1", null));
        when(calendarClient.getEvent(anyString(), eq(EVENT_ID)))
                .thenReturn(new CalendarEvent("evt-1", null));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.PENDING);
        assertThat(result.eventId()).isEqualTo("evt-1");
        assertThat(result.meetingUrl()).isNull();
        verify(calendarClient, times(3)).getEvent(anyString(), eq(EVENT_ID));
    }

    @Test void provisionRecoversViaGetOnConflictWithoutDuplicateInsert() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.CONFLICT, "exists"));
        when(calendarClient.getEvent(anyString(), eq(EVENT_ID)))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.READY);
        assertThat(result.meetingUrl()).isEqualTo(MEET_LINK);
        verify(calendarClient, times(1)).createEvent(anyString(), any());
        verify(calendarClient, times(1)).getEvent(anyString(), eq(EVENT_ID));
    }

    @Test void provisionRefreshesExpiredTokenBeforeCreate() {
        String recruiterId = connectedExpired();
        when(tokenClient.refreshAccessToken("refresh-token"))
                .thenReturn(new RefreshedToken("new-access-token", 3600, null));
        when(calendarClient.createEvent(eq("new-access-token"), any()))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.READY);
        verify(tokenClient, times(1)).refreshAccessToken("refresh-token");
        verify(calendarClient, times(1)).createEvent(eq("new-access-token"), any());
        assertThat(cipher.decrypt(accessToken(recruiterId))).isEqualTo("new-access-token");
        assertThat(cipher.decrypt(refreshToken(recruiterId))).isEqualTo("refresh-token");
        assertThat(version(recruiterId)).isEqualTo(2);
    }

    @Test void provisionRefreshesAndRetriesOnceOnUnauthorized() {
        String recruiterId = connected();
        when(calendarClient.createEvent(eq("access-token"), any()))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED, "expired"));
        when(tokenClient.refreshAccessToken("refresh-token"))
                .thenReturn(new RefreshedToken("new-access-token", 3600, null));
        when(calendarClient.createEvent(eq("new-access-token"), any()))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.READY);
        verify(calendarClient, times(1)).createEvent(eq("access-token"), any());
        verify(calendarClient, times(1)).createEvent(eq("new-access-token"), any());
        verify(tokenClient, times(1)).refreshAccessToken("refresh-token");
    }

    @Test void provisionMarksRevokedAndReturnsReconnectOnInvalidGrant() {
        String recruiterId = connectedExpired();
        when(tokenClient.refreshAccessToken(anyString()))
                .thenThrow(new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.INVALID_GRANT, "revoked"));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_RECONNECT_REQUIRED");
        assertThat(status(recruiterId)).isEqualTo("REVOKED");
        assertThat(meetingProvisioning.requiresReconnect(recruiterId)).isTrue();
    }

    @Test void provisionReturnsUnavailableOnTransientCalendarFailure() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT, "boom"));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        assertThat(status(recruiterId)).isEqualTo("CONNECTED");
    }

    @Test void provisionRejectsNonHttpsMeetLink() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-1", "http://meet.google.com/abc"));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_LINK_INVALID");
        assertThat(result.meetingUrl()).isNull();
    }

    @Test void provisionReturnsNotConnectedWithoutAConnection() {
        ProvisionResult result = provision("missing-recruiter");

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_NOT_CONNECTED");
    }

    @Test void eventIdIsValidAndDeterministic() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        provision(recruiterId);

        ArgumentCaptor<CalendarEventSpec> captor = ArgumentCaptor.forClass(CalendarEventSpec.class);
        verify(calendarClient, times(1)).createEvent(anyString(), captor.capture());
        CalendarEventSpec spec = captor.getValue();
        // A legal Google Calendar id: lowercase a-v and digits only, no hyphens.
        assertThat(spec.eventId()).matches("^[a-v0-9]{5,1024}$");
        assertThat(spec.eventId()).doesNotContain("-");
        // The same correlation id always yields the same event id (idempotent recovery).
        assertThat(GoogleCalendarEventId.fromCorrelationId(CORRELATION_ID).value())
                .isEqualTo(GoogleCalendarEventId.fromCorrelationId(CORRELATION_ID).value())
                .isEqualTo(spec.eventId());
        // The raw correlation id is preserved separately as the conference requestId.
        assertThat(spec.requestId()).isEqualTo(CORRELATION_ID);
        assertThat(spec.eventId()).isNotEqualTo(spec.requestId());
    }

    @Test void provisionThreadsAttendeeEmailIntoCalendarEventSpec() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        provision(recruiterId);

        ArgumentCaptor<CalendarEventSpec> captor = ArgumentCaptor.forClass(CalendarEventSpec.class);
        verify(calendarClient, times(1)).createEvent(anyString(), captor.capture());
        assertThat(captor.getValue().attendeeEmail()).isEqualTo(ATTENDEE_EMAIL);
    }

    @Test void provisionRecoversViaGetOnConflictWhenRecoveryGetUnauthorized() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.CONFLICT, "exists"));
        when(calendarClient.getEvent(eq("access-token"), eq(EVENT_ID)))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED, "expired"));
        when(tokenClient.refreshAccessToken("refresh-token"))
                .thenReturn(new RefreshedToken("new-access-token", 3600, null));
        when(calendarClient.getEvent(eq("new-access-token"), eq(EVENT_ID)))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.READY);
        assertThat(result.meetingUrl()).isEqualTo(MEET_LINK);
        verify(calendarClient, times(1)).createEvent(anyString(), any());
        verify(calendarClient, times(1)).getEvent(eq("access-token"), eq(EVENT_ID));
        verify(calendarClient, times(1)).getEvent(eq("new-access-token"), eq(EVENT_ID));
        verify(tokenClient, times(1)).refreshAccessToken("refresh-token");
    }

    @Test void provisionPollsThroughUnauthorizedRefresh() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-1", null));
        when(calendarClient.getEvent(eq("access-token"), eq(EVENT_ID)))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED, "expired"));
        when(tokenClient.refreshAccessToken("refresh-token"))
                .thenReturn(new RefreshedToken("new-access-token", 3600, null));
        when(calendarClient.getEvent(eq("new-access-token"), eq(EVENT_ID)))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.READY);
        assertThat(result.meetingUrl()).isEqualTo(MEET_LINK);
        verify(calendarClient, times(1)).createEvent(anyString(), any());
        verify(calendarClient, times(1)).getEvent(eq("access-token"), eq(EVENT_ID));
        verify(calendarClient, times(1)).getEvent(eq("new-access-token"), eq(EVENT_ID));
        verify(tokenClient, times(1)).refreshAccessToken("refresh-token");
    }

    @Test void provisionDoesNotRefreshAgainWhenRetryStillUnauthorized() {
        String recruiterId = connected();
        when(calendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-1", null));
        when(calendarClient.getEvent(anyString(), eq(EVENT_ID)))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED, "expired"));
        when(tokenClient.refreshAccessToken("refresh-token"))
                .thenReturn(new RefreshedToken("new-access-token", 3600, null));

        ProvisionResult result = provision(recruiterId);

        assertThat(result.outcome()).isEqualTo(ProvisionOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        verify(tokenClient, times(1)).refreshAccessToken("refresh-token");
        verify(calendarClient, times(1)).createEvent(anyString(), any());
        // One poll: original token 401, then a single refreshed retry 401, then stop.
        verify(calendarClient, times(2)).getEvent(anyString(), eq(EVENT_ID));
    }

    @Test void updateMeetingPatchesEventWithoutCreatingANewOne() {
        String recruiterId = connected();
        when(calendarClient.patchEvent(eq("access-token"), eq("evt-1"), any()))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        MeetingSyncResult result = meetingProvisioning.updateMeeting(
                new MeetingUpdateRequest(recruiterId, "evt-1", SCHEDULED_AT, 60, "Asia/Singapore"));

        assertThat(result.outcome()).isEqualTo(MeetingSyncOutcome.SYNCED);
        assertThat(result.syncErrorCode()).isNull();
        verify(calendarClient, times(1)).patchEvent(eq("access-token"), eq("evt-1"), any());
        verify(calendarClient, never()).createEvent(anyString(), any());
        verify(calendarClient, never()).deleteEvent(anyString(), anyString());
    }

    @Test void updateMeetingPatchesOnlyTimeFields() {
        String recruiterId = connected();
        when(calendarClient.patchEvent(anyString(), eq("evt-1"), any()))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        meetingProvisioning.updateMeeting(
                new MeetingUpdateRequest(recruiterId, "evt-1", SCHEDULED_AT, 45, "Asia/Singapore"));

        ArgumentCaptor<CalendarEventPatch> captor = ArgumentCaptor.forClass(CalendarEventPatch.class);
        verify(calendarClient, times(1)).patchEvent(anyString(), eq("evt-1"), captor.capture());
        CalendarEventPatch patch = captor.getValue();
        assertThat(patch.summary()).isEqualTo("Recruitment interview");
        assertThat(patch.startUtc()).isEqualTo(SCHEDULED_AT);
        assertThat(patch.endUtc()).isEqualTo(SCHEDULED_AT.plusSeconds(45 * 60L));
        assertThat(patch.timezone()).isEqualTo("Asia/Singapore");
    }

    @Test void cancelMeetingDeletesEventWithoutInsert() {
        String recruiterId = connected();

        MeetingSyncResult result = meetingProvisioning.cancelMeeting(new MeetingCancelRequest(recruiterId, "evt-1"));

        assertThat(result.outcome()).isEqualTo(MeetingSyncOutcome.SYNCED);
        verify(calendarClient, times(1)).deleteEvent("access-token", "evt-1");
        verify(calendarClient, never()).createEvent(anyString(), any());
        verify(calendarClient, never()).patchEvent(anyString(), anyString(), any());
    }

    @Test void updateMeetingReturnsUnavailableOnTransientFailure() {
        String recruiterId = connected();
        when(calendarClient.patchEvent(anyString(), anyString(), any()))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT, "boom"));

        MeetingSyncResult result = meetingProvisioning.updateMeeting(
                new MeetingUpdateRequest(recruiterId, "evt-1", SCHEDULED_AT, 60, "Asia/Singapore"));

        assertThat(result.outcome()).isEqualTo(MeetingSyncOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        assertThat(status(recruiterId)).isEqualTo("CONNECTED");
    }

    @Test void cancelMeetingReturnsUnavailableOnTransientFailure() {
        String recruiterId = connected();
        doThrow(new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT, "boom"))
                .when(calendarClient).deleteEvent(anyString(), anyString());

        MeetingSyncResult result = meetingProvisioning.cancelMeeting(new MeetingCancelRequest(recruiterId, "evt-1"));

        assertThat(result.outcome()).isEqualTo(MeetingSyncOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
    }

    @Test void updateMeetingRefreshesAndRetriesOnceOnUnauthorized() {
        String recruiterId = connected();
        when(calendarClient.patchEvent(eq("access-token"), eq("evt-1"), any()))
                .thenThrow(new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED, "expired"));
        when(tokenClient.refreshAccessToken("refresh-token"))
                .thenReturn(new RefreshedToken("new-access-token", 3600, null));
        when(calendarClient.patchEvent(eq("new-access-token"), eq("evt-1"), any()))
                .thenReturn(new CalendarEvent("evt-1", MEET_LINK));

        MeetingSyncResult result = meetingProvisioning.updateMeeting(
                new MeetingUpdateRequest(recruiterId, "evt-1", SCHEDULED_AT, 60, "Asia/Singapore"));

        assertThat(result.outcome()).isEqualTo(MeetingSyncOutcome.SYNCED);
        verify(calendarClient, times(1)).patchEvent(eq("access-token"), eq("evt-1"), any());
        verify(calendarClient, times(1)).patchEvent(eq("new-access-token"), eq("evt-1"), any());
        verify(tokenClient, times(1)).refreshAccessToken("refresh-token");
    }

    @Test void updateMeetingMarksRevokedOnInvalidGrant() {
        String recruiterId = connectedExpired();
        when(tokenClient.refreshAccessToken(anyString()))
                .thenThrow(new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.INVALID_GRANT, "revoked"));

        MeetingSyncResult result = meetingProvisioning.updateMeeting(
                new MeetingUpdateRequest(recruiterId, "evt-1", SCHEDULED_AT, 60, "Asia/Singapore"));

        assertThat(result.outcome()).isEqualTo(MeetingSyncOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_RECONNECT_REQUIRED");
        assertThat(status(recruiterId)).isEqualTo("REVOKED");
    }

    @Test void updateMeetingReturnsNotConnectedWithoutConnection() {
        MeetingSyncResult result = meetingProvisioning.updateMeeting(
                new MeetingUpdateRequest("missing-recruiter", "evt-1", SCHEDULED_AT, 60, "Asia/Singapore"));

        assertThat(result.outcome()).isEqualTo(MeetingSyncOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_NOT_CONNECTED");
    }

    @Test void updateMeetingReturnsReconnectForRevokedConnection() {
        String recruiterId = newRecruiter();
        save(recruiterId, Instant.now().plusSeconds(3600), GoogleConnectionStatus.REVOKED);

        MeetingSyncResult result = meetingProvisioning.updateMeeting(
                new MeetingUpdateRequest(recruiterId, "evt-1", SCHEDULED_AT, 60, "Asia/Singapore"));

        assertThat(result.outcome()).isEqualTo(MeetingSyncOutcome.FAILED);
        assertThat(result.syncErrorCode()).isEqualTo("GOOGLE_MEET_RECONNECT_REQUIRED");
    }

    private ProvisionResult provision(String recruiterId) {
        return meetingProvisioning.provision(new ProvisionRequest(recruiterId, CORRELATION_ID, SCHEDULED_AT,
                "Asia/Singapore", 60, ATTENDEE_EMAIL));
    }

    private String connected() {
        String recruiterId = newRecruiter();
        save(recruiterId, Instant.now().plusSeconds(3600), GoogleConnectionStatus.CONNECTED);
        return recruiterId;
    }

    private String connectedExpired() {
        String recruiterId = newRecruiter();
        save(recruiterId, Instant.now().minusSeconds(60), GoogleConnectionStatus.CONNECTED);
        return recruiterId;
    }

    private String newRecruiter() {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        users.save(new UserEntity(id, id + "@example.com", "hash", "Recruiter", UserRole.RECRUITER,
                UserStatus.ACTIVE, "2026-08", now, now));
        return id;
    }

    private void save(String recruiterId, Instant expiresAt, GoogleConnectionStatus status) {
        connections.save(new GoogleRecruiterConnectionEntity(UUID.randomUUID().toString(), recruiterId,
                cipher.encrypt("access-token"), cipher.encrypt("refresh-token"), expiresAt, status, Instant.now()));
    }

    private String accessToken(String recruiterId) {
        return jdbc.queryForObject(
                "select access_token_encrypted from google_recruiter_connections where recruiter_id=?",
                String.class, recruiterId);
    }

    private String refreshToken(String recruiterId) {
        return jdbc.queryForObject(
                "select refresh_token_encrypted from google_recruiter_connections where recruiter_id=?",
                String.class, recruiterId);
    }

    private String status(String recruiterId) {
        return jdbc.queryForObject("select status from google_recruiter_connections where recruiter_id=?",
                String.class, recruiterId);
    }

    private int version(String recruiterId) {
        return jdbc.queryForObject("select version from google_recruiter_connections where recruiter_id=?",
                Integer.class, recruiterId);
    }
}
