from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from dataclasses import replace
from pathlib import Path

from ad_recommender.data import sidecar_paths
from ad_recommender.evaluation import evaluate_ranking
from ad_recommender.model import TrainingPair, load_bundle
from ad_recommender.prelabeling import PrelabelerV2, read_query_ids
from ad_recommender.schemas import CandidateInput, JobInput


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Audit a trained student model on frozen query groups using teacher labels"
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--teacher-model", type=Path, required=True)
    parser.add_argument("--retrieval-pairs", type=Path, required=True)
    parser.add_argument("--training-pairs", type=Path, required=True)
    parser.add_argument("--query-ids", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    frozen_ids = read_query_ids(args.query_ids)
    training_overlap = find_query_overlap(args.training_pairs, frozen_ids)
    if training_overlap:
        raise ValueError(f"Frozen query IDs occur in training data: {sorted(training_overlap)}")

    candidate_path, job_path = sidecar_paths(args.retrieval_pairs)
    candidates = read_selected_entities(candidate_path, frozen_ids, CandidateInput)
    raw_rows: list[dict[str, str]] = []
    selected_job_ids: set[str] = set()
    with args.retrieval_pairs.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            if row["query_id"] in frozen_ids:
                raw_rows.append(row)
                selected_job_ids.add(row["job_id"])
    jobs = read_selected_entities(job_path, selected_job_ids, JobInput)
    pairs = [
        TrainingPair(
            candidate=candidates[row["candidate_id"]],
            job=jobs[row["job_id"]],
            relevance=0.0,
            query_id=row["query_id"],
        )
        for row in raw_rows
    ]
    teacher = PrelabelerV2.load(args.teacher_model)
    predictions = teacher.predict_pairs(pairs)
    evaluated_pairs = [
        replace(
            pair,
            relevance=prediction.expected_relevance,
            evaluation_relevance=float(prediction.label),
        )
        for pair, prediction in zip(pairs, predictions, strict=True)
    ]
    bundle = load_bundle(args.model)
    bundle.warm_up()
    metrics = evaluate_ranking(bundle, evaluated_pairs)
    result = {
        "audit_type": "FROZEN_QUERY_TEACHER_AGREEMENT_NOT_HUMAN_BLIND_TEST",
        "model_version": bundle.manifest.model_version,
        "teacher_model_version": teacher.model_version,
        "frozen_query_groups": len(frozen_ids),
        "training_overlap_query_groups": 0,
        "evaluated_pairs": len(evaluated_pairs),
        "label_distribution": dict(
            Counter(str(prediction.label) for prediction in predictions)
        ),
        "metrics": metrics,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))


def find_query_overlap(path: Path, query_ids: set[str]) -> set[str]:
    overlap: set[str] = set()
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            query_id = row["query_id"]
            if query_id in query_ids:
                overlap.add(query_id)
    return overlap


def read_selected_entities(path: Path, ids: set[str], schema) -> dict[str, object]:
    selected: dict[str, object] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            raw = json.loads(line)
            entity_id = str(raw.get("entity_id", ""))
            if entity_id in ids:
                selected[entity_id] = schema.model_validate(raw)
                if len(selected) == len(ids):
                    break
    missing = ids - selected.keys()
    if missing:
        raise ValueError(f"Missing selected entities in {path}: {sorted(missing)[:5]}")
    return selected


if __name__ == "__main__":
    main()
