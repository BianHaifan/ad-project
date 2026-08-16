package com.adproject.application.application;

import com.adproject.application.api.ApplicationDtos;
import com.adproject.application.api.ApplicationDtos.CandidateApplicationDetailResponse;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.*;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.conversation.application.ConversationProvisioningService;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.Visibility;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.resume.api.ResumeDtos;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateApplicationService {
    private static final String OPERATION = "SUBMIT_APPLICATION";
    private static final TypeReference<List<ResumeDtos.Experience>> EXPERIENCES = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final UserRepository users;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final ResumeRepository resumes;
    private final ResumeSnapshotRepository snapshots;
    private final ApplicationRepository applications;
    private final ApplicationStatusEventRepository events;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final ConversationProvisioningService conversationProvisioning;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CandidateApplicationService(UserRepository users, JobRepository jobs, CompanyRepository companies,
                                       ResumeRepository resumes, ResumeSnapshotRepository snapshots,
                                       ApplicationRepository applications, ApplicationStatusEventRepository events,
                                       IdempotencyRecordRepository idempotencyRecords,
                                       ConversationProvisioningService conversationProvisioning,
                                       ObjectMapper mapper, Clock clock) {
        this.users = users; this.jobs = jobs; this.companies = companies; this.resumes = resumes;
        this.snapshots = snapshots; this.applications = applications; this.events = events;
        this.idempotencyRecords = idempotencyRecords; this.conversationProvisioning = conversationProvisioning;
        this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public CandidateApplicationDetailResponse submit(AuthenticatedUser principal, String jobId,
                                                     String rawIdempotencyKey,
                                                     ApplicationDtos.SubmitApplicationRequest request,
                                                     String requestId) {
        requireCandidate(principal);
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        String contactEmail = request.contactEmail().trim().toLowerCase(Locale.ROOT);
        String payloadHash = digest(jobId + "\n" + request.resumeId() + "\n" + contactEmail + "\n" + request.shareProfile());

        var candidate = users.findByIdForUpdate(principal.userId())
                .filter(user -> user.getRole() == UserRole.CANDIDATE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));

        var replay = idempotencyRecords.findByUserIdAndOperationAndIdempotencyKey(
                candidate.getId(), OPERATION, idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().getPayloadHash().equals(payloadHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "The idempotency key was already used with a different request");
            }
            return readResponse(replay.get().getResponseJson());
        }

        JobEntity job = jobs.findByIdForUpdate(jobId)
                .filter(value -> value.getStatus() == JobStatus.ACTIVE)
                .filter(value -> value.getVisibility() == Visibility.PUBLIC)
                .orElseThrow(CandidateApplicationService::jobNotFound);
        ResumeEntity resume = resumes.findByIdForUpdate(request.resumeId())
                .filter(value -> value.getCandidateId().equals(candidate.getId()))
                .orElseThrow(CandidateApplicationService::resumeNotFound);

        if (applications.existsByJobIdAndCandidateId(job.getId(), candidate.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS",
                    "You have already applied for this job");
        }

        CompanyEntity company = companies.findById(job.getCompanyId()).orElseThrow(CandidateApplicationService::jobNotFound);
        Instant now = DatabaseTimePrecision.micros(clock.instant());
        String snapshotId = UUID.randomUUID().toString();
        ResumeSnapshotEntity snapshot = snapshots.save(new ResumeSnapshotEntity(
                snapshotId, resume.getId(), candidate.getId(), resume.getFullName(), resume.getAge(),
                resume.getLocation(), resume.getHeadline(), resume.getSummary(), resume.getExperiencesJson(),
                resume.getSkillsJson(),
                resume.getVersion(), resume.getCreatedAt(), resume.getUpdatedAt(), now));
        String applicationId = UUID.randomUUID().toString();
        ApplicationEntity application = applications.save(new ApplicationEntity(
                applicationId, job.getId(), candidate.getId(), resume.getId(), snapshotId,
                contactEmail, request.shareProfile(), ApplicationStatus.APPLIED, now, now, 1));
        events.save(new ApplicationStatusEventEntity(UUID.randomUUID().toString(), applicationId,
                candidate.getId(), job.getCompanyId(), null, ApplicationStatus.APPLIED, now,
                "Application submitted", requestId));
        job.incrementApplicantCount();
        conversationProvisioning.provision(applicationId, job.getId(), candidate.getId(), job.getCompanyId(), now);

        CandidateApplicationDetailResponse response = response(application, snapshot, job, company);
        idempotencyRecords.save(new IdempotencyRecordEntity(UUID.randomUUID().toString(), candidate.getId(),
                OPERATION, idempotencyKey, payloadHash, applicationId, 201, writeResponse(response), now));
        idempotencyRecords.flush();
        return response;
    }

    private CandidateApplicationDetailResponse response(ApplicationEntity application, ResumeSnapshotEntity snapshot,
                                                        JobEntity job, CompanyEntity company) {
        List<ApplicationDtos.Experience> experiences = readExperiences(snapshot.getExperiencesJson()).stream()
                .map(value -> new ApplicationDtos.Experience(value.experienceId(), value.title(), value.company(),
                        value.description(), value.startDate(), value.endDate())).toList();
        var companyDto = new ApplicationDtos.Company(company.getId(), company.getName(), company.getLogoUrl(),
                company.getStage(), company.getEmployeeRange(), company.getVerificationStatus().name(),
                company.getWebsite(), company.getDescription(), company.getLocation(), company.getVersion(),
                company.getCreatedAt(), company.getUpdatedAt());
        var snapshotDto = new ApplicationDtos.ResumeSnapshot(snapshot.getId(), snapshot.getCapturedAt(),
                snapshot.getResumeId(), snapshot.getFullName(), snapshot.getAge(), snapshot.getLocation(),
                snapshot.getHeadline(), snapshot.getSummary(), readStrings(snapshot.getSkillsJson()), experiences,
                snapshot.getResumeVersion(),
                snapshot.getResumeCreatedAt(), snapshot.getResumeUpdatedAt());
        var detail = new ApplicationDtos.CandidateApplicationDetail(application.getId(), job.getId(),
                application.getStatus().name(), application.getAppliedAt(), application.getUpdatedAt(),
                application.getVersion(), job.getTitle(), companyDto, null, null,
                List.of(new ApplicationDtos.TimelineStep("APPLIED", true, application.getAppliedAt())),
                snapshotDto, null, List.of(
                new ApplicationDtos.NextStep("RECRUITER_REVIEW", "Recruiter review",
                        company.getName() + " will review your resume snapshot."),
                new ApplicationDtos.NextStep("STATUS_UPDATE", "Status update",
                        "Your application status will update after recruiter review."),
                new ApplicationDtos.NextStep("INTERVIEW_INVITATION", "Interview invitation",
                        "You will be notified if an interview is scheduled.")));
        return new CandidateApplicationDetailResponse(detail);
    }

    private List<ResumeDtos.Experience> readExperiences(String json) {
        try { return mapper.readValue(json, EXPERIENCES); }
        catch (Exception exception) { throw new IllegalStateException("Stored resume experiences are invalid", exception); }
    }

    private List<String> readStrings(String json) {
        try { return mapper.readValue(json, STRINGS); }
        catch (Exception exception) { throw new IllegalStateException("Stored resume skills are invalid", exception); }
    }

    private String writeResponse(CandidateApplicationDetailResponse response) {
        try { return mapper.writeValueAsString(response); }
        catch (Exception exception) { throw new IllegalStateException("Unable to persist idempotent result", exception); }
    }

    private CandidateApplicationDetailResponse readResponse(String json) {
        try { return mapper.readValue(json, CandidateApplicationDetailResponse.class); }
        catch (Exception exception) { throw new IllegalStateException("Stored idempotent result is invalid", exception); }
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw validation("Idempotency-Key", "is required");
        }
        try { return UUID.fromString(value).toString(); }
        catch (IllegalArgumentException exception) { throw validation("Idempotency-Key", "must be a UUID"); }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }
    private static ApiException validation(String field, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                "Request validation failed", Map.of(field, detail));
    }
    private static ApiException jobNotFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Job not found"); }
    private static ApiException resumeNotFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resume not found"); }
}
