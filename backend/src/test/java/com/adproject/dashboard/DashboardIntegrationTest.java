package com.adproject.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.*;
import com.adproject.company.infrastructure.*;
import com.adproject.job.domain.*;
import com.adproject.job.infrastructure.*;
import com.adproject.resume.infrastructure.*;
import com.adproject.user.domain.*;
import com.adproject.user.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class DashboardIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;
    @Autowired CompanyRepository companies; @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs; @Autowired ResumeRepository resumes; @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test void dashboardAggregatesOwnCompanyMetricsAndRecentItems() throws Exception {
        Fixture f = fixture("Dashboard Candidate");
        String activeOne = job(f, "Active One", JobStatus.ACTIVE, 1);
        String activeTwo = job(f, "Active Two", JobStatus.ACTIVE, 2);
        String activeThree = job(f, "Active Three", JobStatus.ACTIVE, 3);
        String draft = job(f, "Draft Role", JobStatus.DRAFT, 4);

        String applied = submit(f, activeOne);
        String inReview = submit(f, activeTwo);
        String interview = submit(f, activeThree);
        jdbc.update("update applications set status='IN_REVIEW',version=2,updated_at=? where id=?",
                Timestamp.from(Instant.parse("2026-08-12T01:20:00Z")), inReview);
        jdbc.update("update applications set status='INTERVIEW',version=2,updated_at=? where id=?",
                Timestamp.from(Instant.parse("2026-08-12T01:30:00Z")), interview);
        jdbc.update("update applications set updated_at=? where id=?",
                Timestamp.from(Instant.parse("2026-08-12T01:10:00Z")), applied);

        mvc.perform(get("/api/v1/recruiter/dashboard").header("Authorization", recruiter(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.activeJobs").value(3))
                .andExpect(jsonPath("$.data.metrics.appliedApplications").value(1))
                .andExpect(jsonPath("$.data.metrics.inReviewApplications").value(1))
                .andExpect(jsonPath("$.data.metrics.interviewApplications").value(1))
                .andExpect(jsonPath("$.data.metrics.companyVerificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.recentApplications.length()").value(3))
                .andExpect(jsonPath("$.data.recentApplications[0].applicationId").value(interview))
                .andExpect(jsonPath("$.data.recentApplications[0].candidate.fullName").value("Dashboard Candidate"))
                .andExpect(jsonPath("$.data.recentApplications[0].matchScore").isEmpty())
                .andExpect(jsonPath("$.data.recentJobs.length()").value(3))
                .andExpect(jsonPath("$.data.recentJobs[0].jobId").value(draft))
                .andExpect(jsonPath("$.data.recentJobs[0].title").value("Draft Role"));
    }

    @Test void dashboardRequiresRecruiterAuthentication() throws Exception {
        Fixture f = fixture("Dashboard Role Candidate");
        mvc.perform(get("/api/v1/recruiter/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/recruiter/dashboard").header("Authorization", candidate(f)))
                .andExpect(status().isForbidden());
    }

    @Test void dashboardIsCompanyScoped() throws Exception {
        Fixture a = fixture("Company A Candidate");
        Fixture b = fixture("Company B Candidate");
        job(a, "A Job", JobStatus.ACTIVE, 1);
        submit(a, job(a, "A Job Two", JobStatus.ACTIVE, 2));

        mvc.perform(get("/api/v1/recruiter/dashboard").header("Authorization", recruiter(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.activeJobs").value(0))
                .andExpect(jsonPath("$.data.metrics.appliedApplications").value(0))
                .andExpect(jsonPath("$.data.recentApplications.length()").value(0))
                .andExpect(jsonPath("$.data.recentJobs.length()").value(0));

        mvc.perform(get("/api/v1/recruiter/dashboard").header("Authorization", recruiter(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.activeJobs").value(2))
                .andExpect(jsonPath("$.data.metrics.appliedApplications").value(1))
                .andExpect(jsonPath("$.data.recentApplications.length()").value(1))
                .andExpect(jsonPath("$.data.recentJobs.length()").value(2));
    }

    @Test void dashboardHandlesEmptyCompany() throws Exception {
        Fixture f = fixture("Empty Dashboard Candidate");
        mvc.perform(get("/api/v1/recruiter/dashboard").header("Authorization", recruiter(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.activeJobs").value(0))
                .andExpect(jsonPath("$.data.metrics.appliedApplications").value(0))
                .andExpect(jsonPath("$.data.metrics.inReviewApplications").value(0))
                .andExpect(jsonPath("$.data.metrics.interviewApplications").value(0))
                .andExpect(jsonPath("$.data.metrics.companyVerificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.recentApplications.length()").value(0))
                .andExpect(jsonPath("$.data.recentJobs.length()").value(0));
    }

    private String submit(Fixture f, String jobId) throws Exception {
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resumeId\":\"" + f.resumeId() +
                                "\",\"contactEmail\":\"" + f.email() + "\",\"shareProfile\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/applicationId").asText();
    }

    private Fixture fixture(String candidateName) {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(), CompanyMemberRole.ADMIN, now));
        UserEntity candidate = users.save(user(candidateName, UserRole.CANDIDATE, now));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer", "Summary",
                "[]", 1, now, now));
        return new Fixture(jwt.createAccessToken(recruiter), jwt.createAccessToken(candidate), recruiter.getId(),
                candidate.getId(), candidate.getEmail(), company.getId(), resumeId);
    }

    private UserEntity user(String name, UserRole role, Instant now) {
        String id = UUID.randomUUID().toString();
        return new UserEntity(id, id + "@example.com", "hash", name, role, UserStatus.ACTIVE, "2026-08", now, now);
    }

    private String job(Fixture f, String title, JobStatus status, int createdSeconds) {
        Instant created = Instant.parse("2026-08-12T01:00:00Z").plusSeconds(createdSeconds);
        String id = UUID.randomUUID().toString();
        jobs.save(new JobEntity(id, f.companyId(), f.recruiterId(), f.recruiterId(), title, EmploymentType.FULL_TIME,
                WorkplaceType.HYBRID, "Singapore", 5000, 8000, SalaryCurrency.SGD, SalaryPeriod.MONTH, "Description",
                "[]", "[]", null, Visibility.PUBLIC, status, 0, 1, created, created));
        return id;
    }

    private static String recruiter(Fixture f) { return "Bearer " + f.recruiterToken(); }
    private static String candidate(Fixture f) { return "Bearer " + f.candidateToken(); }

    private record Fixture(String recruiterToken, String candidateToken, String recruiterId, String candidateId,
                           String email, String companyId, String resumeId) {}
}
