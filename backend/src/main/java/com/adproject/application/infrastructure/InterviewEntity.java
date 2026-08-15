package com.adproject.application.infrastructure;

import com.adproject.application.domain.InterviewMode;
import com.adproject.application.domain.InterviewStatus;
import com.adproject.application.domain.MeetingProvider;
import com.adproject.application.domain.MeetingSyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "interviews")
public class InterviewEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "application_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String applicationId;
    @Column(name = "scheduled_at", nullable = false) private Instant scheduledAt;
    @Column(nullable = false, length = 64) private String timezone;
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private InterviewMode mode;
    @Column(name = "location_or_meeting_url", length = 1000) private String locationOrMeetingUrl;
    @Column(length = 500) private String note;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private InterviewStatus status;
    @Column(nullable = false) private int version;
    @Enumerated(EnumType.STRING) @Column(name = "meeting_provider", nullable = false, length = 32)
    private MeetingProvider meetingProvider;
    @Enumerated(EnumType.STRING) @Column(name = "meeting_sync_status", nullable = false, length = 32)
    private MeetingSyncStatus meetingSyncStatus;
    @Column(name = "meeting_event_id", length = 255) private String meetingEventId;
    @Column(name = "meeting_sync_error", length = 1000) private String meetingSyncError;
    @Column(name = "meeting_correlation_id", length = 100) private String meetingCorrelationId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected InterviewEntity() {}

    public InterviewEntity(String id, String applicationId, Instant scheduledAt, String timezone,
                           int durationMinutes, InterviewMode mode, String locationOrMeetingUrl, String note,
                           InterviewStatus status, MeetingProvider meetingProvider, Instant now) {
        this.id = id;
        this.applicationId = applicationId;
        this.scheduledAt = scheduledAt;
        this.timezone = timezone;
        this.durationMinutes = durationMinutes;
        this.mode = mode;
        this.locationOrMeetingUrl = locationOrMeetingUrl;
        this.note = note;
        this.status = status;
        this.meetingProvider = meetingProvider;
        this.meetingSyncStatus = meetingProvider == MeetingProvider.MANUAL
                ? MeetingSyncStatus.NOT_APPLICABLE : MeetingSyncStatus.PENDING;
        this.version = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getTimezone() { return timezone; }
    public int getDurationMinutes() { return durationMinutes; }
    public InterviewMode getMode() { return mode; }
    public String getLocationOrMeetingUrl() { return locationOrMeetingUrl; }
    public String getNote() { return note; }
    public InterviewStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public MeetingProvider getMeetingProvider() { return meetingProvider; }
    public MeetingSyncStatus getMeetingSyncStatus() { return meetingSyncStatus; }
    public String getMeetingEventId() { return meetingEventId; }
    public String getMeetingSyncError() { return meetingSyncError; }
    public String getMeetingCorrelationId() { return meetingCorrelationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void reschedule(Instant scheduledAt, String timezone, int durationMinutes, InterviewMode mode,
                           String locationOrMeetingUrl, String note, Instant now) {
        this.scheduledAt = scheduledAt;
        this.timezone = timezone;
        this.durationMinutes = durationMinutes;
        this.mode = mode;
        this.locationOrMeetingUrl = locationOrMeetingUrl;
        this.note = note;
        touch(now);
    }

    public void complete(Instant now) {
        this.status = InterviewStatus.COMPLETED;
        touch(now);
    }

    public void cancel(Instant now) {
        this.status = InterviewStatus.CANCELLED;
        touch(now);
    }

    /**
     * Records the stable provisioning correlation key at creation time. The same
     * value is used as the Google Calendar event id and conference requestId, so
     * a lost response can be recovered without creating a duplicate meeting.
     */
    public void assignCorrelationId(String correlationId) {
        this.meetingCorrelationId = correlationId;
    }

    public void markReady(String eventId, String meetingUrl, Instant now) {
        this.meetingSyncStatus = MeetingSyncStatus.READY;
        this.meetingEventId = eventId;
        this.locationOrMeetingUrl = meetingUrl;
        this.meetingSyncError = null;
        touch(now);
    }

    public void markPending(String eventId, Instant now) {
        this.meetingSyncStatus = MeetingSyncStatus.PENDING;
        this.meetingEventId = eventId;
        this.locationOrMeetingUrl = null;
        this.meetingSyncError = null;
        touch(now);
    }

    public void markFailed(String syncError, Instant now) {
        this.meetingSyncStatus = MeetingSyncStatus.FAILED;
        this.meetingSyncError = syncError;
        this.locationOrMeetingUrl = null;
        touch(now);
    }

    /**
     * Reserves the sync slot before an external reschedule/cancel: the interview
     * stays SCHEDULED with its current time and link, but the version increments
     * and the sync status moves to PENDING so a concurrent update is rejected.
     */
    public void beginSync(Instant now) {
        this.meetingSyncStatus = MeetingSyncStatus.PENDING;
        this.meetingSyncError = null;
        touch(now);
    }

    /**
     * Applies a successful initial-provisioning retry: writes the recovered event
     * id and verified Meet URL, marks the interview READY, and applies the merged
     * schedule from the retry — all in a single version increment. Unlike a normal
     * reschedule, this also establishes the external event that was missing after
     * the first provisioning attempt failed.
     */
    public void completeGoogleProvisionRetry(String eventId, String meetingUrl, Instant scheduledAt,
                                             String timezone, int durationMinutes, String note, Instant now) {
        this.meetingSyncStatus = MeetingSyncStatus.READY;
        this.meetingEventId = eventId;
        this.locationOrMeetingUrl = meetingUrl;
        this.meetingSyncError = null;
        this.scheduledAt = scheduledAt;
        this.timezone = timezone;
        this.durationMinutes = durationMinutes;
        this.note = note;
        touch(now);
    }

    /**
     * Applies a successful Google Meet reschedule. Only the time/timezone/
     * duration/note change; the mode stays ONLINE and the server-managed link and
     * event id are preserved.
     */
    public void completeGoogleReschedule(Instant scheduledAt, String timezone, int durationMinutes,
                                         String note, Instant now) {
        this.scheduledAt = scheduledAt;
        this.timezone = timezone;
        this.durationMinutes = durationMinutes;
        this.note = note;
        this.meetingSyncStatus = MeetingSyncStatus.READY;
        this.meetingSyncError = null;
        touch(now);
    }

    /**
     * Applies a successful Google Meet cancel: the interview is CANCELLED and the
     * now-defunct link is cleared. READY here means the external cancellation has
     * completed, not that a meeting link exists.
     */
    public void completeGoogleCancel(Instant now) {
        this.status = InterviewStatus.CANCELLED;
        this.locationOrMeetingUrl = null;
        this.meetingSyncStatus = MeetingSyncStatus.READY;
        this.meetingSyncError = null;
        touch(now);
    }

    /**
     * Records a failed sync without disturbing the candidate-visible invitation:
     * the old time, link, and status are preserved so the interview stays
     * SCHEDULED exactly as the candidate already saw it.
     */
    public void failSyncPreservingInvitation(String syncError, Instant now) {
        this.meetingSyncStatus = MeetingSyncStatus.FAILED;
        this.meetingSyncError = syncError;
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
        this.version += 1;
    }
}
