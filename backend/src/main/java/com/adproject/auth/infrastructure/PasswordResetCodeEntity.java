package com.adproject.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCodeEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "user_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String userId;
    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordResetCodeEntity() {}

    public PasswordResetCodeEntity(String id, String userId, String codeHash, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void recordFailedAttempt() { attemptCount++; }
    public void consume(Instant now) { consumedAt = now; }
}
