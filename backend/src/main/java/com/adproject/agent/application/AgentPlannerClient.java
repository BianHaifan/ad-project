package com.adproject.agent.application;

import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AgentPlannerClient {
    private final RestClient restClient;

    public AgentPlannerClient(AgentProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.plannerBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public PlanResponse plan(String instruction, String agentType, String jobId, String serverDate,
                             String timezone, List<ConversationMessage> history) {
        try {
            PlanResponse response = restClient.post()
                    .uri("/internal/v1/agent/plan")
                    .body(new PlanRequest(instruction, agentType, jobId, serverDate, timezone, history))
                    .retrieve()
                    .body(PlanResponse.class);
            if (response == null || response.status() == null || response.operations() == null) {
                throw new PlannerException("AGENT_PLANNER_INVALID_RESPONSE", "Agent planner returned an invalid response");
            }
            return response;
        } catch (PlannerException exception) {
            throw exception;
        } catch (HttpServerErrorException exception) {
            String body = exception.getResponseBodyAsString();
            if (body != null) {
                if (body.contains("invalid_plan")) {
                    throw new PlannerException("AGENT_PLAN_REJECTED", "Agent planner produced an invalid plan", exception);
                }
                if (body.contains("no_api_key")) {
                    throw new PlannerException("AGENT_PLANNER_NOT_CONFIGURED", "Agent planner has no API key configured", exception);
                }
                if (body.contains("invalid_response") || body.contains("incomplete_response")) {
                    throw new PlannerException("AGENT_PLANNER_INVALID_RESPONSE", "Agent planner returned an invalid response", exception);
                }
            }
            throw new PlannerException("AGENT_PLANNER_UNAVAILABLE", "Agent planner is unavailable", exception);
        } catch (RestClientException exception) {
            throw new PlannerException("AGENT_PLANNER_UNAVAILABLE", "Agent planner is unavailable", exception);
        }
    }

    public record ConversationMessage(String role, String content) {}
    public record PlanRequest(String instruction, String agentType, String jobId, String serverDate,
                              String timezone, List<ConversationMessage> history) {}
    public record PlanOperation(String tool, Map<String, Object> arguments) {}
    public record PlanResponse(String status, String intent, String target,
                               List<PlanOperation> operations, String message) {}

    public static class PlannerException extends RuntimeException {
        private final String code;

        public PlannerException(String code, String message) {
            super(message);
            this.code = code;
        }

        public PlannerException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() { return code; }
    }
}
