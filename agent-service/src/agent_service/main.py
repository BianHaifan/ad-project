from fastapi import FastAPI, HTTPException

from .deepseek import DeepSeekPlannerError
from .models import PlanRequest, PlanResponse
from .planner import create_plan, planner_diagnostics


app = FastAPI(
    title="AD Project Internal Agent Planner",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", **planner_diagnostics()}


@app.post("/internal/v1/agent/plan", response_model=PlanResponse)
def plan(request: PlanRequest) -> PlanResponse:
    try:
        return create_plan(request)
    except DeepSeekPlannerError as exc:
        raise HTTPException(status_code=503, detail=exc.code)
