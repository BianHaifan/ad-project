package com.adproject.application.application;

import com.adproject.application.api.RecruiterApplicantRecommendationDtos.ApplicantCandidateSummary;
import com.adproject.application.api.RecruiterApplicantRecommendationDtos.RecommendedApplicant;
import com.adproject.application.api.RecruiterApplicantRecommendationDtos.RecommendedApplicantResponse;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.application.infrastructure.ResumeSnapshotEntity;
import com.adproject.application.infrastructure.ResumeSnapshotRepository;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.profile.infrastructure.CandidateProfileRepository;
import com.adproject.recommendation.api.RecommendationDtos.RecommendationMeta;
import com.adproject.recommendation.application.MlRecommendationClient.MlCandidate;
import com.adproject.recommendation.application.MlRecommendationClient.MlJob;
import com.adproject.recommendation.application.MlRecommendationClient.MlPreferences;
import com.adproject.recommendation.application.MlRecommendationClient.MlSalary;
import com.adproject.recommendation.application.RecruiterApplicantRankingService;
import com.adproject.recommendation.application.RecruiterApplicantRankingService.Result;
import com.adproject.recommendation.application.RecruiterApplicantRankingService.ScoredApplicant;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the recruiter-facing applicant ranking: verifies recruiter + job ownership, loads the
 * eligible applications and their frozen resume snapshots, builds clean ML input records, and
 * delegates ranking to the recommendation module. It never exposes non-applicant candidates and
 * never touches the Python HTTP contract directly.
 */
@Service
public class RecruiterApplicantRecommendationService {
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final Pattern REQUIRED_YEARS = Pattern.compile("(?i)\\b(\\d{1,2})\\+?\\s+years?\\b");
    private static final List<ApplicationStatus> ELIGIBLE = List.of(
            ApplicationStatus.APPLIED, ApplicationStatus.IN_REVIEW, ApplicationStatus.INTERVIEW);

    private final ApplicationRepository applications;
    private final ResumeSnapshotRepository snapshots;
    private final JobRepository jobs;
    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final CompanyMemberRepository members;
    private final RecruiterApplicantRankingService ranking;
    private final ObjectMapper mapper;
    private final Clock clock;

    public RecruiterApplicantRecommendationService(ApplicationRepository applications,
                                                   ResumeSnapshotRepository snapshots,
                                                   JobRepository jobs, UserRepository users,
                                                   CandidateProfileRepository profiles,
                                                   CompanyMemberRepository members,
                                                   RecruiterApplicantRankingService ranking,
                                                   ObjectMapper mapper, Clock clock) {
        this.applications = applications;
        this.snapshots = snapshots;
        this.jobs = jobs;
        this.users = users;
        this.profiles = profiles;
        this.members = members;
        this.ranking = ranking;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecommendedApplicantResponse recommend(AuthenticatedUser principal, String jobId,
                                                  int page, int pageSize) {
        String companyId = requireCompany(principal);
        JobEntity job = jobs.findById(jobId)
                .filter(value -> value.getCompanyId().equals(companyId))
                .orElseThrow(this::notFound);

        List<ApplicationEntity> eligible = applications
                .findByJobIdAndStatusInOrderByAppliedAtAscIdAsc(jobId, ELIGIBLE);
        if (eligible.isEmpty()) {
            return new RecommendedApplicantResponse(List.of(), new RecommendationMeta(
                    "NONE", "none", "none", "NOT_APPLICABLE", 0, clock.instant(),
                    page, pageSize, 0, false));
        }
        if (eligible.size() > RecruiterApplicantRankingService.MAX_CANDIDATES) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RECOMMENDATION_INPUT_LIMIT",
                    "Too many eligible applicants for AI ranking (limit "
                            + RecruiterApplicantRankingService.MAX_CANDIDATES + ")");
        }

        MlJob mlJob = toMlJob(job);
        Map<String, ApplicationEntity> byCandidateId = new HashMap<>();
        List<MlCandidate> candidates = new ArrayList<>(eligible.size());
        for (ApplicationEntity application : eligible) {
            ResumeSnapshotEntity snapshot = snapshots.findById(application.getResumeSnapshotId())
                    .orElseThrow(this::notFound);
            byCandidateId.put(application.getCandidateId(), application);
            candidates.add(toMlCandidate(application.getCandidateId(), snapshot));
        }

        Result result = ranking.rankCandidates(mlJob, candidates);

        List<ScoredApplicant> ranked = result.values();
        int total = ranked.size();
        long offset = (long) (page - 1) * pageSize;
        List<RecommendedApplicant> data = new ArrayList<>();
        if (offset < total) {
            int start = (int) offset;
            int toIndex = Math.min(start + pageSize, total);
            for (int index = start; index < toIndex; index++) {
                ScoredApplicant value = ranked.get(index);
                ApplicationEntity application = byCandidateId.get(value.candidate().entityId());
                if (application == null) continue;
                data.add(toDto(application, value, index + 1));
            }
        }
        boolean hasNext = offset + pageSize < total;
        return new RecommendedApplicantResponse(data, new RecommendationMeta(
                result.source(), result.modelVersion(), result.featureVersion(),
                result.source().equals("MODEL") ? "ACTIVE" : "DEGRADED",
                result.inferenceMs(), result.generatedAt(), page, pageSize, total, hasNext));
    }

    private MlJob toMlJob(JobEntity job) {
        List<String> requirements = readStrings(job.getRequirementsJson());
        return new MlJob(job.getId(), job.getTitle(), job.getDescription(), requirements,
                readStrings(job.getSkillsJson()), job.getLocation(), job.getWorkplaceType().name(),
                job.getEmploymentType().name(), new MlSalary(job.getSalaryMin(), job.getSalaryMax(),
                job.getSalaryCurrency().name(), job.getSalaryPeriod().name()),
                requiredYears(job.getDescription() + " " + String.join(" ", requirements)));
    }

    private MlCandidate toMlCandidate(String candidateId, ResumeSnapshotEntity snapshot) {
        List<String> locations = snapshot.getLocation() == null || snapshot.getLocation().isBlank()
                ? List.of() : List.of(snapshot.getLocation());
        return new MlCandidate(candidateId,
                snapshot.getSummary() + " " + snapshot.getExperiencesJson(), snapshot.getHeadline(),
                readStrings(snapshot.getSkillsJson()), null,
                new MlPreferences(List.of(), locations, List.of(), List.of(), null));
    }

    private RecommendedApplicant toDto(ApplicationEntity application, ScoredApplicant value, int rank) {
        return new RecommendedApplicant(application.getId(), candidateSummary(application),
                application.getStatus().name(), application.getAppliedAt(), value.score(), rank,
                value.analysis());
    }

    private ApplicantCandidateSummary candidateSummary(ApplicationEntity application) {
        UserEntity user = users.findById(application.getCandidateId()).orElseThrow(this::notFound);
        var profile = profiles.findById(user.getId()).orElse(null);
        return new ApplicantCandidateSummary(user.getId(), user.getFullName(),
                profile == null ? null : profile.getHeadline(), user.getAvatarUrl(),
                profile == null ? null : profile.getLocation());
    }

    private String requireCompany(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
        return members.findByUserId(principal.userId()).map(member -> member.getCompanyId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Job not found");
    }

    private List<String> readStrings(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(value, STRINGS);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored list field is invalid", exception);
        }
    }

    private static Double requiredYears(String text) {
        Matcher matcher = REQUIRED_YEARS.matcher(text);
        int maximum = -1;
        while (matcher.find()) maximum = Math.max(maximum, Integer.parseInt(matcher.group(1)));
        return maximum < 0 ? null : (double) maximum;
    }
}
