from __future__ import annotations

import argparse
import getpass
import json
import os
import ssl
import urllib.error
import urllib.parse
import urllib.request
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

JsonObject = dict[str, Any]


class ImportFailure(RuntimeError):
    pass


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Import a prepared AD Project job bundle through the backend API"
    )
    script_dir = Path(__file__).resolve().parent
    parser.add_argument("--backend-url", required=True)
    parser.add_argument("--email", required=True)
    parser.add_argument("--data", type=Path, default=script_dir / "jobs.jsonl")
    parser.add_argument("--state-file", type=Path, default=script_dir / "import-state.json")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--no-publish", action="store_true")
    args = parser.parse_args()

    backend_url = validate_backend_url(args.backend_url)
    records = read_records(args.data, args.limit)
    password = os.getenv("AD_IMPORT_PASSWORD") or getpass.getpass("Recruiter password: ")
    try:
        token = login(backend_url, args.email, password)
        result = import_records(
            backend_url,
            records,
            token,
            args.state_file,
            publish=not args.no_publish,
        )
    except ImportFailure as error:
        parser.error(str(error))
    finally:
        password = ""
    print(json.dumps(result, indent=2))


def validate_backend_url(value: str) -> str:
    normalized = value.rstrip("/")
    parsed = urllib.parse.urlparse(normalized)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ImportFailure("--backend-url must be an absolute HTTP(S) URL")
    local_hosts = {"127.0.0.1", "localhost", "::1"}
    if parsed.scheme != "https" and parsed.hostname not in local_hosts:
        raise ImportFailure("Remote imports require HTTPS; plain HTTP is allowed only locally")
    return normalized


def read_records(path: Path, limit: int | None) -> list[JsonObject]:
    if limit is not None and limit < 1:
        raise ImportFailure("--limit must be at least 1")
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise ImportFailure(f"Cannot read bundle data: {error}") from error
    records: list[JsonObject] = []
    seen: set[str] = set()
    required = {
        "sourceJobId",
        "title",
        "employmentType",
        "workplaceType",
        "location",
        "description",
        "requirements",
        "skills",
        "salary",
    }
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as error:
            raise ImportFailure(f"Invalid JSON on data line {line_number}") from error
        if not isinstance(record, dict) or record.get("formatVersion") != 1:
            raise ImportFailure(f"Unsupported record on data line {line_number}")
        missing = required - set(record)
        if missing:
            raise ImportFailure(f"Data line {line_number} is missing {sorted(missing)}")
        source_id = str(record["sourceJobId"])
        if source_id in seen:
            raise ImportFailure(f"Duplicate sourceJobId {source_id}")
        seen.add(source_id)
        records.append(record)
        if limit is not None and len(records) == limit:
            break
    if not records:
        raise ImportFailure("Bundle contains no jobs")
    return records


def login(backend_url: str, email: str, password: str) -> str:
    response = request_json(
        "POST",
        f"{backend_url}/api/v1/auth/login",
        {"email": email, "password": password},
        {},
    )
    data = response_data(response)
    user = data.get("user")
    if not isinstance(user, dict) or user.get("role") != "RECRUITER":
        raise ImportFailure("Import account must have the RECRUITER role")
    company = user.get("company")
    if not isinstance(company, dict) or company.get("verificationStatus") != "APPROVED":
        raise ImportFailure("Recruiter company must be APPROVED before importing jobs")
    token = data.get("accessToken")
    if not isinstance(token, str) or not token:
        raise ImportFailure("Login response did not include an access token")
    return token


def import_records(
    backend_url: str,
    records: list[JsonObject],
    token: str,
    state_file: Path,
    publish: bool,
) -> JsonObject:
    state = read_state(state_file)
    jobs = state.setdefault("jobs", {})
    if not isinstance(jobs, dict):
        raise ImportFailure("Import state has an invalid jobs object")
    headers = {"Authorization": f"Bearer {token}"}
    created = published = skipped = 0
    for record in records:
        source_id = str(record["sourceJobId"])
        existing = jobs.get(source_id)
        was_created = existing is None
        if existing is None:
            body = {
                key: value
                for key, value in record.items()
                if key not in {"formatVersion", "sourceJobId"}
            }
            body["deadline"] = (
                datetime.now(UTC) + timedelta(days=90)
            ).isoformat(timespec="seconds").replace("+00:00", "Z")
            created_job = response_data(
                request_json(
                    "POST", f"{backend_url}/api/v1/recruiter/jobs", body, headers
                )
            )
            job_id = created_job.get("jobId")
            version = created_job.get("version")
            if not isinstance(job_id, str) or not isinstance(version, int):
                raise ImportFailure("Create response is missing jobId or version")
            existing = {
                "backendJobId": job_id,
                "title": record["title"],
                "status": created_job.get("status", "DRAFT"),
                "version": version,
            }
            jobs[source_id] = existing
            created += 1
            save_state(state_file, state)
        if not isinstance(existing, dict):
            raise ImportFailure(f"Invalid state for source job {source_id}")
        if publish and existing.get("status", "DRAFT") != "ACTIVE":
            job_id = existing.get("backendJobId")
            version = existing.get("version")
            if not isinstance(job_id, str) or not isinstance(version, int):
                raise ImportFailure(f"Cannot resume source job {source_id}")
            published_job = response_data(
                request_json(
                    "POST",
                    f"{backend_url}/api/v1/recruiter/jobs/{job_id}/publish",
                    {"expectedVersion": version},
                    headers,
                )
            )
            if published_job.get("status") != "ACTIVE":
                raise ImportFailure(f"Published job {job_id} did not become ACTIVE")
            existing["status"] = "ACTIVE"
            existing["version"] = published_job.get("version", version + 1)
            published += 1
            save_state(state_file, state)
        elif not was_created:
            skipped += 1
    return {
        "selected": len(records),
        "created": created,
        "published": published,
        "skipped": skipped,
        "stateFile": str(state_file),
    }


def read_state(path: Path) -> JsonObject:
    if not path.exists():
        return {"formatVersion": 1, "jobs": {}}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ImportFailure(f"Cannot read import state: {error}") from error
    if not isinstance(value, dict) or value.get("formatVersion") != 1:
        raise ImportFailure("Import state has an unsupported format")
    return value


def save_state(path: Path, state: JsonObject) -> None:
    state["updatedAt"] = datetime.now(UTC).isoformat()
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(f"{path.suffix}.tmp")
    temporary.write_text(json.dumps(state, indent=2), encoding="utf-8")
    os.replace(temporary, path)


def response_data(response: JsonObject) -> JsonObject:
    data = response.get("data")
    if not isinstance(data, dict):
        raise ImportFailure("Backend response does not contain a data object")
    return data


def request_json(
    method: str, url: str, body: JsonObject | None, headers: dict[str, str]
) -> JsonObject:
    request_headers = {"Accept": "application/json", **headers}
    payload = None
    if body is not None:
        payload = json.dumps(body).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=payload, headers=request_headers, method=method)
    try:
        context = ssl.create_default_context()
        with urllib.request.urlopen(request, timeout=30, context=context) as response:  # noqa: S310
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(detail)
            message = parsed.get("message") or parsed.get("error", {}).get("message") or detail
        except (json.JSONDecodeError, AttributeError):
            message = detail
        raise ImportFailure(f"Backend returned HTTP {error.code}: {message}") from error
    except urllib.error.URLError as error:
        raise ImportFailure(f"Cannot reach backend: {error.reason}") from error
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ImportFailure("Backend returned invalid JSON") from error
    if not isinstance(parsed, dict):
        raise ImportFailure("Backend returned a non-object JSON response")
    return parsed


if __name__ == "__main__":
    main()
