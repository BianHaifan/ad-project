package com.adproject.job.api;

import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.Visibility;
import com.adproject.job.domain.WorkplaceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateJobRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull EmploymentType employmentType,
        @NotNull WorkplaceType workplaceType,
        @NotBlank @Size(max = 100) String location,
        @NotNull @Valid Salary salary,
        @NotBlank String description,
        @NotNull @Size(min = 1, max = 100) List<@NotBlank @Size(max = 200) String> requirements,
        @NotNull @Size(min = 1, max = 100) List<@NotBlank @Size(max = 200) String> skills,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z$",
                message = "must be an ISO-8601 UTC date-time ending in Z") String deadline,
        @NotNull Visibility visibility
) {
    public record Salary(
            @NotNull @Min(0) Long min,
            @NotNull @Min(0) Long max,
            @NotNull SalaryCurrency currency,
            @NotNull SalaryPeriod period
    ) {}
}
