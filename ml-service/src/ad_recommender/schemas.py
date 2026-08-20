from __future__ import annotations

from datetime import datetime
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints

ShortText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]
Identifier = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=100)]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class SalaryInput(StrictModel):
    minimum: float | None = Field(default=None, ge=0)
    maximum: float | None = Field(default=None, ge=0)
    currency: Annotated[
        str, StringConstraints(strip_whitespace=True, min_length=3, max_length=3)
    ] = "SGD"
    period: Literal["HOUR", "MONTH", "YEAR"] = "MONTH"


class CandidatePreferences(StrictModel):
    desired_titles: list[ShortText] = Field(default_factory=list, max_length=20)
    preferred_locations: list[ShortText] = Field(default_factory=list, max_length=20)
    workplace_types: list[Literal["ONSITE", "HYBRID", "REMOTE"]] = Field(
        default_factory=list, max_length=3
    )
    employment_types: list[Literal["FULL_TIME", "INTERNSHIP", "PART_TIME"]] = Field(
        default_factory=list, max_length=3
    )
    minimum_salary: SalaryInput | None = None


class CandidateInput(StrictModel):
    entity_id: Identifier
    resume_text: Annotated[str, StringConstraints(strip_whitespace=True, max_length=50_000)]
    headline: Annotated[str, StringConstraints(strip_whitespace=True, max_length=200)] = ""
    skills: list[ShortText] = Field(default_factory=list, max_length=100)
    years_experience: float | None = Field(default=None, ge=0, le=80)
    preferences: CandidatePreferences = Field(default_factory=CandidatePreferences)


class JobInput(StrictModel):
    entity_id: Identifier
    title: ShortText
    description: Annotated[str, StringConstraints(strip_whitespace=True, max_length=50_000)]
    requirements: list[
        Annotated[str, StringConstraints(strip_whitespace=True, max_length=1_000)]
    ] = Field(default_factory=list, max_length=100)
    skills: list[ShortText] = Field(default_factory=list, max_length=100)
    location: Annotated[str, StringConstraints(strip_whitespace=True, max_length=200)] = ""
    workplace_type: Literal["ONSITE", "HYBRID", "REMOTE"] | None = None
    employment_type: Literal["FULL_TIME", "INTERNSHIP", "PART_TIME"] | None = None
    salary: SalaryInput | None = None
    required_years_experience: float | None = Field(default=None, ge=0, le=80)


class RecommendJobsRequest(StrictModel):
    candidate: CandidateInput
    jobs: list[JobInput] = Field(min_length=1, max_length=500)
    limit: int = Field(default=20, ge=1, le=100)


class RecommendCandidatesRequest(StrictModel):
    job: JobInput
    candidates: list[CandidateInput] = Field(min_length=1, max_length=500)
    limit: int = Field(default=20, ge=1, le=100)


class RecommendationItem(StrictModel):
    entity_id: str
    score: int = Field(ge=0, le=100)
    rank: int = Field(ge=1)
    strong_matches: list[str]
    gaps: list[str]
    evidence: list[str]
    component_scores: dict[str, float] = Field(default_factory=dict)
    component_modes: dict[str, str] = Field(default_factory=dict)


class HybridDiagnostics(StrictModel):
    enabled: bool
    components: list[str]
    weights: dict[str, float]
    embedding_algorithm: str
    collaborative_algorithm: str
    collaborative_feedback_source: str


class RecommendationResponse(StrictModel):
    model_version: str
    feature_version: str
    generated_at: datetime
    inference_ms: int = Field(ge=0)
    items: list[RecommendationItem]
    hybrid: HybridDiagnostics | None = None


class HealthResponse(StrictModel):
    status: Literal["ready", "not_ready"]
    model_version: str | None
    feature_version: str | None
    components: list[str] = Field(default_factory=list)
