package com.adproject.profile;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.Visibility;
import com.adproject.job.domain.WorkplaceType;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.profile.infrastructure.RecruiterProfileEntity;
import com.adproject.profile.infrastructure.RecruiterProfileRepository;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidatePublicProfileIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired CompanyMemberRepository members;
    @Autowired RecruiterProfileRepository profiles;
    @Autowired JobRepository jobs;
    @Autowired ResumeRepository resumes;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private static final Instant NOW = Instant.parse("2026-08-13T02:00:00Z");

    @Test
    void candidateSeesRecruiterPublicProfileWithoutPrivateFields() throws Exception {
        Fixture f = fixture("See Recruiter Candidate");
        submit(f, job(f, "Backend Engineer"));
        jdbc.update("update users set avatar_url=? where id=?", "https://example.com/avatar.png", f.recruiterId());

        mvc.perform(get("/api/v1/candidate/recruiters/{id}", f.recruiterId()).header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recruiterId").value(f.recruiterId()))
                .andExpect(jsonPath("$.data.fullName").value("Recruiter"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.title").value("Head of Engineering"))
                .andExpect(jsonPath("$.data.bio").value("Builds teams"))
                .andExpect(jsonPath("$.data.company.companyId").value(f.companyId()))
                .andExpect(jsonPath("$.data.company.name").value("Company"))
                .andExpect(jsonPath("$.data.company.verificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data", not(hasKey("email"))))
                .andExpect(jsonPath("$.data", not(hasKey("role"))))
                .andExpect(jsonPath("$.data", not(hasKey("status"))))
                .andExpect(jsonPath("$.data", not(hasKey("createdAt"))))
                .andExpect(jsonPath("$.data", not(hasKey("passwordHash"))));
    }

    @Test
    void candidateSeesCompanyPublicProfileWithoutInternalFields() throws Exception {
        Fixture f = fixture("See Company Candidate");
        submit(f, job(f, "Company Job"));
        jdbc.update("update companies set description=?, location=?, logo_url=? where id=?",
                "We build tools", "Singapore", "https://example.com/logo.png", f.companyId());

        mvc.perform(get("/api/v1/candidate/companies/{id}", f.companyId()).header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(f.companyId()))
                .andExpect(jsonPath("$.data.name").value("Company"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.data.description").value("We build tools"))
                .andExpect(jsonPath("$.data.location").value("Singapore"))
                .andExpect(jsonPath("$.data.verificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data", not(hasKey("createdBy"))))
                .andExpect(jsonPath("$.data", not(hasKey("version"))))
                .andExpect(jsonPath("$.data", not(hasKey("website"))))
                .andExpect(jsonPath("$.data", not(hasKey("createdAt"))));
    }

    @Test
    void firstTimeBrowserCanViewRecruiterAndCompanyPublicProfiles() throws Exception {
        Fixture f = fixture("First Time Browser");
        job(f, "Public Job"); // public + active, no application or conversation yet
        jdbc.update("update companies set description=?, location=? where id=?", "Public desc", "Singapore",
                f.companyId());

        mvc.perform(get("/api/v1/candidate/recruiters/{id}", f.recruiterId()).header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recruiterId").value(f.recruiterId()))
                .andExpect(jsonPath("$.data.fullName").value("Recruiter"))
                .andExpect(jsonPath("$.data", not(hasKey("email"))));
        mvc.perform(get("/api/v1/candidate/companies/{id}", f.companyId()).header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(f.companyId()))
                .andExpect(jsonPath("$.data.description").value("Public desc"));
    }

    @Test
    void jobDetailIncludesRecruiterPreview() throws Exception {
        Fixture f = fixture("Job Detail Candidate");
        String jobId = job(f, "Detail Job");
        submit(f, jobId);
        jdbc.update("update users set avatar_url=? where id=?", "https://example.com/avatar.png", f.recruiterId());

        mvc.perform(get("/api/v1/jobs/{id}", jobId).header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recruiter.recruiterId").value(f.recruiterId()))
                .andExpect(jsonPath("$.data.recruiter.fullName").value("Recruiter"))
                .andExpect(jsonPath("$.data.recruiter.title").value("Head of Engineering"))
                .andExpect(jsonPath("$.data.recruiter.avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.company.companyId").value(f.companyId()));
    }

    @Test
    void endpointsRequireAuthenticationAndCandidateRole() throws Exception {
        Fixture f = fixture("Role Candidate");
        submit(f, job(f, "Role Job"));

        mvc.perform(get("/api/v1/candidate/recruiters/{id}", f.recruiterId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/candidate/companies/{id}", f.companyId()))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/candidate/recruiters/{id}", f.recruiterId()).header("Authorization", recruiter(f)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/candidate/companies/{id}", f.companyId()).header("Authorization", recruiter(f)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void missingOrUnrelatedResourcesReturnNotFound() throws Exception {
        Fixture f = fixture("Scoped Candidate");
        job(f, "Scoped Job");

        // A recruiter/company whose only job is PRIVATE is not reachable by browsing; an unrelated candidate 404s.
        UserEntity hiddenRecruiter = users.save(user("Hidden Recruiter", UserRole.RECRUITER, NOW));
        profiles.save(new RecruiterProfileEntity(hiddenRecruiter.getId(), "Recruiter", null, NOW, NOW));
        CompanyEntity hiddenCompany = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Hidden Co",
                CompanyVerificationStatus.APPROVED, 1, hiddenRecruiter.getId(), NOW, NOW));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), hiddenCompany.getId(),
                hiddenRecruiter.getId(), CompanyMemberRole.ADMIN, NOW));
        jobs.save(new JobEntity(UUID.randomUUID().toString(), hiddenCompany.getId(), hiddenRecruiter.getId(),
                hiddenRecruiter.getId(), "Hidden Job", EmploymentType.FULL_TIME, WorkplaceType.HYBRID, "Singapore",
                5000, 8000, SalaryCurrency.SGD, SalaryPeriod.MONTH, "Description", "[]", "[]", null,
                Visibility.PRIVATE, JobStatus.ACTIVE, 0, 1, NOW, NOW));

        UserEntity other = users.save(user("Other Candidate", UserRole.CANDIDATE, NOW));
        String otherToken = "Bearer " + jwt.createAccessToken(other);

        mvc.perform(get("/api/v1/candidate/recruiters/{id}", hiddenRecruiter.getId()).header("Authorization", otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/candidate/companies/{id}", hiddenCompany.getId()).header("Authorization", otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // Non-existent ids.
        mvc.perform(get("/api/v1/candidate/recruiters/{id}", "missing").header("Authorization", candidate(f)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/candidate/companies/{id}", "missing").header("Authorization", candidate(f)))
                .andExpect(status().isNotFound());

        // A candidate id looked up as a recruiter resolves to nothing.
        mvc.perform(get("/api/v1/candidate/recruiters/{id}", f.candidateId()).header("Authorization", candidate(f)))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private String submit(Fixture f, String jobId) throws Exception {
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + f.resumeId() + "\",\"contactEmail\":\"" + f.email()
                                + "\",\"shareProfile\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/applicationId").asText();
    }

    private String job(Fixture f, String title) {
        String id = UUID.randomUUID().toString();
        jobs.save(new JobEntity(id, f.companyId(), f.recruiterId(), f.recruiterId(), title, EmploymentType.FULL_TIME,
                WorkplaceType.HYBRID, "Singapore", 5000, 8000, SalaryCurrency.SGD, SalaryPeriod.MONTH, "Description",
                "[]", "[]", null, Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1, NOW, NOW));
        return id;
    }

    private Fixture fixture(String candidateName) {
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, NOW));
        profiles.save(new RecruiterProfileEntity(recruiter.getId(), "Head of Engineering", "Builds teams", NOW, NOW));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), NOW, NOW));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(),
                CompanyMemberRole.ADMIN, NOW));
        UserEntity candidate = users.save(user(candidateName, UserRole.CANDIDATE, NOW));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer",
                "Summary", "[]", 1, NOW, NOW));
        return new Fixture(jwt.createAccessToken(recruiter), jwt.createAccessToken(candidate),
                recruiter.getId(), candidate.getId(), candidate.getEmail(), company.getId(), resumeId);
    }

    private UserEntity user(String name, UserRole role, Instant now) {
        String id = UUID.randomUUID().toString();
        return new UserEntity(id, id + "@example.com", "hash", name, role, UserStatus.ACTIVE, "2026-08", now, now);
    }

    private static String candidate(Fixture f) { return "Bearer " + f.candidateToken(); }
    private static String recruiter(Fixture f) { return "Bearer " + f.recruiterToken(); }

    private record Fixture(String recruiterToken, String candidateToken, String recruiterId, String candidateId,
                           String email, String companyId, String resumeId) {}
}
