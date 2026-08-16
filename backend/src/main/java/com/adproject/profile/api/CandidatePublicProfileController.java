package com.adproject.profile.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.profile.api.CandidatePublicProfileDtos.CompanyPublicProfileResponse;
import com.adproject.profile.api.CandidatePublicProfileDtos.RecruiterPublicProfileResponse;
import com.adproject.profile.application.CandidatePublicProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/candidate")
public class CandidatePublicProfileController {
    private final CandidatePublicProfileService service;

    public CandidatePublicProfileController(CandidatePublicProfileService service) {
        this.service = service;
    }

    @GetMapping("/recruiters/{recruiterId}")
    RecruiterPublicProfileResponse recruiter(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable String recruiterId) {
        return service.getRecruiter(user, recruiterId);
    }

    @GetMapping("/companies/{companyId}")
    CompanyPublicProfileResponse company(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable String companyId) {
        return service.getCompany(user, companyId);
    }
}
