package com.adproject.application.infrastructure;

import com.adproject.resume.application.ResumeForApplication;
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
@Table(name = "resume_snapshots")
public class ResumeSnapshotEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "source_resume_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String sourceResumeId;
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
    @Column(name = "resume_version", nullable = false)
    private int resumeVersion;
    @Column(name = "resume_created_at", nullable = false)
    private Instant resumeCreatedAt;
    @Column(name = "resume_updated_at", nullable = false)
    private Instant resumeUpdatedAt;
    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected ResumeSnapshotEntity() {}

    public ResumeSnapshotEntity(String id, ResumeForApplication resume, Instant capturedAt) {
        this.id = id;
        this.sourceResumeId = resume.resumeId();
        this.fullName = resume.fullName();
        this.age = resume.age();
        this.location = resume.location();
        this.headline = resume.headline();
        this.summary = resume.summary();
        this.experiences = List.copyOf(resume.experiences());
        this.resumeVersion = resume.version();
        this.resumeCreatedAt = resume.createdAt();
        this.resumeUpdatedAt = resume.updatedAt();
        this.capturedAt = capturedAt;
    }

    public String getId() { return id; }
    public String getSourceResumeId() { return sourceResumeId; }
    public String getFullName() { return fullName; }
    public int getAge() { return age; }
    public String getLocation() { return location; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public List<ResumeExperience> getExperiences() { return List.copyOf(experiences); }
    public int getResumeVersion() { return resumeVersion; }
    public Instant getResumeCreatedAt() { return resumeCreatedAt; }
    public Instant getResumeUpdatedAt() { return resumeUpdatedAt; }
    public Instant getCapturedAt() { return capturedAt; }
}
