package com.adproject.job.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.job.api.CreateJobRequest;
import com.adproject.job.api.ChangeJobStatusRequest;
import com.adproject.job.api.JobResponses.Company;
import com.adproject.job.api.JobResponses.JobListResponse;
import com.adproject.job.api.JobResponses.JobResponse;
import com.adproject.job.api.JobResponses.PageMeta;
import com.adproject.job.api.JobResponses.RecruiterJobDetail;
import com.adproject.job.api.JobResponses.Salary;
import com.adproject.job.api.JobResponses.User;
import com.adproject.job.api.PublishJobRequest;
import com.adproject.job.api.UpdateJobRequest;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobAuditEventEntity;
import com.adproject.job.infrastructure.JobAuditEventRepository;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JobRepository jobRepository;
    private final JobAuditEventRepository auditRepository;
    private final CompanyMemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JobService(JobRepository jobRepository, JobAuditEventRepository auditRepository,
                      CompanyMemberRepository memberRepository,
                      CompanyRepository companyRepository, UserRepository userRepository,
                      ObjectMapper objectMapper, Clock clock) {
        this.jobRepository = jobRepository;
        this.auditRepository = auditRepository;
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public JobResponse create(AuthenticatedUser currentUser, CreateJobRequest request) {
        requireRecruiter(currentUser);
        Scope scope = requireScope(currentUser.userId());
        if (scope.company().getVerificationStatus() != CompanyVerificationStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only recruiters from an approved company can create jobs");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.salary().max() < request.salary().min()) {
            errors.put("salary.max", "must be greater than or equal to salary.min");
        }
        Instant deadline = request.deadline() == null
                ? null : DatabaseTimePrecision.micros(Instant.parse(request.deadline()));
        validateDeadline(deadline, now(), errors);
        validateSkills(request.skills(), errors);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed", errors);
        }
        Instant now = now();
        JobEntity entity = new JobEntity(
                UUID.randomUUID().toString(), scope.company().getId(), currentUser.userId(), currentUser.userId(),
                request.title().trim(), request.employmentType(), request.workplaceType(), request.location().trim(),
                request.salary().min(), request.salary().max(), request.salary().currency(), request.salary().period(),
                request.description(), writeList(normalizeList(request.requirements())),
                writeList(normalizeList(request.skills())), deadline, request.visibility(),
                JobStatus.DRAFT, 0, 1, now, now);
        return new JobResponse(toDetail(jobRepository.saveAndFlush(entity), scope.company()));
    }

    @Transactional(readOnly = true)
    public JobListResponse list(AuthenticatedUser currentUser, String q, JobStatus status,
                                EmploymentType employmentType, String location, String ownerId,
                                int page, int pageSize) {
        requireRecruiter(currentUser);
        Scope scope = requireScope(currentUser.userId());
        Specification<JobEntity> specification = (root, query, builder) ->
                builder.equal(root.get("companyId"), scope.company().getId());
        if (q != null && !q.isBlank()) {
            String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("title")), value));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (employmentType != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("employmentType"), employmentType));
        }
        if (location != null && !location.isBlank()) {
            String value = "%" + location.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("location")), value));
        }
        if (ownerId != null && !ownerId.isBlank()) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("ownerId"), ownerId));
        }
        var result = jobRepository.findAll(specification, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<RecruiterJobDetail> items = result.getContent().stream()
                .map(job -> toDetail(job, scope.company())).toList();
        return new JobListResponse(items, new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public long activeJobCount(String companyId) {
        return jobRepository.countByCompanyIdAndStatus(companyId, JobStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<RecruiterJobDetail> recentJobs(String companyId, int limit) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
        return jobRepository.findByCompanyId(companyId,
                        PageRequest.of(0, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .stream().map(job -> toDetail(job, company)).toList();
    }

    @Transactional(readOnly = true)
    public JobResponse get(AuthenticatedUser currentUser, String jobId) {
        requireRecruiter(currentUser);
        Scope scope = requireScope(currentUser.userId());
        JobEntity job = jobRepository.findById(jobId)
                .filter(found -> found.getCompanyId().equals(scope.company().getId()))
                .orElseThrow(JobService::notFound);
        return new JobResponse(toDetail(job, scope.company()));
    }

    @Transactional
    public JobResponse update(AuthenticatedUser currentUser, String jobId, UpdateJobRequest request) {
        requireRecruiter(currentUser);
        Scope scope = requireScope(currentUser.userId());
        if (scope.company().getVerificationStatus() != CompanyVerificationStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only recruiters from an approved company can edit jobs");
        }
        JobEntity job = jobRepository.findOwnJobForUpdate(jobId, scope.company().getId())
                .orElseThrow(JobService::notFound);
        if (job.getVersion() != request.getExpectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "The job has changed; reload it before editing");
        }
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_JOB_TRANSITION",
                    "Only draft jobs can be edited");
        }
        Instant effectiveDeadline = request.isDeadlinePresent()
                ? request.getDeadline() == null ? null : DatabaseTimePrecision.micros(Instant.parse(request.getDeadline()))
                : job.getDeadline();
        Map<String, String> fieldErrors = validateUpdate(request, effectiveDeadline);
        if (!fieldErrors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed", fieldErrors);
        }
        var salary = request.getSalary();
        job.updateDetails(
                request.getTitle() == null ? job.getTitle() : request.getTitle().trim(),
                request.getEmploymentType() == null ? job.getEmploymentType() : request.getEmploymentType(),
                request.getWorkplaceType() == null ? job.getWorkplaceType() : request.getWorkplaceType(),
                request.getLocation() == null ? job.getLocation() : request.getLocation().trim(),
                salary == null ? job.getSalaryMin() : salary.min(),
                salary == null ? job.getSalaryMax() : salary.max(),
                salary == null ? job.getSalaryCurrency() : salary.currency(),
                salary == null ? job.getSalaryPeriod() : salary.period(),
                request.getDescription() == null ? job.getDescription() : request.getDescription(),
                request.getRequirements() == null ? job.getRequirementsJson() : writeList(normalizeList(request.getRequirements())),
                request.getSkills() == null ? job.getSkillsJson() : writeList(normalizeList(request.getSkills())),
                effectiveDeadline,
                request.getVisibility() == null ? job.getVisibility() : request.getVisibility(),
                now());
        jobRepository.flush();
        return new JobResponse(toDetail(job, scope.company()));
    }

    private Map<String, String> validateUpdate(UpdateJobRequest request, Instant effectiveDeadline) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        if (request.getTitle() != null && request.getTitle().isBlank()) errors.put("title", "must not be blank");
        if (request.getLocation() != null && request.getLocation().isBlank()) {
            errors.put("location", "must not be blank");
        }
        if (request.getDescription() != null && request.getDescription().isBlank()) {
            errors.put("description", "must not be blank");
        }
        if (request.getSalary() != null && request.getSalary().max() < request.getSalary().min()) {
            errors.put("salary.max", "must be greater than or equal to salary.min");
        }
        validateDeadline(effectiveDeadline, now(), errors);
        if (request.getSkills() != null) validateSkills(request.getSkills(), errors);
        return errors;
    }

    @Transactional
    public JobResponse publish(AuthenticatedUser currentUser, String jobId, PublishJobRequest request,
                               String requestId) {
        requireRecruiter(currentUser);
        Scope scope = requireScope(currentUser.userId());
        JobEntity job = jobRepository.findOwnJobForUpdate(jobId, scope.company().getId())
                .orElseThrow(JobService::notFound);
        if (scope.company().getVerificationStatus() != CompanyVerificationStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only recruiters from an approved company can publish jobs");
        }
        if (job.getVersion() != request.expectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "The job has changed; reload it before publishing");
        }
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_JOB_TRANSITION",
                    "Only a draft job can be published");
        }
        Instant now = now();
        if (job.getDeadline() != null && !job.getDeadline().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "JOB_DEADLINE_EXPIRED",
                    "The application deadline must be in the future before publishing");
        }
        job.publish(now);
        auditRepository.save(new JobAuditEventEntity(UUID.randomUUID().toString(), job.getId(),
                currentUser.userId(), scope.company().getId(), "JOB_PUBLISHED", JobStatus.DRAFT,
                JobStatus.ACTIVE, now, "Job published", requestId));
        jobRepository.flush();
        return new JobResponse(toDetail(job, scope.company()));
    }

    @Transactional
    public JobResponse changeStatus(AuthenticatedUser currentUser, String jobId, ChangeJobStatusRequest request,
                                    String requestId) {
        requireRecruiter(currentUser);
        Scope scope = requireScope(currentUser.userId());
        JobEntity job = jobRepository.findOwnJobForUpdate(jobId, scope.company().getId())
                .orElseThrow(JobService::notFound);
        if (job.getVersion() != request.expectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "The job has changed; reload it before changing its status");
        }
        JobStatus fromStatus = job.getStatus();
        JobStatus toStatus = JobStatus.valueOf(request.status().name());
        if (!isAllowedStatusTransition(fromStatus, toStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_JOB_TRANSITION",
                    "The requested job status transition is not allowed");
        }
        if (toStatus == JobStatus.ACTIVE
                && scope.company().getVerificationStatus() != CompanyVerificationStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only recruiters from an approved company can resume jobs");
        }
        Instant now = now();
        String reason = request.reason().trim();
        job.changeStatus(toStatus, now);
        auditRepository.save(new JobAuditEventEntity(UUID.randomUUID().toString(), job.getId(),
                currentUser.userId(), scope.company().getId(), "JOB_STATUS_CHANGED", fromStatus,
                toStatus, now, reason, requestId));
        jobRepository.flush();
        return new JobResponse(toDetail(job, scope.company()));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void validateDeadline(Instant deadline, Instant now, Map<String, String> errors) {
        if (deadline != null && !deadline.isAfter(now)) errors.put("deadline", "must be in the future");
    }

    private static void validateSkills(List<String> skills, Map<String, String> errors) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String skill : skills) {
            if (!normalized.add(skill.trim().toLowerCase(Locale.ROOT))) {
                errors.put("skills", "must not contain duplicates");
                return;
            }
        }
    }

    private static List<String> normalizeList(List<String> values) {
        return values.stream().map(String::trim).toList();
    }

    private static boolean isAllowedStatusTransition(JobStatus fromStatus, JobStatus toStatus) {
        return switch (fromStatus) {
            case ACTIVE -> toStatus == JobStatus.PAUSED || toStatus == JobStatus.CLOSED;
            case PAUSED -> toStatus == JobStatus.ACTIVE || toStatus == JobStatus.CLOSED;
            case DRAFT, CLOSED -> false;
        };
    }

    private Scope requireScope(String userId) {
        var member = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
        CompanyEntity company = companyRepository.findById(member.getCompanyId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
        return new Scope(company);
    }

    private static void requireRecruiter(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private RecruiterJobDetail toDetail(JobEntity job, CompanyEntity companyEntity) {
        User owner = job.getOwnerId() == null ? null : userRepository.findById(job.getOwnerId())
                .map(JobService::toUser).orElse(null);
        return new RecruiterJobDetail(job.getId(), job.getTitle(), toCompany(companyEntity),
                job.getEmploymentType().name(), job.getWorkplaceType().name(), job.getLocation(),
                new Salary(job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency().name(),
                        job.getSalaryPeriod().name()), job.getDescription(), readList(job.getRequirementsJson()),
                readList(job.getSkillsJson()), job.getDeadline(), job.getVisibility().name(), job.getStatus().name(),
                job.getPublishedAt(), job.getVersion(), job.getCreatedAt(), job.getUpdatedAt(),
                job.getApplicantCount(), owner);
    }

    private static Company toCompany(CompanyEntity company) {
        return new Company(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus().name(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private static User toUser(UserEntity user) {
        return new User(user.getId(), user.getRole().name(), user.getFullName(), user.getEmail(), user.getAvatarUrl(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize job list field", exception);
        }
    }

    private List<String> readList(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored job list field is invalid", exception);
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Job not found");
    }

    private record Scope(CompanyEntity company) {}
}
