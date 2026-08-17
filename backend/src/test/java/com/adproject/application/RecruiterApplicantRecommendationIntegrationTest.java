package com.adproject.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.application.infrastructure.ResumeSnapshotEntity;
import com.adproject.application.infrastructure.ResumeSnapshotRepository;
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
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RecruiterApplicantRecommendationIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;
    @Autowired CompanyRepository companies; @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs; @Autowired ResumeRepository resumes; @Autowired ObjectMapper mapper;
    @Autowired ResumeSnapshotRepository snapshots; @Autowired ApplicationRepository applications;
    @Autowired JdbcTemplate jdbc;

    @Test void ranksEligibleApplicantsWithDeterministicFallbackWhenMlIsDisabled() throws Exception {
        Fixture fixture = fixture();
        String jobId = job(fixture, "Cobol Engineer", "[\"Cobol\"]");
        submit(fixture, jobId, "Alice Cobol", "[\"Cobol\"]");
        submit(fixture, jobId, "Bob Python", "[\"Python\"]");

        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", jobId)
                        .header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.source").value("FALLBACK"))
                .andExpect(jsonPath("$.meta.modelStatus").value("DEGRADED"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].candidate.fullName").value("Alice Cobol"))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data[0].matchScore").isNumber())
                .andExpect(jsonPath("$.data[0].matchAnalysis.strongMatches").isArray())
                .andExpect(jsonPath("$.data[0].candidate.email").doesNotExist());

        // Pagination slices the already-ranked list without re-ranking.
        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", jobId)
                        .header("Authorization", recruiter(fixture))
                        .param("page", "1").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.hasNext").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].rank").value(1));
    }

    @Test void offeredApplicantsAreExcludedFromRanking() throws Exception {
        Fixture fixture = fixture();
        String jobId = job(fixture, "Offer Job", "[\"Java\"]");
        String offered = submit(fixture, jobId, "Offered Candidate", "[\"Java\"]");
        submit(fixture, jobId, "Active Candidate", "[\"Java\"]");
        jdbc.update("update applications set status='OFFERED', version=2 where id=?", offered);

        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", jobId)
                        .header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].candidate.fullName").value("Active Candidate"));
    }

    @Test void emptyEligibleSetReturnsEmptyWithoutModelCall() throws Exception {
        Fixture fixture = fixture();
        String jobId = job(fixture, "Empty Job", "[]");
        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", jobId)
                        .header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.source").value("NONE"))
                .andExpect(jsonPath("$.meta.modelStatus").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test void overModelInputLimitReturns422() throws Exception {
        Fixture fixture = fixture();
        String jobId = job(fixture, "Limit Job", "[]");
        Instant now = now();
        List<UserEntity> batchUsers = new ArrayList<>();
        List<ResumeEntity> batchResumes = new ArrayList<>();
        List<ResumeSnapshotEntity> batchSnapshots = new ArrayList<>();
        List<ApplicationEntity> batchApplications = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            String candidateId = UUID.randomUUID().toString();
            batchUsers.add(new UserEntity(candidateId, "limit-" + i + "@example.com", "hash",
                    "Candidate " + i, UserRole.CANDIDATE, UserStatus.ACTIVE, "2026-08", now, now));
            String resumeId = UUID.randomUUID().toString();
            batchResumes.add(new ResumeEntity(resumeId, candidateId, "Candidate " + i, 28, "Singapore",
                    "Engineer", "Summary", "[]", "[]", 1, now, now));
            String snapshotId = UUID.randomUUID().toString();
            batchSnapshots.add(new ResumeSnapshotEntity(snapshotId, resumeId, candidateId, "Candidate " + i, 28,
                    "Singapore", "Engineer", "Summary", "[]", "[]", 1, now, now, now));
            batchApplications.add(new ApplicationEntity(UUID.randomUUID().toString(), jobId, candidateId,
                    resumeId, snapshotId, "limit-" + i + "@example.com", false, ApplicationStatus.APPLIED,
                    now, now, 1));
        }
        users.saveAll(batchUsers);
        resumes.saveAll(batchResumes);
        snapshots.saveAll(batchSnapshots);
        applications.saveAll(batchApplications);

        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", jobId)
                        .header("Authorization", recruiter(fixture)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("RECOMMENDATION_INPUT_LIMIT"));
    }

    @Test void crossCompanyRecruiterOrUnknownJobReturns404() throws Exception {
        Fixture owner = fixture();
        String jobId = job(owner, "Own Job", "[]");
        Fixture other = fixture();
        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", jobId)
                        .header("Authorization", recruiter(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", UUID.randomUUID())
                        .header("Authorization", recruiter(owner)))
                .andExpect(status().isNotFound());
    }

    @Test void candidateRoleReturns403() throws Exception {
        Fixture fixture = fixture();
        String jobId = job(fixture, "Job", "[]");
        UserEntity candidate = users.save(user("Candidate", UserRole.CANDIDATE, now()));
        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", jobId)
                        .header("Authorization", "Bearer " + jwt.createAccessToken(candidate)))
                .andExpect(status().isForbidden());
    }

    @Test void unauthenticatedReturns401() throws Exception {
        mvc.perform(get("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private Fixture fixture() {
        Instant now = now();
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(),
                CompanyMemberRole.ADMIN, now));
        return new Fixture(jwt.createAccessToken(recruiter), recruiter.getId(), company.getId());
    }

    private UserEntity user(String name, UserRole role, Instant now) {
        String id = UUID.randomUUID().toString();
        return new UserEntity(id, id + "@example.com", "hash", name, role, UserStatus.ACTIVE, "2026-08", now, now);
    }

    private String job(Fixture f, String title, String skillsJson) {
        Instant now = now();
        String id = UUID.randomUUID().toString();
        jobs.save(new JobEntity(id, f.companyId(), f.recruiterId(), f.recruiterId(), title,
                EmploymentType.FULL_TIME, WorkplaceType.HYBRID, "Singapore", 5000, 8000, SalaryCurrency.SGD,
                SalaryPeriod.MONTH, "Description", "[]", skillsJson, null, Visibility.PUBLIC, JobStatus.ACTIVE,
                0, 1, now, now));
        return id;
    }

    private String submit(Fixture f, String jobId, String fullName, String skillsJson) throws Exception {
        Instant now = now();
        UserEntity candidate = users.save(user(fullName, UserRole.CANDIDATE, now));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), fullName, 28, "Singapore", "Engineer",
                "Summary", "[]", skillsJson, 1, now, now));
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", "Bearer " + jwt.createAccessToken(candidate))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"" + resumeId + "\",\"contactEmail\":\"" + candidate.getEmail()
                                + "\",\"shareProfile\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/applicationId").asText();
    }

    private static Instant now() { return Instant.parse("2026-08-12T01:00:00Z"); }
    private static String recruiter(Fixture f) { return "Bearer " + f.token(); }
    private record Fixture(String token, String recruiterId, String companyId) {}
}
