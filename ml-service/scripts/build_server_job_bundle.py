from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import UTC, datetime
from pathlib import Path

from ad_recommender.backend_import import select_import_jobs, to_create_job_request
from ad_recommender.data import load_jobs

EMAIL_PATTERN = re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Build a shareable backend job import bundle")
    parser.add_argument("--jobs", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    selected = select_import_jobs(load_jobs(args.jobs), args.limit, args.seed)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    data_path = args.output_dir / "jobs.jsonl"
    with data_path.open("w", encoding="utf-8", newline="\n") as handle:
        for job in selected:
            request = to_create_job_request(job)
            request.pop("deadline")
            request["description"] = redact_email(str(request["description"]))
            request["requirements"] = [
                redact_email(str(value)) for value in request["requirements"]
            ]
            handle.write(
                json.dumps(
                    {"formatVersion": 1, "sourceJobId": job.entity_id, **request},
                    ensure_ascii=False,
                    separators=(",", ":"),
                )
            )
            handle.write("\n")

    manifest = {
        "formatVersion": 1,
        "createdAt": datetime.now(UTC).isoformat(),
        "jobCount": len(selected),
        "selectionSeed": args.seed,
        "sourceSha256": sha256_file(args.jobs),
        "dataSha256": sha256_file(data_path),
        "notes": [
            "Import through the Spring Boot recruiter API; never write MySQL directly.",
            "Source redistribution rights must be confirmed before sharing this bundle.",
            "Email addresses in descriptions and requirements were redacted.",
        ],
    }
    manifest_path = args.output_dir / "manifest.json"
    manifest_path.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(json.dumps(manifest, indent=2, ensure_ascii=False))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def redact_email(value: str) -> str:
    return EMAIL_PATTERN.sub("[redacted-email]", value)


if __name__ == "__main__":
    main()
