package com.adproject.application.api;

import com.adproject.application.api.ApplicationResponses.SubmitApplicationResponse;
import com.adproject.application.application.ApplicationService;
import com.adproject.common.api.RequestIdFilter;
import com.adproject.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/applications")
public class ApplicationController {
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    ResponseEntity<SubmitApplicationResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitApplicationRequest request,
            HttpServletRequest httpRequest) {
        var response = applicationService.submit(currentUser.userId(), jobId, idempotencyKey, request,
                RequestIdFilter.current(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
