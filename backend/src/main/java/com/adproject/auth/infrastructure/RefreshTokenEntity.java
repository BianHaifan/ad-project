package com.adproject.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "user_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "replaced_by_token_id", length = 36, columnDefinition = "char(36)")
    private String replacedByTokenId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshTokenEntity() {}

    public RefreshTokenEntity(String id, String userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke(Instant revokedAt, String replacedByTokenId) {
        this.revokedAt = revokedAt;
        this.replacedByTokenId = replacedByTokenId;
    }
}
