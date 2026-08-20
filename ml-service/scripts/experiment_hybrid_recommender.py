from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import time
from collections import Counter
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import ndcg_score

from ad_recommender.data import sidecar_paths
from ad_recommender.hybrid import CollaborativeSvd, LsaEmbeddingIndex, blended_scores
from ad_recommender.model import ModelBundle, TrainingPair, load_bundle
from ad_recommender.prelabeling import PrelabelerV2, read_query_ids
from ad_recommender.schemas import CandidateInput, JobInput


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compare the active ranker with LSA embeddings and synthetic-feedback CF"
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--teacher-model", type=Path, required=True)
    parser.add_argument("--retrieval-pairs", type=Path, required=True)
    parser.add_argument("--training-pairs", type=Path, required=True)
    parser.add_argument("--query-ids", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--dev-queries", type=int, default=100)
    parser.add_argument("--embedding-dimensions", type=int, default=128)
    parser.add_argument("--embedding-max-features", type=int, default=30_000)
    parser.add_argument("--cf-dimensions", type=int, default=64)
    parser.add_argument("--positive-threshold", type=float, default=2.0)
    parser.add_argument("--holdout-fraction", type=float, default=0.2)
    parser.add_argument("--max-text-characters", type=int, default=4_000)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    validate_args(args)

    started = time.perf_counter()
    frozen_ids = read_query_ids(args.query_ids)
    training = read_training_pairs(args.training_pairs)
    overlap = frozen_ids & set(training["query_id"])
    if overlap:
        raise ValueError(f"Frozen query IDs occur in training data: {sorted(overlap)[:5]}")
    dev_ids = select_development_queries(training, args.dev_queries, args.seed)
    frozen_rows = read_frozen_rows(args.retrieval_pairs, frozen_ids)
    selected_rows = pd.concat(
        [training[training["query_id"].isin(dev_ids)].copy(), frozen_rows.copy()],
        ignore_index=True,
    )

    candidate_path, job_path = sidecar_paths(args.retrieval_pairs)
    candidate_ids = set(training["candidate_id"]) | set(frozen_rows["candidate_id"])
    job_ids = set(training["job_id"]) | set(frozen_rows["job_id"])
    selected_candidate_ids = set(selected_rows["candidate_id"])
    selected_job_ids = set(selected_rows["job_id"])
    candidate_documents, selected_candidates = read_entities(
        candidate_path,
        candidate_ids,
        selected_candidate_ids,
        CandidateInput,
        candidate_document,
        args.max_text_characters,
    )
    job_documents, selected_jobs = read_entities(
        job_path,
        job_ids,
        selected_job_ids,
        JobInput,
        job_document,
        args.max_text_characters,
    )

    teacher_started = time.perf_counter()
    frozen_rows = label_frozen_rows(
        frozen_rows,
        selected_candidates,
        selected_jobs,
        args.teacher_model,
    )
    selected_rows = pd.concat(
        [training[training["query_id"].isin(dev_ids)].copy(), frozen_rows.copy()],
        ignore_index=True,
    )
    teacher_seconds = time.perf_counter() - teacher_started

    embedding_started = time.perf_counter()
    embedding = LsaEmbeddingIndex.fit(
        candidate_documents,
        job_documents,
        dimensions=args.embedding_dimensions,
        max_features=args.embedding_max_features,
        random_seed=args.seed,
    )
    selected_rows["embedding_score"] = embedding.score_pairs(
        selected_rows["candidate_id"].tolist(),
        selected_rows["job_id"].tolist(),
    )
    embedding_seconds = time.perf_counter() - embedding_started

    ranker_started = time.perf_counter()
    ranker = load_bundle(args.model)
    ranker.warm_up()
    selected_rows["ranker_score"] = score_active_ranker(
        ranker,
        selected_rows,
        selected_candidates,
        selected_jobs,
    )
    ranker_seconds = time.perf_counter() - ranker_started

    dev_rows = selected_rows[selected_rows["query_id"].isin(dev_ids)].copy()
    test_rows = selected_rows[selected_rows["query_id"].isin(frozen_ids)].copy()
    cold_weight, cold_dev_metric = tune_two_component_blend(dev_rows)
    dev_rows["hybrid_cold_score"] = blended_scores(
        dev_rows["query_id"],
        [dev_rows["ranker_score"], dev_rows["embedding_score"]],
        [cold_weight, 1.0 - cold_weight],
    )
    test_rows["hybrid_cold_score"] = blended_scores(
        test_rows["query_id"],
        [test_rows["ranker_score"], test_rows["embedding_score"]],
        [cold_weight, 1.0 - cold_weight],
    )

    cf_started = time.perf_counter()
    all_labeled = pd.concat([training.copy(), frozen_rows.copy()], ignore_index=True)
    evaluation_ids = dev_ids | frozen_ids
    interactions, history_pairs, warm_eligible = build_synthetic_interactions(
        all_labeled,
        evaluation_ids,
        args.positive_threshold,
        args.holdout_fraction,
        args.seed,
    )
    cf = CollaborativeSvd.fit(
        user_ids=all_labeled["candidate_id"].unique(),
        item_ids=all_labeled["job_id"].unique(),
        interactions=interactions,
        dimensions=args.cf_dimensions,
        random_seed=args.seed,
    )
    selected_rows["cf_score"] = cf.score_pairs(
        selected_rows["candidate_id"].tolist(),
        selected_rows["job_id"].tolist(),
    )
    popularity = Counter(item_id for _, item_id, _ in interactions)
    selected_rows["popularity_score"] = selected_rows["job_id"].map(popularity).fillna(0.0)
    warm_rows = build_warm_evaluation_rows(selected_rows, history_pairs, warm_eligible)
    warm_dev = warm_rows[warm_rows["query_id"].isin(dev_ids)].copy()
    warm_test = warm_rows[warm_rows["query_id"].isin(frozen_ids)].copy()
    warm_weights, warm_dev_metric = tune_three_component_blend(warm_dev)
    warm_dev["hybrid_warm_score"] = blended_scores(
        warm_dev["query_id"],
        [warm_dev["ranker_score"], warm_dev["embedding_score"], warm_dev["cf_score"]],
        warm_weights,
    )
    warm_test["hybrid_warm_score"] = blended_scores(
        warm_test["query_id"],
        [warm_test["ranker_score"], warm_test["embedding_score"], warm_test["cf_score"]],
        warm_weights,
    )
    cf_seconds = time.perf_counter() - cf_started

    report = {
        "experiment_type": "OFFLINE_HYBRID_PROTOTYPE_NOT_PRODUCTION_BEHAVIOR_EVALUATION",
        "evidence": {
            "cold_start_labels": "FROZEN_AI_TEACHER_LABELS_NOT_HUMAN_BLIND_LABELS",
            "collaborative_filtering_feedback": (
                "SYNTHETIC_IMPLICIT_FEEDBACK_DERIVED_FROM_AI_TEACHER_RELEVANCE"
            ),
            "deployment_decision": "EXPERIMENT_ONLY_DO_NOT_REPLACE_ACTIVE_MODEL",
        },
        "data": {
            "training_pairs": int(len(training)),
            "training_queries": int(training["query_id"].nunique()),
            "frozen_queries": len(frozen_ids),
            "frozen_pairs": int(len(frozen_rows)),
            "development_queries": len(dev_ids),
            "unique_jobs": len(job_documents),
            "synthetic_interactions": len(interactions),
            "warm_development_queries": int(warm_dev["query_id"].nunique()),
            "warm_frozen_queries": int(warm_test["query_id"].nunique()),
        },
        "models": {
            "active_ranker": {
                "version": ranker.manifest.model_version,
                "algorithm": ranker.manifest.algorithm,
            },
            "embedding": {
                "algorithm": "TFIDF_PLUS_TRUNCATED_SVD_LSA",
                "dimensions": int(embedding.reducer.n_components),
                "explained_variance_ratio": round(
                    float(embedding.reducer.explained_variance_ratio_.sum()), 6
                ),
            },
            "collaborative_filtering": {
                "algorithm": "IMPLICIT_FEEDBACK_TRUNCATED_SVD_BASELINE",
                "dimensions": int(cf.reducer.n_components),
                "explained_variance_ratio": round(
                    float(cf.reducer.explained_variance_ratio_.sum()), 6
                ),
            },
        },
        "cold_start_teacher_agreement": {
            "tuned_on_development": {
                "ranker_weight": cold_weight,
                "embedding_weight": round(1.0 - cold_weight, 2),
                "map_at_10": cold_dev_metric,
            },
            "frozen_test": score_columns(
                test_rows,
                ["ranker_score", "embedding_score", "hybrid_cold_score"],
            ),
        },
        "warm_start_synthetic_holdout": {
            "positive_threshold": args.positive_threshold,
            "holdout_fraction": args.holdout_fraction,
            "training_history_items_are_excluded_from_ranking": True,
            "tuned_on_development": {
                "ranker_weight": warm_weights[0],
                "embedding_weight": warm_weights[1],
                "cf_weight": warm_weights[2],
                "map_at_10": warm_dev_metric,
            },
            "frozen_test": score_columns(
                warm_test,
                [
                    "ranker_score",
                    "embedding_score",
                    "popularity_score",
                    "cf_score",
                    "hybrid_warm_score",
                ],
            ),
        },
        "timing_seconds": {
            "teacher_labeling": round(teacher_seconds, 3),
            "embedding_fit_and_selected_scoring": round(embedding_seconds, 3),
            "active_ranker_selected_scoring": round(ranker_seconds, 3),
            "cf_fit_tuning_and_scoring": round(cf_seconds, 3),
            "total": round(time.perf_counter() - started, 3),
        },
        "limitations": [
            (
                "No real impression, click, save, application, interview, or hire event log "
                "was available."
            ),
            "CF feedback was synthesized from the same AI teacher relevance used by the ranker.",
            "Frozen labels are teacher labels, not independently reviewed human ground truth.",
            "LSA is a dense scikit-learn embedding baseline, not a neural sentence embedding.",
            "The active ranker saw development query labels during its original training.",
        ],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))


def validate_args(args: argparse.Namespace) -> None:
    if args.dev_queries < 10:
        raise ValueError("At least ten development queries are required")
    if not 0.0 < args.holdout_fraction < 1.0:
        raise ValueError("Holdout fraction must be between zero and one")


def read_training_pairs(path: Path) -> pd.DataFrame:
    frame = pd.read_csv(
        path,
        usecols=["query_id", "candidate_id", "job_id", "relevance", "teacher_label"],
        dtype={"query_id": str, "candidate_id": str, "job_id": str},
    )
    frame["label"] = frame["teacher_label"].astype(float)
    return frame.drop(columns=["teacher_label"])


def read_frozen_rows(path: Path, query_ids: set[str]) -> pd.DataFrame:
    rows: list[dict[str, str]] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            if row["query_id"] in query_ids:
                rows.append(
                    {
                        "query_id": row["query_id"],
                        "candidate_id": row["candidate_id"],
                        "job_id": row["job_id"],
                        "relevance": 0.0,
                        "label": 0.0,
                    }
                )
    frame = pd.DataFrame(rows)
    if frame["query_id"].nunique() != len(query_ids):
        raise ValueError("Not every frozen query is present in the retrieval pairs")
    return frame


def select_development_queries(frame: pd.DataFrame, count: int, seed: int) -> set[str]:
    query_ids = sorted(
        frame["query_id"].unique(),
        key=lambda item: stable_digest(f"{seed}:development:{item}"),
    )
    return set(query_ids[: min(count, len(query_ids))])


def read_entities(
    path: Path,
    required_ids: set[str],
    selected_ids: set[str],
    schema,
    document_builder,
    max_characters: int,
) -> tuple[dict[str, str], dict[str, object]]:
    documents: dict[str, str] = {}
    selected: dict[str, object] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            raw = json.loads(line)
            entity_id = str(raw.get("entity_id", ""))
            if entity_id not in required_ids:
                continue
            documents[entity_id] = document_builder(raw, max_characters)
            if entity_id in selected_ids:
                selected[entity_id] = schema.model_validate(raw)
            if len(documents) == len(required_ids) and len(selected) == len(selected_ids):
                break
    missing_documents = required_ids - documents.keys()
    missing_selected = selected_ids - selected.keys()
    if missing_documents or missing_selected:
        raise ValueError(
            f"Missing entities in {path}: documents={len(missing_documents)}, "
            f"selected={len(missing_selected)}"
        )
    return documents, selected


def candidate_document(raw: dict, max_characters: int) -> str:
    preferences = raw.get("preferences") or {}
    parts = [
        str(raw.get("headline") or ""),
        " ".join(str(item) for item in raw.get("skills") or []),
        " ".join(str(item) for item in preferences.get("desired_titles") or []),
        str(raw.get("resume_text") or "")[:max_characters],
    ]
    return " ".join(parts)


def job_document(raw: dict, max_characters: int) -> str:
    parts = [
        str(raw.get("title") or ""),
        " ".join(str(item) for item in raw.get("skills") or []),
        " ".join(str(item) for item in raw.get("requirements") or []),
        str(raw.get("description") or "")[:max_characters],
    ]
    return " ".join(parts)


def label_frozen_rows(
    rows: pd.DataFrame,
    candidates: dict[str, CandidateInput],
    jobs: dict[str, JobInput],
    teacher_path: Path,
) -> pd.DataFrame:
    pairs = [
        TrainingPair(
            candidate=candidates[row.candidate_id],
            job=jobs[row.job_id],
            relevance=0.0,
            query_id=row.query_id,
        )
        for row in rows.itertuples(index=False)
    ]
    teacher = PrelabelerV2.load(teacher_path)
    predictions = teacher.predict_pairs(pairs)
    labeled = rows.copy()
    labeled["relevance"] = [item.expected_relevance for item in predictions]
    labeled["label"] = [float(item.label) for item in predictions]
    return labeled


def score_active_ranker(
    bundle: ModelBundle,
    rows: pd.DataFrame,
    candidates: dict[str, CandidateInput],
    jobs: dict[str, JobInput],
    batch_size: int = 5_000,
) -> np.ndarray:
    scores: list[int] = []
    for start in range(0, len(rows), batch_size):
        batch = rows.iloc[start : start + batch_size]
        candidate_batch = [candidates[item] for item in batch["candidate_id"]]
        job_batch = [jobs[item] for item in batch["job_id"]]
        if bundle.regressor is not None and bundle.scoring_extractor is not None:
            scores.extend(bundle._score_for_ranking(candidate_batch, job_batch))
        else:
            scores.extend(score for score, _ in bundle.score_pairs(candidate_batch, job_batch))
    return np.asarray(scores, dtype=np.float64)


def build_synthetic_interactions(
    rows: pd.DataFrame,
    evaluation_ids: set[str],
    positive_threshold: float,
    holdout_fraction: float,
    seed: int,
) -> tuple[list[tuple[str, str, float]], set[tuple[str, str]], set[str]]:
    relevant = rows[rows["relevance"] >= positive_threshold]
    interactions: list[tuple[str, str, float]] = []
    history_pairs: set[tuple[str, str]] = set()
    warm_eligible: set[str] = set()
    for query_id, group in relevant.groupby("query_id", sort=False):
        ordered = sorted(
            group.itertuples(index=False),
            key=lambda row: stable_digest(f"{seed}:holdout:{query_id}:{row.job_id}"),
        )
        holdout_count = 0
        if query_id in evaluation_ids and len(ordered) >= 2:
            holdout_count = min(
                len(ordered) - 1,
                max(1, math.ceil(len(ordered) * holdout_fraction)),
            )
            warm_eligible.add(str(query_id))
        held_out = {str(row.job_id) for row in ordered[:holdout_count]}
        for row in ordered:
            pair = (str(row.candidate_id), str(row.job_id))
            if str(row.job_id) in held_out:
                continue
            weight = 1.0 + max(0.0, float(row.relevance) - positive_threshold)
            interactions.append((pair[0], pair[1], weight))
            if query_id in evaluation_ids:
                history_pairs.add(pair)
    return interactions, history_pairs, warm_eligible


def build_warm_evaluation_rows(
    rows: pd.DataFrame,
    history_pairs: set[tuple[str, str]],
    eligible_queries: set[str],
) -> pd.DataFrame:
    mask = [
        str(query_id) in eligible_queries
        and (str(candidate_id), str(job_id)) not in history_pairs
        for query_id, candidate_id, job_id in zip(
            rows["query_id"], rows["candidate_id"], rows["job_id"], strict=True
        )
    ]
    return rows[np.asarray(mask, dtype=bool)].copy()


def tune_two_component_blend(rows: pd.DataFrame) -> tuple[float, float]:
    best_weight = 1.0
    best_metric = -1.0
    for ranker_weight in np.linspace(0.0, 1.0, 21):
        scores = blended_scores(
            rows["query_id"],
            [rows["ranker_score"], rows["embedding_score"]],
            [ranker_weight, 1.0 - ranker_weight],
        )
        metric = ranking_metrics(rows, scores)["map_at_10"]
        if metric > best_metric:
            best_metric = metric
            best_weight = float(ranker_weight)
    return round(best_weight, 2), round(best_metric, 6)


def tune_three_component_blend(rows: pd.DataFrame) -> tuple[list[float], float]:
    best_weights = [1.0, 0.0, 0.0]
    best_metric = -1.0
    for ranker_tenths in range(11):
        for embedding_tenths in range(11 - ranker_tenths):
            cf_tenths = 10 - ranker_tenths - embedding_tenths
            weights = [
                ranker_tenths / 10.0,
                embedding_tenths / 10.0,
                cf_tenths / 10.0,
            ]
            scores = blended_scores(
                rows["query_id"],
                [rows["ranker_score"], rows["embedding_score"], rows["cf_score"]],
                weights,
            )
            metric = ranking_metrics(rows, scores)["map_at_10"]
            if metric > best_metric:
                best_metric = metric
                best_weights = weights
    return best_weights, round(best_metric, 6)


def score_columns(frame: pd.DataFrame, columns: list[str]) -> dict[str, dict[str, float]]:
    return {column: ranking_metrics(frame, frame[column]) for column in columns}


def ranking_metrics(frame: pd.DataFrame, scores) -> dict[str, float]:
    evaluated = frame[["query_id", "label"]].copy()
    evaluated["score"] = np.asarray(scores, dtype=np.float64)
    precision_values: list[float] = []
    recall_values: list[float] = []
    ndcg_values: list[float] = []
    average_precision_values: list[float] = []
    eligible_average_precision_values: list[float] = []
    for _, group in evaluated.groupby("query_id", sort=False):
        labels = group["label"].to_numpy(dtype=np.float64)
        model_scores = group["score"].to_numpy(dtype=np.float64)
        order = np.argsort(-model_scores, kind="stable")
        relevant = labels >= 2.0
        precision_values.append(float(relevant[order[:5]].mean()))
        total_relevant = int(relevant.sum())
        recall_values.append(
            float(relevant[order[:10]].sum() / total_relevant) if total_relevant else 0.0
        )
        if len(labels) >= 2 and labels.max() > labels.min():
            ndcg_values.append(
                float(ndcg_score(labels.reshape(1, -1), model_scores.reshape(1, -1), k=10))
            )
        average_precision = average_precision_at_k(relevant[order], 10)
        average_precision_values.append(average_precision)
        if total_relevant:
            eligible_average_precision_values.append(average_precision)
    query_count = int(evaluated["query_id"].nunique())
    eligible_count = len(eligible_average_precision_values)
    return {
        "precision_at_5": rounded_mean(precision_values),
        "recall_at_10": rounded_mean(recall_values),
        "ndcg_at_10": rounded_mean(ndcg_values),
        "map_at_10": rounded_mean(average_precision_values),
        "map_at_10_eligible": rounded_mean(eligible_average_precision_values),
        "query_count": float(query_count),
        "eligible_query_count": float(eligible_count),
        "relevant_query_coverage": round(eligible_count / query_count, 6),
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


def stable_digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


if __name__ == "__main__":
    main()
