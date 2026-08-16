from __future__ import annotations

import argparse
import csv
import json
import time
from pathlib import Path

from fastapi.testclient import TestClient

from ad_recommender.api import create_app
from ad_recommender.data import sidecar_paths
from ad_recommender.schemas import CandidateInput, JobInput


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke-test a model with real prepared data")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--pairs", type=Path, required=True)
    parser.add_argument("--jobs", type=int, default=50)
    args = parser.parse_args()

    candidate_path, job_path = sidecar_paths(args.pairs)
    with candidate_path.open("r", encoding="utf-8") as handle:
        first_candidate = next(line for line in handle if line.strip())
        candidate = CandidateInput.model_validate_json(first_candidate)

    job_ids: list[str] = []
    with args.pairs.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            if row["query_id"] != candidate.entity_id:
                if job_ids:
                    break
                continue
            job_ids.append(row["job_id"])
            if len(job_ids) == args.jobs:
                break
    wanted = set(job_ids)
    jobs_by_id: dict[str, JobInput] = {}
    with job_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            raw = json.loads(line)
            entity_id = str(raw.get("entity_id", ""))
            if entity_id in wanted:
                jobs_by_id[entity_id] = JobInput.model_validate(raw)
                if len(jobs_by_id) == len(wanted):
                    break
    jobs = [jobs_by_id[job_id] for job_id in reversed(job_ids)]

    started = time.perf_counter()
    application = create_app(model_path=args.model, internal_token="smoke-secret")
    startup_ms = round((time.perf_counter() - started) * 1000)
    bundle = application.state.bundle
    started = time.perf_counter()
    direct_scores = bundle._score_for_ranking([candidate] * len(jobs), jobs)
    scoring_ms = round((time.perf_counter() - started) * 1000)
    selected_jobs = [
        job
        for job, _ in sorted(
            zip(jobs, direct_scores, strict=True),
            key=lambda item: (-item[1], item[0].entity_id),
        )[:10]
    ]
    started = time.perf_counter()
    bundle.extractor.transform_explanations(
        [candidate] * len(selected_jobs), selected_jobs
    )
    explanation_ms = round((time.perf_counter() - started) * 1000)

    client = TestClient(application)
    health = client.get("/internal/v1/health")
    unauthorized = client.post(
        "/internal/v1/recommend/jobs",
        json={"candidate": candidate.model_dump(), "jobs": [job.model_dump() for job in jobs]},
    )
    response = client.post(
        "/internal/v1/recommend/jobs",
        headers={"X-Internal-Token": "smoke-secret"},
        json={
            "candidate": candidate.model_dump(),
            "jobs": [job.model_dump() for job in jobs],
            "limit": 10,
        },
    )
    response.raise_for_status()
    body = response.json()
    scores = [item["score"] for item in body["items"]]
    if health.json()["status"] != "ready":
        raise RuntimeError(f"Model health is not ready: {health.json()}")
    if unauthorized.status_code != 401:
        raise RuntimeError(f"Unauthorized request returned {unauthorized.status_code}")
    if len(body["items"]) != 10 or scores != sorted(scores, reverse=True):
        raise RuntimeError("Recommendation response was not correctly ranked")
    print(
        json.dumps(
            {
                "health": health.json(),
                "unauthorized_status": unauthorized.status_code,
                "candidate_id": candidate.entity_id,
                "input_jobs": len(jobs),
                "returned_jobs": len(body["items"]),
                "top_job_id": body["items"][0]["entity_id"],
                "top_score": body["items"][0]["score"],
                "inference_ms": body["inference_ms"],
                "model_startup_ms": startup_ms,
                "direct_scoring_ms": scoring_ms,
                "direct_explanation_ms": explanation_ms,
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
