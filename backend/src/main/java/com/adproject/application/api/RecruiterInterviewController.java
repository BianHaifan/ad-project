package com.adproject.application.api;

import com.adproject.application.application.InterviewService;
import com.adproject.common.api.RequestIdFilter;
import com.adproject.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class RecruiterInterviewController {
    private final InterviewService service;
    public RecruiterInterviewController(InterviewService service) { this.service = service; }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/recruiter/applications/{applicationId}/interviews")
    InterviewDtos.InterviewResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String applicationId,
            @Valid @RequestBody InterviewDtos.CreateInterviewRequest request,
            HttpServletRequest servletRequest) {
        return new InterviewDtos.InterviewResponse(service.create(user, applicationId, request,
                RequestIdFilter.current(servletRequest)));
    }

    @PatchMapping("/api/v1/recruiter/interviews/{interviewId}")
    InterviewDtos.InterviewResponse update(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String interviewId,
            @Valid @RequestBody InterviewDtos.UpdateInterviewRequest request,
            HttpServletRequest servletRequest) {
        return new InterviewDtos.InterviewResponse(service.update(user, interviewId, request,
                RequestIdFilter.current(servletRequest)));
    }
}
