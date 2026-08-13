package com.adproject.conversation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversations")
public class ConversationEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "application_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)") private String applicationId;
    @Column(name = "job_id", nullable = false, length = 36, columnDefinition = "char(36)") private String jobId;
    @Column(name = "candidate_id", nullable = false, length = 36, columnDefinition = "char(36)") private String candidateId;
    @Column(name = "company_id", nullable = false, length = 36, columnDefinition = "char(36)") private String companyId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "last_message_at") private Instant lastMessageAt;

    protected ConversationEntity() {}

    public ConversationEntity(String id, String applicationId, String jobId, String candidateId, String companyId,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.companyId = companyId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public String getJobId() { return jobId; }
    public String getCandidateId() { return candidateId; }
    public String getCompanyId() { return companyId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }

    public void touch(Instant now) {
        this.updatedAt = now;
        this.lastMessageAt = now;
    }
}
