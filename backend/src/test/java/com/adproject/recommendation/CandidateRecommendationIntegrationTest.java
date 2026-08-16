package com.adproject.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
import com.adproject.recommendation.infrastructure.CandidateJobRecommendationRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateRecommendationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired ResumeRepository resumes;
    @Autowired CandidateJobRecommendationRepository recommendations;

    @Test
    void savesVersionedPreferencesAndRanksWithFallbackWhenMlIsDisabled() throws Exception {
        Fixture fixture = fixture(true);
        String body = """
                {
                  "desiredTitles": ["Quantum Cobol Engineer"],
                  "preferredLocations": ["Singapore"],
                  "workplaceTypes": ["HYBRID"],
                  "employmentTypes": ["FULL_TIME"],
                  "minimumSalary": 6000,
                  "salaryCurrency": "SGD",
                  "salaryPeriod": "MONTH",
                  "expectedVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/candidate/job-preferences")
                        .header("Authorization", bearer(fixture.candidate()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.desiredTitles[0]").value("Quantum Cobol Engineer"));

        mockMvc.perform(put("/api/v1/candidate/job-preferences")
                        .header("Authorization", bearer(fixture.candidate()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("limit", "5")
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.source").value("FALLBACK"))
                .andExpect(jsonPath("$.meta.modelStatus").value("DEGRADED"))
                .andExpect(jsonPath("$.data[*].jobId").value(hasItem(fixture.jobId())))
                .andExpect(jsonPath("$.data[0].matchScore").value(100))
                .andExpect(jsonPath("$.data[0].matchAnalysis.strongMatches").isArray());

        mockMvc.perform(get("/api/v1/jobs/{jobId}", fixture.jobId())
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchScore").value(100))
                .andExpect(jsonPath("$.data.matchAnalysis.strongMatches").isArray());

        assertThat(recommendations.findByCandidateIdAndJobIdIn(
                fixture.candidate().getId(), List.of(fixture.jobId())))
                .singleElement().satisfies(snapshot -> {
                    assertThat(snapshot.getSource()).isEqualTo("FALLBACK");
                    assertThat(snapshot.getScore()).isEqualTo(100);
                    assertThat(snapshot.getResumeVersion()).isEqualTo(1);
                    assertThat(snapshot.getPreferenceVersion()).isEqualTo(1);
                });

        ResumeEntity changed = resumes.findByCandidateId(fixture.candidate().getId()).orElseThrow();
        changed.replace(changed.getFullName(), changed.getAge(), changed.getLocation(),
                changed.getHeadline(), "Updated summary", changed.getExperiencesJson(),
                changed.getSkillsJson(), Instant.parse("2026-08-12T09:00:00Z"));
        resumes.saveAndFlush(changed);

        mockMvc.perform(get("/api/v1/jobs/{jobId}", fixture.jobId())
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchScore").isEmpty())
                .andExpect(jsonPath("$.data.matchAnalysis").isEmpty());
    }

    @Test
    void recommendationRequiresCandidateResumeAndCandidateRole() throws Exception {
        Fixture withoutResume = fixture(false);

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .header("Authorization", bearer(withoutResume.candidate())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("RESUME_REQUIRED"));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .header("Authorization", bearer(withoutResume.recruiter())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private Fixture fixture(boolean withResume) {
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
        String jobId = UUID.randomUUID().toString();
        jobs.save(new JobEntity(jobId, company.getId(), recruiter.getId(), recruiter.getId(),
                "Quantum Cobol Engineer", EmploymentType.FULL_TIME, WorkplaceType.HYBRID,
                "Singapore", 6000, 9000, SalaryCurrency.SGD, SalaryPeriod.MONTH,
                "Build quantum financial systems with Cobol.", "[\"3 years experience\"]",
                "[\"Cobol\"]", null, Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1,
                now, now));
        if (withResume) {
            resumes.save(new ResumeEntity(UUID.randomUUID().toString(), candidate.getId(),
                    "Candidate", 27, "Singapore", "Quantum Cobol Engineer",
                    "Built Cobol financial systems.", "[]", "[\"Cobol\"]", 1,
                    now, now));
        }
        return new Fixture(candidate, recruiter, jobId);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwt.createAccessToken(user);
    }

    private record Fixture(UserEntity candidate, UserEntity recruiter, String jobId) {}
}
