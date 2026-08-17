from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest

from ad_recommender.backend_import import (
    BackendImportError,
    BackendJobImporter,
    select_import_jobs,
    to_create_job_request,
)
from ad_recommender.schemas import JobInput, SalaryInput


def job(
    entity_id: str,
    employment_type: str | None = "FULL_TIME",
    workplace_type: str = "ONSITE",
    salary: SalaryInput | None = None,
) -> JobInput:
    return JobInput(
        entity_id=entity_id,
        title=f"Engineer {entity_id}",
        description="Build and maintain production software.",
        requirements=["Production experience"],
        skills=["Python"],
        location="Singapore",
        workplace_type=workplace_type,
        employment_type=employment_type,
        salary=salary,
    )


def test_select_import_jobs_is_deterministic_and_skips_unsupported_types() -> None:
    jobs = [
        job("full-onsite"),
        job("full-remote", workplace_type="REMOTE"),
        job("intern", employment_type="INTERNSHIP"),
        job("part", employment_type="PART_TIME"),
        job("contract", employment_type=None),
    ]

    first = select_import_jobs(jobs, 4, seed=17)
    second = select_import_jobs(jobs, 4, seed=17)

    assert [item.entity_id for item in first] == [item.entity_id for item in second]
    assert {item.entity_id for item in first} == {
        "full-onsite",
        "full-remote",
        "intern",
        "part",
    }


def test_create_request_preserves_sgd_and_marks_other_currency_unknown() -> None:
    sgd = job("sgd", salary=SalaryInput(minimum=5000, maximum=7000, currency="SGD"))
    usd = job("usd", salary=SalaryInput(minimum=5000, maximum=7000, currency="USD"))

    assert to_create_job_request(sgd)["salary"] == {
        "min": 5000,
        "max": 7000,
        "currency": "SGD",
        "period": "MONTH",
    }
    assert to_create_job_request(usd)["salary"] == {
        "min": 0,
        "max": 0,
        "currency": "SGD",
        "period": "MONTH",
    }


def test_importer_logs_in_creates_publishes_and_skips_recorded_jobs(tmp_path: Path) -> None:
    calls: list[tuple[str, str, dict[str, Any] | None, dict[str, str]]] = []

    def transport(
        method: str, url: str, body: dict[str, Any] | None, headers: dict[str, str]
    ) -> dict[str, Any]:
        calls.append((method, url, body, headers))
        if url.endswith("/auth/login"):
            return {
                "data": {
                    "accessToken": "test-token",
                    "user": {
                        "role": "RECRUITER",
                        "company": {"verificationStatus": "APPROVED"},
                    },
                }
            }
        if url.endswith("/recruiter/jobs"):
            return {"data": {"jobId": "backend-job-1", "version": 1, "status": "DRAFT"}}
        return {"data": {"jobId": "backend-job-1", "version": 2, "status": "ACTIVE"}}

    state_file = tmp_path / "imports" / "jobs.json"
    importer = BackendJobImporter("http://localhost:8080/", state_file, transport)
    token = importer.login("recruiter@example.com", "password")
    first = importer.import_jobs([job("source-1")], token, tmp_path / "company.csv")
    second = importer.import_jobs([job("source-1")], token, tmp_path / "company.csv")

    assert first.created == 1
    assert first.published == 1
    assert second.created == 0
    assert second.skipped == 1
    assert len(calls) == 3
    assert calls[1][3]["Authorization"] == "Bearer test-token"
    state = json.loads(state_file.read_text(encoding="utf-8"))
    assert state["jobs"]["source-1"]["backendJobId"] == "backend-job-1"


def test_importer_resumes_publish_after_a_partial_failure(tmp_path: Path) -> None:
    state_file = tmp_path / "jobs.json"
    state_file.write_text(
        json.dumps(
            {
                "version": 1,
                "jobs": {
                    "source-1": {
                        "backendJobId": "backend-job-1",
                        "title": "Engineer source-1",
                        "status": "DRAFT",
                        "version": 1,
                    }
                },
            }
        ),
        encoding="utf-8",
    )
    calls: list[str] = []

    def transport(
        _method: str, url: str, _body: dict[str, Any] | None, _headers: dict[str, str]
    ) -> dict[str, Any]:
        calls.append(url)
        return {"data": {"jobId": "backend-job-1", "version": 2, "status": "ACTIVE"}}

    importer = BackendJobImporter("http://localhost:8080", state_file, transport)
    result = importer.import_jobs(
        [job("source-1")], "test-token", tmp_path / "company.csv"
    )

    assert result.created == 0
    assert result.published == 1
    assert result.skipped == 0
    assert calls == [
        "http://localhost:8080/api/v1/recruiter/jobs/backend-job-1/publish"
    ]


def test_importer_does_not_count_new_draft_as_skipped(tmp_path: Path) -> None:
    def transport(
        _method: str, _url: str, _body: dict[str, Any] | None, _headers: dict[str, str]
    ) -> dict[str, Any]:
        return {"data": {"jobId": "backend-job-1", "version": 1, "status": "DRAFT"}}

    importer = BackendJobImporter(
        "http://localhost:8080", tmp_path / "jobs.json", transport
    )
    result = importer.import_jobs(
        [job("source-1")], "test-token", tmp_path / "company.csv", publish=False
    )

    assert result.created == 1
    assert result.published == 0
    assert result.skipped == 0


def test_login_rejects_unapproved_company(tmp_path: Path) -> None:
    def transport(*_: Any) -> dict[str, Any]:
        return {
            "data": {
                "accessToken": "test-token",
                "user": {
                    "role": "RECRUITER",
                    "company": {"verificationStatus": "PENDING"},
                },
            }
        }

    importer = BackendJobImporter("http://localhost:8080", tmp_path / "state.json", transport)

    with pytest.raises(BackendImportError, match="APPROVED"):
        importer.login("recruiter@example.com", "password")
