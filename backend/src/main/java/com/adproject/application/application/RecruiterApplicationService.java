package com.adproject.application.application;

import com.adproject.application.api.InterviewDtos;
import com.adproject.application.api.RecruiterApplicationDtos;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.*;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.profile.infrastructure.CandidateProfileRepository;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceEntity;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceRepository;
import com.adproject.recommendation.infrastructure.CandidateJobRecommendationRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruiterApplicationService {
    private static final Logger log = LoggerFactory.getLogger(RecruiterApplicationService.class);
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final ApplicationRepository applications;
    private final ApplicationStatusEventRepository events;
    private final ResumeSnapshotRepository snapshots;
    private final InterviewRepository interviews;
    private final JobRepository jobs;
    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final CompanyMemberRepository members;
    private final CandidateApplicationResponseMapper mapper;
    private final CandidateJobRecommendationRepository recommendations;
    private final CandidateJobPreferenceRepository preferences;
    private final ResumeRepository resumes;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RecruiterApplicationService(ApplicationRepository applications,
                                       ApplicationStatusEventRepository events,
                                       ResumeSnapshotRepository snapshots, InterviewRepository interviews,
                                       JobRepository jobs,
                                       UserRepository users, CandidateProfileRepository profiles,
                                       CompanyMemberRepository members,
                                       CandidateApplicationResponseMapper mapper,
                                       CandidateJobRecommendationRepository recommendations,
                                       CandidateJobPreferenceRepository preferences,
                                       ResumeRepository resumes,
                                       ObjectMapper objectMapper, Clock clock) {
        this.applications = applications; this.events = events; this.snapshots = snapshots; this.interviews = interviews;
        this.jobs = jobs;
        this.users = users; this.profiles = profiles; this.members = members; this.mapper = mapper;
        this.recommendations = recommendations; this.preferences = preferences; this.resumes = resumes;
        this.objectMapper = objectMapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecruiterApplicationDtos.ListResponse list(AuthenticatedUser principal, ApplicationStatus status,
                                                       String jobId, String q, int page, int pageSize, String sort) {
        String companyId = requireCompany(principal);
        Specification<ApplicationEntity> specification = companyScope(companyId);
        if (status != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (jobId != null && !jobId.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("jobId"), jobId.trim()));
        }
        if (q != null && !q.isBlank()) {
            String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> {
                Subquery<String> candidates = query.subquery(String.class);
                var user = candidates.from(UserEntity.class);
                candidates.select(user.get("id")).where(cb.or(
                        cb.like(cb.lower(user.get("fullName")), value),
                        cb.like(cb.lower(user.get("email")), value)));
                return root.get("candidateId").in(candidates);
            });
        }
        var result = applications.findAll(specification, PageRequest.of(page - 1, pageSize, sort(sort)));
        var data = result.getContent().stream().map(this::summary).toList();
        return new RecruiterApplicationDtos.ListResponse(data, new RecruiterApplicationDtos.Meta(
                page, pageSize, result.getTotalElements(), result.hasNext(), counts(companyId)));
    }

    @Transactional(readOnly = true)
    public RecruiterApplicationDtos.Counts counts(String companyId) {
        return new RecruiterApplicationDtos.Counts(
                applications.countByCompanyIdAndStatus(companyId, ApplicationStatus.APPLIED),
                applications.countByCompanyIdAndStatus(companyId, ApplicationStatus.IN_REVIEW),
                applications.countByCompanyIdAndStatus(companyId, ApplicationStatus.INTERVIEW),
                applications.countByCompanyIdAndStatus(companyId, ApplicationStatus.OFFERED),
                applications.countByCompanyIdAndStatus(companyId, ApplicationStatus.REJECTED));
    }

    @Transactional(readOnly = true)
    public List<RecruiterApplicationDtos.Summary> recentSummaries(String companyId, int limit) {
        return applications.findAll(companyScope(companyId),
                        PageRequest.of(0, limit, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))))
                .getContent().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public RecruiterApplicationDtos.DetailResponse detail(AuthenticatedUser principal, String applicationId) {
        String companyId = requireCompany(principal);
        ApplicationEntity application = applications.findById(applicationId).orElseThrow(this::notFound);
        requireJob(application, companyId);
        return new RecruiterApplicationDtos.DetailResponse(detail(application));
    }

    @Transactional
    public RecruiterApplicationDtos.TransitionResponse transition(AuthenticatedUser principal, String applicationId,
            RecruiterApplicationDtos.TransitionRequest request, String requestId) {
        String companyId = requireCompany(principal);
        ApplicationEntity application = applications.findByIdForUpdate(applicationId).orElseThrow(this::notFound);
        requireJob(application, companyId);
        if (application.getVersion() != request.expectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The application has changed");
        }
        ApplicationStatus target = ApplicationStatus.valueOf(request.toStatus().name());
        if (!allowed(application.getStatus(), target)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPLICATION_TRANSITION",
                    "The requested application transition is not allowed");
        }
        ApplicationStatus before = application.getStatus();
        Instant now = DatabaseTimePrecision.micros(clock.instant());
        application.transitionTo(target, now);
        ApplicationStatusEventEntity event = events.save(new ApplicationStatusEventEntity(
                UUID.randomUUID().toString(), application.getId(), principal.userId(), companyId,
                before, target, now, request.reason().trim(), requestId));
        applications.flush();
        return new RecruiterApplicationDtos.TransitionResponse(
                new RecruiterApplicationDtos.TransitionResult(detail(application), audit(event)));
    }

    private Specification<ApplicationEntity> companyScope(String companyId) {
        return (root, query, cb) -> {
            Subquery<String> companyJobs = query.subquery(String.class);
            var job = companyJobs.from(JobEntity.class);
            companyJobs.select(job.get("id")).where(cb.equal(job.get("companyId"), companyId));
            return root.get("jobId").in(companyJobs);
        };
    }

    private Sort sort(String value) {
        if (value == null || value.isBlank() || "appliedAt,desc".equals(value)) {
            return Sort.by(Sort.Order.desc("appliedAt"), Sort.Order.desc("id"));
        }
        if ("appliedAt,asc".equals(value)) return Sort.by(Sort.Order.asc("appliedAt"), Sort.Order.asc("id"));
        if ("updatedAt,asc".equals(value)) return Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id"));
        if ("updatedAt,desc".equals(value)) return Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                Map.of("sort", "must be one of appliedAt,desc, appliedAt,asc, updatedAt,desc, updatedAt,asc"));
    }

    private RecruiterApplicationDtos.Summary summary(ApplicationEntity application) {
        JobEntity job = jobs.findById(application.getJobId()).orElseThrow(this::notFound);
        UserEntity candidate = users.findById(application.getCandidateId()).orElseThrow(this::notFound);
        var profile = profiles.findById(candidate.getId()).orElse(null);
        var candidateDto = new RecruiterApplicationDtos.CandidateSummary(candidate.getId(), candidate.getFullName(),
                candidate.getEmail(), profile == null ? null : profile.getHeadline(), candidate.getAvatarUrl(),
                profile == null ? null : profile.getLocation());
        RecruiterApplicationDtos.MatchAnalysis match = storedMatch(candidate.getId(), job);
        return new RecruiterApplicationDtos.Summary(application.getId(), application.getJobId(),
                application.getStatus().name(), application.getAppliedAt(), application.getUpdatedAt(),
                application.getVersion(), candidateDto, job.getTitle(), match == null ? null : match.score(), null);
    }

    private RecruiterApplicationDtos.Detail detail(ApplicationEntity application) {
        RecruiterApplicationDtos.Summary summary = summary(application);
        JobEntity job = jobs.findById(application.getJobId()).orElseThrow(this::notFound);
        var snapshot = snapshots.findById(application.getResumeSnapshotId()).orElseThrow(this::notFound);
        var timeline = events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId()).stream()
                .map(this::audit).toList();
        InterviewDtos.Interview interview = interviews.findByApplicationId(application.getId())
                .map(this::interviewDto).orElse(null);
        RecruiterApplicationDtos.MatchAnalysis match = storedMatch(summary.candidate().candidateId(), job);
        return new RecruiterApplicationDtos.Detail(summary.applicationId(), summary.jobId(), summary.status(),
                summary.appliedAt(), summary.updatedAt(), summary.version(), summary.candidate(), summary.jobTitle(),
                summary.matchScore(), null, mapper.resumeSnapshot(snapshot), timeline, match, interview, List.of());
    }

    /**
     * Reuses a persisted candidate&rarr;job recommendation snapshot when it is still valid for the candidate's
     * current resume, preference and job versions. Returns null when no snapshot exists or it is stale, in which
     * case the caller surfaces an empty score ("—") rather than a fabricated value.
     */
    private RecruiterApplicationDtos.MatchAnalysis storedMatch(String candidateId, JobEntity job) {
        int resumeVersion = resumes.findByCandidateId(candidateId)
                .map(ResumeEntity::getVersion).orElse(-1);
        int preferenceVersion = preferences.findById(candidateId)
                .map(CandidateJobPreferenceEntity::getVersion).orElse(0);
        return recommendations.findByCandidateIdAndJobId(candidateId, job.getId())
                .filter(value -> value.getResumeVersion() == resumeVersion
                        && value.getPreferenceVersion() == preferenceVersion
                        && value.getJobVersion() == job.getVersion())
                .map(value -> new RecruiterApplicationDtos.MatchAnalysis(value.getScore(),
                        readList(value.getEvidenceJson()), readList(value.getStrongMatchesJson()),
                        readList(value.getGapsJson()), value.getModelVersion(), value.getGeneratedAt()))
                .orElse(null);
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(value, STRINGS);
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException exception) {
            log.warn("Stored recommendation list field is not a valid JSON string array; treating as empty", exception);
            return List.of();
        }
    }

    private InterviewDtos.Interview interviewDto(InterviewEntity interview) {
        return new InterviewDtos.Interview(interview.getId(), interview.getApplicationId(),
                interview.getScheduledAt(), interview.getTimezone(), interview.getDurationMinutes(),
                interview.getMode().name(), interview.getLocationOrMeetingUrl(), interview.getNote(),
                interview.getStatus().name(), interview.getVersion(), interview.getCreatedAt(),
                interview.getUpdatedAt(), interview.getMeetingProvider().name(),
                interview.getMeetingSyncStatus().name());
    }

    private RecruiterApplicationDtos.AuditEvent audit(ApplicationStatusEventEntity event) {
        return new RecruiterApplicationDtos.AuditEvent(event.getId(), event.getActorId(), event.getCompanyId(),
                event.getFromStatus() == null ? null : event.getFromStatus().name(), event.getToStatus().name(),
                event.getOccurredAt(), event.getReason(), event.getRequestId());
    }

    private JobEntity requireJob(ApplicationEntity application, String companyId) {
        return jobs.findById(application.getJobId()).filter(job -> job.getCompanyId().equals(companyId))
                .orElseThrow(this::notFound);
    }

    private String requireCompany(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
        return members.findByUserId(principal.userId()).map(member -> member.getCompanyId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
    }

    private boolean allowed(ApplicationStatus from, ApplicationStatus to) {
        return (from == ApplicationStatus.APPLIED && (to == ApplicationStatus.IN_REVIEW || to == ApplicationStatus.REJECTED))
                || (from == ApplicationStatus.IN_REVIEW && to == ApplicationStatus.REJECTED)
                || (from == ApplicationStatus.INTERVIEW && (to == ApplicationStatus.OFFERED || to == ApplicationStatus.REJECTED));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Application not found");
    }
}
