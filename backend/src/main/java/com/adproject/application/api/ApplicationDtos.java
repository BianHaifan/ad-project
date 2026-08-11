package com.adproject.application.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class ApplicationDtos {
    private ApplicationDtos() {}

    public record SubmitApplicationRequest(
            @NotBlank String resumeId,
            @NotBlank @Email String contactEmail,
            @NotNull Boolean shareProfile
    ) {}

    public record CandidateApplicationDetailResponse(CandidateApplicationDetail data) {}

    public record CandidateApplicationDetail(
            String applicationId,
            String jobId,
            String status,
            Instant appliedAt,
            Instant updatedAt,
            int version,
            String jobTitle,
            Company company,
            Integer matchScore,
            Instant scheduledAt,
            List<TimelineStep> timeline,
            ResumeSnapshot resumeSnapshot,
            Object interview,
            List<NextStep> nextSteps
    ) {}

    public record Company(String companyId, String name, String logoUrl, String stage, String employeeRange,
                          String verificationStatus, String website, String description, String location,
                          int version, Instant createdAt, Instant updatedAt) {}

    public record Experience(String experienceId, String title, String company, String description,
                             String startDate, String endDate) {}

    public record ResumeSnapshot(String snapshotId, Instant capturedAt, String resumeId, String fullName,
                                 int age, String location, String headline, String summary,
                                 List<Experience> experiences, int version, Instant createdAt, Instant updatedAt) {}

    public record TimelineStep(String status, boolean completed, Instant occurredAt) {}
    public record NextStep(String type, String title, String description) {}
}
