package com.adproject.job.api;

import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.Visibility;
import com.adproject.job.domain.WorkplaceType;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class UpdateJobRequest {
    @Size(max = 200)
    private String title;
    private EmploymentType employmentType;
    private WorkplaceType workplaceType;
    @Size(max = 100)
    private String location;
    @Valid
    private CreateJobRequest.Salary salary;
    private String description;
    private List<@NotBlank String> requirements;
    private List<@NotBlank String> skills;
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z$",
            message = "must be an ISO-8601 UTC date-time ending in Z")
    private String deadline;
    private boolean deadlinePresent;
    private Visibility visibility;
    @NotNull
    @Min(1)
    private Integer expectedVersion;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public WorkplaceType getWorkplaceType() { return workplaceType; }
    public void setWorkplaceType(WorkplaceType workplaceType) { this.workplaceType = workplaceType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public CreateJobRequest.Salary getSalary() { return salary; }
    public void setSalary(CreateJobRequest.Salary salary) { this.salary = salary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getRequirements() { return requirements; }
    public void setRequirements(List<String> requirements) { this.requirements = requirements; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public String getDeadline() { return deadline; }
    public boolean isDeadlinePresent() { return deadlinePresent; }
    @JsonSetter("deadline")
    public void setDeadline(String deadline) {
        this.deadline = deadline;
        this.deadlinePresent = true;
    }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
}
