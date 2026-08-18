import numpy as np

from ad_recommender.evaluation import (
    average_precision_at_k,
    empty_metrics,
    evaluate_ranking,
)
from ad_recommender.model import TrainingPair, train_pseudo_regressor_bundle

METRIC_KEYS = {
    "precision_at_5",
    "recall_at_10",
    "ndcg_at_10",
    "map_at_10",
    "map_at_10_eligible",
    "query_count",
    "eligible_query_count",
    "relevant_query_coverage",
}


def test_empty_metrics_are_all_zero():
    metrics = empty_metrics()

    assert set(metrics) == METRIC_KEYS
    assert all(value == 0.0 for value in metrics.values())


def test_evaluate_ranking_returns_empty_metrics_without_pairs(baseline):
    assert evaluate_ranking(baseline, []) == empty_metrics()


def test_evaluate_ranking_baseline_uses_pair_scoring(
    baseline, candidate, matching_job, unrelated_job
):
    pairs = [
        TrainingPair(candidate, matching_job, 3.0, "query-a"),
        TrainingPair(candidate, unrelated_job, 0.0, "query-a"),
        TrainingPair(candidate, matching_job, 3.0, "query-b", 2.5),
        TrainingPair(candidate, unrelated_job, 0.0, "query-b", 0.0),
    ]

    metrics = evaluate_ranking(baseline, pairs)

    assert metrics["query_count"] == 2.0
    assert metrics["eligible_query_count"] == 2.0
    assert metrics["relevant_query_coverage"] == 1.0
    assert metrics["recall_at_10"] == 1.0
    assert 0.0 < metrics["precision_at_5"] <= 1.0
    assert 0.0 < metrics["ndcg_at_10"] <= 1.0
    assert metrics["map_at_10"] == 1.0
    assert metrics["map_at_10_eligible"] == 1.0


def test_evaluate_ranking_uses_fast_scoring_for_trained_bundle(
    candidate, matching_job, unrelated_job
):
    pairs = []
    for index in range(4):
        pairs.append(TrainingPair(candidate, matching_job, 3.0, f"query-{index}"))
        pairs.append(TrainingPair(candidate, unrelated_job, 0.0, f"query-{index}"))
    bundle = train_pseudo_regressor_bundle(pairs, "evaluation-fixture-sha256")

    metrics = evaluate_ranking(bundle, pairs)

    assert metrics["query_count"] == 4.0
    assert metrics["eligible_query_count"] == 4.0


def test_evaluate_ranking_handles_queries_without_relevant_labels(
    baseline, candidate, matching_job
):
    pairs = [
        TrainingPair(candidate, matching_job, 1.0, "all-negative"),
        TrainingPair(candidate, matching_job, 0.5, "all-negative"),
    ]

    metrics = evaluate_ranking(baseline, pairs)

    assert metrics["query_count"] == 1.0
    assert metrics["eligible_query_count"] == 0.0
    assert metrics["relevant_query_coverage"] == 0.0
    assert metrics["map_at_10_eligible"] == 0.0


def test_evaluate_ranking_skips_ndcg_when_labels_do_not_vary(
    baseline, candidate, matching_job
):
    pairs = [
        TrainingPair(candidate, matching_job, 3.0, "flat"),
        TrainingPair(candidate, matching_job, 3.0, "flat"),
    ]

    metrics = evaluate_ranking(baseline, pairs)

    assert metrics["ndcg_at_10"] == 0.0
    assert metrics["precision_at_5"] == 1.0
    assert metrics["recall_at_10"] == 1.0


def test_average_precision_at_k():
    assert average_precision_at_k(np.array([True, True]), 10) == 1.0
    assert average_precision_at_k(np.array([True, False, False]), 3) == 1.0
    assert average_precision_at_k(np.array([False, False, True, True]), 2) == 0.0
    assert average_precision_at_k(np.array([False, False]), 2) == 0.0
