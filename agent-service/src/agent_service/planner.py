from threading import Lock
from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from .deepseek import DeepSeekConfig, DeepSeekPlanner, DeepSeekPlannerError
from .models import ConversationMessage, PlanRequest, PlanResponse


class PlannerState(TypedDict, total=False):
    instruction: str
    agent_type: str
    job_id: str | None
    server_date: str | None
    timezone: str | None
    history: list[dict[str, str]]
    response: PlanResponse


def parse_instruction(state: PlannerState) -> PlannerState:
    if not deepseek_planner:
        raise DeepSeekPlannerError("no_api_key")
    return {
        "response": deepseek_planner.create_plan(
            PlanRequest(
                instruction=state["instruction"],
                agentType=state.get("agent_type", "CANDIDATE"),
                jobId=state.get("job_id"),
                serverDate=state.get("server_date"),
                timezone=state.get("timezone"),
                history=[ConversationMessage.model_validate(message) for message in state.get("history", [])],
            )
        )
    }


def build_graph():
    graph = StateGraph(PlannerState)
    graph.add_node("parse_instruction", parse_instruction)
    graph.add_edge(START, "parse_instruction")
    graph.add_edge("parse_instruction", END)
    return graph.compile()


planner_graph = build_graph()

_deepseek_config = DeepSeekConfig.from_env()
deepseek_planner = DeepSeekPlanner(_deepseek_config) if _deepseek_config else None
_diagnostics_lock = Lock()
_last_provider = "none"
_last_error = "none"


def _record_diagnostics(provider: str, error: str = "none") -> None:
    global _last_provider, _last_error
    with _diagnostics_lock:
        _last_provider = provider
        _last_error = error


def planner_diagnostics() -> dict[str, str]:
    with _diagnostics_lock:
        return {
            "plannerMode": "DEEPSEEK" if deepseek_planner else "NO_API_KEY",
            "model": deepseek_planner.config.model if deepseek_planner else "none",
            "lastPlanProvider": _last_provider,
            "lastError": _last_error,
        }


def create_plan(request: PlanRequest) -> PlanResponse:
    try:
        result = planner_graph.invoke({
            "instruction": request.instruction,
            "agent_type": request.agentType,
            "job_id": request.jobId,
            "server_date": request.serverDate,
            "timezone": request.timezone,
            "history": [message.model_dump() for message in request.history],
        })
        _record_diagnostics("deepseek")
        return result["response"]
    except DeepSeekPlannerError as exc:
        _record_diagnostics("deepseek" if deepseek_planner else "none", exc.code)
        raise
