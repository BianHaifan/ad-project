package com.adproject.application.application;

import com.adproject.auth.application.MailSender;
import com.adproject.application.api.InterviewDtos;
import com.adproject.application.api.RecruiterApplicationDtos;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.*;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.profile.infrastructure.CandidateProfileRepository;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceEntity;
import com.adproject.recommendation.infrastructure.CandidateJobPreferenceRepository;
import com.adproject.recommendation.infrastructure.CandidateJobRecommendationRepository;
import com.adproject.recommendation.application.MlRecommendationClient.MlCandidate;
import com.adproject.recommendation.application.MlRecommendationClient.MlJob;
import com.adproject.recommendation.application.MlRecommendationClient.MlPreferences;
import com.adproject.recommendation.application.MlRecommendationClient.MlSalary;
import com.adproject.recommendation.application.RecruiterApplicantRankingService;
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
    private final CompanyRepository companies;
    private final MailSender mailSender;
    private final CandidateApplicationResponseMapper mapper;
    private final CandidateJobRecommendationRepository recommendations;
    private final CandidateJobPreferenceRepository preferences;
    private final ResumeRepository resumes;
    private final RecruiterApplicantRankingService applicantRanking;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RecruiterApplicationService(ApplicationRepository applications,
                                       ApplicationStatusEventRepository events,
                                       ResumeSnapshotRepository snapshots, InterviewRepository interviews,
                                       JobRepository jobs,
                                       UserRepository users, CandidateProfileRepository profiles,
                                       CompanyMemberRepository members,
                                       CompanyRepository companies, MailSender mailSender,
                                       CandidateApplicationResponseMapper mapper,
                                       CandidateJobRecommendationRepository recommendations,
                                       CandidateJobPreferenceRepository preferences,
                                       ResumeRepository resumes,
                                       RecruiterApplicantRankingService applicantRanking,
                                       ObjectMapper objectMapper, Clock clock) {
        this.applications = applications; this.events = events; this.snapshots = snapshots; this.interviews = interviews;
        this.jobs = jobs;
        this.users = users; this.profiles = profiles; this.members = members; this.companies = companies;
        this.mailSender = mailSender; this.mapper = mapper;
        this.recommendations = recommendations; this.preferences = preferences; this.resumes = resumes;
        this.applicantRanking = applicantRanking;
        this.objectMapper = objectMapper; this.clock = clock;
    }

    /**
     * 申请列表始终以招聘者所属公司为数据边界；筛选和搜索只是在该边界内执行，不能借参数读取其他公司的候选人。
     */
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

    /**
     * 变更申请状态时同时校验公司归属、乐观锁版本和状态机，并写入审计事件。
     * OFFERED 是附加通知：邮件配置或发送失败不能回滚已经确认的业务状态。
     */
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
        if (target == ApplicationStatus.OFFERED) {
            sendOfferEmail(application, companyId, request.reason());
        }
        return new RecruiterApplicationDtos.TransitionResponse(
                new RecruiterApplicationDtos.TransitionResult(detail(application), audit(event)));
    }

    private void sendOfferEmail(ApplicationEntity application, String companyId, String reason) {
        String recipient = application.getContactEmail();
        if (recipient == null || recipient.isBlank()) return;
        String jobTitle = jobs.findById(application.getJobId()).map(JobEntity::getTitle).orElse("the position");
        String companyName = companies.findById(companyId).map(company -> company.getName()).orElse("the company");
        String subject = "Job offer: " + jobTitle + " at " + companyName;
        String body = "Congratulations! " + companyName + " has extended an offer for the position of " + jobTitle
                + "." + (reason == null || reason.isBlank() ? "" : "\n\n" + reason.trim())
                + "\n\nLog in to HireX to review your offer.";
        if (!mailSender.isConfigured()) {
            log.info("Skipping offer email for application {}: mail not configured", application.getId());
            return;
        }
        try {
            mailSender.send(recipient, subject, body);
        } catch (RuntimeException ex) {
            log.warn("Failed to send offer email for application {}: {}", application.getId(), ex.getMessage());
        }
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
        RecruiterApplicationDtos.MatchAnalysis match = match(application, job);
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
        RecruiterApplicationDtos.MatchAnalysis match = match(application, job);
        return new RecruiterApplicationDtos.Detail(summary.applicationId(), summary.jobId(), summary.status(),
                summary.appliedAt(), summary.updatedAt(), summary.version(), summary.candidate(), summary.jobTitle(),
                summary.matchScore(), null, mapper.resumeSnapshot(snapshot), timeline, match, interview, List.of());
    }

    /**
     * 复用已保存的“候选人 → 职位”推荐快照，且仅当候选人当前简历、求职偏好与职位版本均未变化时有效。
     * 缺失或过期时返回 {@code null}，由调用方显示空分数（“—”），绝不能伪造匹配结果。
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

    /**
     * A candidate-side recommendation snapshot is an optimisation, not a prerequisite for the
     * recruiter view.  When it is absent or stale, score the immutable resume submitted with this
     * application using the same reverse-ranking service as the applications AI list.  That service
     * safely falls back to deterministic rules if ML is unavailable, so the UI never becomes blank.
     */
    private RecruiterApplicationDtos.MatchAnalysis match(ApplicationEntity application, JobEntity job) {
        RecruiterApplicationDtos.MatchAnalysis stored = storedMatch(application.getCandidateId(), job);
        if (stored != null) return stored;
        try {
            var snapshot = snapshots.findById(application.getResumeSnapshotId()).orElse(null);
            if (snapshot == null) return null;
            CandidateJobPreferenceEntity preference = preferences.findById(application.getCandidateId()).orElse(null);
            MlJob mlJob = new MlJob(job.getId(), job.getTitle(), job.getDescription(),
                    readList(job.getRequirementsJson()), readList(job.getSkillsJson()), job.getLocation(),
                    job.getWorkplaceType().name(), job.getEmploymentType().name(),
                    new MlSalary(job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency().name(),
                            job.getSalaryPeriod().name()), null);
            MlPreferences mlPreferences = preference == null ? new MlPreferences(List.of(), List.of(), List.of(),
                    List.of(), null) : new MlPreferences(readList(preference.getDesiredTitlesJson()),
                    readList(preference.getPreferredLocationsJson()), readList(preference.getWorkplaceTypesJson()),
                    readList(preference.getEmploymentTypesJson()), new MlSalary(preference.getMinimumSalary(), null,
                    preference.getSalaryCurrency().name(), preference.getSalaryPeriod().name()));
            MlCandidate candidate = new MlCandidate(application.getCandidateId(),
                    snapshot.getSummary() + " " + snapshot.getExperiencesJson(), snapshot.getHeadline(),
                    readList(snapshot.getSkillsJson()), null, mlPreferences);
            var result = applicantRanking.rankCandidates(mlJob, List.of(candidate));
            var value = result.values().getFirst();
            return new RecruiterApplicationDtos.MatchAnalysis(value.score(), value.analysis().evidence(),
                    value.analysis().strongMatches(), value.analysis().gaps(), result.modelVersion(),
                    result.generatedAt());
        } catch (RuntimeException exception) {
            log.warn("Could not derive recruiter match for application {}; showing unavailable score", application.getId(),
                    exception);
            return null;
        }
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
