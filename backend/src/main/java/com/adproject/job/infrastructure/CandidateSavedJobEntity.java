package com.adproject.job.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "candidate_saved_jobs")
public class CandidateSavedJobEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "candidate_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String candidateId;
    @Column(name = "job_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String jobId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CandidateSavedJobEntity() {}

    public CandidateSavedJobEntity(String id, String candidateId, String jobId, Instant createdAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.jobId = jobId;
        this.createdAt = createdAt;
    }

    public String getCandidateId() { return candidateId; }
    public String getJobId() { return jobId; }
    public Instant getCreatedAt() { return createdAt; }
}
