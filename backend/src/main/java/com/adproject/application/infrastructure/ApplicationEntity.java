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
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "job_id", nullable = false, length = 36, columnDefinition = "char(36)") private String jobId;
    @Column(name = "candidate_id", nullable = false, length = 36, columnDefinition = "char(36)") private String candidateId;
    @Column(name = "resume_id", nullable = false, length = 36, columnDefinition = "char(36)") private String resumeId;
    @Column(name = "resume_snapshot_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)") private String resumeSnapshotId;
    @Column(name = "contact_email", nullable = false, length = 255) private String contactEmail;
    @Column(name = "share_profile", nullable = false) private boolean shareProfile;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ApplicationStatus status;
    @Column(name = "applied_at", nullable = false) private Instant appliedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(nullable = false) private int version;

    protected ApplicationEntity() {}

    public ApplicationEntity(String id, String jobId, String candidateId, String resumeId,
                             String resumeSnapshotId, String contactEmail, boolean shareProfile,
                             ApplicationStatus status, Instant appliedAt, Instant updatedAt, int version) {
        this.id = id;
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.resumeId = resumeId;
        this.resumeSnapshotId = resumeSnapshotId;
        this.contactEmail = contactEmail;
        this.shareProfile = shareProfile;
        this.status = status;
        this.appliedAt = appliedAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public String getJobId() { return jobId; }
    public String getCandidateId() { return candidateId; }
    public String getResumeId() { return resumeId; }
    public String getResumeSnapshotId() { return resumeSnapshotId; }
    public String getContactEmail() { return contactEmail; }
    public boolean isShareProfile() { return shareProfile; }
    public ApplicationStatus getStatus() { return status; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
