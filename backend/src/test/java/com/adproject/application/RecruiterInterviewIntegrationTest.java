package com.adproject.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.*;
import com.adproject.company.infrastructure.*;
import com.adproject.integration.google.MeetingCancelRequest;
import com.adproject.integration.google.MeetingProvisioningException;
import com.adproject.integration.google.MeetingProvisioningPort;
import com.adproject.integration.google.MeetingSyncOutcome;
import com.adproject.integration.google.MeetingSyncResult;
import com.adproject.integration.google.MeetingUpdateRequest;
import com.adproject.integration.google.ProvisionOutcome;
import com.adproject.integration.google.ProvisionRequest;
import com.adproject.integration.google.ProvisionResult;
import com.adproject.job.domain.*;
import com.adproject.job.infrastructure.*;
import com.adproject.resume.infrastructure.*;
import com.adproject.user.domain.*;
import com.adproject.user.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RecruiterInterviewIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;
    @Autowired CompanyRepository companies; @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs; @Autowired ResumeRepository resumes; @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MeetingProvisioningPort meetingProvisioning;

    private static final String SCHEDULE = "{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\","
            + "\"durationMinutes\":60,\"mode\":\"ONSITE\",\"locationOrMeetingUrl\":\"12 Marina Blvd, Singapore\","
            + "\"note\":\"Bring portfolio\",\"expectedApplicationVersion\":%d}";

    @Test void createSchedulesInterviewTransitionsApplicationAndWritesAudit() throws Exception {
        Fixture fixture = fixture("Schedule Candidate");
        String id = submit(fixture, job(fixture, "Schedule Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(SCHEDULE, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.applicationId").value(id))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-20T09:00:00Z"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.data.durationMinutes").value(60))
                .andExpect(jsonPath("$.data.mode").value("ONSITE"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("12 Marina Blvd, Singapore"))
                .andExpect(jsonPath("$.data.note").value("Bring portfolio"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.meetingProvider").value("MANUAL"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("NOT_APPLICABLE"));

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from application_status_events where application_id=? " +
                "and from_status='IN_REVIEW' and to_status='INTERVIEW' and actor_id=? and reason='Interview scheduled' " +
                "and request_id is not null", Integer.class, id, fixture.recruiterId())).isEqualTo(1);

        mvc.perform(get("/api/v1/recruiter/applications/{id}", id).header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.data.interview.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.interview.note").value("Bring portfolio"));

        mvc.perform(get("/api/v1/candidate/applications/{id}", id).header("Authorization", candidate(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-20T09:00:00Z"))
                .andExpect(jsonPath("$.data.interview.mode").value("ONSITE"))
                .andExpect(jsonPath("$.data.interview.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.interview.note").isEmpty());
    }

    @Test void googleMeetRejectedWhenNotConnectedAndManualProviderAccepted() throws Exception {
        doThrow(new MeetingProvisioningException("GOOGLE_MEET_NOT_CONNECTED"))
                .when(meetingProvisioning).ensureConnectionUsable(anyString());
        Fixture fixture = fixture("Provider Candidate");
        String id = submit(fixture, job(fixture, "Provider Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_NOT_CONNECTED"));

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?", Integer.class, id))
                .isZero();

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONSITE\"," +
                                "\"locationOrMeetingUrl\":\"12 Marina Blvd, Singapore\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingProvider").value("MANUAL"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("NOT_APPLICABLE"));

        assertThat(jdbc.queryForObject("select meeting_provider from interviews where application_id=?",
                String.class, id)).isEqualTo("MANUAL");
        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where application_id=?",
                String.class, id)).isEqualTo("NOT_APPLICABLE");
    }

    @Test void googleMeetRejectedWhenConnectedButProvisioningUnavailable() throws Exception {
        doThrow(new MeetingProvisioningException("GOOGLE_MEET_PROVISIONING_UNAVAILABLE"))
                .when(meetingProvisioning).ensureConnectionUsable(anyString());

        Fixture fixture = fixture("Provisioning Candidate");
        String id = submit(fixture, job(fixture, "Provisioning Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_PROVISIONING_UNAVAILABLE"));

        // No interview created, application stays IN_REVIEW, no audit event written.
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("select status from applications where id=?", String.class, id))
                .isEqualTo("IN_REVIEW");
        assertThat(jdbc.queryForObject(
                "select count(*) from interview_audit_events where application_id=?", Integer.class, id)).isZero();
    }

    @Test void onsiteAndPhoneRejectGoogleMeet() throws Exception {
        Fixture fixture = fixture("Meet Mode Candidate");
        String id = submit(fixture, job(fixture, "Meet Mode Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONSITE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.meetingProvider").isNotEmpty());

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"PHONE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.meetingProvider").isNotEmpty());

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?", Integer.class, id))
                .isZero();
    }

    @Test void onlineRequiresGoogleMeetAndRejectsManualOrOmittedProvider() throws Exception {
        Fixture fixture = fixture("Online Manual Candidate");
        String id = submit(fixture, job(fixture, "Online Manual Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"MANUAL\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.meetingProvider").isNotEmpty());

        // An omitted provider is equally invalid for online: there is no manual
        // online link, so the provider cannot silently default to MANUAL.
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.meetingProvider").isNotEmpty());

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?", Integer.class, id))
                .isZero();
    }

    @Test void googleMeetRejectsClientProvidedLink() throws Exception {
        Fixture fixture = fixture("Meet Link Candidate");
        String id = submit(fixture, job(fixture, "Meet Link Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"locationOrMeetingUrl\":\"https://meet.google.com/forged\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.locationOrMeetingUrl").isNotEmpty());

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?", Integer.class, id))
                .isZero();
    }

    @Test void googleMeetRejectedWhenConnectionRevoked() throws Exception {
        doThrow(new MeetingProvisioningException("GOOGLE_MEET_RECONNECT_REQUIRED"))
                .when(meetingProvisioning).ensureConnectionUsable(anyString());

        Fixture fixture = fixture("Meet Revoked Candidate");
        String id = submit(fixture, job(fixture, "Meet Revoked Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_RECONNECT_REQUIRED"));

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?", Integer.class, id))
                .isZero();
    }

    @Test void googleMeetSuccessfulProvisionStoresLinkAndMarksReady() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-1", "https://meet.google.com/abc-defg-hij", null));

        Fixture fixture = fixture("Meet Ready Candidate");
        String id = submit(fixture, job(fixture, "Meet Ready Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingProvider").value("GOOGLE_MEET"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("https://meet.google.com/abc-defg-hij"));

        assertThat(jdbc.queryForObject("select meeting_event_id from interviews where application_id=?",
                String.class, id)).isEqualTo("evt-1");
        assertThat(jdbc.queryForObject("select meeting_correlation_id from interviews where application_id=?",
                String.class, id)).isNotNull();
        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where application_id=?",
                String.class, id)).isNull();
    }

    @Test void googleMeetProvisionReceivesApplicationContactEmailNotBrowserInput() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-" + UUID.randomUUID(), "https://meet.google.com/abc-defg-hij", null));

        Fixture fixture = fixture("Meet Attendee Candidate");
        String id = submit(fixture, job(fixture, "Meet Attendee Job"));
        toInReview(fixture, id);

        // The request body intentionally carries no email; the attendee must come
        // from applications.contact_email captured when the candidate applied.
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<ProvisionRequest> captor = ArgumentCaptor.forClass(ProvisionRequest.class);
        verify(meetingProvisioning).provision(captor.capture());
        assertThat(captor.getValue().attendeeEmail()).isEqualTo(fixture.email());
        assertThat(jdbc.queryForObject("select contact_email from applications where id=?",
                String.class, id)).isEqualTo(captor.getValue().attendeeEmail());
    }

    @Test void googleMeetProvisioningFailureStillCreatesInterviewInFailedState() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.FAILED, null, null, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE"));

        Fixture fixture = fixture("Meet Failed Candidate");
        String id = submit(fixture, job(fixture, "Meet Failed Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingProvider").value("GOOGLE_MEET"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").isEmpty());

        // The local interview and application transition are committed even though
        // provisioning failed; only the sync state reflects the failure.
        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where application_id=?",
                String.class, id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where application_id=?",
                String.class, id)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select status from applications where id=?", String.class, id))
                .isEqualTo("INTERVIEW");
    }

    @Test void googleMeetNullProvisionResultFailsSafelyWithout500() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(null);

        Fixture fixture = fixture("Meet Null Provision Candidate");
        String id = submit(fixture, job(fixture, "Meet Null Provision Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingProvider").value("GOOGLE_MEET"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").isEmpty());

        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where application_id=?",
                String.class, id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select meeting_event_id from interviews where application_id=?",
                String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where application_id=?",
                String.class, id)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
    }

    @Test void googleMeetReadyProvisionWithoutMeetUrlFailsSafely() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-1", null, null));

        Fixture fixture = fixture("Meet Ready No Link Candidate");
        String id = submit(fixture, job(fixture, "Meet Ready No Link Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").isEmpty());

        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where application_id=?",
                String.class, id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select meeting_event_id from interviews where application_id=?",
                String.class, id)).isNull();
    }

    @Test void googleMeetPendingProvisionWithoutEventIdFailsSafely() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.PENDING, null, null, null));

        Fixture fixture = fixture("Meet Pending No Event Candidate");
        String id = submit(fixture, job(fixture, "Meet Pending No Event Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").isEmpty());

        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where application_id=?",
                String.class, id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select meeting_event_id from interviews where application_id=?",
                String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where application_id=?",
                String.class, id)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
    }

    @Test void googleMeetFailedProvisionWithBlankCodeUsesGenericCode() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.FAILED, null, null, "   "));

        Fixture fixture = fixture("Meet Blank Code Candidate");
        String id = submit(fixture, job(fixture, "Meet Blank Code Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"));

        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where application_id=?",
                String.class, id)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
    }

    @Test void createRequiresRecruiterAuthentication() throws Exception {
        Fixture fixture = fixture("Auth Candidate");
        String id = submit(fixture, job(fixture, "Auth Job"));
        toInReview(fixture, id);
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .contentType(MediaType.APPLICATION_JSON).content(String.format(SCHEDULE, 2)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", candidate(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(SCHEDULE, 2)))
                .andExpect(status().isForbidden());
    }

    @Test void createHidesCrossCompanyAndRejectsWrongStateDuplicateAndStaleVersion() throws Exception {
        Fixture fixture = fixture("Scope Candidate");
        String id = submit(fixture, job(fixture, "Scope Job"));
        toInReview(fixture, id);
        Fixture other = fixture("Other Scope Candidate");
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(other)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(SCHEDULE, 2)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(SCHEDULE, 99)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(SCHEDULE, 2)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(SCHEDULE, 3)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INTERVIEW_ALREADY_EXISTS"));

        Fixture wrongState = fixture("Wrong State Candidate");
        String appliedId = submit(wrongState, job(wrongState, "Wrong State Job"));
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", appliedId)
                        .header("Authorization", recruiter(wrongState)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(SCHEDULE, 1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_APPLICATION_TRANSITION"));
    }

    @Test void createValidatesRequiredFields() throws Exception {
        Fixture fixture = fixture("Validation Candidate");
        String id = submit(fixture, job(fixture, "Validation Job"));
        toInReview(fixture, id);
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"mode\":\"ONLINE\"," +
                                "\"locationOrMeetingUrl\":\"x\",\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":0,\"mode\":\"ONLINE\",\"locationOrMeetingUrl\":\"x\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test void updateReschedulesCompletesAndCancelsWithStateMachine() throws Exception {
        Fixture fixture = fixture("Update Candidate");
        String id = submit(fixture, job(fixture, "Update Job"));
        toInReview(fixture, id);
        String interviewId = schedule(fixture, id, 2);

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":30,\"mode\":\"PHONE\",\"locationOrMeetingUrl\":\"+65 1234 5678\"," +
                                "\"note\":\"Rescheduled\",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-21T10:00:00Z"))
                .andExpect(jsonPath("$.data.durationMinutes").value(30))
                .andExpect(jsonPath("$.data.mode").value("PHONE"))
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"expectedVersion\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.version").value(3));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_INTERVIEW_TRANSITION"));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-22T11:00:00Z\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_INTERVIEW_TRANSITION"));

        Fixture cancelFixture = fixture("Cancel Candidate");
        String cancelId = submit(cancelFixture, job(cancelFixture, "Cancel Job"));
        toInReview(cancelFixture, cancelId);
        String cancelInterviewId = schedule(cancelFixture, cancelId, 2);
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", cancelInterviewId)
                        .header("Authorization", recruiter(cancelFixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test void updateEnforcesOwnershipRoleAndVersion() throws Exception {
        Fixture fixture = fixture("Update Scope Candidate");
        String id = submit(fixture, job(fixture, "Update Scope Job"));
        toInReview(fixture, id);
        String interviewId = schedule(fixture, id, 2);
        Fixture other = fixture("Update Other Candidate");
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(other)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", candidate(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":99}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }

    @Test void postInterviewRejectionRemainsAllowed() throws Exception {
        Fixture fixture = fixture("Reject After Candidate");
        String id = submit(fixture, job(fixture, "Reject After Job"));
        toInReview(fixture, id);
        schedule(fixture, id, 2);
        mvc.perform(post("/api/v1/recruiter/applications/{id}/transitions", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"REJECTED\",\"reason\":\"Failed interview\",\"expectedVersion\":3}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.application.status").value("REJECTED"));
    }

    @Test void auditEventsAreWrittenForAllFourActions() throws Exception {
        Fixture fixture = fixture("Audit Candidate");
        String id = submit(fixture, job(fixture, "Audit Job"));
        toInReview(fixture, id);
        String interviewId = schedule(fixture, id, 2);
        assertAudit(fixture, interviewId, "CREATED", null, "SCHEDULED");

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        assertAudit(fixture, interviewId, "RESCHEDULED", "SCHEDULED", "SCHEDULED");
        Map<String, Object> rescheduled = jdbc.queryForMap("select before_value, after_value from interview_audit_events " +
                "where interview_id=? and action='RESCHEDULED'", interviewId);
        assertThat((String) rescheduled.get("before_value")).contains("2026-08-20T09:00:00Z");
        assertThat((String) rescheduled.get("after_value")).contains("2026-08-21T10:00:00Z");

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"expectedVersion\":2}"))
                .andExpect(status().isOk());
        assertAudit(fixture, interviewId, "COMPLETED", "SCHEDULED", "COMPLETED");

        Fixture cancelFixture = fixture("Audit Cancel Candidate");
        String cancelId = submit(cancelFixture, job(cancelFixture, "Audit Cancel Job"));
        toInReview(cancelFixture, cancelId);
        String cancelInterviewId = schedule(cancelFixture, cancelId, 2);
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", cancelInterviewId)
                        .header("Authorization", recruiter(cancelFixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        assertAudit(cancelFixture, cancelInterviewId, "CANCELLED", "SCHEDULED", "CANCELLED");
    }

    @Test void rejectsBlankOrNonHttpLocationAndAllowsStatusOnlyUpdates() throws Exception {
        Fixture fixture = fixture("Location Candidate");
        String id = submit(fixture, job(fixture, "Location Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONSITE\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.locationOrMeetingUrl").isNotEmpty());

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"PHONE\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.locationOrMeetingUrl").isNotEmpty());

        String response = mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONSITE\",\"locationOrMeetingUrl\":\"12 Marina Blvd, Singapore\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String interviewId = mapper.readTree(response).at("/data/interviewId").asText();

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationOrMeetingUrl\":\"   \",\"expectedVersion\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.locationOrMeetingUrl").isNotEmpty());

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"ONLINE\",\"expectedVersion\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test void createAndRescheduleRejectInvalidTimezoneButAcceptValidIanaZones() throws Exception {
        Fixture fixture = fixture("Timezone Candidate");
        String id = submit(fixture, job(fixture, "Timezone Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Not/AZone\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONSITE\",\"locationOrMeetingUrl\":\"12 Marina Blvd, Singapore\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.timezone").isNotEmpty());

        String interviewId = schedule(fixture, id, 2);

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Bad/Zone\",\"expectedVersion\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.timezone").isNotEmpty());

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"America/New_York\",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.timezone").value("America/New_York"));
    }

    @Test void phoneInterviewCreationSucceedsWithManualProvider() throws Exception {
        Fixture fixture = fixture("Phone Candidate");
        String id = submit(fixture, job(fixture, "Phone Job"));
        toInReview(fixture, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":30,\"mode\":\"PHONE\"," +
                                "\"locationOrMeetingUrl\":\"Call +65 1234 5678, ask for HR\"," +
                                "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mode").value("PHONE"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("Call +65 1234 5678, ask for HR"))
                .andExpect(jsonPath("$.data.meetingProvider").value("MANUAL"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("NOT_APPLICABLE"));
    }

    @Test void legacyOnlineManualInterviewRemainsReadable() throws Exception {
        Fixture fixture = fixture("Legacy Candidate");
        String id = submit(fixture, job(fixture, "Legacy Job"));
        toInReview(fixture, id);
        String interviewId = schedule(fixture, id, 2);
        // Simulate a pre-simplification record: ONLINE + MANUAL with a pasted link.
        jdbc.update("update interviews set mode='ONLINE', meeting_provider='MANUAL', " +
                "location_or_meeting_url='https://meet.example.com/legacy' where id=?", interviewId);

        mvc.perform(get("/api/v1/recruiter/applications/{id}", id).header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interview.mode").value("ONLINE"))
                .andExpect(jsonPath("$.data.interview.meetingProvider").value("MANUAL"))
                .andExpect(jsonPath("$.data.interview.locationOrMeetingUrl").value("https://meet.example.com/legacy"))
                .andExpect(jsonPath("$.data.interview.meetingSyncStatus").value("NOT_APPLICABLE"));
    }

    @Test void googleMeetReschedulePatchesExternalEventAndPreservesLink() throws Exception {
        Fixture fixture = fixture("Meet Reschedule Candidate");
        String id = submit(fixture, job(fixture, "Meet Reschedule Job"));
        toInReview(fixture, id);
        String eventId = "evt-" + UUID.randomUUID();
        String interviewId = scheduleGoogleMeet(fixture, id, 2, eventId);
        when(meetingProvisioning.updateMeeting(any()))
                .thenReturn(new MeetingSyncResult(MeetingSyncOutcome.SYNCED, null));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"durationMinutes\":30,\"note\":\"Moved\"," +
                                "\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-21T10:00:00Z"))
                .andExpect(jsonPath("$.data.durationMinutes").value(30))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("https://meet.google.com/abc-defg-hij"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"))
                .andExpect(jsonPath("$.data.version").value(4));

        ArgumentCaptor<MeetingUpdateRequest> captor = ArgumentCaptor.forClass(MeetingUpdateRequest.class);
        verify(meetingProvisioning, times(1)).updateMeeting(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(eventId);
        assertThat(captor.getValue().recruiterId()).isEqualTo(fixture.recruiterId());
        // Provisioning happens only at creation; an update never creates a new event.
        verify(meetingProvisioning, times(1)).provision(any());
    }

    @Test void googleMeetCancelDeletesExternalEventAndClearsLink() throws Exception {
        Fixture fixture = fixture("Meet Cancel Candidate");
        String id = submit(fixture, job(fixture, "Meet Cancel Job"));
        toInReview(fixture, id);
        String eventId = "evt-" + UUID.randomUUID();
        String interviewId = scheduleGoogleMeet(fixture, id, 2, eventId);
        when(meetingProvisioning.cancelMeeting(any()))
                .thenReturn(new MeetingSyncResult(MeetingSyncOutcome.SYNCED, null));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").isEmpty())
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"))
                .andExpect(jsonPath("$.data.version").value(4));

        ArgumentCaptor<MeetingCancelRequest> captor = ArgumentCaptor.forClass(MeetingCancelRequest.class);
        verify(meetingProvisioning, times(1)).cancelMeeting(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(eventId);
        verify(meetingProvisioning, never()).updateMeeting(any());
    }

    @Test void googleMeetRescheduleRemoteFailurePreservesInvitationAndIsRetryable() throws Exception {
        Fixture fixture = fixture("Meet Reschedule Fail Candidate");
        String id = submit(fixture, job(fixture, "Meet Reschedule Fail Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        when(meetingProvisioning.updateMeeting(any()))
                .thenReturn(new MeetingSyncResult(MeetingSyncOutcome.FAILED, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE"));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-20T09:00:00Z"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("https://meet.google.com/abc-defg-hij"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.version").value(4));

        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?",
                String.class, interviewId)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        assertAudit(fixture, interviewId, "SYNC_FAILED", "SCHEDULED", "SCHEDULED");

        // The same PATCH is retryable; a later success applies the new time.
        when(meetingProvisioning.updateMeeting(any()))
                .thenReturn(new MeetingSyncResult(MeetingSyncOutcome.SYNCED, null));
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-21T10:00:00Z"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"));
    }

    @Test void googleMeetCancelRemoteFailureKeepsScheduledWithLink() throws Exception {
        Fixture fixture = fixture("Meet Cancel Fail Candidate");
        String id = submit(fixture, job(fixture, "Meet Cancel Fail Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        when(meetingProvisioning.cancelMeeting(any()))
                .thenReturn(new MeetingSyncResult(MeetingSyncOutcome.FAILED, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE"));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("https://meet.google.com/abc-defg-hij"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"));

        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?",
                String.class, interviewId)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        assertAudit(fixture, interviewId, "SYNC_FAILED", "SCHEDULED", "SCHEDULED");
    }

    @Test void googleMeetRejectsUpdateWhenSyncInProgress() throws Exception {
        Fixture fixture = fixture("Meet In Progress Candidate");
        String id = submit(fixture, job(fixture, "Meet In Progress Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        jdbc.update("update interviews set meeting_sync_status='PENDING' where id=?", interviewId);

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_SYNC_IN_PROGRESS"));

        verify(meetingProvisioning, never()).updateMeeting(any());
        verify(meetingProvisioning, never()).cancelMeeting(any());
    }

    @Test void googleMeetUpdateRejectsNonOnlineModeAndClientLink() throws Exception {
        Fixture fixture = fixture("Meet Update Reject Candidate");
        String id = submit(fixture, job(fixture, "Meet Update Reject Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"ONSITE\",\"expectedVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationOrMeetingUrl\":\"https://meet.google.com/forged\",\"expectedVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(meetingProvisioning, never()).updateMeeting(any());
        verify(meetingProvisioning, never()).cancelMeeting(any());
    }

    @Test void googleMeetInitialProvisioningFailureRetryReProvisionsAndMarksReady() throws Exception {
        Fixture fixture = fixture("Meet Retry Ready Candidate");
        String id = submit(fixture, job(fixture, "Meet Retry Ready Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeetInitialFailure(fixture, id, 2);

        assertThat(jdbc.queryForObject("select meeting_event_id from interviews where id=?",
                String.class, interviewId)).isNull();
        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where id=?",
                String.class, interviewId)).isEqualTo("FAILED");
        String correlationId = jdbc.queryForObject("select meeting_correlation_id from interviews where id=?",
                String.class, interviewId);

        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-retry", "https://meet.google.com/retry-abc", null));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("https://meet.google.com/retry-abc"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-21T10:00:00Z"))
                .andExpect(jsonPath("$.data.version").value(4));

        // Only the single local interview exists, and the retry reused the
        // persisted correlation id instead of minting a second event/interview.
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, id)).isEqualTo(1);
        ArgumentCaptor<ProvisionRequest> captor = ArgumentCaptor.forClass(ProvisionRequest.class);
        verify(meetingProvisioning, times(2)).provision(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo(correlationId);
    }

    @Test void googleMeetInitialFailureRetryCarriesContactEmailFromApplicationNotBrowserInput() throws Exception {
        Fixture fixture = fixture("Meet Retry Attendee Candidate");
        String id = submit(fixture, job(fixture, "Meet Retry Attendee Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeetInitialFailure(fixture, id, 2);

        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where id=?",
                String.class, interviewId)).isEqualTo("FAILED");
        String correlationId = jdbc.queryForObject("select meeting_correlation_id from interviews where id=?",
                String.class, interviewId);

        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-" + UUID.randomUUID(), "https://meet.google.com/retry-abc", null));

        // The retry PATCH body carries no email; the attendee must come from the
        // application record captured when the candidate applied, not the browser.
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"));

        // The retry reused the persisted correlation id and never minted a second
        // internal interview; the re-provisioned attendee email equals the
        // application's stored contact_email, not anything from the request body.
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, id)).isEqualTo(1);
        ArgumentCaptor<ProvisionRequest> captor = ArgumentCaptor.forClass(ProvisionRequest.class);
        verify(meetingProvisioning, times(2)).provision(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        ProvisionRequest retry = captor.getAllValues().get(1);
        assertThat(retry.correlationId()).isEqualTo(correlationId);
        assertThat(retry.attendeeEmail()).isEqualTo(fixture.email());
        assertThat(jdbc.queryForObject("select contact_email from applications where id=?",
                String.class, id)).isEqualTo(retry.attendeeEmail());
    }

    @Test void googleMeetInitialProvisioningFailureRetryFailsAgainKeepsSafeFailed() throws Exception {
        Fixture fixture = fixture("Meet Retry Fail Candidate");
        String id = submit(fixture, job(fixture, "Meet Retry Fail Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeetInitialFailure(fixture, id, 2);

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").isEmpty());

        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?",
                String.class, interviewId)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        verify(meetingProvisioning, times(2)).provision(any());
    }

    @Test void googleMeetInitialFailureRetryNullProvisionStaysFailedWithoutSecondInterview() throws Exception {
        Fixture fixture = fixture("Meet Retry Null Provision Candidate");
        String id = submit(fixture, job(fixture, "Meet Retry Null Provision Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeetInitialFailure(fixture, id, 2);
        when(meetingProvisioning.provision(any())).thenReturn(null);

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").isEmpty());

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?",
                String.class, interviewId)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        assertAudit(fixture, interviewId, "SYNC_FAILED", "SCHEDULED", "SCHEDULED");
    }

    @Test void googleMeetInitialProvisioningFailureCancelIsLocalOnly() throws Exception {
        Fixture fixture = fixture("Meet Retry Cancel Candidate");
        String id = submit(fixture, job(fixture, "Meet Retry Cancel Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeetInitialFailure(fixture, id, 2);

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(jdbc.queryForObject("select status from interviews where id=?",
                String.class, interviewId)).isEqualTo("CANCELLED");
        verify(meetingProvisioning, never()).cancelMeeting(any());
        verify(meetingProvisioning, never()).updateMeeting(any());
    }

    @Test void googleMeetCompletionIsLocalOnly() throws Exception {
        Fixture fixture = fixture("Meet Complete Candidate");
        String id = submit(fixture, job(fixture, "Meet Complete Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.version").value(3));

        verify(meetingProvisioning, never()).updateMeeting(any());
        verify(meetingProvisioning, never()).cancelMeeting(any());
    }

    @Test void googleMeetRescheduleCommitsPendingBeforeExternalCall() throws Exception {
        Fixture fixture = fixture("Meet Pending Commit Candidate");
        String id = submit(fixture, job(fixture, "Meet Pending Commit Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());

        when(meetingProvisioning.updateMeeting(any())).thenAnswer(invocation -> {
            // Phase 1 has already committed: the interview is PENDING while the
            // external Google call is still in flight.
            assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where id=?",
                    String.class, interviewId)).isEqualTo("PENDING");
            return new MeetingSyncResult(MeetingSyncOutcome.SYNCED, null);
        });

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-21T10:00:00Z"));
    }

    @Test void googleMeetRejectsStaleVersionAsSyncInProgressWhenPending() throws Exception {
        Fixture fixture = fixture("Meet Pending Priority Candidate");
        String id = submit(fixture, job(fixture, "Meet Pending Priority Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        // Real reservation state: PENDING and the version already advanced 2 -> 3.
        jdbc.update("update interviews set meeting_sync_status='PENDING', version=3 where id=?", interviewId);

        // A stale expectedVersion must surface as sync-in-progress, not VERSION_CONFLICT.
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_SYNC_IN_PROGRESS"));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_SYNC_IN_PROGRESS"));

        verify(meetingProvisioning, never()).updateMeeting(any());
        verify(meetingProvisioning, never()).cancelMeeting(any());
    }

    @Test void googleMeetRejectsCompletionWhenSyncInProgress() throws Exception {
        Fixture fixture = fixture("Meet Complete Pending Candidate");
        String id = submit(fixture, job(fixture, "Meet Complete Pending Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        jdbc.update("update interviews set meeting_sync_status='PENDING', version=3 where id=?", interviewId);

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_SYNC_IN_PROGRESS"));

        // Local state is untouched and no external call is made.
        assertThat(jdbc.queryForObject("select status from interviews where id=?", String.class, interviewId))
                .isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select version from interviews where id=?", Integer.class, interviewId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?", String.class, interviewId))
                .isNull();
        verify(meetingProvisioning, never()).updateMeeting(any());
        verify(meetingProvisioning, never()).cancelMeeting(any());
    }

    @Test void googleMeetRescheduleUnexpectedExceptionFailsSafelyAndIsRetryable() throws Exception {
        Fixture fixture = fixture("Meet Reschedule Runtime Candidate");
        String id = submit(fixture, job(fixture, "Meet Reschedule Runtime Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        when(meetingProvisioning.updateMeeting(any())).thenThrow(new RuntimeException("boom"));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-20T09:00:00Z"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("https://meet.google.com/abc-defg-hij"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.version").value(4));

        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?",
                String.class, interviewId)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where id=?",
                String.class, interviewId)).isEqualTo("FAILED");
        assertAudit(fixture, interviewId, "SYNC_FAILED", "SCHEDULED", "SCHEDULED");
    }

    @Test void googleMeetCancelUnexpectedExceptionKeepsScheduledWithLink() throws Exception {
        Fixture fixture = fixture("Meet Cancel Runtime Candidate");
        String id = submit(fixture, job(fixture, "Meet Cancel Runtime Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        when(meetingProvisioning.cancelMeeting(any())).thenThrow(new RuntimeException("boom"));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("https://meet.google.com/abc-defg-hij"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"));

        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?",
                String.class, interviewId)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
        assertAudit(fixture, interviewId, "SYNC_FAILED", "SCHEDULED", "SCHEDULED");
    }

    @Test void googleMeetNullSyncResultFailsSafely() throws Exception {
        Fixture fixture = fixture("Meet Null Result Candidate");
        String id = submit(fixture, job(fixture, "Meet Null Result Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        when(meetingProvisioning.updateMeeting(any())).thenReturn(null);

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"));

        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?",
                String.class, interviewId)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
    }

    @Test void googleMeetInvalidSyncResultFailsSafely() throws Exception {
        Fixture fixture = fixture("Meet Invalid Result Candidate");
        String id = submit(fixture, job(fixture, "Meet Invalid Result Job"));
        toInReview(fixture, id);
        String interviewId = scheduleGoogleMeet(fixture, id, 2, "evt-" + UUID.randomUUID());
        when(meetingProvisioning.updateMeeting(any())).thenReturn(new MeetingSyncResult(null, null));

        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("FAILED"));

        assertThat(jdbc.queryForObject("select meeting_sync_error from interviews where id=?",
                String.class, interviewId)).isEqualTo("GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
    }

    private void assertAudit(Fixture fixture, String interviewId, String action, String beforeStatus, String afterStatus) {
        assertThat(jdbc.queryForObject("select count(*) from interview_audit_events where interview_id=? and action=? " +
                        "and actor_id=? and company_id=? and occurred_at is not null and request_id is not null",
                Integer.class, interviewId, action, fixture.recruiterId(), fixture.companyId())).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("select before_value, after_value, reason from interview_audit_events " +
                "where interview_id=? and action=?", interviewId, action);
        if (beforeStatus == null) {
            assertThat(row.get("before_value")).isNull();
        } else {
            assertThat((String) row.get("before_value")).contains("\"status\":\"" + beforeStatus + "\"");
        }
        assertThat((String) row.get("after_value")).contains("\"status\":\"" + afterStatus + "\"");
        assertThat((String) row.get("reason")).isNotBlank();
    }

    private void toInReview(Fixture fixture, String applicationId) throws Exception {
        mvc.perform(post("/api/v1/recruiter/applications/{id}/transitions", applicationId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"IN_REVIEW\",\"reason\":\"Reviewing\",\"expectedVersion\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.application.status").value("IN_REVIEW"));
    }

    private String schedule(Fixture fixture, String applicationId, int expectedVersion) throws Exception {
        String response = mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(SCHEDULE, expectedVersion)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/interviewId").asText();
    }

    private String scheduleGoogleMeet(Fixture fixture, String applicationId, int expectedVersion, String eventId)
            throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, eventId, "https://meet.google.com/abc-defg-hij", null));
        String response = mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":" + expectedVersion + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/interviewId").asText();
    }

    private String scheduleGoogleMeetInitialFailure(Fixture fixture, String applicationId, int expectedVersion)
            throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.FAILED, null, null, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE"));
        String response = mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\"," +
                                "\"expectedApplicationVersion\":" + expectedVersion + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/interviewId").asText();
    }

    private String submit(Fixture f, String jobId) throws Exception {
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resumeId\":\"" + f.resumeId() +
                                "\",\"contactEmail\":\"" + f.email() + "\",\"shareProfile\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/applicationId").asText();
    }

    private Fixture fixture(String candidateName) {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(), CompanyMemberRole.ADMIN, now));
        UserEntity candidate = users.save(user(candidateName, UserRole.CANDIDATE, now));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer", "Summary",
                "[]", 1, now, now));
        return new Fixture(jwt.createAccessToken(recruiter), jwt.createAccessToken(candidate), recruiter.getId(),
                candidate.getId(), candidate.getEmail(), company.getId(), resumeId);
    }
    private UserEntity user(String name, UserRole role, Instant now) { String id=UUID.randomUUID().toString(); return new UserEntity(id,id+"@example.com","hash",name,role,UserStatus.ACTIVE,"2026-08",now,now); }
    private String job(Fixture f, String title) { Instant now=Instant.parse("2026-08-12T01:00:00Z"); String id=UUID.randomUUID().toString(); jobs.save(new JobEntity(id,f.companyId(),f.recruiterId(),f.recruiterId(),title,EmploymentType.FULL_TIME,WorkplaceType.HYBRID,"Singapore",5000,8000,SalaryCurrency.SGD,SalaryPeriod.MONTH,"Description","[]","[]",null,Visibility.PUBLIC,JobStatus.ACTIVE,0,1,now,now)); return id; }
    private static String recruiter(Fixture f){return "Bearer "+f.recruiterToken();} private static String candidate(Fixture f){return "Bearer "+f.candidateToken();}
    private record Fixture(String recruiterToken,String candidateToken,String recruiterId,String candidateId,String email,String companyId,String resumeId){}
}
