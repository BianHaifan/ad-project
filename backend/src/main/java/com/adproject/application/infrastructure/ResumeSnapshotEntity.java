package com.adproject.application.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resume_snapshots")
public class ResumeSnapshotEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "resume_id", nullable = false, length = 36, columnDefinition = "char(36)") private String resumeId;
    @Column(name = "candidate_id", nullable = false, length = 36, columnDefinition = "char(36)") private String candidateId;
    @Column(name = "full_name", nullable = false, length = 100) private String fullName;
    @Column(nullable = false) private int age;
    @Column(nullable = false, length = 100) private String location;
    @Column(nullable = false, length = 200) private String headline;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(name = "experiences_json", nullable = false, columnDefinition = "TEXT") private String experiencesJson;
    @Column(name = "skills_json", nullable = false, columnDefinition = "TEXT") private String skillsJson;
    @Column(name = "resume_version", nullable = false) private int resumeVersion;
    @Column(name = "resume_created_at", nullable = false) private Instant resumeCreatedAt;
    @Column(name = "resume_updated_at", nullable = false) private Instant resumeUpdatedAt;
    @Column(name = "captured_at", nullable = false) private Instant capturedAt;

    protected ResumeSnapshotEntity() {}

    public ResumeSnapshotEntity(String id, String resumeId, String candidateId, String fullName, int age,
                                String location, String headline, String summary, String experiencesJson,
                                String skillsJson,
                                int resumeVersion, Instant resumeCreatedAt, Instant resumeUpdatedAt,
                                Instant capturedAt) {
        this.id = id;
        this.resumeId = resumeId;
        this.candidateId = candidateId;
        this.fullName = fullName;
        this.age = age;
        this.location = location;
        this.headline = headline;
        this.summary = summary;
        this.experiencesJson = experiencesJson;
        this.skillsJson = skillsJson;
        this.resumeVersion = resumeVersion;
        this.resumeCreatedAt = resumeCreatedAt;
        this.resumeUpdatedAt = resumeUpdatedAt;
        this.capturedAt = capturedAt;
    }

    public String getId() { return id; }
    public String getResumeId() { return resumeId; }
    public String getFullName() { return fullName; }
    public int getAge() { return age; }
    public String getLocation() { return location; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public String getExperiencesJson() { return experiencesJson; }
    public String getSkillsJson() { return skillsJson; }
    public int getResumeVersion() { return resumeVersion; }
    public Instant getResumeCreatedAt() { return resumeCreatedAt; }
    public Instant getResumeUpdatedAt() { return resumeUpdatedAt; }
    public Instant getCapturedAt() { return capturedAt; }
}
