package com.adproject.application.api;

import com.adproject.application.domain.ApplicationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class RecruiterApplicationDtos {
    private RecruiterApplicationDtos() {}
    public enum TransitionTarget { IN_REVIEW, INTERVIEW, REJECTED }

    public record CandidateSummary(String candidateId, String fullName, String email, String headline,
                                   String avatarUrl, String location) {}
    public record User(String userId, String role, String fullName, String email, String avatarUrl,
                       Instant createdAt, Instant updatedAt) {}
    public record Summary(String applicationId, String jobId, String status, Instant appliedAt, Instant updatedAt,
                          int version, CandidateSummary candidate, String jobTitle, Integer matchScore, User owner) {}
    public record Detail(String applicationId, String jobId, String status, Instant appliedAt, Instant updatedAt,
                         int version, CandidateSummary candidate, String jobTitle, Integer matchScore, User owner,
                         ApplicationDtos.ResumeSnapshot resumeSnapshot, List<AuditEvent> timeline,
                         Object matchAnalysis, Object interview, List<Object> notes) {}
    public record AuditEvent(String eventId, String actorId, String companyId, String fromStatus, String toStatus,
                             Instant occurredAt, String reason, String requestId) {}
    public record Counts(long applied, long inReview, long interview, long rejected) {}
    public record Meta(int page, int pageSize, long total, boolean hasNext, Counts counts) {}
    public record ListResponse(List<Summary> data, Meta meta) {}
    public record DetailResponse(Detail data) {}
    public record TransitionRequest(@NotNull TransitionTarget toStatus,
                                    @NotBlank @Size(max = 500) String reason,
                                    @Min(1) int expectedVersion) {}
    public record TransitionResult(Detail application, AuditEvent event) {}
    public record TransitionResponse(TransitionResult data) {}
}
