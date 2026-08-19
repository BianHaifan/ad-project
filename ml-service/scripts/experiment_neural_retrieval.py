from __future__ import annotations

import argparse
import json
import time
from collections import defaultdict
from pathlib import Path

import numpy as np
from experiment_neural_embedding import (
    DEFAULT_MODEL,
    DEFAULT_REVISION,
    candidate_document,
    job_document,
    load_sentence_model,
    ranking_metrics,
    read_frozen_rows,
    read_selected_entities,
)

from ad_recommender.data import sidecar_paths
from ad_recommender.model import TrainingPair, load_bundle
from ad_recommender.prelabeling import PrelabelerV2, read_query_ids
from ad_recommender.schemas import CandidateInput, JobInput


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Evaluate neural embeddings as full-catalog retrieval before v4 reranking"
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--teacher-model", type=Path, required=True)
    parser.add_argument("--retrieval-pairs", type=Path, required=True)
    parser.add_argument("--query-ids", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--sentence-model", default=DEFAULT_MODEL)
    parser.add_argument("--sentence-model-revision", default=DEFAULT_REVISION)
    parser.add_argument("--top-k", type=int, default=300)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--max-sequence-length", type=int, default=256)
    parser.add_argument("--resume-characters", type=int, default=1_200)
    parser.add_argument("--job-description-characters", type=int, default=1_200)
    args = parser.parse_args()

    started = time.perf_counter()
    frozen_ids = read_query_ids(args.query_ids)
    baseline_rows = read_frozen_rows(args.retrieval_pairs, frozen_ids)
    candidate_path, job_path = sidecar_paths(args.retrieval_pairs)
    candidate_raw, candidates = read_selected_entities(
        candidate_path,
        {row["candidate_id"] for row in baseline_rows},
        CandidateInput,
    )
    job_ids, job_documents = read_all_job_documents(
        job_path,
        args.job_description_characters,
    )

    encoding_started = time.perf_counter()
    sentence_model, device = load_sentence_model(
        args.sentence_model,
        args.sentence_model_revision,
        args.max_sequence_length,
    )
    candidate_ids = tuple(sorted(candidate_raw))
    candidate_vectors = sentence_model.encode(
        [
            candidate_document(candidate_raw[item], args.resume_characters)
            for item in candidate_ids
        ],
        batch_size=args.batch_size,
        normalize_embeddings=True,
        show_progress_bar=True,
    )
    job_vectors = sentence_model.encode(
        job_documents,
        batch_size=args.batch_size,
        normalize_embeddings=True,
        show_progress_bar=True,
    )
    similarities = np.asarray(candidate_vectors) @ np.asarray(job_vectors).T
    neural_rows = build_neural_rows(
        candidate_ids,
        job_ids,
        similarities,
        args.top_k,
    )
    encoding_seconds = time.perf_counter() - encoding_started

    all_rows = union_rows(baseline_rows, neural_rows)
    selected_job_ids = {row["job_id"] for row in all_rows}
    _, selected_jobs = read_selected_entities(job_path, selected_job_ids, JobInput)
    pairs = [
        TrainingPair(
            candidate=candidates[row["candidate_id"]],
            job=selected_jobs[row["job_id"]],
            relevance=0.0,
            query_id=row["query_id"],
        )
        for row in all_rows
    ]

    scoring_started = time.perf_counter()
    teacher = PrelabelerV2.load(args.teacher_model)
    teacher_predictions = teacher.predict_pairs(pairs)
    ranker = load_bundle(args.model)
    ranker.warm_up()
    ranker_scores = ranker._score_for_ranking(
        [pair.candidate for pair in pairs],
        [pair.job for pair in pairs],
    )
    pair_results = {
        (row["query_id"], row["job_id"]): {
            "label": float(prediction.label),
            "relevance": float(prediction.expected_relevance),
            "ranker_score": float(ranker_score),
            "embedding_score": float(row.get("embedding_score", 0.0)),
        }
        for row, prediction, ranker_score in zip(
            all_rows,
            teacher_predictions,
            ranker_scores,
            strict=True,
        )
    }
    scoring_seconds = time.perf_counter() - scoring_started

    baseline_evaluation = evaluate_pool(baseline_rows, pair_results, "ranker_score")
    neural_embedding_evaluation = evaluate_pool(
        neural_rows,
        pair_results,
        "embedding_score",
    )
    neural_reranked_evaluation = evaluate_pool(neural_rows, pair_results, "ranker_score")
    union_evaluation = evaluate_pool(all_rows, pair_results, "ranker_score")
    overlap = pool_overlap_by_query(baseline_rows, neural_rows)
    report = {
        "experiment_type": "FULL_CATALOG_NEURAL_RETRIEVAL_TEACHER_AUDIT",
        "evidence": {
            "labels": "AI_TEACHER_LABELS_NOT_HUMAN_BLIND_LABELS",
            "deployment_decision": "EXPERIMENT_ONLY_DO_NOT_REPLACE_ACTIVE_RETRIEVAL",
        },
        "data": {
            "query_count": len(frozen_ids),
            "catalog_jobs": len(job_ids),
            "top_k": args.top_k,
            "baseline_pairs": len(baseline_rows),
            "neural_pairs": len(neural_rows),
            "union_pairs": len(all_rows),
            "mean_pool_overlap_at_k": round(float(np.mean(overlap)), 6),
            "mean_new_neural_jobs_at_k": round(args.top_k - float(np.mean(overlap)), 6),
        },
        "neural_embedding": {
            "model": args.sentence_model,
            "revision": args.sentence_model_revision,
            "device": device,
            "dimensions": int(candidate_vectors.shape[1]),
            "max_sequence_length": args.max_sequence_length,
        },
        "results": {
            "existing_retrieval_then_v4": baseline_evaluation,
            "neural_retrieval_embedding_order": neural_embedding_evaluation,
            "neural_retrieval_then_v4": neural_reranked_evaluation,
            "union_retrieval_then_v4": union_evaluation,
        },
        "timing_seconds": {
            "model_load_and_full_catalog_encoding": round(encoding_seconds, 3),
            "teacher_and_ranker_union_scoring": round(scoring_seconds, 3),
            "total": round(time.perf_counter() - started, 3),
        },
        "interpretation_limits": [
            "Pool relevant counts are teacher labels, not observed user outcomes.",
            "The existing retrieval pool defines neither complete nor human-verified ground truth.",
            "Union recall is measured only against relevant items discovered inside that union.",
            "The neural model is general-purpose and was not fine-tuned on recruitment data.",
        ],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))


def read_all_job_documents(path: Path, description_characters: int) -> tuple[list[str], list[str]]:
    job_ids: list[str] = []
    documents: list[str] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            raw = json.loads(line)
            job_ids.append(str(raw["entity_id"]))
            documents.append(job_document(raw, description_characters))
    if not job_ids:
        raise ValueError(f"No jobs found in {path}")
    return job_ids, documents


def build_neural_rows(
    candidate_ids: tuple[str, ...],
    job_ids: list[str],
    similarities: np.ndarray,
    top_k: int,
) -> list[dict[str, str | float]]:
    rows: list[dict[str, str | float]] = []
    job_array = np.asarray(job_ids)
    for candidate_index, candidate_id in enumerate(candidate_ids):
        scores = similarities[candidate_index]
        selected = np.argpartition(-scores, min(top_k, len(scores)) - 1)[:top_k]
        selected = selected[np.argsort(-scores[selected], kind="stable")]
        rows.extend(
            {
                "query_id": candidate_id,
                "candidate_id": candidate_id,
                "job_id": str(job_array[index]),
                "embedding_score": float(scores[index]),
            }
            for index in selected
        )
    return rows


def union_rows(
    baseline_rows: list[dict[str, str]],
    neural_rows: list[dict[str, str | float]],
) -> list[dict[str, str | float]]:
    rows: dict[tuple[str, str], dict[str, str | float]] = {}
    for row in neural_rows:
        rows[(str(row["query_id"]), str(row["job_id"]))] = dict(row)
    for row in baseline_rows:
        key = (row["query_id"], row["job_id"])
        rows.setdefault(key, dict(row))
    return list(rows.values())


def evaluate_pool(
    rows: list[dict],
    pair_results: dict[tuple[str, str], dict[str, float]],
    score_name: str,
) -> dict:
    query_ids = [str(row["query_id"]) for row in rows]
    results = [pair_results[(str(row["query_id"]), str(row["job_id"]))] for row in rows]
    labels = np.asarray([item["label"] for item in results])
    scores = np.asarray([item[score_name] for item in results])
    relevant_counts: dict[str, int] = defaultdict(int)
    for query_id, label in zip(query_ids, labels, strict=True):
        relevant_counts[query_id] += int(label >= 2.0)
    metrics = ranking_metrics(query_ids, labels, scores)
    metrics["mean_relevant_jobs_in_pool"] = round(
        float(np.mean(list(relevant_counts.values()))),
        6,
    )
    metrics["total_relevant_pairs_in_pool"] = int((labels >= 2.0).sum())
    return metrics


def pool_overlap_by_query(
    baseline_rows: list[dict], neural_rows: list[dict]
) -> list[int]:
    baseline: dict[str, set[str]] = defaultdict(set)
    neural: dict[str, set[str]] = defaultdict(set)
    for row in baseline_rows:
        baseline[str(row["query_id"])].add(str(row["job_id"]))
    for row in neural_rows:
        neural[str(row["query_id"])].add(str(row["job_id"]))
    return [len(baseline[query_id] & neural[query_id]) for query_id in sorted(baseline)]


if __name__ == "__main__":
    main()
