package com.adproject.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
