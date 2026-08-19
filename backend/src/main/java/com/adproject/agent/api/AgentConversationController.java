package com.adproject.agent.api;

import com.adproject.agent.application.AgentRunService;
import com.adproject.agent.application.AgentRunsPort;
import com.adproject.agent.application.HrAgentRunService;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.user.domain.UserRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/conversations")
public class AgentConversationController {
    private final AgentRunsPort candidateRuns;
    private final AgentRunsPort recruiterRuns;

    public AgentConversationController(AgentRunService candidateRuns, HrAgentRunService recruiterRuns) {
        this.candidateRuns = candidateRuns;
        this.recruiterRuns = recruiterRuns;
    }

    @GetMapping
    AgentDtos.ConversationListResponse list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service(principal).listConversations(principal);
    }

    @GetMapping("/recent")
    AgentDtos.ConversationResponse recent(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service(principal).recentConversation(principal);
    }

    @GetMapping("/{conversationId}")
    AgentDtos.ConversationResponse get(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable String conversationId) {
        return service(principal).getConversation(principal, conversationId);
    }

    private AgentRunsPort service(AuthenticatedUser principal) {
        if (principal != null && principal.role() == UserRole.RECRUITER) {
            return recruiterRuns;
        }
        return candidateRuns;
    }
}
