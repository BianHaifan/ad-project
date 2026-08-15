package com.adproject.integration.google.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.CompanyMemberRole;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.integration.google.MeetingProvisioningPort;
import com.adproject.integration.google.domain.GoogleConnectionStatus;
import com.adproject.integration.google.infrastructure.GoogleOAuthStateEntity;
import com.adproject.integration.google.infrastructure.GoogleOAuthStateRepository;
import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionEntity;
import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionRepository;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.Visibility;
import com.adproject.job.domain.WorkplaceType;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class GoogleOAuthIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;
    @Autowired CompanyRepository companies; @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs; @Autowired ResumeRepository resumes;
    @Autowired GoogleRecruiterConnectionRepository connections; @Autowired GoogleOAuthStateRepository oauthStates;
    @Autowired MeetingProvisioningPort meetingProvisioning; @Autowired SecretCipher cipher;
    @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper mapper;
    @MockitoBean GoogleOAuthClient googleOAuthClient;
    @MockitoBean GoogleCalendarClient googleCalendarClient;
    @MockitoBean GoogleTokenClient tokenClient;

    private static final String ACCESS_TOKEN = "fake-access-token";
    private static final String REFRESH_TOKEN = "fake-refresh-token";
    private static final String WEB_RETURN_URI = "http://localhost:3000/recruiter/google-oauth";
    private static final String GOOGLE_MEET = "{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\","
            + "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\","
            + "\"expectedApplicationVersion\":2}";

    @Test void authorizeRequiresRecruiterRole() throws Exception {
        Fixture fixture = fixture("OAuth Candidate");
        mvc.perform(post("/api/v1/recruiter/google-oauth/authorize")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/recruiter/google-oauth/authorize").header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/recruiter/google-oauth/authorize").header("Authorization", "Bearer " + fixture.recruiterToken()))
                .andExpect(status().isOk());
    }

    @Test void authorizationUrlUsesFixedHostScopeAndRedirect() throws Exception {
        Fixture fixture = fixture("Url Candidate");
        String body = begin(fixture);
        String url = mapper.readTree(body).at("/data/authorizationUrl").asText();
        URI uri = URI.create(url);
        assertThat(uri.getHost()).isEqualTo("accounts.google.com");
        assertThat(uri.getPath()).isEqualTo("/o/oauth2/v2/auth");
        assertThat(uri.getRawQuery()).contains("response_type=code")
                .contains("access_type=offline")
                .contains("prompt=consent")
                .contains("code_challenge_method=S256")
                .contains("client_id=test-client-id")
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fv1%2Frecruiter%2Fgoogle-oauth%2Fcallback")
                .contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar.events");
        assertThat(queryParam(url, "state")).isNotBlank();
        assertThat(queryParam(url, "code_challenge")).isNotBlank();
    }

    @Test void statusReturnsDisconnectedThenConnectedWithoutLeakingTokens() throws Exception {
        Fixture fixture = fixture("Status Candidate");
        mvc.perform(get("/api/v1/recruiter/google-oauth/status").header("Authorization", "Bearer " + fixture.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.connectedAt").isEmpty());

        connect(fixture);

        String body = mvc.perform(get("/api/v1/recruiter/google-oauth/status").header("Authorization", "Bearer " + fixture.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.connectedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("access_token", "refresh_token", "client_secret", "verifier");
    }

    @Test void callbackStoresEncryptedTokensAtRest() throws Exception {
        Fixture fixture = fixture("Encrypt Candidate");
        connect(fixture);

        String storedAccess = jdbc.queryForObject(
                "select access_token_encrypted from google_recruiter_connections where recruiter_id=?",
                String.class, fixture.recruiterId());
        String storedRefresh = jdbc.queryForObject(
                "select refresh_token_encrypted from google_recruiter_connections where recruiter_id=?",
                String.class, fixture.recruiterId());
        assertThat(storedAccess).doesNotContain(ACCESS_TOKEN);
        assertThat(storedRefresh).doesNotContain(REFRESH_TOKEN);
        assertThat(cipher.decrypt(storedAccess)).isEqualTo(ACCESS_TOKEN);
        assertThat(cipher.decrypt(storedRefresh)).isEqualTo(REFRESH_TOKEN);
    }

    @Test void callbackRejectsUnknownTamperedState() throws Exception {
        Fixture fixture = fixture("Tamper Candidate");
        begin(fixture);
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("code", "fake").param("state", "tampered-state"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=failed"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void callbackRejectsReplayedState() throws Exception {
        Fixture fixture = fixture("Replay Candidate");
        String state = connect(fixture);
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("code", "fake").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=failed"));
    }

    @Test void callbackRejectsExpiredState() throws Exception {
        Fixture fixture = fixture("Expired Candidate");
        String state = OAuthUtil.generateState();
        Instant now = Instant.now();
        oauthStates.save(new GoogleOAuthStateEntity(UUID.randomUUID().toString(), OAuthUtil.sha256Hex(state),
                fixture.recruiterId(), cipher.encrypt("verifier"), now.minusSeconds(700), now.minusSeconds(100)));
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("code", "fake").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=failed"));
    }

    @Test void callbackWithErrorButNoStateRedirectsFailed() throws Exception {
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("error", "access_denied"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=failed"));
    }

    @Test void callbackConsumesStateBeforeDenied() throws Exception {
        Fixture fixture = fixture("Denied Candidate");
        String state = beginState(fixture);
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("error", "access_denied").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=denied"));
        assertThat(consumedCount(state)).isEqualTo(1);
        // A replay of the same (now consumed) state must be rejected.
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("code", "fake").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=failed"));
    }

    @Test void callbackConsumesStateBeforeTokenExchangeFailure() throws Exception {
        when(googleOAuthClient.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .thenThrow(new GoogleOAuthTokenExchangeException("boom"));
        Fixture fixture = fixture("Exchange Candidate");
        String state = beginState(fixture);
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("code", "fake").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=failed"));
        // The state is consumed in a committed transaction and must stay consumed:
        // the exchange failure must not create a connection or allow a retry.
        assertThat(connections.findByRecruiterId(fixture.recruiterId())).isEmpty();
        assertThat(consumedCount(state)).isEqualTo(1);
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("code", "fake").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=failed"));
    }

    @Test void successfulCallbackRedirectsToFixedUriWithoutLeakingSecrets() throws Exception {
        when(googleOAuthClient.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .thenReturn(new TokenExchangeResult(ACCESS_TOKEN, REFRESH_TOKEN, 3600, "Bearer"));
        Fixture fixture = fixture("Redirect Candidate");
        String state = beginState(fixture);
        String location = mvc.perform(get("/api/v1/recruiter/google-oauth/callback")
                        .param("code", "fake-code").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=connected"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).doesNotContain("code", "state", ACCESS_TOKEN, REFRESH_TOKEN, fixture.recruiterId(), "fake-code");
    }

    @Test void callbackIgnoresMaliciousRedirectParams() throws Exception {
        when(googleOAuthClient.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .thenReturn(new TokenExchangeResult(ACCESS_TOKEN, REFRESH_TOKEN, 3600, "Bearer"));
        Fixture fixture = fixture("Malicious Candidate");
        String state = beginState(fixture);
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback")
                        .param("code", "fake").param("state", state)
                        .param("returnUrl", "https://evil.example")
                        .param("redirect_uri", "https://evil.example")
                        .param("next", "https://evil.example"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=connected"));
    }

    @Test void callbackWithUnknownErrorRedirectsFailed() throws Exception {
        Fixture fixture = fixture("Server Error Candidate");
        String state = beginState(fixture);
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("error", "server_error").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=failed"));
    }

    @Test void disconnectDeletesConnectionAndUnconsumedStates() throws Exception {
        Fixture fixture = fixture("Disconnect Candidate");
        connect(fixture);
        assertThat(connections.findByRecruiterId(fixture.recruiterId())).isPresent();

        mvc.perform(delete("/api/v1/recruiter/google-oauth").header("Authorization", "Bearer " + fixture.recruiterToken()))
                .andExpect(status().isNoContent());

        assertThat(connections.findByRecruiterId(fixture.recruiterId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from google_oauth_states where recruiter_id=?",
                Integer.class, fixture.recruiterId())).isZero();
        assertThat(meetingProvisioning.isConnected(fixture.recruiterId())).isFalse();
    }

    @Test void connectedRecruiterProvisionsMeetingAfterConnect() throws Exception {
        Fixture fixture = fixture("Provision Candidate");
        String applicationId = submit(fixture, job(fixture, "Provision Job"));
        toInReview(fixture, applicationId);

        assertThat(meetingProvisioning.isConnected(fixture.recruiterId())).isFalse();
        connect(fixture);
        assertThat(meetingProvisioning.isConnected(fixture.recruiterId())).isTrue();
        assertThat(meetingProvisioning.isProvisioningAvailable(fixture.recruiterId())).isTrue();

        when(googleCalendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-connected", "https://meet.google.com/abc-defg-hij"));

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", "Bearer " + fixture.recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON).content(GOOGLE_MEET))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingProvider").value("GOOGLE_MEET"))
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"))
                .andExpect(jsonPath("$.data.locationOrMeetingUrl").value("https://meet.google.com/abc-defg-hij"));
        assertThat(jdbc.queryForObject("select meeting_event_id from interviews where application_id=?",
                String.class, applicationId)).isEqualTo("evt-connected");
        assertThat(jdbc.queryForObject("select meeting_correlation_id from interviews where application_id=?",
                String.class, applicationId)).isNotNull();
    }

    @Test void statusReportsRevokedAsDisconnected() throws Exception {
        Fixture fixture = fixture("Revoked Status Candidate");
        connect(fixture);
        markRevoked(fixture);

        mvc.perform(get("/api/v1/recruiter/google-oauth/status")
                        .header("Authorization", "Bearer " + fixture.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test void revokedConnectionRecoversAfterReconnectAndCanProvisionAgain() throws Exception {
        Fixture fixture = fixture("Revoked Reconnect Candidate");
        String applicationId = submit(fixture, job(fixture, "Revoked Reconnect Job"));
        toInReview(fixture, applicationId);
        connect(fixture);
        markRevoked(fixture);

        // A revoked connection must yield RECONNECT_REQUIRED (not NOT_CONNECTED)
        // and must not create the interview or move the application.
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", "Bearer " + fixture.recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON).content(GOOGLE_MEET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_RECONNECT_REQUIRED"));
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, applicationId)).isZero();
        assertThat(jdbc.queryForObject("select status from applications where id=?",
                String.class, applicationId)).isEqualTo("IN_REVIEW");

        // Re-authorize: the callback restores the connection to CONNECTED.
        connect(fixture);
        mvc.perform(get("/api/v1/recruiter/google-oauth/status")
                        .header("Authorization", "Bearer " + fixture.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"));

        // A Google Meet interview can now be created again.
        when(googleCalendarClient.createEvent(anyString(), any()))
                .thenReturn(new CalendarEvent("evt-reconnect", "https://meet.google.com/abc-defg-hij"));
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", "Bearer " + fixture.recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON).content(GOOGLE_MEET))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.meetingSyncStatus").value("READY"));
    }

    @Test void expiredTokenInvalidGrantPreflightRejectsBeforeInterviewCreation() throws Exception {
        when(tokenClient.refreshAccessToken(anyString()))
                .thenThrow(new GoogleTokenRefreshException(GoogleTokenRefreshException.Category.INVALID_GRANT, "revoked"));
        Fixture fixture = fixture("Preflight Candidate");
        String applicationId = submit(fixture, job(fixture, "Preflight Job"));
        toInReview(fixture, applicationId);
        // A connected connection whose access token is already expired.
        connections.save(new GoogleRecruiterConnectionEntity(UUID.randomUUID().toString(), fixture.recruiterId(),
                cipher.encrypt("access-token"), cipher.encrypt("refresh-token"),
                Instant.now().minusSeconds(60), GoogleConnectionStatus.CONNECTED, Instant.now()));

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", "Bearer " + fixture.recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON).content(GOOGLE_MEET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_MEET_RECONNECT_REQUIRED"));

        // The invalid grant is discovered before any local state is created.
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, applicationId)).isZero();
        assertThat(jdbc.queryForObject("select status from applications where id=?",
                String.class, applicationId)).isEqualTo("IN_REVIEW");
        assertThat(jdbc.queryForObject("select status from google_recruiter_connections where recruiter_id=?",
                String.class, fixture.recruiterId())).isEqualTo("REVOKED");
    }

    @Test void invalidTimezoneRejectedBeforeConnectionPreflight() throws Exception {
        Fixture fixture = fixture("Bad Timezone Candidate");
        String applicationId = submit(fixture, job(fixture, "Bad Timezone Job"));
        toInReview(fixture, applicationId);

        // A connected connection whose access token is already expired, so a
        // refresh would fire if the connection preflight were reached.
        connections.save(new GoogleRecruiterConnectionEntity(UUID.randomUUID().toString(), fixture.recruiterId(),
                cipher.encrypt("access-token"), cipher.encrypt("refresh-token"),
                Instant.now().minusSeconds(60), GoogleConnectionStatus.CONNECTED, Instant.now()));
        int beforeVersion = jdbc.queryForObject(
                "select version from google_recruiter_connections where recruiter_id=?",
                Integer.class, fixture.recruiterId());

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", "Bearer " + fixture.recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Not/AZone\","
                                + "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\","
                                + "\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.timezone").isNotEmpty());

        // The invalid request must never reach the token transport or mutate
        // the connection state.
        verify(tokenClient, never()).refreshAccessToken(anyString());
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, applicationId)).isZero();
        assertThat(jdbc.queryForObject("select status from applications where id=?",
                String.class, applicationId)).isEqualTo("IN_REVIEW");
        assertThat(jdbc.queryForObject("select status from google_recruiter_connections where recruiter_id=?",
                String.class, fixture.recruiterId())).isEqualTo("CONNECTED");
        assertThat(cipher.decrypt(jdbc.queryForObject(
                "select access_token_encrypted from google_recruiter_connections where recruiter_id=?",
                String.class, fixture.recruiterId()))).isEqualTo("access-token");
        assertThat(cipher.decrypt(jdbc.queryForObject(
                "select refresh_token_encrypted from google_recruiter_connections where recruiter_id=?",
                String.class, fixture.recruiterId()))).isEqualTo("refresh-token");
        assertThat(jdbc.queryForObject("select version from google_recruiter_connections where recruiter_id=?",
                Integer.class, fixture.recruiterId())).isEqualTo(beforeVersion);
    }

    private String begin(Fixture fixture) throws Exception {
        String body = mvc.perform(post("/api/v1/recruiter/google-oauth/authorize")
                        .header("Authorization", "Bearer " + fixture.recruiterToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).at("/data/authorizationUrl").asText()).isNotBlank();
        return body;
    }

    private String beginState(Fixture fixture) throws Exception {
        String url = mapper.readTree(begin(fixture)).at("/data/authorizationUrl").asText();
        return queryParam(url, "state");
    }

    private String connect(Fixture fixture) throws Exception {
        when(googleOAuthClient.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .thenReturn(new TokenExchangeResult(ACCESS_TOKEN, REFRESH_TOKEN, 3600, "Bearer"));
        String state = beginState(fixture);
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("code", "fake").param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", WEB_RETURN_URI + "?googleOAuth=connected"))
                .andExpect(header().string("Cache-Control", "no-store"));
        return state;
    }

    private void markRevoked(Fixture fixture) {
        GoogleRecruiterConnectionEntity connection = connections.findByRecruiterId(fixture.recruiterId()).orElseThrow();
        connection.markRevoked(Instant.now());
        connections.save(connection);
    }

    private static String queryParam(String url, String name) {
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private int consumedCount(String state) {
        return jdbc.queryForObject(
                "select count(*) from google_oauth_states where state_hash=? and consumed_at is not null",
                Integer.class, OAuthUtil.sha256Hex(state));
    }

    private void toInReview(Fixture fixture, String applicationId) throws Exception {
        mvc.perform(post("/api/v1/recruiter/applications/{id}/transitions", applicationId)
                        .header("Authorization", "Bearer " + fixture.recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"IN_REVIEW\",\"reason\":\"Reviewing\",\"expectedVersion\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.application.status").value("IN_REVIEW"));
    }

    private String submit(Fixture f, String jobId) throws Exception {
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", "Bearer " + f.candidateToken()).header("Idempotency-Key", UUID.randomUUID())
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
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(),
                CompanyMemberRole.ADMIN, now));
        UserEntity candidate = users.save(user(candidateName, UserRole.CANDIDATE, now));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer", "Summary",
                "[]", 1, now, now));
        return new Fixture(jwt.createAccessToken(recruiter), jwt.createAccessToken(candidate), recruiter.getId(),
                candidate.getId(), candidate.getEmail(), company.getId(), resumeId);
    }

    private UserEntity user(String name, UserRole role, Instant now) {
        String id = UUID.randomUUID().toString();
        return new UserEntity(id, id + "@example.com", "hash", name, role, UserStatus.ACTIVE, "2026-08", now, now);
    }

    private String job(Fixture f, String title) {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        String id = UUID.randomUUID().toString();
        jobs.save(new JobEntity(id, f.companyId(), f.recruiterId(), f.recruiterId(), title, EmploymentType.FULL_TIME,
                WorkplaceType.HYBRID, "Singapore", 5000, 8000, SalaryCurrency.SGD, SalaryPeriod.MONTH, "Description",
                "[]", "[]", null, Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1, now, now));
        return id;
    }

    private record Fixture(String recruiterToken, String candidateToken, String recruiterId, String candidateId,
                           String email, String companyId, String resumeId) {}
}
