package com.adproject.profile.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.profile.api.RecruiterProfileDtos.ProfileResponse;
import com.adproject.profile.api.RecruiterProfileDtos.UpdateRecruiterProfileRequest;
import com.adproject.profile.application.RecruiterProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recruiter/profile")
public class RecruiterProfileController {
    private final RecruiterProfileService service;

    public RecruiterProfileController(RecruiterProfileService service) {
        this.service = service;
    }

    @GetMapping
    ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(user);
    }

    @PatchMapping
    ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                           @RequestBody UpdateRecruiterProfileRequest request) {
        return service.update(user, request);
    }
}
