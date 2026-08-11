package com.adproject.application.api;

import com.adproject.application.application.CandidateApplicationQueryService;
import com.adproject.application.domain.ApplicationListFilter;
import com.adproject.common.api.RequestIdFilter;
import com.adproject.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/candidate/applications")
public class CandidateApplicationQueryController {
    private final CandidateApplicationQueryService service;

    public CandidateApplicationQueryController(CandidateApplicationQueryService service) {
        this.service = service;
    }

    @GetMapping
    ApplicationDtos.CandidateApplicationListResponse list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) ApplicationListFilter filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.list(currentUser, filter, page, pageSize);
    }

    @GetMapping("/{applicationId}")
    ApplicationDtos.CandidateApplicationDetailResponse detail(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String applicationId) {
        return service.detail(currentUser, applicationId);
    }

    @PostMapping("/{applicationId}/withdraw")
    ApplicationDtos.CandidateApplicationDetailResponse withdraw(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String applicationId,
            @Valid @RequestBody ApplicationDtos.WithdrawApplicationRequest request,
            HttpServletRequest servletRequest) {
        return service.withdraw(currentUser, applicationId, request,
                RequestIdFilter.current(servletRequest));
    }
}
