package com.adproject.recommendation.api;

import com.adproject.job.api.CandidateJobResponses.RecruiterContact;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.WorkplaceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class RecommendationDtos {
    private RecommendationDtos() {}

    public record JobPreferenceResponse(JobPreference data) {}

    public record JobPreference(
            List<String> desiredTitles,
            List<String> preferredLocations,
            List<WorkplaceType> workplaceTypes,
            List<EmploymentType> employmentTypes,
            Long minimumSalary,
            SalaryCurrency salaryCurrency,
            SalaryPeriod salaryPeriod,
            int version,
            Instant createdAt,
            Instant updatedAt) {}

    public record SaveJobPreferenceRequest(
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 200) String> desiredTitles,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 200) String> preferredLocations,
            @NotNull @Size(max = 3) List<WorkplaceType> workplaceTypes,
            @NotNull @Size(max = 3) List<EmploymentType> employmentTypes,
            @Min(0) Long minimumSalary,
            @NotNull SalaryCurrency salaryCurrency,
            @NotNull SalaryPeriod salaryPeriod,
            @Min(0) int expectedVersion) {}

    public record RecommendedJobResponse(List<RecommendedJob> data, RecommendationMeta meta) {}

    public record RecommendedJob(
            String jobId,
            String title,
            String companyId,
            String companyName,
            String location,
            EmploymentType employmentType,
            WorkplaceType workplaceType,
            long salaryMin,
            long salaryMax,
            SalaryCurrency salaryCurrency,
            SalaryPeriod salaryPeriod,
            String description,
            List<String> skills,
            int matchScore,
            int rank,
            MatchAnalysis matchAnalysis,
            boolean isSaved,
            RecruiterContact recruiter) {}

    public record MatchAnalysis(
            List<String> strongMatches,
            List<String> gaps,
            List<String> evidence) {}

    public record RecommendationMeta(
            String source,
            String modelVersion,
            String featureVersion,
            String modelStatus,
            int inferenceMs,
            Instant generatedAt,
            int page,
            int pageSize,
            int total,
            boolean hasNext) {}
}
