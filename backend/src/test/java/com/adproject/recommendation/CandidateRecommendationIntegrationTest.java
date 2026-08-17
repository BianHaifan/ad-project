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
                        .queryParam("pageSize", "5")
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.source").value("FALLBACK"))
                .andExpect(jsonPath("$.meta.modelStatus").value("DEGRADED"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(5))
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

    @Test
    void paginationReturnsPageMetaAndSlicesResults() throws Exception {
        String token = token("pag");
        List<JobSpec> specs = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            specs.add(new JobSpec(token + " Engineer " + i, EmploymentType.FULL_TIME));
        }
        MultiFixture fixture = fixtureMany(true, specs);

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token)
                        .queryParam("page", "1").queryParam("pageSize", "5")
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(5))
                .andExpect(jsonPath("$.meta.total").value(15))
                .andExpect(jsonPath("$.meta.hasNext").value(true))
                .andExpect(jsonPath("$.data.length()").value(5));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token)
                        .queryParam("page", "3").queryParam("pageSize", "5")
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(3))
                .andExpect(jsonPath("$.meta.total").value(15))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void tailPageBeyondTotalReturnsEmpty() throws Exception {
        String token = token("tail");
        MultiFixture fixture = fixtureMany(true, List.of(
                new JobSpec(token + " A", EmploymentType.FULL_TIME),
                new JobSpec(token + " B", EmploymentType.FULL_TIME)));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token)
                        .queryParam("page", "5").queryParam("pageSize", "10")
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(5))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void hugePageDoesNotOverflowAndReturnsEmptyPage() throws Exception {
        String token = token("huge");
        MultiFixture fixture = fixtureMany(true, List.of(
                new JobSpec(token + " Engineer", EmploymentType.FULL_TIME)));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token)
                        .queryParam("page", "2147483647").queryParam("pageSize", "20")
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(2147483647))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void employmentTypeFilterNarrowsResults() throws Exception {
        String token = token("type");
        MultiFixture fixture = fixtureMany(true, List.of(
                new JobSpec(token + " Full A", EmploymentType.FULL_TIME),
                new JobSpec(token + " Full B", EmploymentType.FULL_TIME),
                new JobSpec(token + " Intern A", EmploymentType.INTERNSHIP),
                new JobSpec(token + " Part A", EmploymentType.PART_TIME)));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token)
                        .queryParam("employmentType", "INTERNSHIP")
                        .queryParam("page", "1").queryParam("pageSize", "10")
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].employmentType").value("INTERNSHIP"));
    }

    @Test
    void keywordSearchFiltersByTitle() throws Exception {
        String token = token("kw");
        MultiFixture fixture = fixtureMany(true, List.of(
                new JobSpec(token + " Engineer", EmploymentType.FULL_TIME),
                new JobSpec(token + " Designer", EmploymentType.FULL_TIME),
                new JobSpec("Unrelated Backend Job", EmploymentType.FULL_TIME)));

        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs")
                        .queryParam("q", token)
                        .queryParam("page", "1").queryParam("pageSize", "10")
                        .header("Authorization", bearer(fixture.candidate())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].jobId").value(hasItem(fixture.jobIds().get(0))))
                .andExpect(jsonPath("$.data[*].jobId").value(hasItem(fixture.jobIds().get(1))));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/candidate/recommendations/jobs"))
                .andExpect(status().isUnauthorized());
    }

    private static String token(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
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

    private MultiFixture fixtureMany(boolean withResume, List<JobSpec> specs) {
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
        List<String> jobIds = new java.util.ArrayList<>();
        for (JobSpec spec : specs) {
            String jobId = UUID.randomUUID().toString();
            jobs.save(new JobEntity(jobId, company.getId(), recruiter.getId(), recruiter.getId(),
                    spec.title(), spec.type(), WorkplaceType.HYBRID, "Singapore", 6000, 9000,
                    SalaryCurrency.SGD, SalaryPeriod.MONTH, "Generic job description.",
                    "[]", "[\"Cobol\"]", null, Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1,
                    now, now));
            jobIds.add(jobId);
        }
        if (withResume) {
            resumes.save(new ResumeEntity(UUID.randomUUID().toString(), candidate.getId(),
                    "Candidate", 27, "Singapore", "Engineer",
                    "Built Cobol systems.", "[]", "[\"Cobol\"]", 1,
                    now, now));
        }
        return new MultiFixture(candidate, recruiter, jobIds);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwt.createAccessToken(user);
    }

    private record Fixture(UserEntity candidate, UserEntity recruiter, String jobId) {}
    private record JobSpec(String title, EmploymentType type) {}
    private record MultiFixture(UserEntity candidate, UserEntity recruiter, List<String> jobIds) {}
}
