package com.adproject.job.api;

import java.time.Instant;
import java.util.List;

public final class CandidateJobResponses {
    private CandidateJobResponses() {}

    public record CandidateJobListResponse(List<CandidateJobSummary> data, PageMeta meta) {}
    public record CandidateJobDetailResponse(CandidateJobDetail data) {}
    public record PageMeta(int page, int pageSize, long total, boolean hasNext) {}
    public record Salary(long min, long max, String currency, String period) {}
    public record Company(String companyId, String name, String logoUrl, String stage, String employeeRange,
                          String verificationStatus, String website, String description, String location,
                          int version, Instant createdAt, Instant updatedAt) {}
    public record RecruiterContact(String recruiterId, String fullName, String title, String avatarUrl) {}
    public record CandidateJobSummary(
            String jobId,
            String title,
            Company company,
            String employmentType,
            String workplaceType,
            String location,
            Salary salary,
            String description,
            List<String> requirements,
            List<String> skills,
            Instant deadline,
            String visibility,
            String status,
            Instant publishedAt,
            int version,
            Instant createdAt,
            Instant updatedAt,
            Integer matchScore,
            RecruiterContact recruiter
    ) {}
    public record CandidateJobDetail(
            String jobId,
            String title,
            Company company,
            String employmentType,
            String workplaceType,
            String location,
            Salary salary,
            String description,
            List<String> requirements,
            List<String> skills,
            Instant deadline,
            String visibility,
            String status,
            Instant publishedAt,
            int version,
            Instant createdAt,
            Instant updatedAt,
            Integer matchScore,
            RecruiterContact recruiter,
            Object matchAnalysis,
            String applicationState,
            boolean isSaved
    ) {}
}
