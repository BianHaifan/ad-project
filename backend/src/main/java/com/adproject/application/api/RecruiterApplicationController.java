package com.adproject.application.api;

import com.adproject.application.application.RecruiterApplicationService;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.common.api.RequestIdFilter;
import com.adproject.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/recruiter/applications")
public class RecruiterApplicationController {
    private final RecruiterApplicationService service;
    public RecruiterApplicationController(RecruiterApplicationService service) { this.service = service; }

    @GetMapping
    RecruiterApplicationDtos.ListResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String jobId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String sort) {
        return service.list(user, status, jobId, q, page, pageSize, sort);
    }

    @GetMapping("/{applicationId}")
    RecruiterApplicationDtos.DetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable String applicationId) {
        return service.detail(user, applicationId);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{applicationId}/transitions")
    RecruiterApplicationDtos.TransitionResponse transition(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String applicationId, @Valid @RequestBody RecruiterApplicationDtos.TransitionRequest request,
            HttpServletRequest servletRequest) {
        return service.transition(user, applicationId, request, RequestIdFilter.current(servletRequest));
    }
}
