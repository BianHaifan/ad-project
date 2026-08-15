from __future__ import annotations

import pytest

from ad_recommender.model import TrainingPair, baseline_bundle
from ad_recommender.schemas import CandidateInput, CandidatePreferences, JobInput


@pytest.fixture
def candidate() -> CandidateInput:
    return CandidateInput(
        entity_id="candidate-1",
        headline="Backend Python Engineer",
        resume_text=(
            "Built Python FastAPI services with SQL, Docker and Kubernetes for three years."
        ),
        skills=["Python", "FastAPI", "SQL", "Docker", "Kubernetes"],
        years_experience=3,
        preferences=CandidatePreferences(
            desired_titles=["Backend Engineer"],
            preferred_locations=["Singapore"],
            workplace_types=["HYBRID", "REMOTE"],
            employment_types=["FULL_TIME"],
        ),
    )


@pytest.fixture
def matching_job() -> JobInput:
    return JobInput(
        entity_id="job-python",
        title="Python Backend Engineer",
        description="Build production APIs using Python, FastAPI, SQL and Docker.",
        requirements=["Three years of backend development"],
        skills=["Python", "FastAPI", "SQL", "Docker"],
        location="Singapore",
        workplace_type="HYBRID",
        employment_type="FULL_TIME",
        required_years_experience=3,
    )


@pytest.fixture
def unrelated_job() -> JobInput:
    return JobInput(
        entity_id="job-sales",
        title="Retail Sales Associate",
        description="Assist customers and meet retail sales targets in a physical store.",
        skills=["Sales", "Customer Service"],
        location="London",
        workplace_type="ONSITE",
        employment_type="PART_TIME",
        required_years_experience=1,
    )


@pytest.fixture
def baseline(candidate, matching_job, unrelated_job):
    pairs = [
        TrainingPair(candidate, matching_job, 3, candidate.entity_id),
        TrainingPair(candidate, unrelated_job, 0, candidate.entity_id),
    ]
    return baseline_bundle(pairs, "fixture-sha256")
