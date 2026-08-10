package com.adproject.resume.infrastructure;

import com.adproject.resume.domain.ResumeExperience;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resumes")
public class ResumeEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "candidate_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String candidateId;
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;
    @Column(nullable = false)
    private int age;
    @Column(nullable = false, length = 100)
    private String location;
    @Column(nullable = false, length = 200)
    private String headline;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "experiences_json", nullable = false, columnDefinition = "json")
    private List<ResumeExperience> experiences;
    @Column(nullable = false)
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ResumeEntity() {}

    public ResumeEntity(String id, String candidateId, String fullName, int age, String location, String headline,
                        String summary, List<ResumeExperience> experiences, int version, Instant createdAt,
                        Instant updatedAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.fullName = fullName;
        this.age = age;
        this.location = location;
        this.headline = headline;
        this.summary = summary;
        this.experiences = List.copyOf(experiences);
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getCandidateId() { return candidateId; }
    public String getFullName() { return fullName; }
    public int getAge() { return age; }
    public String getLocation() { return location; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public List<ResumeExperience> getExperiences() { return List.copyOf(experiences); }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
