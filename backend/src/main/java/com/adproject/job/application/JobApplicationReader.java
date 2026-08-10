package com.adproject.job.application;

import com.adproject.common.api.ApiException;
import com.adproject.company.application.CompanyQueryService;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.JobVisibility;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobApplicationReader {
    private final JobRepository jobRepository;
    private final CompanyQueryService companyQueryService;
    private final Clock clock;

    public JobApplicationReader(JobRepository jobRepository, CompanyQueryService companyQueryService, Clock clock) {
        this.jobRepository = jobRepository;
        this.companyQueryService = companyQueryService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JobForApplication lockAcceptingJob(String jobId) {
        JobEntity job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Job not found"));
        boolean deadlinePassed = job.getDeadline() != null && !job.getDeadline().isAfter(clock.instant());
        if (job.getStatus() != JobStatus.ACTIVE || job.getVisibility() != JobVisibility.PUBLIC || deadlinePassed) {
            throw new ApiException(HttpStatus.CONFLICT, "JOB_NOT_ACCEPTING_APPLICATIONS",
                    "Job is not accepting applications");
        }
        return new JobForApplication(job.getId(), job.getTitle(), companyQueryService.require(job.getCompanyId()));
    }

    public JobForApplication requireJob(String jobId) {
        JobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Job not found"));
        return new JobForApplication(job.getId(), job.getTitle(), companyQueryService.require(job.getCompanyId()));
    }
}
