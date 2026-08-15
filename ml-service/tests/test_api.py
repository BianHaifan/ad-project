from fastapi.testclient import TestClient

from ad_recommender.api import create_app


def test_recommend_jobs_requires_internal_token(baseline, candidate, matching_job):
    client = TestClient(create_app(bundle=baseline, internal_token="secret"))

    response = client.post(
        "/internal/v1/recommend/jobs",
        json={"candidate": candidate.model_dump(), "jobs": [matching_job.model_dump()]},
    )

    assert response.status_code == 401


def test_recommend_jobs_returns_ranked_explanation(
    baseline, candidate, matching_job, unrelated_job
):
    client = TestClient(create_app(bundle=baseline, internal_token="secret"))

    response = client.post(
        "/internal/v1/recommend/jobs",
        headers={"X-Internal-Token": "secret"},
        json={
            "candidate": candidate.model_dump(),
            "jobs": [unrelated_job.model_dump(), matching_job.model_dump()],
            "limit": 2,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["model_version"] == "match-baseline-v1"
    assert body["items"][0]["entity_id"] == matching_job.entity_id
    assert body["items"][0]["rank"] == 1
    assert body["items"][0]["strong_matches"]


def test_sensitive_extra_fields_are_rejected(baseline, candidate, matching_job):
    client = TestClient(create_app(bundle=baseline, internal_token="secret"))
    payload = candidate.model_dump()
    payload["age"] = 29

    response = client.post(
        "/internal/v1/recommend/jobs",
        headers={"X-Internal-Token": "secret"},
        json={"candidate": payload, "jobs": [matching_job.model_dump()]},
    )

    assert response.status_code == 422


def test_health_reports_missing_model(tmp_path):
    client = TestClient(
        create_app(model_path=tmp_path / "missing-model.joblib", internal_token="secret")
    )

    response = client.get("/internal/v1/health")

    assert response.status_code == 200
    assert response.json()["status"] == "not_ready"
