from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class ConversationMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=2000)


class PlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    instruction: str = Field(min_length=1, max_length=2000)
    agentType: Literal["CANDIDATE", "RECRUITER"] = Field(default="CANDIDATE")
    jobId: str | None = Field(default=None, max_length=100)
    serverDate: str | None = Field(default=None, max_length=32)
    timezone: str | None = Field(default=None, max_length=64)
    history: list[ConversationMessage] = Field(default_factory=list, max_length=20)

    @model_validator(mode="after")
    def _check_job_id_requires_recruiter(self) -> "PlanRequest":
        if self.jobId is not None and self.agentType != "RECRUITER":
            raise ValueError("jobId is only allowed for RECRUITER requests")
        return self


class PlanOperation(BaseModel):
    model_config = ConfigDict(extra="forbid")

    tool: Literal[
        "get_my_resume",
        "read_resume_section",
        "preview_resume_patch",
        "screen_applicants",
        "schedule_interview",
        "reschedule_interview",
        "cancel_interview",
    ]
    arguments: dict[str, Any]


class PlanResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: Literal["READY", "NEEDS_CLARIFICATION", "CHAT"]
    intent: Literal[
        "QUERY_RESUME",
        "UPDATE_RESUME",
        "SCREEN_APPLICANTS",
        "SCHEDULE_INTERVIEW",
        "RESCHEDULE_INTERVIEW",
        "CANCEL_INTERVIEW",
        "CHAT",
    ] | None
    target: Literal["DEFAULT_RESUME"] | None
    operations: list[PlanOperation]
    message: str = Field(max_length=500)
