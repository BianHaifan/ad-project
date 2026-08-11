package com.adproject.job.infrastructure;

import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.Visibility;
import com.adproject.job.domain.WorkplaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "jobs")
public class JobEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "company_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String companyId;
    @Column(name = "created_by", nullable = false, length = 36, columnDefinition = "char(36)")
    private String createdBy;
    @Column(name = "owner_id", length = 36, columnDefinition = "char(36)")
    private String ownerId;
    @Column(nullable = false, length = 200)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 32)
    private EmploymentType employmentType;
    @Enumerated(EnumType.STRING)
    @Column(name = "workplace_type", nullable = false, length = 32)
    private WorkplaceType workplaceType;
    @Column(nullable = false, length = 100)
    private String location;
    @Column(name = "salary_min", nullable = false)
    private long salaryMin;
    @Column(name = "salary_max", nullable = false)
    private long salaryMax;
    @Enumerated(EnumType.STRING)
    @Column(name = "salary_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private SalaryCurrency salaryCurrency;
    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", nullable = false, length = 16)
    private SalaryPeriod salaryPeriod;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @Column(name = "requirements_json", nullable = false, columnDefinition = "TEXT")
    private String requirementsJson;
    @Column(name = "skills_json", nullable = false, columnDefinition = "TEXT")
    private String skillsJson;
    private Instant deadline;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status;
    @Column(name = "applicant_count", nullable = false)
    private int applicantCount;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(nullable = false)
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobEntity() {}

    public JobEntity(String id, String companyId, String createdBy, String ownerId, String title,
                     EmploymentType employmentType, WorkplaceType workplaceType, String location,
                     long salaryMin, long salaryMax, SalaryCurrency salaryCurrency, SalaryPeriod salaryPeriod,
                     String description, String requirementsJson, String skillsJson, Instant deadline,
                     Visibility visibility, JobStatus status, int applicantCount, int version,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = companyId;
        this.createdBy = createdBy;
        this.ownerId = ownerId;
        this.title = title;
        this.employmentType = employmentType;
        this.workplaceType = workplaceType;
        this.location = location;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.salaryCurrency = salaryCurrency;
        this.salaryPeriod = salaryPeriod;
        this.description = description;
        this.requirementsJson = requirementsJson;
        this.skillsJson = skillsJson;
        this.deadline = deadline;
        this.visibility = visibility;
        this.status = status;
        this.applicantCount = applicantCount;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getCreatedBy() { return createdBy; }
    public String getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public WorkplaceType getWorkplaceType() { return workplaceType; }
    public String getLocation() { return location; }
    public long getSalaryMin() { return salaryMin; }
    public long getSalaryMax() { return salaryMax; }
    public SalaryCurrency getSalaryCurrency() { return salaryCurrency; }
    public SalaryPeriod getSalaryPeriod() { return salaryPeriod; }
    public String getDescription() { return description; }
    public String getRequirementsJson() { return requirementsJson; }
    public String getSkillsJson() { return skillsJson; }
    public Instant getDeadline() { return deadline; }
    public Visibility getVisibility() { return visibility; }
    public JobStatus getStatus() { return status; }
    public int getApplicantCount() { return applicantCount; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void publish(Instant now) {
        this.status = JobStatus.ACTIVE;
        this.publishedAt = now;
        this.updatedAt = now;
        this.version += 1;
    }

    public void changeStatus(JobStatus targetStatus, Instant now) {
        this.status = targetStatus;
        this.updatedAt = now;
        this.version += 1;
    }

    public void updateDetails(String title, EmploymentType employmentType, WorkplaceType workplaceType,
                              String location, long salaryMin, long salaryMax, SalaryCurrency salaryCurrency,
                              SalaryPeriod salaryPeriod, String description, String requirementsJson,
                              String skillsJson, Instant deadline, Visibility visibility, Instant now) {
        this.title = title;
        this.employmentType = employmentType;
        this.workplaceType = workplaceType;
        this.location = location;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.salaryCurrency = salaryCurrency;
        this.salaryPeriod = salaryPeriod;
        this.description = description;
        this.requirementsJson = requirementsJson;
        this.skillsJson = skillsJson;
        this.deadline = deadline;
        this.visibility = visibility;
        this.updatedAt = now;
        this.version += 1;
    }
}
