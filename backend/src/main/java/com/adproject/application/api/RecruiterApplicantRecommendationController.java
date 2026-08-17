package com.adproject.application.api;

import com.adproject.application.application.RecruiterApplicantRecommendationService;
import com.adproject.common.security.AuthenticatedUser;
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
@RequestMapping("/api/v1/recruiter/jobs/{jobId}/applicant-recommendations")
public class RecruiterApplicantRecommendationController {
    private final RecruiterApplicantRecommendationService service;

    public RecruiterApplicantRecommendationController(RecruiterApplicantRecommendationService service) {
        this.service = service;
    }

    @GetMapping
    RecruiterApplicantRecommendationDtos.RecommendedApplicantResponse recommend(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String jobId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.recommend(user, jobId, page, pageSize);
    }
}
