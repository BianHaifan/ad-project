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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class CandidateApplicationQueryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired ResumeRepository resumes;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void listIsOwnedFilteredCountedPagedAndStablySorted() throws Exception {
        Fixture owner = fixture(UserRole.CANDIDATE);
        String first = submit(owner, addJob(owner, "First"));
        String second = submit(owner, addJob(owner, "Second"));
        String third = submit(owner, addJob(owner, "Third"));
        Fixture other = fixture(UserRole.CANDIDATE);
        submit(other, addJob(other, "Other candidate"));

        Instant same = Instant.parse("2026-08-11T10:00:00Z");
        jdbc.update("update applications set applied_at=?, updated_at=? where id in (?,?,?)",
                Timestamp.from(same), Timestamp.from(same), first, second, third);
        setStatus(second, "INTERVIEW", 2, owner, "APPLIED", same.plusSeconds(1));
        setStatus(third, "WITHDRAWN", 2, owner, "APPLIED", same.plusSeconds(2));

        JsonNode page = json(mvc.perform(get("/api/v1/candidate/applications")
                        .header("Authorization", bearer(owner)).param("page", "1").param("pageSize", "2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.at("/data/0/applicationId").asText())
                .isEqualTo(List.of(first, second, third).stream().max(String::compareTo).orElseThrow());
        assertThat(page.at("/meta/page").asInt()).isEqualTo(1);
        assertThat(page.at("/meta/pageSize").asInt()).isEqualTo(2);
        assertThat(page.at("/meta/total").asInt()).isEqualTo(3);
        assertThat(page.at("/meta/hasNext").asBoolean()).isTrue();
        assertThat(page.at("/meta/counts/active").asInt()).isEqualTo(1);
        assertThat(page.at("/meta/counts/interview").asInt()).isEqualTo(1);
        assertThat(page.at("/meta/counts/archived").asInt()).isEqualTo(1);
        assertThat(page.toString()).contains("\"matchScore\":null").contains("\"scheduledAt\":null")
                .doesNotContain("resumeSnapshot").doesNotContain("recruiterNote");

        mvc.perform(get("/api/v1/candidate/applications").header("Authorization", bearer(owner))
                        .param("filter", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
        mvc.perform(get("/api/v1/candidate/applications").header("Authorization", bearer(owner))
                        .param("filter", "INTERVIEW"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("INTERVIEW"));
        mvc.perform(get("/api/v1/candidate/applications").header("Authorization", bearer(owner))
                        .param("filter", "ARCHIVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("WITHDRAWN"));
    }

    @Test
    void emptyAuthenticationAndRoleResponsesAreSafe() throws Exception {
        Fixture candidate = fixture(UserRole.CANDIDATE);
        mvc.perform(get("/api/v1/candidate/applications").header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0)).andExpect(jsonPath("$.meta.hasNext").value(false));
        mvc.perform(get("/api/v1/candidate/applications"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        Fixture recruiter = fixture(UserRole.RECRUITER);
        mvc.perform(get("/api/v1/candidate/applications").header("Authorization", bearer(recruiter)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/candidate/applications/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(recruiter)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/candidate/applications/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/candidate/applications").header("Authorization", bearer(candidate))
                        .param("page", "0"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void detailReturnsRealTimelineAndImmutableSnapshotButHidesOtherCandidates() throws Exception {
        Fixture owner = fixture(UserRole.CANDIDATE);
        String applicationId = submit(owner, addJob(owner, "Snapshot role"));
        jdbc.update("update resumes set full_name='Changed Later', experiences_json='[]', version=version+1 where id=?",
                owner.resumeId());

        String response = mvc.perform(get("/api/v1/candidate/applications/{id}", applicationId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.resumeSnapshot.fullName").value("Candidate"))
                .andExpect(jsonPath("$.data.resumeSnapshot.experiences[0].title").value("First"))
                .andExpect(jsonPath("$.data.resumeSnapshot.experiences[1].title").value("Second"))
                .andExpect(jsonPath("$.data.timeline[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.timeline[0].completed").value(true))
                .andExpect(jsonPath("$.data.interview").isEmpty())
                .andExpect(jsonPath("$.data.matchScore").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("Z").doesNotContain("recruiterNote").doesNotContain("owner");

        Fixture other = fixture(UserRole.CANDIDATE);
        mvc.perform(get("/api/v1/candidate/applications/{id}", applicationId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/candidate/applications/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(owner))).andExpect(status().isNotFound());
    }

    @Test
    void withdrawalSupportsFrozenSourceStatesWritesEventAndPreservesApplicantCount() throws Exception {
        for (String status : List.of("APPLIED", "IN_REVIEW", "INTERVIEW")) {
            Fixture owner = fixture(UserRole.CANDIDATE);
            String jobId = addJob(owner, "Withdraw " + status);
            String applicationId = submit(owner, jobId);
            int version = 1;
            if (!status.equals("APPLIED")) {
                version = 2;
                setStatus(applicationId, status, version, owner, "APPLIED", Instant.now().minusSeconds(1));
            }
            mvc.perform(post("/api/v1/candidate/applications/{id}/withdraw", applicationId)
                            .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"No longer available\",\"expectedVersion\":" + version + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                    .andExpect(jsonPath("$.data.version").value(version + 1))
                    .andExpect(jsonPath("$.data.timeline[-1:].status").value("WITHDRAWN"));
            assertThat(jdbc.queryForObject("select applicant_count from jobs where id=?", Integer.class, jobId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from application_status_events where application_id=? " +
                    "and to_status='WITHDRAWN' and reason='No longer available' and request_id is not null",
                    Integer.class, applicationId)).isEqualTo(1);
        }
    }

    @Test
    void withdrawalEnforcesOwnershipVersionTerminalStateAndValidation() throws Exception {
        Fixture owner = fixture(UserRole.CANDIDATE);
        String applicationId = submit(owner, addJob(owner, "Conflicts"));
        Fixture other = fixture(UserRole.CANDIDATE);
        withdraw(other, applicationId, 1, 404, "NOT_FOUND");
        withdraw(owner, applicationId, 99, 409, "VERSION_CONFLICT");
        withdraw(owner, applicationId, 1, 200, null);
        withdraw(owner, applicationId, 1, 409, "VERSION_CONFLICT");
        withdraw(owner, applicationId, 2, 409, "INVALID_APPLICATION_TRANSITION");
        mvc.perform(post("/api/v1/candidate/applications/{id}/withdraw", applicationId)
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\",\"expectedVersion\":2}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        Fixture recruiter = fixture(UserRole.RECRUITER);
        withdraw(recruiter, applicationId, 2, 403, "FORBIDDEN");
    }

    @Test
    void offeredIsArchivedAndCannotBeWithdrawn() throws Exception {
        Fixture owner = fixture(UserRole.CANDIDATE);
        String applicationId = submit(owner, addJob(owner, "Offered role"));
        setStatus(applicationId, "OFFERED", 2, owner, "INTERVIEW", Instant.now());

        mvc.perform(get("/api/v1/candidate/applications").header("Authorization", bearer(owner))
                        .param("filter", "ARCHIVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("OFFERED"));

        withdraw(owner, applicationId, 2, 409, "INVALID_APPLICATION_TRANSITION");
    }

    private void withdraw(Fixture fixture, String applicationId, int version, int expected, String code) throws Exception {
        var result = mvc.perform(post("/api/v1/candidate/applications/{id}/withdraw", applicationId)
                .header("Authorization", bearer(fixture)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Changed plans\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().is(expected));
        if (code != null) result.andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    private String submit(Fixture fixture, String jobId) throws Exception {
        String body = "{\"resumeId\":\"" + fixture.resumeId() + "\",\"contactEmail\":\"" + fixture.email()
                + "\",\"shareProfile\":true}";
        String response = mvc.perform(post("/api/v1/jobs/{jobId}/applications", jobId)
                        .header("Authorization", bearer(fixture)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json(response).at("/data/applicationId").asText();
    }

    private void setStatus(String applicationId, String status, int version, Fixture fixture,
                           String fromStatus, Instant occurredAt) {
        jdbc.update("update applications set status=?, version=?, updated_at=? where id=?", status, version,
                Timestamp.from(occurredAt), applicationId);
        jdbc.update("insert into application_status_events " +
                        "(id,application_id,actor_id,company_id,from_status,to_status,occurred_at,reason,request_id) " +
                        "values (?,?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(), applicationId, fixture.userId(),
                fixture.companyId(), fromStatus, status, Timestamp.from(occurredAt), "Test transition", "test-request");
    }

    private Fixture fixture(UserRole role) {
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        String id = UUID.randomUUID().toString();
        String email = id + "@example.com";
        UserEntity user = users.save(new UserEntity(id, email, "hash", role == UserRole.CANDIDATE ? "Candidate" : "Recruiter",
                role, UserStatus.ACTIVE, "2026-08", now, now));
        UserEntity recruiter = role == UserRole.RECRUITER ? user : users.save(new UserEntity(UUID.randomUUID().toString(),
                UUID.randomUUID() + "@example.com", "hash", "Recruiter", UserRole.RECRUITER,
                UserStatus.ACTIVE, "2026-08", now, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Real Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        String resumeId = UUID.randomUUID().toString();
        if (role == UserRole.CANDIDATE) {
            resumes.save(new ResumeEntity(resumeId, id, "Candidate", 27, "Singapore", "Engineer", "Summary",
                    "[{\"experienceId\":\"1\",\"title\":\"First\",\"company\":\"A\",\"description\":\"One\",\"startDate\":\"2024-01\",\"endDate\":null},{\"experienceId\":\"2\",\"title\":\"Second\",\"company\":\"B\",\"description\":\"Two\",\"startDate\":\"2025-01\",\"endDate\":null}]",
                    1, now, now));
        }
        return new Fixture(jwt.createAccessToken(user), id, email, resumeId, company.getId(), recruiter.getId());
    }

    private String addJob(Fixture fixture, String title) {
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        String id = UUID.randomUUID().toString();
        jobs.save(new JobEntity(id, fixture.companyId(), fixture.recruiterId(), fixture.recruiterId(), title,
                EmploymentType.FULL_TIME, WorkplaceType.HYBRID, "Singapore", 5000, 8000,
                SalaryCurrency.SGD, SalaryPeriod.MONTH, "Description", "[\"Requirement\"]", "[\"Java\"]",
                null, Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1, now, now));
        return id;
    }

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }
    private static String bearer(Fixture fixture) { return "Bearer " + fixture.token(); }
    private record Fixture(String token, String userId, String email, String resumeId,
                           String companyId, String recruiterId) {}
}
