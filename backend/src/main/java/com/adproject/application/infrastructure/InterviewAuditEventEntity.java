package com.adproject.application.infrastructure;

import com.adproject.application.domain.InterviewAuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "interview_audit_events")
public class InterviewAuditEventEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "interview_id", nullable = false, length = 36, columnDefinition = "char(36)") private String interviewId;
    @Column(name = "application_id", nullable = false, length = 36, columnDefinition = "char(36)") private String applicationId;
    @Column(name = "actor_id", nullable = false, length = 36, columnDefinition = "char(36)") private String actorId;
    @Column(name = "company_id", nullable = false, length = 36, columnDefinition = "char(36)") private String companyId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private InterviewAuditAction action;
    @Column(name = "before_value", length = 2000) private String beforeValue;
    @Column(name = "after_value", nullable = false, length = 2000) private String afterValue;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(length = 500) private String reason;
    @Column(name = "request_id", nullable = false, length = 100) private String requestId;

    protected InterviewAuditEventEntity() {}

    public InterviewAuditEventEntity(String id, String interviewId, String applicationId, String actorId,
                                     String companyId, InterviewAuditAction action, String beforeValue,
                                     String afterValue, Instant occurredAt, String reason, String requestId) {
        this.id = id;
        this.interviewId = interviewId;
        this.applicationId = applicationId;
        this.actorId = actorId;
        this.companyId = companyId;
        this.action = action;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.occurredAt = occurredAt;
        this.reason = reason;
        this.requestId = requestId;
    }

    public String getId() { return id; }
    public String getInterviewId() { return interviewId; }
    public String getApplicationId() { return applicationId; }
    public String getActorId() { return actorId; }
    public String getCompanyId() { return companyId; }
    public InterviewAuditAction getAction() { return action; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getReason() { return reason; }
    public String getRequestId() { return requestId; }
}
