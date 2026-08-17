package com.adproject.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CandidateSavedJobIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void saveIsIdempotentListShowsSavedJobAndDetailReflectsSaved() throws Exception {
        Account recruiter = recruiter("saved-basic");
        Account candidate = candidate("saved-basic");
        String title = "saved-" + UUID.randomUUID();
        String jobId = insertJob(recruiter, title, "ACTIVE", "PUBLIC",
                "FULL_TIME", "2026-08-11T08:00:00Z");

        save(candidate, jobId);
        save(candidate, jobId); // idempotent

        mockMvc.perform(get("/api/v1/candidate/saved-jobs").header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data[0].isSaved").value(true));

        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId).header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isSaved").value(true));
        mockMvc.perform(get("/api/v1/jobs").queryParam("q", title)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].isSaved").value(true));
    }

    @Test
    void unsaveIsIdempotentAndRemovesFromList() throws Exception {
        Account recruiter = recruiter("saved-unsave");
        Account candidate = candidate("saved-unsave");
        String jobId = insertJob(recruiter, "saved-" + UUID.randomUUID(), "ACTIVE", "PUBLIC",
                "FULL_TIME", "2026-08-11T08:00:00Z");

        save(candidate, jobId);
        unsave(candidate, jobId);
        unsave(candidate, jobId); // idempotent

        mockMvc.perform(get("/api/v1/candidate/saved-jobs").header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId).header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isSaved").value(false));
    }

    @Test
    void savedJobsEnforceAuthenticationCandidateRoleAndNotFound() throws Exception {
        Account recruiter = recruiter("saved-perm");
        Account candidate = candidate("saved-perm");
        String jobId = insertJob(recruiter, "saved-" + UUID.randomUUID(), "ACTIVE", "PUBLIC",
                "FULL_TIME", "2026-08-11T08:00:00Z");

        // Unauthenticated -> 401
        mockMvc.perform(get("/api/v1/candidate/saved-jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/candidate/saved-jobs/{jobId}", jobId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/candidate/saved-jobs/{jobId}", jobId)).andExpect(status().isUnauthorized());

        // Wrong role -> 403
        mockMvc.perform(get("/api/v1/candidate/saved-jobs").header("Authorization", bearer(recruiter)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/v1/candidate/saved-jobs/{jobId}", jobId)
                        .header("Authorization", bearer(recruiter)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/candidate/saved-jobs/{jobId}", jobId)
                        .header("Authorization", bearer(recruiter)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Missing job -> 404
        mockMvc.perform(put("/api/v1/candidate/saved-jobs/{jobId}", UUID.randomUUID().toString())
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        // Non-browsable job -> 404
        String draft = insertJob(recruiter, "saved-draft-" + UUID.randomUUID(), "DRAFT", "PUBLIC",
                "FULL_TIME", null);
        mockMvc.perform(put("/api/v1/candidate/saved-jobs/{jobId}", draft)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void savedJobsAreIsolatedBetweenCandidates() throws Exception {
        Account recruiter = recruiter("saved-isolation");
        Account alice = candidate("saved-alice");
        Account bob = candidate("saved-bob");
        String jobId = insertJob(recruiter, "saved-" + UUID.randomUUID(), "ACTIVE", "PUBLIC",
                "FULL_TIME", "2026-08-11T08:00:00Z");

        save(alice, jobId);

        mockMvc.perform(get("/api/v1/candidate/saved-jobs").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(0));
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId).header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isSaved").value(false));
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isSaved").value(true));
    }

    @Test
    void savedJobsPaginateAndHideNoLongerBrowsableJobs() throws Exception {
        Account recruiter = recruiter("saved-page");
        Account candidate = candidate("saved-page");
        String marker = "saved-page-" + UUID.randomUUID();
        String first = insertJob(recruiter, marker + " a", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T08:00:00Z");
        String second = insertJob(recruiter, marker + " b", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T08:00:00Z");
        String closed = insertJob(recruiter, marker + " closed", "ACTIVE", "PUBLIC", "FULL_TIME",
                "2026-08-11T08:00:00Z");

        save(candidate, first);
        save(candidate, second);
        save(candidate, closed);

        // Mark the closed job CLOSED so it drops out of the browsable list.
        jdbcTemplate.update("update jobs set status = 'CLOSED' where id = ?", closed);

        JsonNode pageOne = read(mockMvc.perform(get("/api/v1/candidate/saved-jobs")
                        .queryParam("page", "1").queryParam("pageSize", "1")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.hasNext").value(true))
                .andReturn().getResponse().getContentAsString());
        JsonNode pageTwo = read(mockMvc.perform(get("/api/v1/candidate/saved-jobs")
                        .queryParam("page", "2").queryParam("pageSize", "1")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.hasNext").value(false))
                .andReturn().getResponse().getContentAsString());
        Set<String> ids = new HashSet<>(Set.of(pageOne.at("/data/0/jobId").asText(),
                pageTwo.at("/data/0/jobId").asText()));
        assertThat(ids).containsExactlyInAnyOrder(first, second);
    }

    private void save(Account candidate, String jobId) throws Exception {
        mockMvc.perform(put("/api/v1/candidate/saved-jobs/{jobId}", jobId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNoContent());
    }

    private void unsave(Account candidate, String jobId) throws Exception {
        mockMvc.perform(delete("/api/v1/candidate/saved-jobs/{jobId}", jobId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNoContent());
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
