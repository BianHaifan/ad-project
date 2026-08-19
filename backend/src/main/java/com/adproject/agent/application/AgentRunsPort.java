package com.adproject.agent.application;

import com.adproject.agent.api.AgentDtos;
import com.adproject.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;

/**
 * Shared operation surface of the candidate and recruiter agent run services. Both services
 * enforce their own role checks; controllers dispatch on the caller role.
 */
public interface AgentRunsPort {
    record ConfirmResult(HttpStatus status, AgentDtos.RunResponse response) {}

    AgentDtos.RunResponse create(AuthenticatedUser principal, AgentDtos.CreateRunRequest request);

    ConfirmResult confirm(AuthenticatedUser principal, String runId, String rawIdempotencyKey,
                          AgentDtos.ConfirmRunRequest request);

    AgentDtos.RunResponse get(AuthenticatedUser principal, String runId);

    AgentDtos.RunResponse cancel(AuthenticatedUser principal, String runId);

    AgentDtos.ConversationListResponse listConversations(AuthenticatedUser principal);

    AgentDtos.ConversationResponse recentConversation(AuthenticatedUser principal);

    AgentDtos.ConversationResponse getConversation(AuthenticatedUser principal, String rawConversationId);

    void deleteConversation(AuthenticatedUser principal, String rawConversationId);
}
