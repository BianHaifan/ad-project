package com.adproject.job.infrastructure;

import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.JobVisibility;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.WorkplaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    private int salaryMin;
    @Column(name = "salary_max", nullable = false)
    private int salaryMax;
    @Column(name = "salary_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String salaryCurrency;
    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", nullable = false, length = 16)
    private SalaryPeriod salaryPeriod;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirements_json", nullable = false, columnDefinition = "json")
    private List<String> requirements;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills_json", nullable = false, columnDefinition = "json")
    private List<String> skills;
    @Column
    private Instant deadline;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobVisibility visibility;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(nullable = false)
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobEntity() {}

    public JobEntity(String id, String companyId, String createdBy, String title, EmploymentType employmentType,
                     WorkplaceType workplaceType, String location, int salaryMin, int salaryMax,
                     String salaryCurrency, SalaryPeriod salaryPeriod, String description, List<String> requirements,
                     List<String> skills, Instant deadline, JobVisibility visibility, JobStatus status,
                     Instant publishedAt, int version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = companyId;
        this.createdBy = createdBy;
        this.title = title;
        this.employmentType = employmentType;
        this.workplaceType = workplaceType;
        this.location = location;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.salaryCurrency = salaryCurrency;
        this.salaryPeriod = salaryPeriod;
        this.description = description;
        this.requirements = List.copyOf(requirements);
        this.skills = List.copyOf(skills);
        this.deadline = deadline;
        this.visibility = visibility;
        this.status = status;
        this.publishedAt = publishedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getTitle() { return title; }
    public Instant getDeadline() { return deadline; }
    public JobVisibility getVisibility() { return visibility; }
    public JobStatus getStatus() { return status; }
}
