package com.adproject.agent.infrastructure;

import com.adproject.agent.domain.AgentStepStatus;
import com.adproject.agent.domain.AgentStepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_steps")
public class AgentStepEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;

    @Column(name = "run_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String runId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 32)
    private AgentStepType stepType;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "input_summary", length = 500)
    private String inputSummary;

    @Column(name = "output_summary", length = 500)
    private String outputSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentStepStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentStepEntity() {}

    public AgentStepEntity(String id, String runId, int sequenceNo, AgentStepType stepType, String toolName,
                           String inputSummary, String outputSummary, AgentStepStatus status,
                           String errorCode, long durationMs, Instant createdAt) {
        this.id = id;
        this.runId = runId;
        this.sequenceNo = sequenceNo;
        this.stepType = stepType;
        this.toolName = toolName;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.status = status;
        this.errorCode = errorCode;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public int getSequenceNo() { return sequenceNo; }
    public AgentStepType getStepType() { return stepType; }
    public String getToolName() { return toolName; }
    public String getInputSummary() { return inputSummary; }
    public String getOutputSummary() { return outputSummary; }
    public AgentStepStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }
}
