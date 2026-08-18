package com.adproject.community.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import com.adproject.community.domain.CommunityCategory;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CommunityCategory category;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CommunityPostEntity() {}

    public CommunityPostEntity(String id, String authorId, String body, CommunityCategory category, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.authorId = authorId;
        this.body = body;
        this.category = category;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public CommunityCategory getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
