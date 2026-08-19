package com.adproject.agent.api;

import com.adproject.agent.application.AgentRunService;
import com.adproject.agent.application.AgentRunsPort;
import com.adproject.agent.application.HrAgentRunService;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/runs")
public class AgentRunController {
    private final AgentRunsPort candidateRuns;
    private final AgentRunsPort recruiterRuns;

    public AgentRunController(AgentRunService candidateRuns, HrAgentRunService recruiterRuns) {
        this.candidateRuns = candidateRuns;
        this.recruiterRuns = recruiterRuns;
    }

    @PostMapping
    ResponseEntity<AgentDtos.RunResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @Valid @RequestBody AgentDtos.CreateRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service(principal).create(principal, request));
    }

    @GetMapping("/{runId}")
    AgentDtos.RunResponse get(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable String runId) {
        return service(principal).get(principal, runId);
    }

    @PostMapping("/{runId}/cancel")
    AgentDtos.RunResponse cancel(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @PathVariable String runId) {
        return service(principal).cancel(principal, runId);
    }

    @PostMapping("/{runId}/confirm")
    ResponseEntity<AgentDtos.RunResponse> confirm(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable String runId,
                                                   @RequestHeader(name = "Idempotency-Key", required = false)
                                                   String idempotencyKey,
                                                   @Valid @RequestBody AgentDtos.ConfirmRunRequest request) {
        var result = service(principal).confirm(principal, runId, idempotencyKey, request);
        return ResponseEntity.status(result.status()).body(result.response());
    }

    private AgentRunsPort service(AuthenticatedUser principal) {
        if (principal != null && principal.role() == UserRole.RECRUITER) {
            return recruiterRuns;
        }
        return candidateRuns;
    }
}
