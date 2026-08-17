package com.adproject.recommendation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateRecommendationFilterIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired ResumeRepository resumes;

    @Test
    void workplaceTypeLocationAndMinimumSalaryFiltersApplyBeforeRanking() throws Exception {
        String token = "flt" + UUID.randomUUID().toString().substring(0, 8);
        Fixture fixture = fixture(List.of(
                new JobSpec(token + " Hybrid Sg", EmploymentType.FULL_TIME, WorkplaceType.HYBRID,
                        "Singapore", 5000),
                new JobSpec(token + " Remote Sg", EmploymentType.FULL_TIME, WorkplaceType.REMOTE,
                        "Singapore", 5000),
                new JobSpec(token + " Hybrid Tokyo", EmploymentType.FULL_TIME, WorkplaceType.HYBRID,
                        "Tokyo", 5000),
                new JobSpec(token + " Hybrid Sg High", EmploymentType.FULL_TIME, WorkplaceType.HYBRID,
                        "Singapore", 12000)));

        String bearer = bearer(fixture.candidate());
        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token).queryParam("workplaceType", "REMOTE")
                        .queryParam("pageSize", "10")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value(fixture.jobIds().get(1)));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token).queryParam("location", "Tokyo")
                        .queryParam("pageSize", "10")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value(fixture.jobIds().get(2)));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token).queryParam("minimumSalary", "6000")
                        .queryParam("pageSize", "10")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value(fixture.jobIds().get(3)));

        // Combined: hybrid + Singapore + min 6000 -> only the high-salary hybrid job.
        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token).queryParam("workplaceType", "HYBRID")
                        .queryParam("location", "Singapore").queryParam("minimumSalary", "6000")
                        .queryParam("pageSize", "10")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value(fixture.jobIds().get(3)));
    }

    @Test
    void recommendationReflectsSavedState() throws Exception {
        String token = "svd" + UUID.randomUUID().toString().substring(0, 8);
        Fixture fixture = fixture(List.of(
                new JobSpec(token + " Engineer", EmploymentType.FULL_TIME, WorkplaceType.HYBRID,
                        "Singapore", 9000)));
        String bearer = bearer(fixture.candidate());
        String jobId = fixture.jobIds().get(0);

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token).queryParam("pageSize", "10")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].isSaved").value(false));

        mockMvc.perform(put("/api/v1/candidate/saved-jobs/{jobId}", jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token).queryParam("pageSize", "10")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value(jobId))
                .andExpect(jsonPath("$.data[0].isSaved").value(true));
    }

    private Fixture fixture(List<JobSpec> specs) {
        Instant now = Instant.parse("2026-08-12T08:00:00Z");
        String suffix = UUID.randomUUID().toString();
        UserEntity candidate = users.save(new UserEntity(UUID.randomUUID().toString(),
                "candidate-" + suffix + "@example.com", "hash", "Candidate", UserRole.CANDIDATE,
                UserStatus.ACTIVE, "2026-08", now, now));
        UserEntity recruiter = users.save(new UserEntity(UUID.randomUUID().toString(),
                "recruiter-" + suffix + "@example.com", "hash", "Recruiter", UserRole.RECRUITER,
                UserStatus.ACTIVE, "2026-08", now, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(),
                "Quantum Company " + suffix, CompanyVerificationStatus.APPROVED, 1,
                recruiter.getId(), now, now));
        List<String> jobIds = new ArrayList<>();
        for (JobSpec spec : specs) {
            String jobId = UUID.randomUUID().toString();
            jobs.save(new JobEntity(jobId, company.getId(), recruiter.getId(), recruiter.getId(),
                    spec.title(), spec.employmentType(), spec.workplaceType(), spec.location(),
                    3000, spec.salaryMax(), SalaryCurrency.SGD, SalaryPeriod.MONTH,
                    "Generic job description.", "[]", "[\"Cobol\"]", null,
                    Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1, now, now));
            jobIds.add(jobId);
        }
        resumes.save(new ResumeEntity(UUID.randomUUID().toString(), candidate.getId(),
                "Candidate", 27, "Singapore", "Engineer", "Built Cobol systems.", "[]",
                "[\"Cobol\"]", 1, now, now));
        return new Fixture(candidate, recruiter, jobIds);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwt.createAccessToken(user);
    }

    private record JobSpec(String title, EmploymentType employmentType, WorkplaceType workplaceType,
                           String location, long salaryMax) {}
    private record Fixture(UserEntity candidate, UserEntity recruiter, List<String> jobIds) {}
}
