package com.adproject.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
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
class CandidateJobIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void candidateListsOnlyActivePublicJobsWithRealCompanyAndTransitionalProjection() throws Exception {
        Account recruiter = recruiter("candidate-visible");
        Account candidate = candidate("candidate-visible");
        String marker = "visible-" + UUID.randomUUID();
        String visible = insertJob(recruiter, marker + " active", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T08:00:00Z");
        insertJob(recruiter, marker + " draft", "DRAFT", "PUBLIC", "FULL_TIME", null);
        insertJob(recruiter, marker + " paused", "PAUSED", "PUBLIC", "FULL_TIME", "2026-08-11T09:00:00Z");
        insertJob(recruiter, marker + " closed", "CLOSED", "PUBLIC", "FULL_TIME", "2026-08-11T10:00:00Z");
        insertJob(recruiter, marker + " private", "ACTIVE", "PRIVATE", "FULL_TIME", "2026-08-11T11:00:00Z");

        mockMvc.perform(get("/api/v1/jobs").queryParam("q", marker)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value(visible))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data[0].company.companyId").value(recruiter.companyId()))
                .andExpect(jsonPath("$.data[0].company.name").value(recruiter.companyName()))
                .andExpect(jsonPath("$.data[0].matchScore").isEmpty())
                .andExpect(jsonPath("$.data[0].recruiter.fullName").value("Recruiter One"))
                .andExpect(jsonPath("$.data[0].publishedAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }

    @Test
    void listSupportsEmptyPagePaginationStableSortTitleQueryAndEmploymentType() throws Exception {
        Account recruiter = recruiter("candidate-filters");
        Account candidate = candidate("candidate-filters");
        String marker = "filters-" + UUID.randomUUID();
        String first = insertJob(recruiter, marker + " backend alpha", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T12:00:00Z");
        String second = insertJob(recruiter, marker + " backend beta", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T12:00:00Z");
        insertJob(recruiter, marker + " data internship", "ACTIVE", "PUBLIC", "INTERNSHIP",
                "2026-08-11T13:00:00Z");

        JsonNode pageOne = read(mockMvc.perform(get("/api/v1/jobs")
                        .queryParam("q", marker).queryParam("employmentType", "FULL_TIME")
                        .queryParam("page", "1").queryParam("pageSize", "1")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.hasNext").value(true))
                .andReturn().getResponse().getContentAsString());
        JsonNode pageTwo = read(mockMvc.perform(get("/api/v1/jobs")
                        .queryParam("q", marker).queryParam("employmentType", "FULL_TIME")
                        .queryParam("page", "2").queryParam("pageSize", "1")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.hasNext").value(false))
                .andReturn().getResponse().getContentAsString());
        Set<String> ids = new HashSet<>(Set.of(pageOne.at("/data/0/jobId").asText(),
                pageTwo.at("/data/0/jobId").asText()));
        assertThat(ids).containsExactlyInAnyOrder(first, second);
        assertThat(pageOne.at("/data/0/jobId").asText())
                .isGreaterThan(pageTwo.at("/data/0/jobId").asText());

        mockMvc.perform(get("/api/v1/jobs").queryParam("q", "missing-" + UUID.randomUUID())
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.total").value(0));
        mockMvc.perform(get("/api/v1/jobs").queryParam("q", marker + " DATA")
                        .queryParam("employmentType", "INTERNSHIP")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void candidateGetsVisibleDetailAndHiddenStatesAreIndistinguishableFromMissing() throws Exception {
        Account recruiter = recruiter("candidate-detail");
        Account candidate = candidate("candidate-detail");
        String marker = "detail-" + UUID.randomUUID();
        String visible = insertJob(recruiter, marker + " visible", "ACTIVE", "PUBLIC", "PART_TIME",
                "2026-08-11T14:00:00Z");

        mockMvc.perform(get("/api/v1/jobs/{jobId}", visible).header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(marker + " visible"))
                .andExpect(jsonPath("$.data.employmentType").value("PART_TIME"))
                .andExpect(jsonPath("$.data.workplaceType").value("HYBRID"))
                .andExpect(jsonPath("$.data.location").value("Singapore"))
                .andExpect(jsonPath("$.data.salary.currency").value("SGD"))
                .andExpect(jsonPath("$.data.description").value("Real persisted description"))
                .andExpect(jsonPath("$.data.requirements[0]").value("Reliable APIs"))
                .andExpect(jsonPath("$.data.skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.matchScore").isEmpty())
                .andExpect(jsonPath("$.data.matchAnalysis").isEmpty())
                .andExpect(jsonPath("$.data.applicationState").value("NOT_APPLIED"))
                .andExpect(jsonPath("$.data.isSaved").value(false))
                .andExpect(jsonPath("$.data.publishedAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.data.updatedAt").value(org.hamcrest.Matchers.endsWith("Z")));

        for (String[] hidden : new String[][]{
                {"DRAFT", "PUBLIC"}, {"PAUSED", "PUBLIC"}, {"CLOSED", "PUBLIC"}, {"ACTIVE", "PRIVATE"}}) {
            String jobId = insertJob(recruiter, marker + hidden[0] + hidden[1], hidden[0], hidden[1],
                    "FULL_TIME", hidden[0].equals("DRAFT") ? null : "2026-08-11T15:00:00Z");
            assertNotFound(candidate, jobId);
        }
        assertNotFound(candidate, UUID.randomUUID().toString());
    }

    @Test
    void listAndDetailEnforceAuthenticationAndCandidateRoleWithRequestIds() throws Exception {
        Account recruiter = recruiter("candidate-permission");
        Account candidate = candidate("candidate-permission");
        String jobId = insertJob(recruiter, "permission-" + UUID.randomUUID(), "ACTIVE", "PUBLIC",
                "FULL_TIME", "2026-08-11T16:00:00Z");

        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/api/v1/jobs"), get("/api/v1/jobs/{jobId}", jobId)}) {
            mockMvc.perform(request.header("X-Request-Id", "req_candidate_unauthorized"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-Request-Id", "req_candidate_unauthorized"))
                    .andExpect(jsonPath("$.error.requestId").value("req_candidate_unauthorized"));
        }
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/api/v1/jobs"), get("/api/v1/jobs/{jobId}", jobId)}) {
            mockMvc.perform(request.header("Authorization", bearer(recruiter)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.error.requestId").isNotEmpty());
        }
        mockMvc.perform(get("/api/v1/jobs").queryParam("page", "0")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void listSurvivesJobsWithEmptyOrMalformedListJson() throws Exception {
        Account recruiter = recruiter("candidate-robust-json");
        Account candidate = candidate("candidate-robust-json");
        String marker = "robust-" + UUID.randomUUID();
        String good = insertJob(recruiter, marker + " good", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T08:00:00Z");
        insertJobWithListJson(recruiter, marker + " empty-req", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T09:00:00Z", "", "[\"Java\"]");
        insertJobWithListJson(recruiter, marker + " empty-skills", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T10:00:00Z", "[\"Java\"]", "");
        insertJobWithListJson(recruiter, marker + " malformed", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T11:00:00Z", "not-json", "[\"Java\"]");

        // A single row with empty/malformed list JSON must not 500 the whole list.
        mockMvc.perform(get("/api/v1/jobs").queryParam("q", marker)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(4))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[?(@.jobId == '%s')].requirements[0]".formatted(good))
                        .value("Reliable APIs"));
    }

    private void assertNotFound(Account candidate, String jobId) throws Exception {
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId).header("Authorization", bearer(candidate)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    private String insertJob(Account recruiter, String title, String status, String visibility,
                             String employmentType, String publishedAt) {
        String jobId = UUID.randomUUID().toString();
        Timestamp created = Timestamp.from(Instant.parse("2026-08-11T06:00:00Z"));
        jdbcTemplate.update("insert into jobs (id,company_id,created_by,owner_id,title,employment_type," +
                        "workplace_type,location,salary_min,salary_max,salary_currency,salary_period,description," +
                        "requirements_json,skills_json,deadline,visibility,status,applicant_count,published_at,version," +
                        "created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                jobId, recruiter.companyId(), recruiter.userId(), recruiter.userId(), title, employmentType,
                "HYBRID", "Singapore", 5000, 8000, "SGD", "MONTH", "Real persisted description",
                "[\"Reliable APIs\"]", "[\"Java\",\"MySQL\"]", Timestamp.from(Instant.parse("2026-09-30T15:59:59Z")),
                visibility, status, 0, publishedAt == null ? null : Timestamp.from(Instant.parse(publishedAt)),
                2, created, created);
        return jobId;
    }

    private String insertJobWithListJson(Account recruiter, String title, String status, String visibility,
                                         String employmentType, String publishedAt,
                                         String requirementsJson, String skillsJson) {
        String jobId = UUID.randomUUID().toString();
        Timestamp created = Timestamp.from(Instant.parse("2026-08-11T06:00:00Z"));
        jdbcTemplate.update("insert into jobs (id,company_id,created_by,owner_id,title,employment_type," +
                        "workplace_type,location,salary_min,salary_max,salary_currency,salary_period,description," +
                        "requirements_json,skills_json,deadline,visibility,status,applicant_count,published_at,version," +
                        "created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                jobId, recruiter.companyId(), recruiter.userId(), recruiter.userId(), title, employmentType,
                "HYBRID", "Singapore", 5000, 8000, "SGD", "MONTH", "Real persisted description",
                requirementsJson, skillsJson, Timestamp.from(Instant.parse("2026-09-30T15:59:59Z")),
                visibility, status, 0, publishedAt == null ? null : Timestamp.from(Instant.parse(publishedAt)),
                2, created, created);
        return jobId;
    }

    private Account recruiter(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String companyName = prefix + " Company";
        String body = """
                {"role":"RECRUITER","companyName":"%s","fullName":"Recruiter One",
                 "email":"%s","password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(companyName, email);
        JsonNode response = read(mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return new Account(response.at("/data/accessToken").asText(), response.at("/data/user/userId").asText(),
                response.at("/data/user/company/companyId").asText(), companyName);
    }

    private Account candidate(String prefix) throws Exception {
        String body = """
                {"role":"CANDIDATE","fullName":"Candidate One","email":"%s",
                 "password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(uniqueEmail(prefix));
        JsonNode response = read(mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return new Account(response.at("/data/accessToken").asText(), response.at("/data/user/userId").asText(),
                null, null);
    }

    private JsonNode read(String body) throws Exception { return objectMapper.readTree(body); }
    private static String bearer(Account account) { return "Bearer " + account.accessToken(); }
    private static String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@example.com"; }
    private record Account(String accessToken, String userId, String companyId, String companyName) {}
}
