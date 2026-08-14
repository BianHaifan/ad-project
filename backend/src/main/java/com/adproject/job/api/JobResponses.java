package com.adproject.job.api;

import java.time.Instant;
import java.util.List;

public final class JobResponses {
    private JobResponses() {}

    public record JobResponse(RecruiterJobDetail data) {}
    public record JobListResponse(List<RecruiterJobDetail> data, PageMeta meta) {}
    public record PageMeta(int page, int pageSize, long total, boolean hasNext) {}
    public record Salary(long min, long max, String currency, String period) {}
    public record Company(String companyId, String name, String logoUrl, String stage, String employeeRange,
                          String verificationStatus, String website, String description, String location,
                          int version, Instant createdAt, Instant updatedAt) {}
    public record User(String userId, String role, String fullName, String email, String avatarUrl,
                       Instant createdAt, Instant updatedAt) {}
    public record RecruiterJobDetail(
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
            int applicantCount,
            User owner
    ) {}
}
