from __future__ import annotations

import argparse
import csv
import json
import time
from collections import Counter
from pathlib import Path

import numpy as np
from sklearn.metrics import ndcg_score

from ad_recommender.data import sidecar_paths
from ad_recommender.hybrid import blended_scores
from ad_recommender.model import TrainingPair, load_bundle
from ad_recommender.prelabeling import PrelabelerV2, read_query_ids
from ad_recommender.schemas import CandidateInput, JobInput

DEFAULT_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
DEFAULT_REVISION = "c315f904dfc467d8b9c40ab4ed50b3a8d0866c15"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Evaluate an optional neural sentence embedding on frozen query groups"
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--teacher-model", type=Path, required=True)
    parser.add_argument("--retrieval-pairs", type=Path, required=True)
    parser.add_argument("--query-ids", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--sentence-model", default=DEFAULT_MODEL)
    parser.add_argument("--sentence-model-revision", default=DEFAULT_REVISION)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--max-sequence-length", type=int, default=256)
    parser.add_argument("--resume-characters", type=int, default=1_200)
    parser.add_argument("--job-description-characters", type=int, default=1_200)
    args = parser.parse_args()

    started = time.perf_counter()
    frozen_ids = read_query_ids(args.query_ids)
    rows = read_frozen_rows(args.retrieval_pairs, frozen_ids)
    candidate_path, job_path = sidecar_paths(args.retrieval_pairs)
    candidate_raw, candidates = read_selected_entities(
        candidate_path,
        {row["candidate_id"] for row in rows},
        CandidateInput,
    )
    job_raw, jobs = read_selected_entities(
        job_path,
        {row["job_id"] for row in rows},
        JobInput,
    )
    pairs = [
        TrainingPair(
            candidate=candidates[row["candidate_id"]],
            job=jobs[row["job_id"]],
            relevance=0.0,
            query_id=row["query_id"],
        )
        for row in rows
    ]

    teacher_started = time.perf_counter()
    teacher = PrelabelerV2.load(args.teacher_model)
    teacher_predictions = teacher.predict_pairs(pairs)
    labels = np.asarray([float(item.label) for item in teacher_predictions])
    teacher_seconds = time.perf_counter() - teacher_started

    ranker_started = time.perf_counter()
    ranker = load_bundle(args.model)
    ranker.warm_up()
    ranker_scores = np.asarray(ranker._score_for_ranking(
        [pair.candidate for pair in pairs],
        [pair.job for pair in pairs],
    ))
    ranker_seconds = time.perf_counter() - ranker_started

    embedding_started = time.perf_counter()
    neural_model, device = load_sentence_model(
        args.sentence_model,
        args.sentence_model_revision,
        args.max_sequence_length,
    )
    candidate_ids = tuple(sorted(candidate_raw))
    job_ids = tuple(sorted(job_raw))
    candidate_vectors = neural_model.encode(
        [
            candidate_document(candidate_raw[item], args.resume_characters)
            for item in candidate_ids
        ],
        batch_size=args.batch_size,
        normalize_embeddings=True,
        show_progress_bar=True,
    )
    job_vectors = neural_model.encode(
        [
            job_document(job_raw[item], args.job_description_characters)
            for item in job_ids
        ],
        batch_size=args.batch_size,
        normalize_embeddings=True,
        show_progress_bar=True,
    )
    candidate_index = {item: index for index, item in enumerate(candidate_ids)}
    job_index = {item: index for index, item in enumerate(job_ids)}
    embedding_scores = np.asarray(
        [
            float(
                candidate_vectors[candidate_index[row["candidate_id"]]]
                @ job_vectors[job_index[row["job_id"]]]
            )
            for row in rows
        ]
    )
    embedding_seconds = time.perf_counter() - embedding_started

    query_ids = [row["query_id"] for row in rows]
    variants: dict[str, np.ndarray] = {
        "ranker_only": ranker_scores,
        "neural_embedding_only": embedding_scores,
    }
    for embedding_weight in (0.1, 0.2, 0.3, 0.5):
        variants[f"ranker_{1.0 - embedding_weight:.1f}_embedding_{embedding_weight:.1f}"] = (
            blended_scores(
                query_ids,
                [ranker_scores, embedding_scores],
                [1.0 - embedding_weight, embedding_weight],
            )
        )
    result = {
        "experiment_type": "FROZEN_QUERY_NEURAL_EMBEDDING_EXPLORATION_NOT_MODEL_SELECTION",
        "evidence": {
            "labels": "FROZEN_AI_TEACHER_LABELS_NOT_HUMAN_BLIND_LABELS",
            "blend_weights": "PREDECLARED_FIXED_WEIGHTS_NOT_TUNED_ON_FROZEN_TEST",
            "deployment_decision": "EXPERIMENT_ONLY_DO_NOT_REPLACE_ACTIVE_MODEL",
        },
        "data": {
            "query_count": len(frozen_ids),
            "pair_count": len(rows),
            "unique_candidates": len(candidate_ids),
            "unique_jobs": len(job_ids),
            "teacher_label_distribution": dict(
                Counter(str(int(item)) for item in labels)
            ),
        },
        "neural_embedding": {
            "model": args.sentence_model,
            "revision": args.sentence_model_revision,
            "device": device,
            "dimensions": int(candidate_vectors.shape[1]),
            "max_sequence_length": args.max_sequence_length,
            "resume_characters": args.resume_characters,
            "job_description_characters": args.job_description_characters,
        },
        "metrics": {
            name: ranking_metrics(query_ids, labels, scores)
            for name, scores in variants.items()
        },
        "timing_seconds": {
            "teacher_labeling": round(teacher_seconds, 3),
            "active_ranker_scoring": round(ranker_seconds, 3),
            "model_load_encoding_and_similarity": round(embedding_seconds, 3),
            "total": round(time.perf_counter() - started, 3),
        },
        "limitations": [
            "The sentence model is general-purpose and was not fine-tuned on hiring outcomes.",
            "Long resumes and job descriptions are summarized and then token-truncated.",
            "Frozen labels are generated by the same AI teacher used to train the active ranker.",
            "The test contains only twenty query groups and is not a human blind evaluation.",
        ],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))


def load_sentence_model(model_name: str, revision: str, max_sequence_length: int):
    try:
        import torch
        from sentence_transformers import SentenceTransformer
    except ImportError as error:
        raise RuntimeError(
            "Install the optional experiment dependency: sentence-transformers==6.0.0"
        ) from error
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = SentenceTransformer(model_name, revision=revision, device=device)
    model.max_seq_length = max_sequence_length
    return model, device


def read_frozen_rows(path: Path, query_ids: set[str]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            if row["query_id"] in query_ids:
                rows.append(row)
    if len({row["query_id"] for row in rows}) != len(query_ids):
        raise ValueError("Not every frozen query is present in the retrieval pairs")
    return rows


def read_selected_entities(path: Path, ids: set[str], schema):
    raw_entities: dict[str, dict] = {}
    entities: dict[str, object] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            raw = json.loads(line)
            entity_id = str(raw.get("entity_id", ""))
            if entity_id not in ids:
                continue
            raw_entities[entity_id] = raw
            entities[entity_id] = schema.model_validate(raw)
            if len(entities) == len(ids):
                break
    missing = ids - entities.keys()
    if missing:
        raise ValueError(f"Missing selected entities in {path}: {sorted(missing)[:5]}")
    return raw_entities, entities


def candidate_document(raw: dict, resume_characters: int) -> str:
    preferences = raw.get("preferences") or {}
    return " ".join(
        [
            "Candidate desired roles:",
            " ".join(str(item) for item in preferences.get("desired_titles") or []),
            "Headline:",
            str(raw.get("headline") or ""),
            "Skills:",
            " ".join(str(item) for item in raw.get("skills") or []),
            "Resume summary:",
            str(raw.get("resume_text") or "")[:resume_characters],
        ]
    )


def job_document(raw: dict, description_characters: int) -> str:
    return " ".join(
        [
            "Job title:",
            str(raw.get("title") or ""),
            "Required skills:",
            " ".join(str(item) for item in raw.get("skills") or []),
            "Requirements:",
            " ".join(str(item) for item in raw.get("requirements") or []),
            "Job description:",
            str(raw.get("description") or "")[:description_characters],
        ]
    )


def ranking_metrics(
    query_ids: list[str], labels: np.ndarray, scores: np.ndarray
) -> dict[str, float]:
    query_array = np.asarray(query_ids)
    precision_values: list[float] = []
    recall_values: list[float] = []
    ndcg_values: list[float] = []
    average_precision_values: list[float] = []
    for query_id in np.unique(query_array):
        indexes = np.flatnonzero(query_array == query_id)
        local_labels = labels[indexes]
        local_scores = scores[indexes]
        order = np.argsort(-local_scores, kind="stable")
        relevant = local_labels >= 2.0
        precision_values.append(float(relevant[order[:5]].mean()))
        total_relevant = int(relevant.sum())
        recall_values.append(float(relevant[order[:10]].sum() / total_relevant))
        ndcg_values.append(
            float(
                ndcg_score(
                    local_labels.reshape(1, -1),
                    local_scores.reshape(1, -1),
                    k=10,
                )
            )
        )
        average_precision_values.append(average_precision_at_k(relevant[order], 10))
    return {
        "precision_at_5": rounded_mean(precision_values),
        "recall_at_10": rounded_mean(recall_values),
        "ndcg_at_10": rounded_mean(ndcg_values),
        "map_at_10": rounded_mean(average_precision_values),
        "query_count": float(len(np.unique(query_array))),
    }


def average_precision_at_k(relevant: np.ndarray, k: int) -> float:
    hits = 0
    precision_sum = 0.0
    for index, is_relevant in enumerate(relevant[:k], start=1):
        if is_relevant:
            hits += 1
            precision_sum += hits / index
    denominator = min(int(relevant.sum()), k)
    return precision_sum / denominator if denominator else 0.0


def rounded_mean(values: list[float]) -> float:
    return round(float(np.mean(values)) if values else 0.0, 6)


if __name__ == "__main__":
    main()
