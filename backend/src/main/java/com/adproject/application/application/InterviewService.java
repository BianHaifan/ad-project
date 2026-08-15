package com.adproject.application.application;

import com.adproject.application.api.InterviewDtos;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.domain.InterviewAuditAction;
import com.adproject.application.domain.InterviewMode;
import com.adproject.application.domain.InterviewStatus;
import com.adproject.application.domain.MeetingProvider;
import com.adproject.application.domain.MeetingSyncStatus;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.application.infrastructure.ApplicationStatusEventEntity;
import com.adproject.application.infrastructure.ApplicationStatusEventRepository;
import com.adproject.application.infrastructure.InterviewAuditEventEntity;
import com.adproject.application.infrastructure.InterviewAuditEventRepository;
import com.adproject.application.infrastructure.InterviewEntity;
import com.adproject.application.infrastructure.InterviewRepository;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.integration.google.MeetingCancelRequest;
import com.adproject.integration.google.MeetingProvisioningException;
import com.adproject.integration.google.MeetingProvisioningPort;
import com.adproject.integration.google.MeetingSyncOutcome;
import com.adproject.integration.google.MeetingSyncResult;
import com.adproject.integration.google.MeetingUpdateRequest;
import com.adproject.integration.google.ProvisionOutcome;
import com.adproject.integration.google.ProvisionRequest;
import com.adproject.integration.google.ProvisionResult;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.user.domain.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InterviewService {
    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    private final InterviewRepository interviews;
    private final ApplicationRepository applications;
    private final ApplicationStatusEventRepository events;
    private final InterviewAuditEventRepository audits;
    private final JobRepository jobs;
    private final CompanyMemberRepository members;
    private final MeetingProvisioningPort meetingProvisioning;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public InterviewService(InterviewRepository interviews, ApplicationRepository applications,
                            ApplicationStatusEventRepository events, InterviewAuditEventRepository audits,
                            JobRepository jobs, CompanyMemberRepository members,
                            MeetingProvisioningPort meetingProvisioning, ObjectMapper mapper, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.interviews = interviews;
        this.applications = applications;
        this.events = events;
        this.audits = audits;
        this.jobs = jobs;
        this.members = members;
        this.meetingProvisioning = meetingProvisioning;
        this.mapper = mapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public InterviewDtos.Interview create(AuthenticatedUser principal, String applicationId,
                                           InterviewDtos.CreateInterviewRequest request, String requestId) {
        String companyId = requireCompany(principal);
        MeetingProvider requestedProvider = request.meetingProvider();
        String location = request.locationOrMeetingUrl() == null ? null : request.locationOrMeetingUrl().trim();
        String timezone = request.timezone().trim();

        // Mode-first validation before any row is touched. All pure input
        // validation (mode / provider / location / timezone) runs first so an
        // invalid request never triggers an external OAuth refresh or mutates
        // connection state; the connection preflight runs last, outside any
        // business transaction or application lock, and must complete before
        // the local interview or application transition is created.
        //
        // The simplified product rule: ONLINE always provisions a Google Meet and
        // Calendar invitation (never a recruiter-supplied link); ONSITE and PHONE
        // are manual and never request Google Meet.
        if (request.mode() == InterviewMode.ONLINE) {
            if (requestedProvider != MeetingProvider.GOOGLE_MEET) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "Online interviews always use Google Meet",
                        Map.of("meetingProvider", "must be GOOGLE_MEET for online interviews"));
            }
            if (location != null && !location.isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "A meeting link must not be provided for online interviews",
                        Map.of("locationOrMeetingUrl", "must be blank for online interviews"));
            }
            validateTimezone(timezone);
            ensureMeetingConnectionUsable(principal.userId());
        } else {
            if (requestedProvider == MeetingProvider.GOOGLE_MEET) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "Google Meet is only available for online interviews",
                        Map.of("meetingProvider", "must be MANUAL for on-site and phone interviews"));
            }
            validateLocation(request.mode(), location);
            validateTimezone(timezone);
        }

        MeetingProvider provider = request.mode() == InterviewMode.ONLINE
                ? MeetingProvider.GOOGLE_MEET : MeetingProvider.MANUAL;
        String correlationId = provider == MeetingProvider.GOOGLE_MEET ? UUID.randomUUID().toString() : null;

        // The candidate's application contact email is the only allowed Calendar
        // attendee. It is read from the locked server-side application record here
        // and never from the request body, so a browser cannot inject a recipient.
        String[] contactEmail = new String[1];

        // Transaction 1: lock the application and commit the local interview in
        // PENDING state (no link), together with the status transition and audit.
        InterviewEntity created = transactionTemplate.execute(status -> {
            ApplicationEntity application = applications.findByIdForUpdate(applicationId).orElseThrow(this::notFound);
            requireJob(application, companyId);
            contactEmail[0] = application.getContactEmail();
            if (application.getVersion() != request.expectedApplicationVersion()) {
                throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The application has changed");
            }
            if (interviews.existsByApplicationId(applicationId)) {
                throw new ApiException(HttpStatus.CONFLICT, "INTERVIEW_ALREADY_EXISTS",
                        "An interview is already scheduled for this application");
            }
            if (application.getStatus() != ApplicationStatus.IN_REVIEW) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPLICATION_TRANSITION",
                        "An interview can only be scheduled while the application is in review");
            }
            Instant now = DatabaseTimePrecision.micros(clock.instant());
            InterviewEntity interview = new InterviewEntity(UUID.randomUUID().toString(), applicationId,
                    DatabaseTimePrecision.micros(request.scheduledAt()), timezone, request.durationMinutes(), request.mode(),
                    location, request.note() == null ? null : request.note().trim(),
                    InterviewStatus.SCHEDULED, provider, now);
            if (correlationId != null) {
                interview.assignCorrelationId(correlationId);
            }
            interview = interviews.save(interview);
            ApplicationStatus before = application.getStatus();
            application.transitionTo(ApplicationStatus.INTERVIEW, now);
            events.save(new ApplicationStatusEventEntity(UUID.randomUUID().toString(), application.getId(),
                    principal.userId(), companyId, before, ApplicationStatus.INTERVIEW, now,
                    "Interview scheduled", requestId));
            audits.save(new InterviewAuditEventEntity(UUID.randomUUID().toString(), interview.getId(),
                    interview.getApplicationId(), principal.userId(), companyId, InterviewAuditAction.CREATED,
                    null, snapshot(interview), now, reasonFor(InterviewAuditAction.CREATED), requestId));
            applications.flush();
            return interview;
        });

        if (provider != MeetingProvider.GOOGLE_MEET) {
            return toDto(created);
        }

        // External call outside any transaction; a remote failure must never roll
        // back the already-committed local interview.
        ProvisionResult result = provisionMeeting(principal.userId(), correlationId, request.scheduledAt(),
                timezone, request.durationMinutes(), contactEmail[0]);

        // Transaction 2: short write-back of the provider result by interview id.
        InterviewEntity updated = transactionTemplate.execute(status -> {
            InterviewEntity interview = interviews.findByIdForUpdate(created.getId()).orElseThrow(this::notFound);
            Instant now = DatabaseTimePrecision.micros(clock.instant());
            switch (result.outcome()) {
                case READY -> interview.markReady(result.eventId(), result.meetingUrl(), now);
                case PENDING -> interview.markPending(result.eventId(), now);
                case FAILED -> interview.markFailed(result.syncErrorCode(), now);
            }
            return interview;
        });
        return toDto(updated);
    }

    private void ensureMeetingConnectionUsable(String recruiterId) {
        try {
            meetingProvisioning.ensureConnectionUsable(recruiterId);
        } catch (MeetingProvisioningException e) {
            String message = switch (e.code()) {
                case "GOOGLE_MEET_NOT_CONNECTED" -> "Google Meet is not connected for this recruiter";
                case "GOOGLE_MEET_RECONNECT_REQUIRED" -> "Google Meet connection is no longer valid; reconnect to continue";
                default -> "Google Meet provisioning is not available";
            };
            throw new ApiException(HttpStatus.CONFLICT, e.code(), message);
        }
    }

    /**
     * Invokes the provisioning port and normalizes its return into a safe
     * {@link ProvisionResult}. This is the single choke point for both the
     * initial-create write-back and the retry write-back, so a malformed provider
     * response can never leave an interview stuck in PENDING or fabricate a link.
     *
     * <p>An unexpected exception, a {@code null} result, a {@code null} outcome,
     * or a result missing its required fields (READY without an event id or a
     * usable meet link; PENDING without an event id; FAILED without a safe error
     * code) is downgraded to {@link ProvisionOutcome#FAILED} with no event id, no
     * link, and the safe {@code GOOGLE_MEET_PROVISIONING_UNAVAILABLE} code. Only
     * the exception category is logged — never a token, provider response, event
     * id, or meet link.
     */
    private ProvisionResult provisionMeeting(String recruiterId, String correlationId, Instant scheduledAt,
                                             String timezone, int durationMinutes, String attendeeEmail) {
        ProvisionResult result;
        try {
            result = meetingProvisioning.provision(new ProvisionRequest(recruiterId, correlationId,
                    DatabaseTimePrecision.micros(scheduledAt), timezone, durationMinutes, attendeeEmail));
        } catch (RuntimeException e) {
            log.warn("Google Meet provisioning failed unexpectedly: {}", e.getClass().getSimpleName());
            return safeProvisionFailure();
        }
        return normalizeProvisionResult(result);
    }

    private static ProvisionResult normalizeProvisionResult(ProvisionResult result) {
        if (result == null || result.outcome() == null) {
            return safeProvisionFailure();
        }
        return switch (result.outcome()) {
            case READY -> isNotBlank(result.eventId()) && isUsableMeetUrl(result.meetingUrl())
                    ? result : safeProvisionFailure();
            case PENDING -> isNotBlank(result.eventId()) ? result : safeProvisionFailure();
            case FAILED -> isNotBlank(result.syncErrorCode()) ? result : safeProvisionFailure();
        };
    }

    private static ProvisionResult safeProvisionFailure() {
        return new ProvisionResult(ProvisionOutcome.FAILED, null, null, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * A provision result is only usable when it carries a server-verified HTTPS
     * {@code meet.google.com} link (mirroring the port's own {@code isValidMeetLink}
     * contract); anything else is treated as a missing link rather than stored as
     * a fabricated or client-controlled value.
     */
    private static boolean isUsableMeetUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && "meet.google.com".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public InterviewDtos.Interview update(AuthenticatedUser principal, String interviewId,
                                           InterviewDtos.UpdateInterviewRequest request, String requestId) {
        String companyId = requireCompany(principal);
        InterviewEntity interview = interviews.findById(interviewId).orElseThrow(this::notFound);
        if (interview.getMeetingProvider() == MeetingProvider.GOOGLE_MEET) {
            return updateGoogleMeet(principal, companyId, interviewId, request, requestId);
        }
        return updateLocal(principal, companyId, interviewId, request, requestId);
    }

    /**
     * Local-only update for MANUAL interviews (and GOOGLE_MEET completion, which
     * has no external counterpart). Runs in a single short transaction; no Google
     * HTTP happens here.
     */
    private InterviewDtos.Interview updateLocal(AuthenticatedUser principal, String companyId, String interviewId,
                                                 InterviewDtos.UpdateInterviewRequest request, String requestId) {
        return transactionTemplate.execute(status -> {
            InterviewEntity interview = interviews.findByIdForUpdate(interviewId).orElseThrow(this::notFound);
            ApplicationEntity application = applications.findById(interview.getApplicationId()).orElseThrow(this::notFound);
            requireJob(application, companyId);
            if (interview.getVersion() != request.expectedVersion()) {
                throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The interview has changed");
            }
            if (interview.getStatus() != InterviewStatus.SCHEDULED) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_INTERVIEW_TRANSITION",
                        "A completed or cancelled interview cannot be changed");
            }
            Instant now = DatabaseTimePrecision.micros(clock.instant());
            String before = snapshot(interview);
            InterviewAuditAction action;
            if (request.status() == InterviewStatus.COMPLETED) {
                interview.complete(now);
                action = InterviewAuditAction.COMPLETED;
            } else if (request.status() == InterviewStatus.CANCELLED) {
                interview.cancel(now);
                action = InterviewAuditAction.CANCELLED;
            } else {
                String location = request.locationOrMeetingUrl() != null
                        ? request.locationOrMeetingUrl().trim() : interview.getLocationOrMeetingUrl();
                InterviewMode mode = request.mode() != null ? request.mode() : interview.getMode();
                String timezone = request.timezone() != null ? request.timezone().trim() : interview.getTimezone();
                validateLocation(mode, location);
                validateTimezone(timezone);
                interview.reschedule(
                        request.scheduledAt() != null ? DatabaseTimePrecision.micros(request.scheduledAt()) : interview.getScheduledAt(),
                        timezone,
                        request.durationMinutes() != null ? request.durationMinutes() : interview.getDurationMinutes(),
                        mode,
                        location,
                        request.note() != null ? request.note().trim() : interview.getNote(),
                        now);
                action = InterviewAuditAction.RESCHEDULED;
            }
            audits.save(new InterviewAuditEventEntity(UUID.randomUUID().toString(), interview.getId(),
                    interview.getApplicationId(), principal.userId(), companyId, action,
                    before, snapshot(interview), now, reasonFor(action), requestId));
            interviews.flush();
            return toDto(interview);
        });
    }

    /**
     * Google Meet update. Completion is local-only; reschedule and cancel follow a
     * two-phase pattern: a short reservation transaction, the Google HTTP call
     * outside any transaction or lock, then a short write-back transaction. This
     * keeps the external call out of a pessimistic lock and MySQL transaction.
     */
    private InterviewDtos.Interview updateGoogleMeet(AuthenticatedUser principal, String companyId,
                                                      String interviewId,
                                                      InterviewDtos.UpdateInterviewRequest request,
                                                      String requestId) {
        boolean cancel = request.status() == InterviewStatus.CANCELLED;

        if (request.status() == InterviewStatus.COMPLETED) {
            return transactionTemplate.execute(status -> {
                InterviewEntity interview = interviews.findByIdForUpdate(interviewId).orElseThrow(this::notFound);
                ApplicationEntity application = applications.findById(interview.getApplicationId()).orElseThrow(this::notFound);
                requireJob(application, companyId);
                if (interview.getStatus() != InterviewStatus.SCHEDULED) {
                    throw new ApiException(HttpStatus.CONFLICT, "INVALID_INTERVIEW_TRANSITION",
                            "A completed or cancelled interview cannot be changed");
                }
                rejectIfSyncInProgress(interview);
                if (interview.getVersion() != request.expectedVersion()) {
                    throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The interview has changed");
                }
                Instant now = DatabaseTimePrecision.micros(clock.instant());
                String before = snapshot(interview);
                interview.complete(now);
                audits.save(new InterviewAuditEventEntity(UUID.randomUUID().toString(), interview.getId(),
                        interview.getApplicationId(), principal.userId(), companyId, InterviewAuditAction.COMPLETED,
                        before, snapshot(interview), now, reasonFor(InterviewAuditAction.COMPLETED), requestId));
                interviews.flush();
                return toDto(interview);
            });
        }

        // Phase 1: validate locally and reserve the sync slot (PENDING + version++).
        SyncPlan plan = transactionTemplate.execute(status -> {
            InterviewEntity interview = interviews.findByIdForUpdate(interviewId).orElseThrow(this::notFound);
            ApplicationEntity application = applications.findById(interview.getApplicationId()).orElseThrow(this::notFound);
            requireJob(application, companyId);
            if (interview.getStatus() != InterviewStatus.SCHEDULED) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_INTERVIEW_TRANSITION",
                        "A completed or cancelled interview cannot be changed");
            }
            // PENDING is checked before the optimistic version so a stale
            // expectedVersion from a request that raced a reservation is reported
            // as a sync-in-progress, never as a version conflict.
            rejectIfSyncInProgress(interview);
            if (interview.getVersion() != request.expectedVersion()) {
                throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The interview has changed");
            }
            if (request.mode() != null && request.mode() != InterviewMode.ONLINE) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "Google Meet interviews must stay online", Map.of("mode", "must be ONLINE"));
            }
            if (request.locationOrMeetingUrl() != null && !request.locationOrMeetingUrl().isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                        "The meeting link is managed by Google Meet and cannot be changed",
                        Map.of("locationOrMeetingUrl", "must not be provided for Google Meet"));
            }
            Instant now = DatabaseTimePrecision.micros(clock.instant());
            String before = snapshot(interview);

            // Initial provisioning failure: no external event was ever created, so
            // a retry re-provisions with the persisted correlation id (rather than
            // failing with a reconnect error), and cancellation is local-only.
            if (interview.getMeetingEventId() == null || interview.getMeetingEventId().isBlank()) {
                if (interview.getMeetingSyncStatus() != MeetingSyncStatus.FAILED) {
                    // Unreachable in practice (PENDING is rejected above and READY
                    // always carries an event id), but keep a safe guard.
                    throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_MEET_RECONNECT_REQUIRED",
                            "This interview has no Google Meet event to synchronize");
                }
                if (cancel) {
                    interview.cancel(now);
                    audits.save(new InterviewAuditEventEntity(UUID.randomUUID().toString(), interview.getId(),
                            interview.getApplicationId(), principal.userId(), companyId, InterviewAuditAction.CANCELLED,
                            before, snapshot(interview), now, reasonFor(InterviewAuditAction.CANCELLED), requestId));
                    interviews.flush();
                    return SyncPlan.localCancel(interview.getId());
                }
                String timezone = request.timezone() != null ? request.timezone().trim() : interview.getTimezone();
                validateTimezone(timezone);
                Instant scheduledAt = request.scheduledAt() != null
                        ? DatabaseTimePrecision.micros(request.scheduledAt()) : interview.getScheduledAt();
                int durationMinutes = request.durationMinutes() != null
                        ? request.durationMinutes() : interview.getDurationMinutes();
                String note = request.note() != null ? request.note().trim() : interview.getNote();
                interview.beginSync(now);
                interviews.flush();
                return SyncPlan.provision(interview.getId(), interview.getMeetingCorrelationId(), principal.userId(),
                        before, interview.getVersion(), scheduledAt, timezone, durationMinutes, note,
                        application.getContactEmail());
            }

            if (cancel) {
                interview.beginSync(now);
                interviews.flush();
                return SyncPlan.cancel(interview.getId(), interview.getMeetingEventId(), principal.userId(), before,
                        interview.getVersion());
            }

            String timezone = request.timezone() != null ? request.timezone().trim() : interview.getTimezone();
            validateTimezone(timezone);
            Instant scheduledAt = request.scheduledAt() != null
                    ? DatabaseTimePrecision.micros(request.scheduledAt()) : interview.getScheduledAt();
            int durationMinutes = request.durationMinutes() != null
                    ? request.durationMinutes() : interview.getDurationMinutes();
            String note = request.note() != null ? request.note().trim() : interview.getNote();

            interview.beginSync(now);
            interviews.flush();
            return SyncPlan.reschedule(interview.getId(), interview.getMeetingEventId(), principal.userId(), before,
                    interview.getVersion(), scheduledAt, timezone, durationMinutes, note);
        });

        // Local cancel of a never-provisioned meeting: the cancellation and its
        // audit were already committed in phase 1; no external call is made.
        if (plan.localCancel()) {
            return toDto(interviews.findById(plan.interviewId()).orElseThrow(this::notFound));
        }

        // Initial-provisioning retry: re-provision with the persisted correlation
        // id (never creating a second internal interview), then write back the
        // recovered event id / link / READY state in a short transaction.
        if (plan.provision()) {
            ProvisionResult result = provisionMeeting(plan.recruiterId(), plan.correlationId(),
                    plan.scheduledAt(), plan.timezone(), plan.durationMinutes(), plan.attendeeEmail());
            return writeBackProvisionRetry(plan, result, principal, companyId, requestId);
        }

        // External phase: Google HTTP, outside any transaction or lock. An
        // unexpected RuntimeException or a null/invalid result must never leave
        // the interview stuck in PENDING, so it is normalized to a safe FAILED
        // result that phase 2 persists via failSyncPreservingInvitation.
        MeetingSyncResult result = syncExternal(plan);

        // Phase 2: short write-back, guarded by provider + reservation version + PENDING.
        return transactionTemplate.execute(status -> {
            InterviewEntity interview = interviews.findByIdForUpdate(plan.interviewId()).orElseThrow(this::notFound);
            Instant now = DatabaseTimePrecision.micros(clock.instant());
            if (interview.getMeetingProvider() != MeetingProvider.GOOGLE_MEET
                    || interview.getVersion() != plan.reservedVersion()
                    || interview.getMeetingSyncStatus() != MeetingSyncStatus.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_MEET_SYNC_IN_PROGRESS",
                        "The interview changed while Google Meet was being synchronized");
            }
            boolean synced = result.outcome() == MeetingSyncOutcome.SYNCED;
            if (plan.cancel()) {
                if (synced) {
                    interview.completeGoogleCancel(now);
                } else {
                    interview.failSyncPreservingInvitation(result.syncErrorCode(), now);
                }
            } else {
                if (synced) {
                    interview.completeGoogleReschedule(plan.scheduledAt(), plan.timezone(), plan.durationMinutes(),
                            plan.note(), now);
                } else {
                    interview.failSyncPreservingInvitation(result.syncErrorCode(), now);
                }
            }
            InterviewAuditAction action = synced
                    ? (plan.cancel() ? InterviewAuditAction.CANCELLED : InterviewAuditAction.RESCHEDULED)
                    : InterviewAuditAction.SYNC_FAILED;
            audits.save(new InterviewAuditEventEntity(UUID.randomUUID().toString(), interview.getId(),
                    interview.getApplicationId(), principal.userId(), companyId, action,
                    plan.before(), snapshot(interview), now, reasonFor(action), requestId));
            interviews.flush();
            return toDto(interview);
        });
    }

    /**
     * Runs the external reschedule/cancel call outside any transaction or lock,
     * converting an unexpected {@link RuntimeException} (or a null/invalid
     * {@link MeetingSyncResult}) into a safe FAILED result so phase 2 can persist
     * it and the interview never stays stuck in PENDING. Only the exception
     * category is logged — never a token, Google response, Meet link, or
     * candidate-identifying detail.
     */
    private MeetingSyncResult syncExternal(SyncPlan plan) {
        MeetingSyncResult result;
        try {
            result = plan.cancel()
                    ? meetingProvisioning.cancelMeeting(new MeetingCancelRequest(plan.recruiterId(), plan.eventId()))
                    : meetingProvisioning.updateMeeting(new MeetingUpdateRequest(plan.recruiterId(), plan.eventId(),
                            plan.scheduledAt(), plan.durationMinutes(), plan.timezone()));
        } catch (RuntimeException e) {
            log.warn("Google Meet synchronization failed unexpectedly: {}", e.getClass().getSimpleName());
            return safeSyncFailure();
        }
        if (result == null || result.outcome() == null) {
            return safeSyncFailure();
        }
        return result;
    }

    private static MeetingSyncResult safeSyncFailure() {
        return new MeetingSyncResult(MeetingSyncOutcome.FAILED, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE");
    }

    /**
     * Writes back the result of an initial-provisioning retry. READY applies the
     * recovered event id, verified Meet URL, READY status, and the merged
     * schedule; FAILED preserves the original invitation and keeps the interview
     * SCHEDULED + FAILED with no fabricated link. PENDING records the event id and
     * leaves the slot reserved without inventing a link.
     */
    private InterviewDtos.Interview writeBackProvisionRetry(SyncPlan plan, ProvisionResult result,
                                                            AuthenticatedUser principal, String companyId,
                                                            String requestId) {
        return transactionTemplate.execute(status -> {
            InterviewEntity interview = interviews.findByIdForUpdate(plan.interviewId()).orElseThrow(this::notFound);
            Instant now = DatabaseTimePrecision.micros(clock.instant());
            if (interview.getMeetingProvider() != MeetingProvider.GOOGLE_MEET
                    || interview.getVersion() != plan.reservedVersion()
                    || interview.getMeetingSyncStatus() != MeetingSyncStatus.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_MEET_SYNC_IN_PROGRESS",
                        "The interview changed while Google Meet was being synchronized");
            }
            ProvisionOutcome outcome = result.outcome();
            if (outcome == ProvisionOutcome.READY) {
                interview.completeGoogleProvisionRetry(result.eventId(), result.meetingUrl(),
                        plan.scheduledAt(), plan.timezone(), plan.durationMinutes(), plan.note(), now);
                audits.save(new InterviewAuditEventEntity(UUID.randomUUID().toString(), interview.getId(),
                        interview.getApplicationId(), principal.userId(), companyId, InterviewAuditAction.RESCHEDULED,
                        plan.before(), snapshot(interview), now, reasonFor(InterviewAuditAction.RESCHEDULED), requestId));
            } else if (outcome == ProvisionOutcome.PENDING) {
                // Still in flight on the provider side; keep the reservation and
                // the event id, but never fabricate a link. No audit yet — nothing
                // has succeeded or failed.
                interview.markPending(result.eventId(), now);
            } else {
                interview.failSyncPreservingInvitation(result.syncErrorCode(), now);
                audits.save(new InterviewAuditEventEntity(UUID.randomUUID().toString(), interview.getId(),
                        interview.getApplicationId(), principal.userId(), companyId, InterviewAuditAction.SYNC_FAILED,
                        plan.before(), snapshot(interview), now, reasonFor(InterviewAuditAction.SYNC_FAILED), requestId));
            }
            interviews.flush();
            return toDto(interview);
        });
    }

    private void rejectIfSyncInProgress(InterviewEntity interview) {
        if (interview.getMeetingSyncStatus() == MeetingSyncStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_MEET_SYNC_IN_PROGRESS",
                    "A Google Meet synchronization is already in progress");
        }
    }

    private InterviewDtos.Interview toDto(InterviewEntity interview) {
        return new InterviewDtos.Interview(interview.getId(), interview.getApplicationId(),
                interview.getScheduledAt(), interview.getTimezone(), interview.getDurationMinutes(),
                interview.getMode().name(), interview.getLocationOrMeetingUrl(), interview.getNote(),
                interview.getStatus().name(), interview.getVersion(), interview.getCreatedAt(),
                interview.getUpdatedAt(), interview.getMeetingProvider().name(),
                interview.getMeetingSyncStatus().name());
    }

    private String snapshot(InterviewEntity interview) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("scheduledAt", interview.getScheduledAt() == null ? null : interview.getScheduledAt().toString());
        value.put("timezone", interview.getTimezone());
        value.put("durationMinutes", interview.getDurationMinutes());
        value.put("mode", interview.getMode().name());
        value.put("locationOrMeetingUrl", interview.getLocationOrMeetingUrl());
        value.put("note", interview.getNote());
        value.put("status", interview.getStatus().name());
        value.put("version", interview.getVersion());
        value.put("meetingProvider", interview.getMeetingProvider().name());
        value.put("meetingSyncStatus", interview.getMeetingSyncStatus().name());
        value.put("meetingSyncError", interview.getMeetingSyncError());
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize interview audit snapshot", e);
        }
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Timezone must be a valid IANA timezone",
                    Map.of("timezone", "must be a valid IANA timezone"));
        }
    }

    private void validateLocation(InterviewMode mode, String location) {
        if (location == null || location.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Location or meeting link is required",
                    Map.of("locationOrMeetingUrl", "must not be blank"));
        }
        if (mode == InterviewMode.ONLINE && !(location.startsWith("http://") || location.startsWith("https://"))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Online interviews require an http(s) meeting link",
                    Map.of("locationOrMeetingUrl", "must be an http(s) URL"));
        }
    }

    private String reasonFor(InterviewAuditAction action) {
        return switch (action) {
            case CREATED -> "Interview scheduled";
            case RESCHEDULED -> "Interview rescheduled";
            case COMPLETED -> "Interview completed";
            case CANCELLED -> "Interview cancelled";
            case SYNC_FAILED -> "Google Meet synchronization failed";
        };
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

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    /**
     * Data captured during the reservation transaction and threaded through the
     * external phase into the write-back transaction. For a cancel the time
     * fields are null/zero and {@code cancel} is true; for an initial-provisioning
     * retry {@code provision} is true and {@code correlationId} carries the
     * persisted provisioning correlation key instead of an event id.
     */
    private record SyncPlan(String interviewId, String eventId, String correlationId, String recruiterId,
                            String before, int reservedVersion, boolean cancel, boolean provision,
                            boolean localCancel, Instant scheduledAt, String timezone, int durationMinutes,
                            String note, String attendeeEmail) {

        static SyncPlan cancel(String interviewId, String eventId, String recruiterId, String before,
                               int reservedVersion) {
            return new SyncPlan(interviewId, eventId, null, recruiterId, before, reservedVersion, true, false, false,
                    null, null, 0, null, null);
        }

        static SyncPlan reschedule(String interviewId, String eventId, String recruiterId, String before,
                                   int reservedVersion, Instant scheduledAt, String timezone,
                                   int durationMinutes, String note) {
            return new SyncPlan(interviewId, eventId, null, recruiterId, before, reservedVersion, false, false, false,
                    scheduledAt, timezone, durationMinutes, note, null);
        }

        static SyncPlan provision(String interviewId, String correlationId, String recruiterId, String before,
                                  int reservedVersion, Instant scheduledAt, String timezone,
                                  int durationMinutes, String note, String attendeeEmail) {
            return new SyncPlan(interviewId, null, correlationId, recruiterId, before, reservedVersion, false, true,
                    false, scheduledAt, timezone, durationMinutes, note, attendeeEmail);
        }

        static SyncPlan localCancel(String interviewId) {
            return new SyncPlan(interviewId, null, null, null, null, 0, true, false, true, null, null, 0, null, null);
        }
    }
}
