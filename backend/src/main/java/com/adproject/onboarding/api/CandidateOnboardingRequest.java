package com.adproject.onboarding.api;

import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.WorkplaceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CandidateOnboardingRequest(
        @NotBlank @Size(max = 200) String headline,
        @NotBlank @Size(max = 100) String location,
        @Min(16) @Max(100) int age,
        @NotBlank @Size(max = 5000) String resumeSummary,
        @NotNull @Size(min = 1, max = 100) List<@NotBlank @Size(max = 200) String> skills,
        @NotBlank @Size(max = 200) String desiredTitle,
        @NotBlank @Size(max = 200) String preferredLocation,
        @NotNull WorkplaceType workplaceType,
        @NotNull EmploymentType employmentType) {}
