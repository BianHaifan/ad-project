package com.adproject.application.infrastructure;

import com.adproject.application.domain.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "applications")
public class ApplicationEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "job_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String jobId;
    @Column(name = "candidate_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String candidateId;
    @Column(name = "resume_snapshot_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String resumeSnapshotId;
    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;
    @Column(name = "share_profile", nullable = false)
    private boolean shareProfile;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status;
    @Column(nullable = false)
    private int version;
    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApplicationEntity() {}

    public ApplicationEntity(String id, String jobId, String candidateId, String resumeSnapshotId,
                             String contactEmail, boolean shareProfile, Instant now) {
        this.id = id;
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.resumeSnapshotId = resumeSnapshotId;
        this.contactEmail = contactEmail;
        this.shareProfile = shareProfile;
        this.status = ApplicationStatus.APPLIED;
        this.version = 1;
        this.appliedAt = now;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getJobId() { return jobId; }
    public String getCandidateId() { return candidateId; }
    public String getResumeSnapshotId() { return resumeSnapshotId; }
    public ApplicationStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
