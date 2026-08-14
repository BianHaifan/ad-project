package com.adproject.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
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
class RecruiterJobIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void approvedRecruiterCreatesCompleteDraftBoundToServerScope() throws Exception {
        Account recruiter = recruiter("approved-create", "APPROVED");

        JsonNode response = create(recruiter, createBody("Platform Engineer"), 201);

        assertThat(response.at("/data/status").asText()).isEqualTo("DRAFT");
        assertThat(response.at("/data/applicantCount").asInt()).isZero();
        assertThat(response.at("/data/version").asInt()).isEqualTo(1);
        assertThat(response.at("/data/company/companyId").asText()).isEqualTo(recruiter.companyId());
        assertThat(response.at("/data/owner/userId").asText()).isEqualTo(recruiter.userId());
        assertThat(response.at("/data/requirements/0").asText()).isEqualTo("Build reliable APIs");
        assertThat(response.at("/data/skills/1").asText()).isEqualTo("MySQL");
        assertThat(response.at("/data/salary/currency").asText()).isEqualTo("SGD");
        assertThat(response.at("/data/deadline").asText()).isEqualTo("2026-09-30T15:59:59Z");
        assertThat(response.at("/data/createdAt").asText()).endsWith("Z");
        assertThat(response.at("/data/updatedAt").asText()).endsWith("Z");
        assertThat(response.at("/data/publishedAt").isNull()).isTrue();

        String jobId = response.at("/data/jobId").asText();
        var stored = jdbcTemplate.queryForMap("select * from jobs where id = ?", jobId);
        assertThat(stored.get("company_id")).isEqualTo(recruiter.companyId());
        assertThat(stored.get("created_by")).isEqualTo(recruiter.userId());
        assertThat(stored.get("status")).isEqualTo("DRAFT");
        assertThat(stored.get("applicant_count")).isEqualTo(0);
    }

    @Test
    void pendingAndRejectedCompaniesCannotCreate() throws Exception {
        for (String verification : new String[]{"PENDING", "REJECTED"}) {
            Account recruiter = recruiter("blocked-" + verification.toLowerCase(), verification);
            mockMvc.perform(post("/api/v1/recruiter/jobs")
                            .header("Authorization", bearer(recruiter))
                            .contentType(MediaType.APPLICATION_JSON).content(createBody("Blocked role")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.error.fieldErrors").isMap())
                    .andExpect(jsonPath("$.error.requestId").isNotEmpty());
        }
    }

    @Test
    void allEndpointsRequireAuthenticationAndRecruiterRole() throws Exception {
        Account candidate = candidate("wrong-role");
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/api/v1/recruiter/jobs").contentType(MediaType.APPLICATION_JSON).content(createBody("Role")),
                get("/api/v1/recruiter/jobs"),
                get("/api/v1/recruiter/jobs/unknown")}) {
            mockMvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/api/v1/recruiter/jobs").header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Role")),
                get("/api/v1/recruiter/jobs").header("Authorization", bearer(candidate)),
                get("/api/v1/recruiter/jobs/unknown").header("Authorization", bearer(candidate))}) {
            mockMvc.perform(request).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    void createValidatesMissingFieldsEnumsUnknownFieldsAndSalaryRange() throws Exception {
        Account recruiter = recruiter("validation", "APPROVED");
        mockMvc.perform(post("/api/v1/recruiter/jobs").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.title").exists());

        mockMvc.perform(post("/api/v1/recruiter/jobs").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Invalid enum").replace("FULL_TIME", "CONTRACT")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.employmentType").exists());

        mockMvc.perform(post("/api/v1/recruiter/jobs").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Unknown").replace("}", ",\"companyId\":\"other\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.companyId").exists());

        String requestId = "req_job_salary_validation";
        mockMvc.perform(post("/api/v1/recruiter/jobs").header("Authorization", bearer(recruiter))
                        .header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Salary").replace("\"min\":5000,\"max\":8000", "\"min\":9000,\"max\":8000")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.error.requestId").value(requestId))
                .andExpect(jsonPath("$.error.fieldErrors['salary.max']").exists());
    }

    @Test
    void listIsCompanyScopedPagedStableAndSupportsAllContractFilters() throws Exception {
        Account first = recruiter("list-owner", "APPROVED");
        Account second = recruiter("other-company", "APPROVED");
        JsonNode alpha = create(first, createBody("Alpha Backend"), 201);
        JsonNode beta = create(first, createBody("Beta Data").replace("FULL_TIME", "INTERNSHIP")
                .replace("Singapore", "Jurong"), 201);
        create(first, createBody("Gamma Backend"), 201);
        create(second, createBody("Other company secret"), 201);

        mockMvc.perform(get("/api/v1/recruiter/jobs").header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.hasNext").value(false));

        JsonNode pageOne = read(mockMvc.perform(get("/api/v1/recruiter/jobs?page=1&pageSize=2")
                .header("Authorization", bearer(first))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode pageTwo = read(mockMvc.perform(get("/api/v1/recruiter/jobs?page=2&pageSize=2")
                .header("Authorization", bearer(first))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Set<String> ids = new HashSet<>();
        pageOne.path("data").forEach(item -> ids.add(item.path("jobId").asText()));
        pageTwo.path("data").forEach(item -> ids.add(item.path("jobId").asText()));
        assertThat(ids).hasSize(3);
        assertThat(pageOne.at("/meta/hasNext").asBoolean()).isTrue();

        assertFiltered(first, "q=Alpha", alpha.at("/data/jobId").asText());
        assertFiltered(first, "status=DRAFT", null, 3);
        assertFiltered(first, "employmentType=INTERNSHIP", beta.at("/data/jobId").asText());
        assertFiltered(first, "location=jur", beta.at("/data/jobId").asText());
        assertFiltered(first, "ownerId=" + first.userId(), null, 3);
    }

    @Test
    void emptyListIsAValidPage() throws Exception {
        Account recruiter = recruiter("empty", "PENDING");
        mockMvc.perform(get("/api/v1/recruiter/jobs").header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }

    @Test
    void detailReturnsOwnCompanyAndHidesMissingOrCrossCompanyJobs() throws Exception {
        Account owner = recruiter("detail-owner", "APPROVED");
        Account outsider = recruiter("detail-outsider", "APPROVED");
        String jobId = create(owner, createBody("Detail role"), 201).at("/data/jobId").asText();

        mockMvc.perform(get("/api/v1/recruiter/jobs/{jobId}", jobId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.title").value("Detail role"));
        for (String hidden : new String[]{jobId, UUID.randomUUID().toString()}) {
            mockMvc.perform(get("/api/v1/recruiter/jobs/{jobId}", hidden)
                            .header("Authorization", bearer(outsider)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    @Test
    void invalidListParametersReturnContractValidationError() throws Exception {
        Account recruiter = recruiter("bad-filter", "APPROVED");
        for (String query : new String[]{"page=0", "pageSize=101", "status=PUBLISHED", "employmentType=CONTRACT"}) {
            mockMvc.perform(get("/api/v1/recruiter/jobs?" + query).header("Authorization", bearer(recruiter)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void approvedRecruiterPublishesDraftAndPersistsAuditAndVersion() throws Exception {
        Account recruiter = recruiter("publish-success", "APPROVED");
        String jobId = create(recruiter, createBody("Publishable role"), 201).at("/data/jobId").asText();
        String requestId = "req_publish_success";

        String responseBody = mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                        .header("Authorization", bearer(recruiter)).header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.publishedAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.data.updatedAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = read(responseBody);
        var stored = jdbcTemplate.queryForMap(
                "select status,published_at,updated_at,version from jobs where id = ?", jobId);
        assertThat(stored.get("status")).isEqualTo("ACTIVE");
        assertThat(stored.get("published_at")).isNotNull();
        assertThat(stored.get("updated_at")).isNotNull();
        assertThat(stored.get("version")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from job_audit_events where job_id = ? and actor_id = ? and company_id = ? " +
                        "and action = 'JOB_PUBLISHED' and from_status = 'DRAFT' and to_status = 'ACTIVE' " +
                        "and reason = 'Job published' and request_id = ?",
                Integer.class, jobId, recruiter.userId(), recruiter.companyId(), requestId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/recruiter/jobs/{jobId}", jobId).header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.publishedAt").value(response.at("/data/publishedAt").asText()));
    }

    @Test
    void publishRequiresAuthenticationAndRecruiterRole() throws Exception {
        Account owner = recruiter("publish-owner", "APPROVED");
        Account candidate = candidate("publish-candidate");
        String jobId = create(owner, createBody("Protected publish"), 201).at("/data/jobId").asText();

        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void pendingAndRejectedCompaniesCannotPublishExistingDrafts() throws Exception {
        for (String verification : new String[]{"PENDING", "REJECTED"}) {
            Account recruiter = recruiter("publish-" + verification.toLowerCase(), "APPROVED");
            String jobId = create(recruiter, createBody("Blocked publish"), 201).at("/data/jobId").asText();
            jdbcTemplate.update("update companies set verification_status = ? where id = ?",
                    verification, recruiter.companyId());
            mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                            .header("Authorization", bearer(recruiter))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.error.requestId").isNotEmpty());
            assertThat(jdbcTemplate.queryForObject("select status from jobs where id = ?", String.class, jobId))
                    .isEqualTo("DRAFT");
        }
    }

    @Test
    void publishHidesCrossCompanyAndMissingJobs() throws Exception {
        Account owner = recruiter("publish-hidden-owner", "APPROVED");
        Account outsider = recruiter("publish-hidden-outsider", "APPROVED");
        String jobId = create(owner, createBody("Hidden publish"), 201).at("/data/jobId").asText();
        for (String hidden : new String[]{jobId, UUID.randomUUID().toString()}) {
            mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", hidden)
                            .header("Authorization", bearer(outsider))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    @Test
    void publishRejectsVersionConflictInvalidStateAndInvalidBodies() throws Exception {
        Account recruiter = recruiter("publish-conflicts", "APPROVED");
        String jobId = create(recruiter, createBody("Conflict publish"), 201).at("/data/jobId").asText();
        String requestId = "req_publish_conflict";
        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                        .header("Authorization", bearer(recruiter)).header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.requestId").value(requestId));

        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                        .header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                        .header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_JOB_TRANSITION"));

        for (String body : new String[]{"{}", "{\"expectedVersion\":0}"}) {
            mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                            .header("Authorization", bearer(recruiter))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.fieldErrors.expectedVersion").exists());
        }
        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                        .header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.status").exists());
    }

    @Test
    void statusTransitionsPauseResumeAndClosePersistVersionTimesAndAudit() throws Exception {
        Account recruiter = recruiter("status-lifecycle", "APPROVED");
        String jobId = create(recruiter, createBody("Lifecycle role"), 201).at("/data/jobId").asText();
        JsonNode published = publish(recruiter, jobId, 1);
        String publishedAt = published.at("/data/publishedAt").asText();

        JsonNode paused = changeStatus(recruiter, jobId, "PAUSED", "Pause for planning", 2,
                "req_status_pause", 200);
        assertThat(paused.at("/data/status").asText()).isEqualTo("PAUSED");
        assertThat(paused.at("/data/version").asInt()).isEqualTo(3);
        assertThat(paused.at("/data/updatedAt").asText()).endsWith("Z");
        assertThat(paused.at("/data/publishedAt").asText()).isEqualTo(publishedAt);

        JsonNode resumed = changeStatus(recruiter, jobId, "ACTIVE", "Recruitment resumed", 3,
                "req_status_resume", 200);
        assertThat(resumed.at("/data/status").asText()).isEqualTo("ACTIVE");
        assertThat(resumed.at("/data/version").asInt()).isEqualTo(4);
        assertThat(resumed.at("/data/publishedAt").asText()).isEqualTo(publishedAt);

        JsonNode closed = changeStatus(recruiter, jobId, "CLOSED", "Position filled", 4,
                "req_status_close", 200);
        assertThat(closed.at("/data/status").asText()).isEqualTo("CLOSED");
        assertThat(closed.at("/data/version").asInt()).isEqualTo(5);
        assertThat(closed.at("/data/publishedAt").asText()).isEqualTo(publishedAt);

        var stored = jdbcTemplate.queryForMap(
                "select status,published_at,updated_at,version from jobs where id = ?", jobId);
        assertThat(stored.get("status")).isEqualTo("CLOSED");
        assertThat(stored.get("published_at")).isNotNull();
        assertThat(stored.get("updated_at")).isNotNull();
        assertThat(stored.get("version")).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from job_audit_events where job_id = ? and actor_id = ? and company_id = ? " +
                        "and action = 'JOB_STATUS_CHANGED'",
                Integer.class, jobId, recruiter.userId(), recruiter.companyId())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from job_audit_events where job_id = ? and from_status = 'ACTIVE' " +
                        "and to_status = 'PAUSED' and reason = 'Pause for planning' and request_id = 'req_status_pause'",
                Integer.class, jobId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/recruiter/jobs/{jobId}", jobId).header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.version").value(5))
                .andExpect(jsonPath("$.data.publishedAt").value(publishedAt));
    }

    @Test
    void pausedJobCanCloseAndClosedIsTerminal() throws Exception {
        Account recruiter = recruiter("status-terminal", "APPROVED");
        String jobId = create(recruiter, createBody("Terminal role"), 201).at("/data/jobId").asText();
        publish(recruiter, jobId, 1);
        changeStatus(recruiter, jobId, "PAUSED", "Pause first", 2, null, 200);
        changeStatus(recruiter, jobId, "CLOSED", "Close while paused", 3, null, 200);
        for (String target : new String[]{"ACTIVE", "PAUSED", "CLOSED"}) {
            changeStatus(recruiter, jobId, target, "Terminal attempt", 4, null, 409);
        }
    }

    @Test
    void draftAndRepeatedTransitionsReturnInvalidJobTransition() throws Exception {
        Account recruiter = recruiter("status-invalid", "APPROVED");
        for (String target : new String[]{"ACTIVE", "PAUSED", "CLOSED"}) {
            String draftId = create(recruiter, createBody("Draft " + target), 201).at("/data/jobId").asText();
            changeStatus(recruiter, draftId, target, "Cannot bypass publish", 1, null, 409);
        }
        String activeId = create(recruiter, createBody("Repeated state"), 201).at("/data/jobId").asText();
        publish(recruiter, activeId, 1);
        changeStatus(recruiter, activeId, "ACTIVE", "Already active", 2, null, 409);
        changeStatus(recruiter, activeId, "PAUSED", "Pause once", 2, null, 200);
        changeStatus(recruiter, activeId, "PAUSED", "Pause twice", 3, null, 409);
    }

    @Test
    void statusRequiresAuthenticationRoleAndOwnCompanyResource() throws Exception {
        Account owner = recruiter("status-owner", "APPROVED");
        Account outsider = recruiter("status-outsider", "APPROVED");
        Account candidate = candidate("status-candidate");
        String jobId = create(owner, createBody("Protected status"), 201).at("/data/jobId").asText();
        publish(owner, jobId, 1);
        String body = statusBody("PAUSED", "Protected operation", 2);

        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/status", jobId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/status", jobId)
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        for (String hidden : new String[]{jobId, UUID.randomUUID().toString()}) {
            mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/status", hidden)
                            .header("Authorization", bearer(outsider))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    @Test
    void statusRejectsVersionConflictAndInvalidBodiesWithStructuredErrors() throws Exception {
        Account recruiter = recruiter("status-validation", "APPROVED");
        String jobId = create(recruiter, createBody("Status validation"), 201).at("/data/jobId").asText();
        publish(recruiter, jobId, 1);
        String requestId = "req_status_version";
        changeStatus(recruiter, jobId, "PAUSED", "Stale update", 1, requestId, 409);

        for (String body : new String[]{
                "{}",
                "{\"status\":\"PAUSED\",\"reason\":\"Valid\",\"expectedVersion\":0}",
                "{\"status\":\"PAUSED\",\"expectedVersion\":2}",
                "{\"status\":\"PAUSED\",\"reason\":\"\",\"expectedVersion\":2}",
                "{\"status\":\"PAUSED\",\"reason\":\"   \",\"expectedVersion\":2}"}) {
            mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/status", jobId)
                            .header("Authorization", bearer(recruiter))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.requestId").isNotEmpty());
        }
        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/status", jobId)
                        .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRAFT\",\"reason\":\"Invalid enum\",\"expectedVersion\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.status").exists());
        mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/status", jobId)
                        .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\",\"reason\":\"Unknown field\",\"expectedVersion\":2," +
                                "\"companyId\":\"other\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.companyId").exists());
    }

    @Test
    void unapprovedCompaniesCannotResumeButCanPauseAndClose() throws Exception {
        for (String verification : new String[]{"PENDING", "REJECTED"}) {
            Account recruiter = recruiter("status-" + verification.toLowerCase(), "APPROVED");
            String jobId = create(recruiter, createBody(verification + " lifecycle"), 201)
                    .at("/data/jobId").asText();
            publish(recruiter, jobId, 1);
            jdbcTemplate.update("update companies set verification_status = ? where id = ?",
                    verification, recruiter.companyId());
            changeStatus(recruiter, jobId, "PAUSED", "Pause remains allowed", 2, null, 200);
            changeStatus(recruiter, jobId, "ACTIVE", "Resume requires approval", 3, null, 403);
            changeStatus(recruiter, jobId, "CLOSED", "Close remains allowed", 3, null, 200);
        }
    }

    @Test
    void draftUpdateSupportsPartialFieldsPreservesOmittedDeadlineAndClearsExplicitNull() throws Exception {
        Account recruiter = recruiter("update-success", "APPROVED");
        String jobId = create(recruiter, createBody("Original draft"), 201).at("/data/jobId").asText();

        String firstBody = """
                {"title":" Updated draft ","employmentType":"PART_TIME","workplaceType":"REMOTE",
                 "location":" Remote ","salary":{"min":6000,"max":9000,"currency":"SGD","period":"YEAR"},
                 "description":"Updated description","requirements":["Updated requirement"],
                 "skills":["Spring Boot"],"visibility":"PRIVATE","expectedVersion":1}
                """;
        String firstResponse = mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                        .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated draft"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.deadline").value("2026-09-30T15:59:59Z"))
                .andExpect(jsonPath("$.data.updatedAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andReturn().getResponse().getContentAsString();
        assertThat(read(firstResponse).at("/data/requirements/0").asText()).isEqualTo("Updated requirement");

        mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                        .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deadline\":null,\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadline").isEmpty())
                .andExpect(jsonPath("$.data.version").value(3));

        var stored = jdbcTemplate.queryForMap(
                "select title,employment_type,workplace_type,location,salary_min,salary_max,salary_period," +
                        "visibility,status,deadline,version from jobs where id = ?", jobId);
        assertThat(stored.get("title")).isEqualTo("Updated draft");
        assertThat(stored.get("employment_type")).isEqualTo("PART_TIME");
        assertThat(stored.get("workplace_type")).isEqualTo("REMOTE");
        assertThat(stored.get("location")).isEqualTo("Remote");
        assertThat(stored.get("salary_min")).isEqualTo(6000L);
        assertThat(stored.get("salary_max")).isEqualTo(9000L);
        assertThat(stored.get("salary_period")).isEqualTo("YEAR");
        assertThat(stored.get("visibility")).isEqualTo("PRIVATE");
        assertThat(stored.get("status")).isEqualTo("DRAFT");
        assertThat(stored.get("deadline")).isNull();
        assertThat(stored.get("version")).isEqualTo(3);
    }

    @Test
    void onlyDraftJobsCanBeEdited() throws Exception {
        Account recruiter = recruiter("update-states", "APPROVED");
        String jobId = create(recruiter, createBody("State-protected edit"), 201).at("/data/jobId").asText();
        publish(recruiter, jobId, 1);
        updateExpectingConflict(recruiter, jobId, 2);
        changeStatus(recruiter, jobId, "PAUSED", "Pause before edit", 2, null, 200);
        updateExpectingConflict(recruiter, jobId, 3);
        changeStatus(recruiter, jobId, "CLOSED", "Close before edit", 3, null, 200);
        updateExpectingConflict(recruiter, jobId, 4);
    }

    @Test
    void updateRequiresAuthenticationRoleAndOwnCompanyResource() throws Exception {
        Account owner = recruiter("update-owner", "APPROVED");
        Account outsider = recruiter("update-outsider", "APPROVED");
        Account candidate = candidate("update-candidate");
        String jobId = create(owner, createBody("Protected edit"), 201).at("/data/jobId").asText();
        String body = "{\"title\":\"Changed\",\"expectedVersion\":1}";

        mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                        .header("Authorization", bearer(candidate)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        for (String hidden : new String[]{jobId, UUID.randomUUID().toString()}) {
            mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", hidden)
                            .header("Authorization", bearer(outsider)).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    @Test
    void updateRejectsVersionValidationEnumsAndUnknownFields() throws Exception {
        Account recruiter = recruiter("update-validation", "APPROVED");
        String jobId = create(recruiter, createBody("Validated edit"), 201).at("/data/jobId").asText();
        String requestId = "req_update_version";
        mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                        .header("Authorization", bearer(recruiter)).header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Stale\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.requestId").value(requestId));

        for (String body : new String[]{
                "{}",
                "{\"title\":\"Changed\",\"expectedVersion\":0}",
                "{\"title\":\"   \",\"expectedVersion\":1}",
                "{\"description\":\"   \",\"expectedVersion\":1}",
                "{\"salary\":{\"min\":9000,\"max\":8000,\"currency\":\"SGD\",\"period\":\"MONTH\"}," +
                        "\"expectedVersion\":1}"}) {
            mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                            .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                        .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employmentType\":\"CONTRACT\",\"expectedVersion\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.employmentType").exists());
        mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                        .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Unknown\",\"expectedVersion\":1,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.status").exists());
    }

    @Test
    void companyVerificationDoesNotBlockEditingAnExistingDraft() throws Exception {
        for (String verification : new String[]{"PENDING", "REJECTED"}) {
            Account recruiter = recruiter("update-" + verification.toLowerCase(), "APPROVED");
            String jobId = create(recruiter, createBody(verification + " editable draft"), 201)
                    .at("/data/jobId").asText();
            jdbcTemplate.update("update companies set verification_status = ? where id = ?",
                    verification, recruiter.companyId());
            mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                            .header("Authorization", bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Edited while " + verification + "\",\"expectedVersion\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Edited while " + verification))
                    .andExpect(jsonPath("$.data.version").value(2));
        }
    }

    private void assertFiltered(Account account, String query, String expectedId) throws Exception {
        assertFiltered(account, query, expectedId, 1);
    }

    private void assertFiltered(Account account, String query, String expectedId, int total) throws Exception {
        var result = mockMvc.perform(get("/api/v1/recruiter/jobs?" + query).header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(total));
        if (expectedId != null) result.andExpect(jsonPath("$.data[0].jobId").value(expectedId));
    }

    private JsonNode create(Account account, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/api/v1/recruiter/jobs")
                        .header("Authorization", bearer(account)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return read(response);
    }

    private JsonNode publish(Account account, String jobId, int expectedVersion) throws Exception {
        String response = mockMvc.perform(post("/api/v1/recruiter/jobs/{jobId}/publish", jobId)
                        .header("Authorization", bearer(account)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + expectedVersion + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return read(response);
    }

    private void updateExpectingConflict(Account account, String jobId, int expectedVersion) throws Exception {
        mockMvc.perform(patch("/api/v1/recruiter/jobs/{jobId}", jobId)
                        .header("Authorization", bearer(account)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Forbidden edit\",\"expectedVersion\":" + expectedVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_JOB_TRANSITION"));
    }

    private JsonNode changeStatus(Account account, String jobId, String target, String reason,
                                  int expectedVersion, String requestId, int expectedStatus) throws Exception {
        var request = post("/api/v1/recruiter/jobs/{jobId}/status", jobId)
                .header("Authorization", bearer(account)).contentType(MediaType.APPLICATION_JSON)
                .content(statusBody(target, reason, expectedVersion));
        if (requestId != null) request.header("X-Request-Id", requestId);
        var result = mockMvc.perform(request).andExpect(status().is(expectedStatus));
        if (expectedStatus == 409) {
            String code = expectedVersion == 1 && requestId != null ? "VERSION_CONFLICT" : "INVALID_JOB_TRANSITION";
            result.andExpect(jsonPath("$.error.code").value(code));
        }
        if (expectedStatus == 403) result.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        if (requestId != null) {
            result.andExpect(header().string("X-Request-Id", requestId));
            if (expectedStatus >= 400) result.andExpect(jsonPath("$.error.requestId").value(requestId));
        }
        String response = result.andReturn().getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : read(response);
    }

    private static String statusBody(String target, String reason, int expectedVersion) {
        return "{\"status\":\"" + target + "\",\"reason\":\"" + reason
                + "\",\"expectedVersion\":" + expectedVersion + "}";
    }

    private Account recruiter(String prefix, String verification) throws Exception {
        String email = uniqueEmail(prefix);
        String body = """
                {"role":"RECRUITER","companyName":"%s Company","fullName":"Recruiter One",
                 "email":"%s","password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(prefix, email);
        JsonNode response = read(mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String companyId = response.at("/data/user/company/companyId").asText();
        jdbcTemplate.update("update companies set verification_status = ? where id = ?", verification, companyId);
        return new Account(response.at("/data/accessToken").asText(), response.at("/data/user/userId").asText(), companyId);
    }

    private Account candidate(String prefix) throws Exception {
        String body = """
                {"role":"CANDIDATE","fullName":"Candidate One","email":"%s",
                 "password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(uniqueEmail(prefix));
        JsonNode response = read(mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return new Account(response.at("/data/accessToken").asText(), response.at("/data/user/userId").asText(), null);
    }

    private JsonNode read(String body) throws Exception { return objectMapper.readTree(body); }
    private static String bearer(Account account) { return "Bearer " + account.accessToken(); }
    private static String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@example.com"; }

    private static String createBody(String title) {
        return """
                {"title":"%s","employmentType":"FULL_TIME","workplaceType":"HYBRID","location":"Singapore",
                 "salary":{"min":5000,"max":8000,"currency":"SGD","period":"MONTH"},
                 "description":"Build dependable recruiting platform services.",
                 "requirements":["Build reliable APIs","Work across teams"],"skills":["Java","MySQL"],
                 "deadline":"2026-09-30T15:59:59Z","visibility":"PUBLIC"}
                """.formatted(title);
    }

    private record Account(String accessToken, String userId, String companyId) {}
}
