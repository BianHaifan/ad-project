package com.adproject.agent.api;

import com.adproject.resume.api.ResumeDtos.Experience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AgentDtos {
    private AgentDtos() {}

    public record CreateRunRequest(
            @NotBlank @Size(max = 2000) String instruction,
            @Size(max = 36) String conversationId,
            @Size(max = 36) String jobId,
            @Size(max = 64) String timezone
    ) {}

    public record ConfirmRunRequest(
            @NotBlank @Size(max = 100) String confirmationId,
            @Min(1) int expectedRunVersion
    ) {}

    public record Target(String type, String id) {}

    public record FieldChange(String field, Object oldValue, Object newValue) {}

    public record Preview(
            String confirmationId,
            String targetType,
            String targetId,
            int expectedVersion,
            Instant expiresAt,
            List<FieldChange> changes
    ) {}

    public record QueryResult(
            String section,
            String summary,
            List<String> skills,
            List<Experience> experiences
    ) {}

    public record ExecutionResult(
            String operation,
            String targetType,
            String targetId,
            int previousVersion,
            int newVersion,
            Instant completedAt,
            List<FieldChange> appliedChanges,
            QueryResult queryResult
    ) {}

    public record Step(
            int sequence,
            String type,
            String tool,
            String status,
            String inputSummary,
            String outputSummary,
            String errorCode,
            long durationMs,
            Instant createdAt
    ) {}

    public record RankedCandidate(
            String candidateId,
            String applicationId,
            String fullName,
            String applicationStatus,
            int rank,
            List<String> strongMatches,
            List<String> gaps
    ) {}

    public record ScreeningResult(
            String jobId,
            String jobTitle,
            List<RankedCandidate> ranked,
            String message
    ) {}

    public record Run(
            String runId,
            String conversationId,
            String instruction,
            String status,
            String confirmationStatus,
            Target target,
            List<Step> steps,
            Preview preview,
            ExecutionResult result,
            ScreeningResult screening,
            String message,
            String errorCode,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record RunResponse(Run data) {}

    public record Conversation(String conversationId, List<Run> runs) {}

    public record ConversationResponse(Conversation data) {}

    public record ConversationSummary(
            String conversationId,
            String lastInstruction,
            String lastMessage,
            Instant updatedAt
    ) {}

    public record ConversationListResponse(List<ConversationSummary> data) {}
}
