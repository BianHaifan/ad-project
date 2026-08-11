package com.adproject.application.application;

import com.adproject.application.api.ApplicationResponses.SubmitApplicationResponse;
import com.adproject.application.api.SubmitApplicationRequest;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.application.infrastructure.ApplicationStatusEventEntity;
import com.adproject.application.infrastructure.ApplicationStatusEventRepository;
import com.adproject.application.infrastructure.IdempotencyRecordEntity;
import com.adproject.application.infrastructure.IdempotencyRecordRepository;
import com.adproject.application.infrastructure.ResumeSnapshotEntity;
import com.adproject.application.infrastructure.ResumeSnapshotRepository;
import com.adproject.common.api.ApiException;
import com.adproject.job.application.JobApplicationReader;
import com.adproject.job.application.JobForApplication;
import com.adproject.resume.application.ResumeApplicationReader;
import com.adproject.user.application.CandidateSubmissionLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
    private static final String OPERATION = "SUBMIT_APPLICATION";

    private final ApplicationRepository applicationRepository;
    private final ResumeSnapshotRepository snapshotRepository;
    private final ApplicationStatusEventRepository eventRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final JobApplicationReader jobReader;
    private final ResumeApplicationReader resumeReader;
    private final CandidateSubmissionLock candidateSubmissionLock;
    private final CandidateApplicationMapper mapper;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applicationRepository,
                              ResumeSnapshotRepository snapshotRepository,
                              ApplicationStatusEventRepository eventRepository,
                              IdempotencyRecordRepository idempotencyRepository,
                              JobApplicationReader jobReader,
                              ResumeApplicationReader resumeReader,
                              CandidateSubmissionLock candidateSubmissionLock,
                              CandidateApplicationMapper mapper,
                              Clock clock) {
        this.applicationRepository = applicationRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.jobReader = jobReader;
        this.resumeReader = resumeReader;
        this.candidateSubmissionLock = candidateSubmissionLock;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public SubmitApplicationResponse submit(String candidateId, String jobId, String rawIdempotencyKey,
                                            SubmitApplicationRequest request, String requestId) {
        String idempotencyKey = canonicalIdempotencyKey(rawIdempotencyKey);
        String resumeId = request.resumeId().trim();
        String contactEmail = request.contactEmail().trim().toLowerCase(Locale.ROOT);
        String requestHash = requestHash(jobId, resumeId, contactEmail, request.shareProfile());

        candidateSubmissionLock.lock(candidateId);
        var existing = idempotencyRepository.findForUpdate(candidateId, OPERATION, idempotencyKey);
        if (existing.isPresent()) {
            return replay(candidateId, requestHash, existing.get());
        }

        JobForApplication job = jobReader.lockAcceptingJob(jobId);

        var resume = resumeReader.requireOwnedResume(candidateId, resumeId);
        if (applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS",
                    "You have already applied for this job");
        }

        Instant now = clock.instant();
        ResumeSnapshotEntity snapshot = snapshotRepository.saveAndFlush(
                new ResumeSnapshotEntity(UUID.randomUUID().toString(), resume, now));
        ApplicationEntity application = applicationRepository.saveAndFlush(
                new ApplicationEntity(UUID.randomUUID().toString(), jobId, candidateId, snapshot.getId(),
                        contactEmail, request.shareProfile(), now));
        eventRepository.save(new ApplicationStatusEventEntity(UUID.randomUUID().toString(), application.getId(),
                candidateId, null, ApplicationStatus.APPLIED, now, "Application submitted", requestId));
        idempotencyRepository.save(new IdempotencyRecordEntity(UUID.randomUUID().toString(), candidateId,
                OPERATION, idempotencyKey, requestHash, application.getId(), now));

        return mapper.toResponse(application, snapshot, job,
                eventRepository.findByApplicationIdOrderByOccurredAtAsc(application.getId()));
    }

    private SubmitApplicationResponse replay(String candidateId, String requestHash,
                                             IdempotencyRecordEntity record) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was already used with a different request");
        }
        ApplicationEntity application = applicationRepository.findByIdAndCandidateId(record.getResourceId(), candidateId)
                .orElseThrow(() -> new IllegalStateException("Idempotency application is missing"));
        ResumeSnapshotEntity snapshot = snapshotRepository.findById(application.getResumeSnapshotId())
                .orElseThrow(() -> new IllegalStateException("Application resume snapshot is missing"));
        return mapper.toResponse(application, snapshot, jobReader.requireJob(application.getJobId()),
                eventRepository.findByApplicationIdOrderByOccurredAtAsc(application.getId()));
    }

    private static String canonicalIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw validation("Idempotency-Key", "must be provided");
        }
        try {
            return UUID.fromString(rawKey).toString();
        } catch (IllegalArgumentException exception) {
            throw validation("Idempotency-Key", "must be a UUID");
        }
    }

    private static String requestHash(String jobId, String resumeId, String contactEmail, boolean shareProfile) {
        String canonical = jobId + "\n" + resumeId + "\n" + contactEmail + "\n" + shareProfile;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ApiException validation(String field, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                Map.of(field, detail));
    }
}
