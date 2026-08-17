package com.adproject.community.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "community_posts")
public class CommunityPostEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "author_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String authorId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CommunityPostEntity() {}

    public CommunityPostEntity(String id, String authorId, String body, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
