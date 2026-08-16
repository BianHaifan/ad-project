package com.adproject.recommendation.infrastructure;

import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "candidate_job_preferences")
public class CandidateJobPreferenceEntity {
    @Id
    @Column(name = "candidate_id", length = 36, columnDefinition = "char(36)")
    private String candidateId;
    @Column(name = "desired_titles_json", nullable = false, columnDefinition = "TEXT")
    private String desiredTitlesJson;
    @Column(name = "preferred_locations_json", nullable = false, columnDefinition = "TEXT")
    private String preferredLocationsJson;
    @Column(name = "workplace_types_json", nullable = false, columnDefinition = "TEXT")
    private String workplaceTypesJson;
    @Column(name = "employment_types_json", nullable = false, columnDefinition = "TEXT")
    private String employmentTypesJson;
    @Column(name = "minimum_salary")
    private Long minimumSalary;
    @Enumerated(EnumType.STRING)
    @Column(name = "salary_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private SalaryCurrency salaryCurrency;
    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", nullable = false, length = 16)
    private SalaryPeriod salaryPeriod;
    @Column(nullable = false)
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CandidateJobPreferenceEntity() {}

    public CandidateJobPreferenceEntity(
            String candidateId,
            String desiredTitlesJson,
            String preferredLocationsJson,
            String workplaceTypesJson,
            String employmentTypesJson,
            Long minimumSalary,
            SalaryCurrency salaryCurrency,
            SalaryPeriod salaryPeriod,
            int version,
            Instant createdAt,
            Instant updatedAt) {
        this.candidateId = candidateId;
        this.desiredTitlesJson = desiredTitlesJson;
        this.preferredLocationsJson = preferredLocationsJson;
        this.workplaceTypesJson = workplaceTypesJson;
        this.employmentTypesJson = employmentTypesJson;
        this.minimumSalary = minimumSalary;
        this.salaryCurrency = salaryCurrency;
        this.salaryPeriod = salaryPeriod;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void replace(
            String desiredTitlesJson,
            String preferredLocationsJson,
            String workplaceTypesJson,
            String employmentTypesJson,
            Long minimumSalary,
            SalaryCurrency salaryCurrency,
            SalaryPeriod salaryPeriod,
            Instant now) {
        this.desiredTitlesJson = desiredTitlesJson;
        this.preferredLocationsJson = preferredLocationsJson;
        this.workplaceTypesJson = workplaceTypesJson;
        this.employmentTypesJson = employmentTypesJson;
        this.minimumSalary = minimumSalary;
        this.salaryCurrency = salaryCurrency;
        this.salaryPeriod = salaryPeriod;
        this.version += 1;
        this.updatedAt = now;
    }

    public String getCandidateId() { return candidateId; }
    public String getDesiredTitlesJson() { return desiredTitlesJson; }
    public String getPreferredLocationsJson() { return preferredLocationsJson; }
    public String getWorkplaceTypesJson() { return workplaceTypesJson; }
    public String getEmploymentTypesJson() { return employmentTypesJson; }
    public Long getMinimumSalary() { return minimumSalary; }
    public SalaryCurrency getSalaryCurrency() { return salaryCurrency; }
    public SalaryPeriod getSalaryPeriod() { return salaryPeriod; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
