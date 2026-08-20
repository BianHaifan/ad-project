package com.adproject.job.application;

import com.adproject.application.application.CandidateApplicationStateService;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.job.api.CandidateJobResponses.CandidateJobDetail;
import com.adproject.job.api.CandidateJobResponses.CandidateJobDetailResponse;
import com.adproject.job.api.CandidateJobResponses.CandidateJobListResponse;
import com.adproject.job.api.CandidateJobResponses.CandidateJobSummary;
import com.adproject.job.api.CandidateJobResponses.Company;
import com.adproject.job.api.CandidateJobResponses.PageMeta;
import com.adproject.job.api.CandidateJobResponses.Salary;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.Visibility;
import com.adproject.job.infrastructure.CandidateSavedJobEntity;
import com.adproject.job.infrastructure.CandidateSavedJobRepository;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.recommendation.api.RecommendationDtos.MatchAnalysis;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceRepository;
import com.adproject.recommendation.infrastructure.CandidateJobRecommendationEntity;
import com.adproject.recommendation.infrastructure.CandidateJobRecommendationRepository;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateJobQueryService {
    private static final Logger log = LoggerFactory.getLogger(CandidateJobQueryService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;
    private final CandidateApplicationStateService applicationStateService;
    private final CandidateJobRecommendationRepository recommendations;
    private final CandidateJobPreferenceRepository preferences;
    private final ResumeRepository resumes;
    private final RecruiterContactResolver recruiterResolver;
    private final CandidateSavedJobRepository savedJobs;
    private final Clock clock;

    public CandidateJobQueryService(JobRepository jobRepository, CompanyRepository companyRepository,
                                    ObjectMapper objectMapper,
                                    CandidateApplicationStateService applicationStateService,
                                    CandidateJobRecommendationRepository recommendations,
                                    CandidateJobPreferenceRepository preferences,
                                    ResumeRepository resumes,
                                    RecruiterContactResolver recruiterResolver,
                                    CandidateSavedJobRepository savedJobs,
                                    Clock clock) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.objectMapper = objectMapper;
        this.applicationStateService = applicationStateService;
        this.recommendations = recommendations;
        this.preferences = preferences;
        this.resumes = resumes;
        this.recruiterResolver = recruiterResolver;
        this.savedJobs = savedJobs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CandidateJobListResponse list(AuthenticatedUser currentUser, String q,
                                         EmploymentType employmentType, int page, int pageSize) {
        requireCandidate(currentUser);
        Specification<JobEntity> specification = visibleToCandidate();
        if (q != null && !q.isBlank()) {
            String titleQuery = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("title")), titleQuery));
        }
        if (employmentType != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("employmentType"), employmentType));
        }
        Sort sort = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
        var result = jobRepository.findAll(specification, PageRequest.of(page - 1, pageSize, sort));
        Map<String, CompanyEntity> companies = companyRepository
                .findAllById(result.getContent().stream().map(JobEntity::getCompanyId).distinct().toList())
                .stream().collect(Collectors.toMap(CompanyEntity::getId, Function.identity()));
        CurrentVersions versions = currentVersions(currentUser.userId());
        Map<String, CandidateJobRecommendationEntity> matches =
                matchMap(currentUser.userId(), result.getContent(), versions);
        Set<String> savedIds = savedJobs.findByCandidateIdAndJobIdIn(currentUser.userId(),
                        result.getContent().stream().map(JobEntity::getId).toList())
                .stream().map(CandidateSavedJobEntity::getJobId)
                .collect(Collectors.toSet());
        List<CandidateJobSummary> data = result.getContent().stream()
                .map(job -> toSummary(job, requireCompany(companies, job.getCompanyId()),
                        matches.get(job.getId()), savedIds.contains(job.getId())))
                .toList();
        return new CandidateJobListResponse(data,
                new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public CandidateJobDetailResponse get(AuthenticatedUser currentUser, String jobId) {
        requireCandidate(currentUser);
        JobEntity job = jobRepository.findById(jobId)
                .filter(found -> found.getStatus() == JobStatus.ACTIVE)
                .filter(found -> found.getVisibility() == Visibility.PUBLIC)
                .orElseThrow(CandidateJobQueryService::notFound);
        CompanyEntity company = companyRepository.findById(job.getCompanyId())
                .orElseThrow(CandidateJobQueryService::notFound);
        CurrentVersions versions = currentVersions(currentUser.userId());
        CandidateJobRecommendationEntity recommendation = recommendations
                .findByCandidateIdAndJobId(currentUser.userId(), job.getId())
                .filter(value -> isCurrent(value, versions, job)).orElse(null);
        boolean saved = savedJobs.findByCandidateIdAndJobId(currentUser.userId(), job.getId()).isPresent();
        CandidateJobSummary summary = toSummary(job, company, recommendation, saved);
        String applicationState = applicationStateService.state(currentUser.userId(), job.getId());
        return new CandidateJobDetailResponse(new CandidateJobDetail(
                summary.jobId(), summary.title(), summary.company(), summary.employmentType(),
                summary.workplaceType(), summary.location(), summary.salary(), summary.description(),
                summary.requirements(), summary.skills(), summary.deadline(), summary.visibility(),
                summary.status(), summary.publishedAt(), summary.version(), summary.createdAt(),
                summary.updatedAt(), summary.matchScore(), recruiterResolver.resolve(job),
                recommendation == null ? null : analysis(recommendation), applicationState, saved));
    }

    @Transactional(readOnly = true)
    public CandidateJobListResponse savedJobs(AuthenticatedUser currentUser, int page, int pageSize) {
        requireCandidate(currentUser);
        List<CandidateSavedJobEntity> saved = savedJobs
                .findByCandidateIdOrderByCreatedAtDesc(currentUser.userId());
        Map<String, JobEntity> jobsById = jobRepository.findAllById(
                        saved.stream().map(CandidateSavedJobEntity::getJobId).distinct().toList())
                .stream().collect(Collectors.toMap(JobEntity::getId, Function.identity()));
        List<JobEntity> browsable = saved.stream()
                .map(CandidateSavedJobEntity::getJobId)
                .map(jobsById::get)
                .filter(job -> job.getStatus() == JobStatus.ACTIVE && job.getVisibility() == Visibility.PUBLIC)
                .toList();
        Map<String, CompanyEntity> companies = companyRepository
                .findAllById(browsable.stream().map(JobEntity::getCompanyId).distinct().toList())
                .stream().collect(Collectors.toMap(CompanyEntity::getId, Function.identity()));
        CurrentVersions versions = currentVersions(currentUser.userId());
        Map<String, CandidateJobRecommendationEntity> matches =
                matchMap(currentUser.userId(), browsable, versions);
        int total = browsable.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<CandidateJobSummary> data = browsable.subList(from, to).stream()
                .map(job -> toSummary(job, requireCompany(companies, job.getCompanyId()),
                        matches.get(job.getId()), true))
                .toList();
        return new CandidateJobListResponse(data,
                new PageMeta(page, pageSize, total, from + pageSize < total));
    }

    @Transactional
    public void save(AuthenticatedUser currentUser, String jobId) {
        requireCandidate(currentUser);
        jobRepository.findById(jobId)
                .filter(found -> found.getStatus() == JobStatus.ACTIVE)
                .filter(found -> found.getVisibility() == Visibility.PUBLIC)
                .orElseThrow(CandidateJobQueryService::notFound);
        if (savedJobs.findByCandidateIdAndJobId(currentUser.userId(), jobId).isPresent()) {
            return;
        }
        savedJobs.save(new CandidateSavedJobEntity(UUID.randomUUID().toString(),
                currentUser.userId(), jobId, clock.instant()));
    }

    @Transactional
    public void unsave(AuthenticatedUser currentUser, String jobId) {
        requireCandidate(currentUser);
        savedJobs.deleteByCandidateIdAndJobId(currentUser.userId(), jobId);
    }

    private Map<String, CandidateJobRecommendationEntity> matchMap(String candidateId,
                                                                   List<JobEntity> jobs,
                                                                   CurrentVersions versions) {
        return recommendations.findByCandidateIdAndJobIdIn(candidateId,
                        jobs.stream().map(JobEntity::getId).toList())
                .stream().filter(value -> isCurrent(value, versions,
                        jobs.stream().filter(job -> job.getId().equals(value.getJobId()))
                                .findFirst().orElseThrow()))
                .collect(Collectors.toMap(CandidateJobRecommendationEntity::getJobId, Function.identity()));
    }

    private static Specification<JobEntity> visibleToCandidate() {
        return (root, query, builder) -> builder.and(
                builder.equal(root.get("status"), JobStatus.ACTIVE),
                builder.equal(root.get("visibility"), Visibility.PUBLIC));
    }

    private CandidateJobSummary toSummary(JobEntity job, CompanyEntity company,
                                          CandidateJobRecommendationEntity recommendation,
                                          boolean saved) {
        return new CandidateJobSummary(job.getId(), job.getTitle(), toCompany(company),
                job.getEmploymentType().name(), job.getWorkplaceType().name(), job.getLocation(),
                new Salary(job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency().name(),
                        job.getSalaryPeriod().name()), job.getDescription(), readList(job.getRequirementsJson()),
                readList(job.getSkillsJson()), job.getDeadline(), job.getVisibility().name(), job.getStatus().name(),
                job.getPublishedAt(), job.getVersion(), job.getCreatedAt(), job.getUpdatedAt(),
                recommendation == null ? null : recommendation.getScore(), recruiterResolver.resolve(job), saved);
    }

    private CurrentVersions currentVersions(String candidateId) {
        int resumeVersion = resumes.findByCandidateId(candidateId)
                .map(value -> value.getVersion()).orElse(-1);
        int preferenceVersion = preferences.findById(candidateId)
                .map(value -> value.getVersion()).orElse(0);
        return new CurrentVersions(resumeVersion, preferenceVersion);
    }

    private static boolean isCurrent(CandidateJobRecommendationEntity value,
                                     CurrentVersions versions, JobEntity job) {
        return value.getResumeVersion() == versions.resumeVersion()
                && value.getPreferenceVersion() == versions.preferenceVersion()
                && value.getJobVersion() == job.getVersion();
    }

    private MatchAnalysis analysis(CandidateJobRecommendationEntity value) {
        return new MatchAnalysis(readList(value.getStrongMatchesJson()),
                readList(value.getGapsJson()), readList(value.getEvidenceJson()));
    }

    private record CurrentVersions(int resumeVersion, int preferenceVersion) {}

    private static Company toCompany(CompanyEntity company) {
        return new Company(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus().name(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(value, STRING_LIST);
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException exception) {
            log.warn("Stored job list field is not a valid JSON string array; treating as empty", exception);
            return List.of();
        }
    }

    private static CompanyEntity requireCompany(Map<String, CompanyEntity> companies, String companyId) {
        CompanyEntity company = companies.get(companyId);
        if (company == null) throw notFound();
        return company;
    }

    private static void requireCandidate(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Job not found");
    }
}
