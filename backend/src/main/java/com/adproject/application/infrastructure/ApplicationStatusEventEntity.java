package com.adproject.application.infrastructure;

import com.adproject.application.domain.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "application_status_events")
public class ApplicationStatusEventEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "application_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String applicationId;
    @Column(name = "actor_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String actorId;
    @Column(name = "company_id", length = 36, columnDefinition = "char(36)")
    private String companyId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private ApplicationStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private ApplicationStatus toStatus;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(length = 500)
    private String reason;
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    protected ApplicationStatusEventEntity() {}

    public ApplicationStatusEventEntity(String id, String applicationId, String actorId, ApplicationStatus fromStatus,
                                        ApplicationStatus toStatus, Instant occurredAt, String reason,
                                        String requestId) {
        this.id = id;
        this.applicationId = applicationId;
        this.actorId = actorId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.occurredAt = occurredAt;
        this.reason = reason;
        this.requestId = requestId;
    }

    public ApplicationStatus getToStatus() { return toStatus; }
    public Instant getOccurredAt() { return occurredAt; }
}
