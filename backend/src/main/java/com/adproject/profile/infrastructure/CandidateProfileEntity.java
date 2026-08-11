package com.adproject.profile.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "candidate_profiles")
public class CandidateProfileEntity {
    @Id @Column(name = "user_id", length = 36, columnDefinition = "char(36)") private String userId;
    @Column(nullable = false, length = 200) private String headline;
    @Column(nullable = false, length = 100) private String location;
    @Column(nullable = false) private int version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected CandidateProfileEntity() {}
    public CandidateProfileEntity(String userId, String headline, String location, int version, Instant createdAt, Instant updatedAt) {
        this.userId = userId; this.headline = headline; this.location = location; this.version = version;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }
    public String getHeadline() { return headline; }
    public String getLocation() { return location; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(String headline, String location, Instant now) {
        this.headline = headline; this.location = location; this.version++; this.updatedAt = now;
    }
}
