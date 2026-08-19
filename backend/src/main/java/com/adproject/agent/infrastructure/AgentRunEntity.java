package com.adproject.agent.infrastructure;

import com.adproject.agent.domain.AgentConfirmationStatus;
import com.adproject.agent.domain.AgentRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_runs")
public class AgentRunEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;

    @Column(name = "user_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String userId;

    @Column(name = "conversation_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String conversationId;

    @Column(name = "job_id", length = 36, columnDefinition = "char(36)")
    private String jobId;

    @Column(nullable = false, length = 2000)
    private String instruction;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id", length = 36, columnDefinition = "char(36)")
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_status", nullable = false, length = 32)
    private AgentConfirmationStatus confirmationStatus;

    @Column(name = "preview_json", columnDefinition = "TEXT")
    private String previewJson;

    @Column(name = "preview_expires_at")
    private Instant previewExpiresAt;

    @Column(name = "confirmation_id", length = 36, columnDefinition = "char(36)")
    private String confirmationId;

    @Column(name = "execution_idempotency_key", length = 36, columnDefinition = "char(36)")
    private String executionIdempotencyKey;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(length = 500)
    private String message;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentRunEntity() {}

    public AgentRunEntity(String id, String userId, String conversationId, String instruction,
                          Instant now) {
        this.id = id;
        this.userId = userId;
        this.conversationId = conversationId;
        this.instruction = instruction;
        this.status = AgentRunStatus.PROCESSING;
        this.confirmationStatus = AgentConfirmationStatus.NOT_REQUIRED;
        this.version = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void awaitingConfirmation(String targetType, String targetId, String previewJson, String confirmationId,
                                     Instant expiresAt, String message, Instant now) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.previewJson = previewJson;
        this.previewExpiresAt = expiresAt;
        this.confirmationId = confirmationId;
        this.message = message;
        this.errorCode = null;
        this.status = AgentRunStatus.AWAITING_CONFIRMATION;
        this.confirmationStatus = AgentConfirmationStatus.PENDING;
        touch(now);
    }

    public void needsClarification(String message, Instant now) {
        this.message = message;
        this.errorCode = null;
        this.status = AgentRunStatus.NEEDS_CLARIFICATION;
        this.confirmationStatus = AgentConfirmationStatus.NOT_REQUIRED;
        touch(now);
    }

    public void noActionRequired(String targetType, String targetId, String message, Instant now) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.message = message;
        this.errorCode = null;
        this.status = AgentRunStatus.NO_ACTION_REQUIRED;
        this.confirmationStatus = AgentConfirmationStatus.NOT_REQUIRED;
        this.completedAt = now;
        touch(now);
    }

    public void fail(String errorCode, String message, Instant now) {
        this.errorCode = errorCode;
        this.message = message;
        this.status = AgentRunStatus.FAILED;
        this.confirmationStatus = AgentConfirmationStatus.NOT_REQUIRED;
        touch(now);
    }

    public void cancel(Instant now) {
        this.status = AgentRunStatus.CANCELLED;
        this.confirmationStatus = AgentConfirmationStatus.CANCELLED;
        this.message = "The agent run was cancelled.";
        touch(now);
    }

    public void expire(String message, Instant now) {
        this.status = AgentRunStatus.FAILED;
        this.confirmationStatus = AgentConfirmationStatus.EXPIRED;
        this.errorCode = "AGENT_CONFIRMATION_EXPIRED";
        this.message = message;
        touch(now);
    }

    public void rejectExecution(String errorCode, String message, Instant now) {
        this.status = AgentRunStatus.FAILED;
        this.confirmationStatus = AgentConfirmationStatus.EXPIRED;
        this.errorCode = errorCode;
        this.message = message;
        touch(now);
    }

    public void startExecution(String idempotencyKey, Instant now) {
        this.status = AgentRunStatus.EXECUTING;
        this.confirmationStatus = AgentConfirmationStatus.CONFIRMED;
        this.executionIdempotencyKey = idempotencyKey;
        this.confirmedAt = now;
        this.errorCode = null;
        this.message = "The confirmed change is being applied.";
        touch(now);
    }

    public void complete(String resultJson, String message, Instant now) {
        this.status = AgentRunStatus.COMPLETED;
        this.resultJson = resultJson;
        this.completedAt = now;
        this.errorCode = null;
        this.message = message;
        touch(now);
    }

    public void completeRead(String targetType, String targetId, String resultJson, String message, Instant now) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.confirmationStatus = AgentConfirmationStatus.NOT_REQUIRED;
        complete(resultJson, message, now);
    }

    public void completeChat(String message, Instant now) {
        this.confirmationStatus = AgentConfirmationStatus.NOT_REQUIRED;
        complete(null, message, now);
    }

    private void touch(Instant now) {
        this.version++;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getConversationId() { return conversationId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getInstruction() { return instruction; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public AgentRunStatus getStatus() { return status; }
    public AgentConfirmationStatus getConfirmationStatus() { return confirmationStatus; }
    public String getPreviewJson() { return previewJson; }
    public Instant getPreviewExpiresAt() { return previewExpiresAt; }
    public String getConfirmationId() { return confirmationId; }
    public String getExecutionIdempotencyKey() { return executionIdempotencyKey; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getResultJson() { return resultJson; }
    public String getMessage() { return message; }
    public String getErrorCode() { return errorCode; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
