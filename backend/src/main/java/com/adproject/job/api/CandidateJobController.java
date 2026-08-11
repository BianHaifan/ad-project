package com.adproject.job.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.job.api.CandidateJobResponses.CandidateJobDetailResponse;
import com.adproject.job.api.CandidateJobResponses.CandidateJobListResponse;
import com.adproject.job.application.CandidateJobQueryService;
import com.adproject.job.domain.EmploymentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/jobs")
public class CandidateJobController {
    private final CandidateJobQueryService queryService;

    public CandidateJobController(CandidateJobQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    CandidateJobListResponse list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) EmploymentType employmentType,
                                  @RequestParam(defaultValue = "1") @Min(1) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return queryService.list(currentUser, q, employmentType, page, pageSize);
    }

    @GetMapping("/{jobId}")
    CandidateJobDetailResponse get(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                   @PathVariable String jobId) {
        return queryService.get(currentUser, jobId);
    }
}
