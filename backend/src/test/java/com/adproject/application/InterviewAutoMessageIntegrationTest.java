package com.adproject.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.CompanyMemberRole;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.integration.google.MeetingProvisioningException;
import com.adproject.integration.google.MeetingProvisioningPort;
import com.adproject.integration.google.ProvisionOutcome;
import com.adproject.integration.google.ProvisionResult;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewAutoMessageIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs;
    @Autowired ResumeRepository resumes;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MeetingProvisioningPort meetingProvisioning;

    private static final String ONSITE = "{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\","
            + "\"durationMinutes\":60,\"mode\":\"ONSITE\",\"locationOrMeetingUrl\":\"12 Marina Blvd, Singapore\","
            + "\"expectedApplicationVersion\":%d}";

    private static final String ONLINE = "{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\","
            + "\"durationMinutes\":60,\"mode\":\"ONLINE\",\"meetingProvider\":\"GOOGLE_MEET\","
            + "\"expectedApplicationVersion\":%d}";

    @Test
    void manualInterviewCreationAppendsExactlyOneSystemMessageCandidateCanRead() throws Exception {
        Fixture f = fixture("Manual Message Candidate");
        String id = submit(f, job(f, "Backend Engineer"));
        toInReview(f, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(ONSITE, 2)))
                .andExpect(status().isCreated());

        String conversationId = conversationId(id);
        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id=?",
                Integer.class, conversationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sender_type from messages where conversation_id=?",
                String.class, conversationId)).isEqualTo("SYSTEM");

        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].senderType").value("SYSTEM"))
                .andExpect(jsonPath("$.data[0].body").value(containsString("Backend Engineer")));
    }

    @Test
    void manualMessageBodyContainsJobTitleTimeTimezoneModeAndLocationWithoutTokenOrLink() throws Exception {
        Fixture f = fixture("Manual Content Candidate");
        String id = submit(f, job(f, "Backend Engineer"));
        toInReview(f, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(ONSITE, 2)))
                .andExpect(status().isCreated());

        String body = messageBody(id);
        assertThat(body).contains("Backend Engineer");
        assertThat(body).contains("2026-08-20 17:00");
        assertThat(body).contains("Asia/Singapore");
        assertThat(body).contains("On-site");
        assertThat(body).contains("Location: 12 Marina Blvd, Singapore");
        assertThat(body).doesNotContain("meet.google.com", "token", "Bearer", "evt-");
    }

    @Test
    void googleMeetReadyInterviewMessageIncludesMeetLinkButNoEventIdOrToken() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-secret-123", "https://meet.google.com/abc-defg-hij", null));

        Fixture f = fixture("Meet Ready Message Candidate");
        String id = submit(f, job(f, "Online Engineer"));
        toInReview(f, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(ONLINE, 2)))
                .andExpect(status().isCreated());

        assertThat(messageCount(id)).isEqualTo(1);
        String body = messageBody(id);
        assertThat(body).contains("Online Engineer");
        assertThat(body).contains("Online");
        assertThat(body).contains("Meeting link: https://meet.google.com/abc-defg-hij");
        assertThat(body).doesNotContain("evt-secret-123", "token", "Bearer", "refresh");
    }

    @Test
    void googleMeetProvisioningFailureStillWritesMessageWithoutLink() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.FAILED, null, null, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE"));

        Fixture f = fixture("Meet Failed Message Candidate");
        String id = submit(f, job(f, "Online Engineer"));
        toInReview(f, id);

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(ONLINE, 2)))
                .andExpect(status().isCreated());

        assertThat(messageCount(id)).isEqualTo(1);
        String body = messageBody(id);
        assertThat(body).contains("Online Engineer");
        assertThat(body).contains("Online");
        assertThat(body).doesNotContain("Meeting link", "meet.google.com");
    }

    @Test
    void noMessageWhenInterviewCreationFails() throws Exception {
        // Connection preflight failure: no interview is created, so no message.
        doThrow(new MeetingProvisioningException("GOOGLE_MEET_NOT_CONNECTED"))
                .when(meetingProvisioning).ensureConnectionUsable(anyString());
        Fixture meet = fixture("Meet Preflight Candidate");
        String meetId = submit(meet, job(meet, "Preflight Job"));
        toInReview(meet, meetId);
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", meetId)
                        .header("Authorization", recruiter(meet)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(ONLINE, 2)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, meetId)).isZero();
        assertThat(messageCount(meetId)).isZero();

        // Validation failure: no interview and no message.
        Fixture validation = fixture("Validation Message Candidate");
        String validationId = submit(validation, job(validation, "Validation Job"));
        toInReview(validation, validationId);
        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", validationId)
                        .header("Authorization", recruiter(validation)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-20T09:00:00Z\",\"timezone\":\"Asia/Singapore\"," +
                                "\"durationMinutes\":60,\"mode\":\"ONSITE\",\"expectedApplicationVersion\":2}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, validationId)).isZero();
        assertThat(messageCount(validationId)).isZero();
    }

    @Test
    void crossCompanyRecruiterCannotScheduleAndProducesNoMessage() throws Exception {
        Fixture f = fixture("Owner Message Candidate");
        String id = submit(f, job(f, "Owner Job"));
        toInReview(f, id);
        Fixture other = fixture("Other Company Candidate");

        mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", id)
                        .header("Authorization", recruiter(other)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(ONSITE, 2)))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, id)).isZero();
        assertThat(messageCount(id)).isZero();
    }

    @Test
    void googleMeetRetryAfterInitialFailureDoesNotDuplicateMessage() throws Exception {
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.FAILED, null, null, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE"));

        Fixture f = fixture("Retry Message Candidate");
        String id = submit(f, job(f, "Retry Job"));
        toInReview(f, id);
        String interviewId = scheduleOnline(f, id, 2);

        assertThat(messageCount(id)).isEqualTo(1);

        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-" + UUID.randomUUID(), "https://meet.google.com/retry-abc", null));
        mvc.perform(patch("/api/v1/recruiter/interviews/{id}", interviewId)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-08-21T10:00:00Z\",\"expectedVersion\":2}"))
                .andExpect(status().isOk());

        assertThat(messageCount(id)).isEqualTo(1);
    }

    // ---- helpers ----

    private String conversationId(String applicationId) {
        return jdbc.queryForObject("select id from conversations where application_id=?",
                String.class, applicationId);
    }

    private int messageCount(String applicationId) {
        return jdbc.queryForObject("select count(*) from messages where conversation_id=?",
                Integer.class, conversationId(applicationId));
    }

    private String messageBody(String applicationId) {
        return jdbc.queryForObject("select body from messages where conversation_id=?",
                String.class, conversationId(applicationId));
    }

    private String scheduleOnline(Fixture f, String applicationId, int expectedVersion) throws Exception {
        String response = mvc.perform(post("/api/v1/recruiter/applications/{id}/interviews", applicationId)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(ONLINE, expectedVersion)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/interviewId").asText();
    }

    private void toInReview(Fixture f, String applicationId) throws Exception {
        mvc.perform(post("/api/v1/recruiter/applications/{id}/transitions", applicationId)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"IN_REVIEW\",\"reason\":\"Reviewing\",\"expectedVersion\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.application.status").value("IN_REVIEW"));
    }

    private String submit(Fixture f, String jobId) throws Exception {
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + f.resumeId() + "\",\"contactEmail\":\"" + f.email()
                                + "\",\"shareProfile\":true}"))
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
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer",
                "Summary", "[]", 1, now, now));
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

    private static String recruiter(Fixture f) { return "Bearer " + f.recruiterToken(); }
    private static String candidate(Fixture f) { return "Bearer " + f.candidateToken(); }

    private record Fixture(String recruiterToken, String candidateToken, String recruiterId, String candidateId,
                           String email, String companyId, String resumeId) {}
}
