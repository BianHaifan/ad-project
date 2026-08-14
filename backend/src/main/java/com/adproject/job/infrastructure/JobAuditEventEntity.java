package com.adproject.job.infrastructure;

import com.adproject.job.domain.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "job_audit_events")
public class JobAuditEventEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "job_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String jobId;
    @Column(name = "actor_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String actorId;
    @Column(name = "company_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String companyId;
    @Column(nullable = false, length = 32)
    private String action;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private JobStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private JobStatus toStatus;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    protected JobAuditEventEntity() {}

    public JobAuditEventEntity(String id, String jobId, String actorId, String companyId, String action,
                               JobStatus fromStatus, JobStatus toStatus, Instant occurredAt,
                               String reason, String requestId) {
        this.id = id;
        this.jobId = jobId;
        this.actorId = actorId;
        this.companyId = companyId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.occurredAt = occurredAt;
        this.reason = reason;
        this.requestId = requestId;
    }
}
