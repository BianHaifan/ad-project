from __future__ import annotations

from collections import defaultdict

import numpy as np
from sklearn.metrics import ndcg_score

from ad_recommender.model import ModelBundle, TrainingPair


def evaluate_ranking(bundle: ModelBundle, pairs: list[TrainingPair]) -> dict[str, float]:
    grouped: dict[str, list[tuple[float, float]]] = defaultdict(list)
    candidates = [pair.candidate for pair in pairs]
    jobs = [pair.job for pair in pairs]
    if bundle.regressor is not None and bundle.scoring_extractor is not None:
        scores = bundle._score_for_ranking(candidates, jobs)
    else:
        scores = [score for score, _ in bundle.score_pairs(candidates, jobs)]
    for pair, score in zip(pairs, scores, strict=True):
        label = (
            pair.evaluation_relevance
            if pair.evaluation_relevance is not None
            else pair.relevance
        )
        grouped[pair.query_id].append((label, float(score)))
    if not grouped:
        return empty_metrics()

    precision_values: list[float] = []
    recall_values: list[float] = []
    ndcg_values: list[float] = []
    average_precision_values: list[float] = []
    eligible_average_precision_values: list[float] = []
    eligible_queries = 0
    for query_values in grouped.values():
        labels = np.array([label for label, _ in query_values], dtype=float)
        scores = np.array([score for _, score in query_values], dtype=float)
        order = np.argsort(-scores, kind="stable")
        relevant = labels >= 2.0
        top_five = relevant[order[:5]]
        top_ten = relevant[order[:10]]
        precision_values.append(float(top_five.mean()) if len(top_five) else 0.0)
        total_relevant = int(relevant.sum())
        eligible_queries += int(total_relevant > 0)
        recall_values.append(float(top_ten.sum() / total_relevant) if total_relevant else 0.0)
        if len(labels) >= 2 and labels.max() > labels.min():
            ndcg_values.append(
                float(ndcg_score(labels.reshape(1, -1), scores.reshape(1, -1), k=10))
            )
        average_precision = average_precision_at_k(relevant[order], 10)
        average_precision_values.append(average_precision)
        if total_relevant:
            eligible_average_precision_values.append(average_precision)
    return {
        "precision_at_5": round(float(np.mean(precision_values)), 6),
        "recall_at_10": round(float(np.mean(recall_values)), 6),
        "ndcg_at_10": round(float(np.mean(ndcg_values)) if ndcg_values else 0.0, 6),
        "map_at_10": round(float(np.mean(average_precision_values)), 6),
        "map_at_10_eligible": round(
            float(np.mean(eligible_average_precision_values))
            if eligible_average_precision_values
            else 0.0,
            6,
        ),
        "query_count": float(len(grouped)),
        "eligible_query_count": float(eligible_queries),
        "relevant_query_coverage": round(eligible_queries / len(grouped), 6),
    }


def empty_metrics() -> dict[str, float]:
    return {
        "precision_at_5": 0.0,
        "recall_at_10": 0.0,
        "ndcg_at_10": 0.0,
        "map_at_10": 0.0,
        "map_at_10_eligible": 0.0,
        "query_count": 0.0,
        "eligible_query_count": 0.0,
        "relevant_query_coverage": 0.0,
    }


def average_precision_at_k(relevant: np.ndarray, k: int) -> float:
    limited = relevant[:k]
    hits = 0
    precision_sum = 0.0
    for index, is_relevant in enumerate(limited, start=1):
        if is_relevant:
            hits += 1
            precision_sum += hits / index
    denominator = min(int(relevant.sum()), k)
    return precision_sum / denominator if denominator else 0.0
