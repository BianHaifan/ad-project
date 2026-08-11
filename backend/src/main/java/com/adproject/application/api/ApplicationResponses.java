package com.adproject.application.api;

import com.adproject.application.domain.ApplicationStatus;
import java.time.Instant;
import java.util.List;

public final class ApplicationResponses {
    private ApplicationResponses() {}

    public record SubmitApplicationResponse(CandidateApplicationDetail data) {}

    public record CandidateApplicationDetail(
            String applicationId,
            String jobId,
            ApplicationStatus status,
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
    ) {
        public CandidateApplicationDetail {
            timeline = List.copyOf(timeline);
            nextSteps = List.copyOf(nextSteps);
        }
    }

    public record Company(
            String companyId,
            String name,
            String logoUrl,
            String stage,
            String employeeRange,
            String verificationStatus,
            String website,
            String description,
            String location,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record TimelineStep(ApplicationStatus status, boolean completed, Instant occurredAt) {}

    public record ResumeSnapshot(
            String resumeId,
            String fullName,
            int age,
            String location,
            String headline,
            String summary,
            List<Experience> experiences,
            int version,
            Instant createdAt,
            Instant updatedAt,
            String snapshotId,
            Instant capturedAt
    ) {
        public ResumeSnapshot {
            experiences = List.copyOf(experiences);
        }
    }

    public record Experience(
            String experienceId,
            String title,
            String company,
            String description,
            String startDate,
            String endDate
    ) {}

    public record NextStep(String type, String title, String description) {}
}
