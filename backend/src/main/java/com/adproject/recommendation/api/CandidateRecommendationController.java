package com.adproject.recommendation.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.WorkplaceType;
import com.adproject.recommendation.api.RecommendationDtos.JobPreferenceResponse;
import com.adproject.recommendation.api.RecommendationDtos.RecommendedJobResponse;
import com.adproject.recommendation.api.RecommendationDtos.SaveJobPreferenceRequest;
import com.adproject.recommendation.application.CandidateJobPreferenceService;
import com.adproject.recommendation.application.CandidateRecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/candidate")
public class CandidateRecommendationController {
    private final CandidateJobPreferenceService preferenceService;
    private final CandidateRecommendationService recommendationService;

    public CandidateRecommendationController(
            CandidateJobPreferenceService preferenceService,
            CandidateRecommendationService recommendationService) {
        this.preferenceService = preferenceService;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/job-preferences")
    JobPreferenceResponse getPreferences(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return preferenceService.get(principal);
    }

    @PutMapping("/job-preferences")
    JobPreferenceResponse savePreferences(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SaveJobPreferenceRequest request) {
        return preferenceService.save(principal, request);
    }

    @GetMapping("/recommendations/jobs")
    RecommendedJobResponse recommendJobs(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) WorkplaceType workplaceType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) @Min(0) Long minimumSalary,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int pageSize) {
        return recommendationService.recommendJobs(principal, q, employmentType, workplaceType,
                location, minimumSalary, page, pageSize);
    }
}
