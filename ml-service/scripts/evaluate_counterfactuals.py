from __future__ import annotations

import argparse
import json
from pathlib import Path

from ad_recommender.model import load_bundle
from ad_recommender.schemas import (
    CandidateInput,
    CandidatePreferences,
    JobInput,
    SalaryInput,
)


def candidate(skills: list[str], desired_title: str, headline: str) -> CandidateInput:
    return CandidateInput(
        entity_id="counterfactual-candidate",
        resume_text=f"{headline}. Skills: {', '.join(skills)}.",
        headline=headline,
        skills=skills,
        years_experience=3,
        preferences=CandidatePreferences(
            desired_titles=[desired_title],
            preferred_locations=["Singapore"],
            workplace_types=["HYBRID", "REMOTE"],
            employment_types=["FULL_TIME"],
            minimum_salary=SalaryInput(minimum=5000, currency="SGD", period="MONTH"),
        ),
    )


def job(entity_id: str, title: str, skills: list[str], description: str) -> JobInput:
    return JobInput(
        entity_id=entity_id,
        title=title,
        description=description,
        requirements=["3 years relevant experience"],
        skills=skills,
        location="Singapore",
        workplace_type="HYBRID",
        employment_type="FULL_TIME",
        salary=SalaryInput(minimum=6000, maximum=9000, currency="SGD", period="MONTH"),
        required_years_experience=3,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Run deterministic ML counterfactual checks")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    bundle = load_bundle(args.model)
    backend_job = job(
        "job-backend",
        "Backend Engineer",
        ["Java", "Spring Boot", "SQL", "REST API"],
        "Build Java Spring Boot REST APIs and MySQL services.",
    )
    frontend_job = job(
        "job-frontend",
        "Frontend React Engineer",
        ["React", "TypeScript", "CSS"],
        "Build React and TypeScript user interfaces.",
    )
    data_job = job(
        "job-data",
        "Data Scientist",
        ["Python", "scikit-learn", "Pandas"],
        "Train Python machine learning models.",
    )
    backend_candidate = candidate(
        ["Java", "Spring Boot", "SQL", "REST API"],
        "Backend Engineer",
        "Backend Software Engineer",
    )
    frontend_candidate = candidate(
        ["React", "TypeScript", "CSS"],
        "Frontend Engineer",
        "Frontend React Engineer",
    )
    empty_skill_candidate = candidate([], "Backend Engineer", "Backend Software Engineer")

    backend_results = bundle.recommend_jobs(
        backend_candidate, [frontend_job, data_job, backend_job], limit=3
    )
    frontend_results = bundle.recommend_jobs(
        frontend_candidate, [backend_job, frontend_job], limit=2
    )
    empty_skill_score, _ = bundle.score(empty_skill_candidate, backend_job)
    full_skill_score, _ = bundle.score(backend_candidate, backend_job)
    by_id = {item.entity_id: item for item in backend_results}
    checks = {
        "backend_ranks_first": backend_results[0].entity_id == "job-backend",
        "frontend_ranks_first_for_frontend_candidate": (
            frontend_results[0].entity_id == "job-frontend"
        ),
        "zero_skill_title_mismatch_capped": by_id["job-frontend"].score <= 45,
        "matching_skills_do_not_reduce_score": full_skill_score >= empty_skill_score,
        "zero_match_not_presented_as_strong": not any(
            value.startswith("Skills matched: 0")
            for value in by_id["job-frontend"].strong_matches
        ),
        "zero_match_has_no_skill_evidence": not by_id["job-frontend"].evidence,
    }
    report = {
        "model_version": bundle.manifest.model_version,
        "feature_version": bundle.manifest.feature_version,
        "backend_candidate_results": [item.model_dump() for item in backend_results],
        "frontend_candidate_results": [item.model_dump() for item in frontend_results],
        "skill_monotonicity": {
            "empty_skills": empty_skill_score,
            "matching_skills": full_skill_score,
        },
        "checks": checks,
        "passed": all(checks.values()),
    }
    rendered = json.dumps(report, indent=2, ensure_ascii=False)
    print(rendered)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered + "\n", encoding="utf-8")
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
