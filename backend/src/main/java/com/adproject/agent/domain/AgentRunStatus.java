package com.adproject.agent.domain;

public enum AgentRunStatus {
    PROCESSING,
    AWAITING_CONFIRMATION,
    NEEDS_CLARIFICATION,
    NO_ACTION_REQUIRED,
    FAILED,
    CANCELLED,
    EXECUTING,
    COMPLETED
}
