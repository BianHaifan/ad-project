package com.adproject.profile.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfileEntity {
    @Id
    @Column(name = "user_id", length = 36, columnDefinition = "char(36)")
    private String userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String bio;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecruiterProfileEntity() {}

    public RecruiterProfileEntity(String userId, String title, String bio, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.title = title;
        this.bio = bio;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getBio() { return bio; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String bio, Instant now) {
        this.title = title;
        this.bio = bio;
        this.updatedAt = now;
    }
}
