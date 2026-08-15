package com.adproject.integration.google.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "google_oauth_states")
public class GoogleOAuthStateEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "state_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String stateHash;
    @Column(name = "recruiter_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String recruiterId;
    @Column(name = "pkce_verifier_encrypted", nullable = false, columnDefinition = "TEXT")
    private String pkceVerifierEncrypted;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected GoogleOAuthStateEntity() {}

    public GoogleOAuthStateEntity(String id, String stateHash, String recruiterId,
                                  String pkceVerifierEncrypted, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.stateHash = stateHash;
        this.recruiterId = recruiterId;
        this.pkceVerifierEncrypted = pkceVerifierEncrypted;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.consumedAt = null;
    }

    public String getStateHash() { return stateHash; }
    public String getRecruiterId() { return recruiterId; }
    public String getPkceVerifierEncrypted() { return pkceVerifierEncrypted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }

    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
