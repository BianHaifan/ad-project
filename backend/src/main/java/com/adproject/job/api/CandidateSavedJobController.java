package com.adproject.job.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.job.api.CandidateJobResponses.CandidateJobListResponse;
import com.adproject.job.application.CandidateJobQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/candidate/saved-jobs")
public class CandidateSavedJobController {
    private final CandidateJobQueryService queryService;

    public CandidateSavedJobController(CandidateJobQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    CandidateJobListResponse list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                  @RequestParam(defaultValue = "1") @Min(1) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return queryService.savedJobs(currentUser, page, pageSize);
    }

    @PutMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void save(@AuthenticationPrincipal AuthenticatedUser currentUser,
              @PathVariable String jobId) {
        queryService.save(currentUser, jobId);
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsave(@AuthenticationPrincipal AuthenticatedUser currentUser,
                @PathVariable String jobId) {
        queryService.unsave(currentUser, jobId);
    }
}
