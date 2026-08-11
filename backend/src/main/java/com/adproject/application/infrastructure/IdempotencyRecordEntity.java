package com.adproject.application.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "user_id", nullable = false, length = 36, columnDefinition = "char(36)") private String userId;
    @Column(nullable = false, length = 100) private String operation;
    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)") private String idempotencyKey;
    @Column(name = "payload_hash", nullable = false, length = 64, columnDefinition = "char(64)") private String payloadHash;
    @Column(name = "application_id", nullable = false, length = 36, columnDefinition = "char(36)") private String applicationId;
    @Column(name = "http_status", nullable = false) private int httpStatus;
    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT") private String responseJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected IdempotencyRecordEntity() {}
    public IdempotencyRecordEntity(String id, String userId, String operation, String idempotencyKey,
                                   String payloadHash, String applicationId, int httpStatus,
                                   String responseJson, Instant createdAt) {
        this.id = id; this.userId = userId; this.operation = operation; this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash; this.applicationId = applicationId; this.httpStatus = httpStatus;
        this.responseJson = responseJson; this.createdAt = createdAt;
    }
    public String getPayloadHash() { return payloadHash; }
    public String getResponseJson() { return responseJson; }
}
