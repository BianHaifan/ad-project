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
import com.adproject.job.api.CandidateJobResponses.RecruiterContact;
import com.adproject.job.api.CandidateJobResponses.Salary;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.Visibility;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.profile.infrastructure.RecruiterProfileEntity;
import com.adproject.profile.infrastructure.RecruiterProfileRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final UserRepository userRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;

    public CandidateJobQueryService(JobRepository jobRepository, CompanyRepository companyRepository,
                                    ObjectMapper objectMapper,
                                    CandidateApplicationStateService applicationStateService,
                                    UserRepository userRepository,
                                    RecruiterProfileRepository recruiterProfileRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.objectMapper = objectMapper;
        this.applicationStateService = applicationStateService;
        this.userRepository = userRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
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
        List<CandidateJobSummary> data = result.getContent().stream()
                .map(job -> toSummary(job, requireCompany(companies, job.getCompanyId())))
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
        CandidateJobSummary summary = toSummary(job, company);
        String applicationState = applicationStateService.state(currentUser.userId(), job.getId());
        return new CandidateJobDetailResponse(new CandidateJobDetail(
                summary.jobId(), summary.title(), summary.company(), summary.employmentType(),
                summary.workplaceType(), summary.location(), summary.salary(), summary.description(),
                summary.requirements(), summary.skills(), summary.deadline(), summary.visibility(),
                summary.status(), summary.publishedAt(), summary.version(), summary.createdAt(),
                summary.updatedAt(), null, toRecruiterContact(job), null, applicationState, false));
    }

    private static Specification<JobEntity> visibleToCandidate() {
        return (root, query, builder) -> builder.and(
                builder.equal(root.get("status"), JobStatus.ACTIVE),
                builder.equal(root.get("visibility"), Visibility.PUBLIC));
    }

    private CandidateJobSummary toSummary(JobEntity job, CompanyEntity company) {
        return new CandidateJobSummary(job.getId(), job.getTitle(), toCompany(company),
                job.getEmploymentType().name(), job.getWorkplaceType().name(), job.getLocation(),
                new Salary(job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency().name(),
                        job.getSalaryPeriod().name()), job.getDescription(), readList(job.getRequirementsJson()),
                readList(job.getSkillsJson()), job.getDeadline(), job.getVisibility().name(), job.getStatus().name(),
                job.getPublishedAt(), job.getVersion(), job.getCreatedAt(), job.getUpdatedAt(), null, null);
    }

    private static Company toCompany(CompanyEntity company) {
        return new Company(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus().name(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private RecruiterContact toRecruiterContact(JobEntity job) {
        String recruiterId = job.getOwnerId() != null ? job.getOwnerId() : job.getCreatedBy();
        UserEntity recruiter = userRepository.findById(recruiterId)
                .filter(user -> user.getRole() == UserRole.RECRUITER)
                .orElse(null);
        if (recruiter == null) {
            return null;
        }
        RecruiterProfileEntity profile = recruiterProfileRepository.findById(recruiterId).orElse(null);
        return new RecruiterContact(recruiter.getId(), recruiter.getFullName(),
                profile == null ? "" : profile.getTitle(), recruiter.getAvatarUrl());
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
