package com.adproject.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.job.domain.*;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.*;
import com.adproject.user.infrastructure.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
class CandidateApplicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired ResumeRepository resumes;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;

    @Test
    void successfulSubmissionCreatesImmutableSnapshotEventCounterAndRealJobState() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE, Visibility.PUBLIC, true);
        String key = UUID.randomUUID().toString();
        String response = submit(fixture, key, body(fixture.resumeId(), fixture.email(), true), 201);

        assertThat(response).contains("\"status\":\"APPLIED\"")
                .contains("\"version\":1")
                .contains("\"matchScore\":null")
                .contains("\"interview\":null")
                .contains("\"resumeId\":\"" + fixture.resumeId() + "\"")
                .doesNotContain("recruiterNote");
        assertThat(response).contains("2026-").contains("Z");
        assertThat(jdbc.queryForObject("select count(*) from applications where job_id=? and candidate_id=?",
                Integer.class, fixture.jobId(), fixture.candidateId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from resume_snapshots where resume_id=?",
                Integer.class, fixture.resumeId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from application_status_events where to_status='APPLIED' " +
                "and from_status is null and request_id is not null", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select applicant_count from jobs where id=?", Integer.class, fixture.jobId()))
                .isEqualTo(1);

        mvc.perform(get("/api/v1/jobs/{jobId}", fixture.jobId()).header("Authorization", bearer(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.applicationState").value("APPLIED"));

        String snapshotName = jdbc.queryForObject("select full_name from resume_snapshots where resume_id=?",
                String.class, fixture.resumeId());
        jdbc.update("update resumes set full_name='Changed Later', experiences_json='[]', version=version+1 where id=?",
                fixture.resumeId());
        assertThat(jdbc.queryForObject("select full_name from resume_snapshots where resume_id=?",
                String.class, fixture.resumeId())).isEqualTo(snapshotName);
        assertThat(jdbc.queryForObject("select experiences_json from resume_snapshots where resume_id=?",
                String.class, fixture.resumeId())).contains("First").contains("Second");
    }

    @Test
    void idempotencyReplaysOriginalAndRejectsChangedPayloadOrDifferentKeyDuplicate() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE, Visibility.PUBLIC, true);
        String key = UUID.randomUUID().toString();
        String first = submit(fixture, key, body(fixture.resumeId(), fixture.email(), true), 201);
        String replay = submit(fixture, key, body(fixture.resumeId(), fixture.email(), true), 201);
        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from applications where job_id=?", Integer.class, fixture.jobId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from resume_snapshots where resume_id=?", Integer.class,
                fixture.resumeId())).isEqualTo(1);

        submit(fixture, key, body(fixture.resumeId(), fixture.email(), false), 409, "IDEMPOTENCY_KEY_REUSED");
        submit(fixture, UUID.randomUUID().toString(), body(fixture.resumeId(), fixture.email(), true),
                409, "APPLICATION_ALREADY_EXISTS");
    }

    @Test
    void hiddenOrMissingJobsReturnSameSafe404WithoutPartialWrites() throws Exception {
        for (JobStatus status : List.of(JobStatus.DRAFT, JobStatus.PAUSED, JobStatus.CLOSED)) {
            Fixture fixture = fixture(status, Visibility.PUBLIC, true);
            submit(fixture, UUID.randomUUID().toString(), body(fixture.resumeId(), fixture.email(), true), 404, "NOT_FOUND");
        }
        Fixture privateJob = fixture(JobStatus.ACTIVE, Visibility.PRIVATE, true);
        submit(privateJob, UUID.randomUUID().toString(), body(privateJob.resumeId(), privateJob.email(), true),
                404, "NOT_FOUND");
        Fixture missing = fixture(JobStatus.ACTIVE, Visibility.PUBLIC, true);
        submit(new Fixture(missing.token(), missing.candidateId(), UUID.randomUUID().toString(), missing.resumeId(),
                        missing.email()), UUID.randomUUID().toString(), body(missing.resumeId(), missing.email(), true),
                404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from applications where candidate_id=?", Integer.class,
                missing.candidateId())).isZero();
    }

    @Test
    void authenticationRoleResumeOwnershipHeadersAndEmailAreValidated() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE, Visibility.PUBLIC, true);
        mvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.resumeId(), fixture.email(), true)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        Fixture recruiter = fixtureForRole(UserRole.RECRUITER);
        submit(recruiter, UUID.randomUUID().toString(), body(recruiter.resumeId(), recruiter.email(), true),
                403, "FORBIDDEN");

        submit(fixture, null, body(fixture.resumeId(), fixture.email(), true), 422, "VALIDATION_ERROR");
        submit(fixture, "not-a-uuid", body(fixture.resumeId(), fixture.email(), true), 422, "VALIDATION_ERROR");
        submit(fixture, UUID.randomUUID().toString(), body(fixture.resumeId(), "not-an-email", true),
                422, "VALIDATION_ERROR");
        submit(fixture, UUID.randomUUID().toString(), body(UUID.randomUUID().toString(), fixture.email(), true),
                404, "NOT_FOUND");

        Fixture other = fixture(JobStatus.ACTIVE, Visibility.PUBLIC, true);
        submit(fixture, UUID.randomUUID().toString(), body(other.resumeId(), fixture.email(), true),
                404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from resume_snapshots where candidate_id=?", Integer.class,
                fixture.candidateId())).isZero();
    }

    @Test
    void concurrentDifferentKeysCreateOnlyOneApplicationAndSnapshot() throws Exception {
        Fixture fixture = fixture(JobStatus.ACTIVE, Visibility.PUBLIC, true);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return statusOnly(fixture, UUID.randomUUID().toString()); });
            var second = pool.submit(() -> { start.await(); return statusOnly(fixture, UUID.randomUUID().toString()); });
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("select count(*) from applications where job_id=? and candidate_id=?",
                Integer.class, fixture.jobId(), fixture.candidateId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from resume_snapshots where candidate_id=?",
                Integer.class, fixture.candidateId())).isEqualTo(1);
    }

    private int statusOnly(Fixture fixture, String key) throws Exception {
        return mvc.perform(post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                        .header("Authorization", bearer(fixture)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body(fixture.resumeId(), fixture.email(), true)))
                .andReturn().getResponse().getStatus();
    }

    private String submit(Fixture fixture, String key, String body, int expected) throws Exception {
        var builder = post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                .header("Authorization", bearer(fixture)).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) builder.header("Idempotency-Key", key);
        return mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }
    private void submit(Fixture fixture, String key, String body, int expected, String code) throws Exception {
        var builder = post("/api/v1/jobs/{jobId}/applications", fixture.jobId())
                .header("Authorization", bearer(fixture)).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) builder.header("Idempotency-Key", key);
        mvc.perform(builder).andExpect(status().is(expected)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    private Fixture fixture(JobStatus status, Visibility visibility, boolean withResume) {
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        String candidateId = UUID.randomUUID().toString();
        String email = candidateId + "@example.com";
        UserEntity candidate = users.save(new UserEntity(candidateId, email, "hash", "Candidate", UserRole.CANDIDATE,
                UserStatus.ACTIVE, "2026-08", now, now));
        UserEntity recruiter = users.save(new UserEntity(UUID.randomUUID().toString(), UUID.randomUUID() + "@example.com",
                "hash", "Recruiter", UserRole.RECRUITER, UserStatus.ACTIVE, "2026-08", now, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Real Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        String jobId = UUID.randomUUID().toString();
        jobs.save(new JobEntity(jobId, company.getId(), recruiter.getId(), recruiter.getId(), "Backend Engineer",
                EmploymentType.FULL_TIME, WorkplaceType.HYBRID, "Singapore", 5000, 8000,
                SalaryCurrency.SGD, SalaryPeriod.MONTH, "Description", "[\"Requirement\"]", "[\"Java\"]",
                null, visibility, status, 0, 1, now, now));
        String resumeId = UUID.randomUUID().toString();
        if (withResume) resumes.save(new ResumeEntity(resumeId, candidateId, "Candidate", 27, "Singapore",
                "Engineer", "Summary", "[{\"experienceId\":\"1\",\"title\":\"First\",\"company\":\"A\",\"description\":\"One\",\"startDate\":\"2024-01\",\"endDate\":null},{\"experienceId\":\"2\",\"title\":\"Second\",\"company\":\"B\",\"description\":\"Two\",\"startDate\":\"2025-01\",\"endDate\":null}]",
                "[]",
                1, now, now));
        return new Fixture(jwt.createAccessToken(candidate), candidateId, jobId, resumeId, email);
    }

    private Fixture fixtureForRole(UserRole role) {
        Fixture candidateFixture = fixture(JobStatus.ACTIVE, Visibility.PUBLIC, true);
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        UserEntity user = users.save(new UserEntity(UUID.randomUUID().toString(), UUID.randomUUID() + "@example.com",
                "hash", "Wrong role", role, UserStatus.ACTIVE, "2026-08", now, now));
        return new Fixture(jwt.createAccessToken(user), user.getId(), candidateFixture.jobId(),
                candidateFixture.resumeId(), user.getEmail());
    }

    private static String bearer(Fixture fixture) { return "Bearer " + fixture.token(); }
    private static String body(String resumeId, String email, boolean share) {
        return "{\"resumeId\":\"" + resumeId + "\",\"contactEmail\":\"" + email +
                "\",\"shareProfile\":" + share + "}";
    }
    private record Fixture(String token, String candidateId, String jobId, String resumeId, String email) {}
}
