package com.adproject.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.JobVisibility;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.WorkplaceType;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.resume.domain.ResumeExperience;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JobRepository jobRepository;
    @Autowired ResumeRepository resumeRepository;

    @Test
    void candidateSubmitsApplicationAndCreatesImmutableSnapshotAndTimeline() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE);
        String requestId = "req_submit_application_1";
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = applicationBody(fixture.resumeId(), fixture.candidate().email(), true);

        String responseBody = mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Authorization", "Bearer " + fixture.candidate().accessToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.data.jobId").value(fixture.jobId()))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.matchScore").value(nullValue()))
                .andExpect(jsonPath("$.data.interview").value(nullValue()))
                .andExpect(jsonPath("$.data.timeline[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.timeline[0].completed").value(true))
                .andExpect(jsonPath("$.data.timeline[1].completed").value(false))
                .andExpect(jsonPath("$.data.resumeSnapshot.resumeId").value(fixture.resumeId()))
                .andExpect(jsonPath("$.data.resumeSnapshot.fullName").value("Candidate One"))
                .andExpect(jsonPath("$.data.resumeSnapshot.experiences[0].title").value("Backend Intern"))
                .andExpect(jsonPath("$.data.nextSteps.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        String applicationId = response.at("/data/applicationId").asText();
        String snapshotId = response.at("/data/resumeSnapshot/snapshotId").asText();
        assertThat(applicationId).isNotBlank();
        assertThat(snapshotId).isNotBlank();
        assertThat(response.at("/data/appliedAt").asText()).endsWith("Z");
        assertThat(count("applications", "id", applicationId)).isEqualTo(1);
        assertThat(count("resume_snapshots", "id", snapshotId)).isEqualTo(1);
        assertThat(count("application_status_events", "application_id", applicationId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select request_id from application_status_events where application_id = ?", String.class,
                applicationId)).isEqualTo(requestId);

        jdbcTemplate.update("update resumes set full_name = ?, version = version + 1 where id = ?",
                "Changed After Applying", fixture.resumeId());
        String replay = submit(fixture.candidate().accessToken(), fixture.jobId(), idempotencyKey, requestBody, 201);
        assertThat(objectMapper.readTree(replay).at("/data/resumeSnapshot/fullName").asText())
                .isEqualTo("Candidate One");
    }

    @Test
    void sameIdempotencyKeyAndPayloadReturnsOriginalApplication() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE);
        UUID key = UUID.randomUUID();
        String body = applicationBody(fixture.resumeId(), fixture.candidate().email(), true);

        String first = submit(fixture.candidate().accessToken(), fixture.jobId(), key.toString(), body, 201);
        String replay = submit(fixture.candidate().accessToken(), fixture.jobId(), key.toString(), body, 201);

        String firstId = objectMapper.readTree(first).at("/data/applicationId").asText();
        String replayId = objectMapper.readTree(replay).at("/data/applicationId").asText();
        assertThat(replayId).isEqualTo(firstId);
        assertThat(count("applications", "candidate_id", fixture.candidate().userId())).isEqualTo(1);
        assertThat(count("idempotency_records", "resource_id", firstId)).isEqualTo(1);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadReturnsConflict() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE);
        String key = UUID.randomUUID().toString();
        submit(fixture.candidate().accessToken(), fixture.jobId(), key,
                applicationBody(fixture.resumeId(), fixture.candidate().email(), true), 201);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Authorization", "Bearer " + fixture.candidate().accessToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody(fixture.resumeId(), "different@example.com", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void differentKeyForSameCandidateAndJobReturnsDuplicateConflict() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE);
        String body = applicationBody(fixture.resumeId(), fixture.candidate().email(), true);
        submit(fixture.candidate().accessToken(), fixture.jobId(), UUID.randomUUID().toString(), body, 201);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Authorization", "Bearer " + fixture.candidate().accessToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_ALREADY_EXISTS"));
    }

    @Test
    void concurrentSubmissionsCreateOnlyOneApplication() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE);
        String body = applicationBody(fixture.resumeId(), fixture.candidate().email(), true);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> submitAfter(start, fixture, body));
            var second = executor.submit(() -> submitAfter(start, fixture, body));
            start.countDown();

            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList();
            assertThat(statuses).containsExactly(201, 409);
        }
        assertThat(count("applications", "candidate_id", fixture.candidate().userId())).isEqualTo(1);
    }

    @Test
    void nonActiveJobCannotReceiveApplications() throws Exception {
        Fixture fixture = fixture(JobStatus.CLOSED);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Authorization", "Bearer " + fixture.candidate().accessToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody(fixture.resumeId(), fixture.candidate().email(), true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_ACCEPTING_APPLICATIONS"));
    }

    @Test
    void candidateCannotSubmitAnotherCandidatesResume() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE);
        Account other = registerCandidate("other");
        String otherResumeId = saveResume(other.userId());

        mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Authorization", "Bearer " + fixture.candidate().accessToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody(otherResumeId, fixture.candidate().email(), true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESUME_NOT_FOUND"));
    }

    @Test
    void recruiterCannotCallCandidateSubmissionEndpoint() throws Exception {
        Account recruiter = registerRecruiter();

        mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", "job_unknown")
                        .header("Authorization", "Bearer " + recruiter.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody("resume_unknown", recruiter.email(), true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void submissionRequiresAuthenticationAndValidIdempotencyKey() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE);
        String body = applicationBody(fixture.resumeId(), fixture.candidate().email(), true);

        mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        for (String key : new String[]{"", "not-a-uuid"}) {
            var request = post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                    .header("Authorization", "Bearer " + fixture.candidate().accessToken())
                    .contentType(MediaType.APPLICATION_JSON).content(body);
            if (!key.isEmpty()) request.header("Idempotency-Key", key);
            mockMvc.perform(request)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.fieldErrors['Idempotency-Key']").exists());
        }
    }

    private Fixture fixture(JobStatus status) throws Exception {
        Account candidate = registerCandidate("candidate");
        Account recruiter = registerRecruiter();
        String resumeId = saveResume(candidate.userId());
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jobRepository.saveAndFlush(new JobEntity(jobId, recruiter.companyId(), recruiter.userId(),
                "Backend Engineer", EmploymentType.FULL_TIME, WorkplaceType.HYBRID, "Singapore", 5000, 8000,
                "SGD", SalaryPeriod.MONTH, "Build reliable recruiting APIs.", List.of("Java", "Spring Boot"),
                List.of("Java", "MySQL"), now.plus(30, ChronoUnit.DAYS), JobVisibility.PUBLIC, status,
                status == JobStatus.ACTIVE ? now : null, 1, now, now));
        return new Fixture(candidate, resumeId, jobId);
    }

    private String saveResume(String candidateId) {
        String resumeId = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        resumeRepository.saveAndFlush(new ResumeEntity(resumeId, candidateId, "Candidate One", 24, "Singapore",
                "Backend Engineer", "Builds secure Java services.", List.of(new ResumeExperience(
                UUID.randomUUID().toString(), "Backend Intern", "Example Labs", "Built REST APIs", "2025-06",
                "2025-12")), 1, now, now));
        return resumeId;
    }

    private Account registerCandidate(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"role":"CANDIDATE","fullName":"Candidate One","email":"%s",
                 "password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(email);
        return register(body, email);
    }

    private Account registerRecruiter() throws Exception {
        String email = "recruiter-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"role":"RECRUITER","companyName":"Example Labs","fullName":"Recruiter One","email":"%s",
                 "password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(email);
        return register(body, email);
    }

    private Account register(String body, String email) throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        JsonNode user = response.at("/data/user");
        return new Account(user.at("/userId").asText(), response.at("/data/accessToken").asText(), email,
                user.at("/company/companyId").asText(null));
    }

    private String submit(String accessToken, String jobId, String key, String body, int expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private int submitAfter(CountDownLatch start, Fixture fixture, String body) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Authorization", "Bearer " + fixture.candidate().accessToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private String applicationBody(String resumeId, String contactEmail, boolean shareProfile) throws Exception {
        return objectMapper.createObjectNode()
                .put("resumeId", resumeId)
                .put("contactEmail", contactEmail)
                .put("shareProfile", shareProfile)
                .toString();
    }

    private int count(String table, String column, String value) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + column + " = ?",
                Integer.class, value);
    }

    private record Account(String userId, String accessToken, String email, String companyId) {}
    private record Fixture(Account candidate, String resumeId, String jobId) {}
}
