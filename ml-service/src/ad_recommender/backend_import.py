from __future__ import annotations

import hashlib
import json
import os
import urllib.error
import urllib.request
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

from ad_recommender.schemas import JobInput

JsonObject = dict[str, Any]
Transport = Callable[[str, str, JsonObject | None, dict[str, str]], JsonObject]


class BackendImportError(RuntimeError):
    """Raised when the backend rejects or cannot complete an import operation."""


@dataclass(frozen=True)
class ImportResult:
    selected: int
    created: int
    published: int
    skipped: int
    state_file: Path


def select_import_jobs(jobs: Sequence[JobInput], limit: int, seed: int) -> list[JobInput]:
    if not 1 <= limit <= 500:
        raise ValueError("Import limit must be between 1 and 500")

    supported = [job for job in jobs if job.employment_type is not None]
    buckets: dict[tuple[str, str], list[JobInput]] = {}
    for job in supported:
        key = (job.employment_type or "", job.workplace_type or "ONSITE")
        buckets.setdefault(key, []).append(job)

    for bucket in buckets.values():
        bucket.sort(key=lambda job: _stable_order(job.entity_id, seed))

    selected: list[JobInput] = []
    ordered_keys = sorted(buckets)
    while len(selected) < limit and ordered_keys:
        remaining: list[tuple[str, str]] = []
        for key in ordered_keys:
            bucket = buckets[key]
            if bucket and len(selected) < limit:
                selected.append(bucket.pop())
            if bucket:
                remaining.append(key)
        ordered_keys = remaining
    return selected


def to_create_job_request(job: JobInput, deadline_days: int = 90) -> JsonObject:
    if job.employment_type is None:
        raise ValueError(f"Job {job.entity_id} has an unsupported employment type")
    salary = _backend_salary(job)
    deadline = datetime.now(UTC) + timedelta(days=deadline_days)
    return {
        "title": job.title,
        "employmentType": job.employment_type,
        "workplaceType": job.workplace_type or "ONSITE",
        "location": job.location or "Unspecified",
        "salary": salary,
        "description": job.description,
        "requirements": job.requirements,
        "skills": job.skills,
        "deadline": deadline.isoformat(timespec="seconds").replace("+00:00", "Z"),
        "visibility": "PUBLIC",
    }


class BackendJobImporter:
    def __init__(
        self,
        backend_url: str,
        state_file: Path,
        transport: Transport | None = None,
    ) -> None:
        self.backend_url = backend_url.rstrip("/")
        self.state_file = state_file
        self.transport = transport or _request_json

    def login(self, email: str, password: str) -> str:
        response = self.transport(
            "POST",
            f"{self.backend_url}/api/v1/auth/login",
            {"email": email, "password": password},
            {},
        )
        data = _response_data(response)
        user = data.get("user")
        if not isinstance(user, dict) or user.get("role") != "RECRUITER":
            raise BackendImportError("The import account must have the RECRUITER role")
        company = user.get("company")
        if not isinstance(company, dict) or company.get("verificationStatus") != "APPROVED":
            raise BackendImportError(
                "The recruiter company must be APPROVED before publishing jobs"
            )
        token = data.get("accessToken")
        if not isinstance(token, str) or not token:
            raise BackendImportError("Login response did not contain an access token")
        return token

    def import_jobs(
        self,
        jobs: Sequence[JobInput],
        access_token: str,
        source_file: Path,
        publish: bool = True,
    ) -> ImportResult:
        state = self._read_state()
        imported = state.setdefault("jobs", {})
        if not isinstance(imported, dict):
            raise BackendImportError("Import state has an invalid jobs object")

        created = 0
        published = 0
        skipped = 0
        headers = {"Authorization": f"Bearer {access_token}"}
        for job in jobs:
            existing = imported.get(job.entity_id)
            was_created = existing is None
            if existing is None:
                create_response = self.transport(
                    "POST",
                    f"{self.backend_url}/api/v1/recruiter/jobs",
                    to_create_job_request(job),
                    headers,
                )
                created_job = _response_data(create_response)
                job_id = created_job.get("jobId")
                version = created_job.get("version")
                if not isinstance(job_id, str) or not isinstance(version, int):
                    raise BackendImportError("Create-job response is missing jobId or version")
                created += 1
                existing = {
                    "backendJobId": job_id,
                    "title": job.title,
                    "status": created_job.get("status", "DRAFT"),
                    "version": version,
                }
                imported[job.entity_id] = existing
                self._save_progress(state, source_file)
            elif not isinstance(existing, dict):
                raise BackendImportError(f"Import state for source job {job.entity_id} is invalid")

            status = existing.get("status", "DRAFT")
            if publish and status != "ACTIVE":
                job_id = existing.get("backendJobId")
                version = existing.get("version")
                if not isinstance(job_id, str) or not isinstance(version, int):
                    raise BackendImportError(
                        f"Import state cannot resume source job {job.entity_id}"
                    )
                published_job = self._publish(job_id, version, headers)
                status = published_job.get("status", status)
                if status != "ACTIVE":
                    raise BackendImportError(f"Published job {job_id} did not become ACTIVE")
                existing["status"] = status
                existing["version"] = published_job.get("version", version + 1)
                published += 1
                self._save_progress(state, source_file)
            elif not was_created:
                skipped += 1

        return ImportResult(len(jobs), created, published, skipped, self.state_file)

    def _publish(
        self, job_id: str, version: int, headers: dict[str, str]
    ) -> JsonObject:
        response = self.transport(
            "POST",
            f"{self.backend_url}/api/v1/recruiter/jobs/{job_id}/publish",
            {"expectedVersion": version},
            headers,
        )
        return _response_data(response)

    def _save_progress(self, state: JsonObject, source_file: Path) -> None:
        state["source"] = str(source_file.resolve())
        state["updatedAt"] = datetime.now(UTC).isoformat()
        self._write_state(state)

    def _read_state(self) -> JsonObject:
        if not self.state_file.exists():
            return {"version": 1, "jobs": {}}
        try:
            value = json.loads(self.state_file.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise BackendImportError(f"Cannot read import state: {error}") from error
        if not isinstance(value, dict) or value.get("version") != 1:
            raise BackendImportError("Import state has an unsupported format")
        return value

    def _write_state(self, state: JsonObject) -> None:
        self.state_file.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.state_file.with_suffix(f"{self.state_file.suffix}.tmp")
        temporary.write_text(json.dumps(state, indent=2, sort_keys=True), encoding="utf-8")
        os.replace(temporary, self.state_file)


def _backend_salary(job: JobInput) -> JsonObject:
    salary = job.salary
    if salary is None or salary.currency.upper() != "SGD":
        return {"min": 0, "max": 0, "currency": "SGD", "period": "MONTH"}
    minimum = max(0, round(salary.minimum or 0))
    maximum = max(minimum, round(salary.maximum or minimum))
    return {
        "min": minimum,
        "max": maximum,
        "currency": "SGD",
        "period": salary.period,
    }


def _stable_order(entity_id: str, seed: int) -> str:
    return hashlib.sha256(f"{seed}:{entity_id}".encode()).hexdigest()


def _response_data(response: JsonObject) -> JsonObject:
    data = response.get("data")
    if not isinstance(data, dict):
        raise BackendImportError("Backend response does not contain a data object")
    return data


def _request_json(
    method: str, url: str, body: JsonObject | None, headers: dict[str, str]
) -> JsonObject:
    request_headers = {"Accept": "application/json", **headers}
    payload = None
    if body is not None:
        payload = json.dumps(body).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=payload, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:  # noqa: S310
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(detail)
            message = parsed.get("message") or parsed.get("error", {}).get("message") or detail
        except (json.JSONDecodeError, AttributeError):
            message = detail
        raise BackendImportError(f"Backend returned HTTP {error.code}: {message}") from error
    except urllib.error.URLError as error:
        raise BackendImportError(f"Cannot reach backend: {error.reason}") from error
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as error:
        raise BackendImportError("Backend returned invalid JSON") from error
    if not isinstance(parsed, dict):
        raise BackendImportError("Backend returned a non-object JSON response")
    return parsed
