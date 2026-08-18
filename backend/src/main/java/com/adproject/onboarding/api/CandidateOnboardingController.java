package com.adproject.onboarding.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.onboarding.application.CandidateOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/candidate/onboarding")
public class CandidateOnboardingController {
    private final CandidateOnboardingService service;
    public CandidateOnboardingController(CandidateOnboardingService service) { this.service = service; }

    @PostMapping
    ResponseEntity<Void> complete(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CandidateOnboardingRequest request) {
        service.complete(user, request);
        return ResponseEntity.noContent().build();
    }
}
