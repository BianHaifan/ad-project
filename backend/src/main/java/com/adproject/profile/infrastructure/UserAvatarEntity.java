package com.adproject.profile.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "user_avatars")
public class UserAvatarEntity {
    @Id @Column(name = "user_id", length = 36, columnDefinition = "char(36)") private String userId;
    @Column(name = "content_type", nullable = false, length = 32) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @JdbcTypeCode(Types.BLOB) @Column(nullable = false, columnDefinition = "LONGBLOB") private byte[] content;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected UserAvatarEntity() {}

    public UserAvatarEntity(String userId, String contentType, long sizeBytes, byte[] content,
                            Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getUserId() { return userId; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void replace(String contentType, long sizeBytes, byte[] content, Instant updatedAt) {
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.content = content;
        this.updatedAt = updatedAt;
    }
}
