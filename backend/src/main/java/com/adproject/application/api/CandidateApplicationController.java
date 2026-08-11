package com.adproject.application.api;

import com.adproject.application.application.CandidateApplicationService;
import com.adproject.common.api.RequestIdFilter;
import com.adproject.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/applications")
public class CandidateApplicationController {
    private final CandidateApplicationService service;

    public CandidateApplicationController(CandidateApplicationService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ApplicationDtos.CandidateApplicationDetailResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String jobId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ApplicationDtos.SubmitApplicationRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(
                currentUser, jobId, idempotencyKey, request, RequestIdFilter.current(servletRequest)));
    }
}
