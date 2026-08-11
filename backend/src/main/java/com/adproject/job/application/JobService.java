package com.adproject.job.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.job.api.CreateJobRequest;
import com.adproject.job.api.JobResponses.Company;
import com.adproject.job.api.JobResponses.JobListResponse;
import com.adproject.job.api.JobResponses.JobResponse;
import com.adproject.job.api.JobResponses.PageMeta;
import com.adproject.job.api.JobResponses.RecruiterJobDetail;
import com.adproject.job.api.JobResponses.Salary;
import com.adproject.job.api.JobResponses.User;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
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
    private final CompanyMemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JobService(JobRepository jobRepository, CompanyMemberRepository memberRepository,
                      CompanyRepository companyRepository, UserRepository userRepository,
                      ObjectMapper objectMapper, Clock clock) {
        this.jobRepository = jobRepository;
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
        if (request.salary().max() < request.salary().min()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed", Map.of("salary.max", "must be greater than or equal to salary.min"));
        }
        Instant now = clock.instant();
        JobEntity entity = new JobEntity(
                UUID.randomUUID().toString(), scope.company().getId(), currentUser.userId(), currentUser.userId(),
                request.title().trim(), request.employmentType(), request.workplaceType(), request.location().trim(),
                request.salary().min(), request.salary().max(), request.salary().currency(), request.salary().period(),
                request.description(), writeList(request.requirements()), writeList(request.skills()),
                request.deadline() == null ? null : Instant.parse(request.deadline()), request.visibility(),
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
    public JobResponse get(AuthenticatedUser currentUser, String jobId) {
        requireRecruiter(currentUser);
        Scope scope = requireScope(currentUser.userId());
        JobEntity job = jobRepository.findById(jobId)
                .filter(found -> found.getCompanyId().equals(scope.company().getId()))
                .orElseThrow(JobService::notFound);
        return new JobResponse(toDetail(job, scope.company()));
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
