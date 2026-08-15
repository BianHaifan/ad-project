package com.adproject.integration.google.infrastructure;

import com.adproject.integration.google.domain.GoogleConnectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "google_recruiter_connections")
public class GoogleRecruiterConnectionEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "recruiter_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String recruiterId;
    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;
    @Column(name = "refresh_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenEncrypted;
    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GoogleConnectionStatus status;
    @Column(nullable = false)
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GoogleRecruiterConnectionEntity() {}

    public GoogleRecruiterConnectionEntity(String id, String recruiterId, String accessTokenEncrypted,
                                           String refreshTokenEncrypted, Instant accessTokenExpiresAt,
                                           GoogleConnectionStatus status, Instant now) {
        this.id = id;
        this.recruiterId = recruiterId;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.status = status;
        this.version = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getRecruiterId() { return recruiterId; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public GoogleConnectionStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void replaceTokens(String accessTokenEncrypted, String refreshTokenEncrypted,
                              Instant accessTokenExpiresAt, Instant now) {
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.updatedAt = now;
        this.version += 1;
    }

    public void markRevoked(Instant now) {
        this.status = GoogleConnectionStatus.REVOKED;
        this.updatedAt = now;
        this.version += 1;
    }

    /**
     * Reconnects an existing (possibly {@link GoogleConnectionStatus#REVOKED})
     * connection: stores fresh tokens, clears the revoked flag, and bumps the
     * version and timestamps. Used by the OAuth callback so a successful
     * re-authorization restores a previously revoked connection.
     */
    public void reconnect(String accessTokenEncrypted, String refreshTokenEncrypted,
                          Instant accessTokenExpiresAt, Instant now) {
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.status = GoogleConnectionStatus.CONNECTED;
        this.updatedAt = now;
        this.version += 1;
    }
}
