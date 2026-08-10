package com.adproject.application.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "user_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String userId;
    @Column(name = "operation_name", nullable = false, length = 100)
    private String operationName;
    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;
    @Column(name = "resource_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String resourceId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecordEntity() {}

    public IdempotencyRecordEntity(String id, String userId, String operationName, String idempotencyKey,
                                   String requestHash, String resourceId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.operationName = operationName;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.resourceId = resourceId;
        this.createdAt = createdAt;
    }

    public String getRequestHash() { return requestHash; }
    public String getResourceId() { return resourceId; }
}
