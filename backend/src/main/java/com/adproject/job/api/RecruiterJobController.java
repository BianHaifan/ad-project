package com.adproject.job.api;

import com.adproject.common.api.RequestIdFilter;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.job.api.JobResponses.JobListResponse;
import com.adproject.job.api.JobResponses.JobResponse;
import com.adproject.job.application.JobService;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recruiter/jobs")
public class RecruiterJobController {
    private final JobService jobService;

    public RecruiterJobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    ResponseEntity<JobResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.create(currentUser, request));
    }

    @GetMapping
    JobListResponse list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) JobStatus status,
                         @RequestParam(required = false) EmploymentType employmentType,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String ownerId,
                         @RequestParam(defaultValue = "1") @Min(1) int page,
                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return jobService.list(currentUser, q, status, employmentType, location, ownerId, page, pageSize);
    }

    @GetMapping("/{jobId}")
    JobResponse get(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable String jobId) {
        return jobService.get(currentUser, jobId);
    }

    @PostMapping("/{jobId}/publish")
    JobResponse publish(@AuthenticationPrincipal AuthenticatedUser currentUser,
                        @PathVariable String jobId,
                        @Valid @RequestBody PublishJobRequest request,
                        HttpServletRequest servletRequest) {
        return jobService.publish(currentUser, jobId, request, RequestIdFilter.current(servletRequest));
    }
}
