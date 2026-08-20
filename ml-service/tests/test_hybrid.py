from __future__ import annotations

import numpy as np

from ad_recommender.hybrid import (
    CollaborativeSvd,
    HybridRuntime,
    LsaEmbeddingIndex,
    blended_scores,
    rank_normalize_by_query,
)
from ad_recommender.model import TrainingPair, baseline_bundle


def test_lsa_embedding_prefers_related_job_text() -> None:
    index = LsaEmbeddingIndex.fit(
        {
            "candidate": "python backend api django database",
            "other": "accounting payroll finance audit",
        },
        {
            "backend": "python backend engineer api database",
            "finance": "financial accountant payroll audit",
        },
        dimensions=2,
        max_features=100,
    )

    scores = index.score_pairs(
        ["candidate", "candidate"],
        ["backend", "finance"],
    )

    assert scores[0] > scores[1]


def test_collaborative_svd_learns_shared_item_pattern() -> None:
    model = CollaborativeSvd.fit(
        user_ids=["u1", "u2", "u3", "u4"],
        item_ids=["a", "b", "c", "d"],
        interactions=[
            ("u1", "a", 1.0),
            ("u1", "b", 1.0),
            ("u2", "a", 1.0),
            ("u2", "b", 1.0),
            ("u2", "c", 1.0),
            ("u3", "c", 1.0),
            ("u3", "d", 1.0),
            ("u4", "c", 1.0),
            ("u4", "d", 1.0),
        ],
        dimensions=2,
    )

    scores = model.score_pairs(["u1", "u1"], ["c", "d"])

    assert scores[0] > scores[1]


def test_rank_normalization_is_scoped_to_each_query() -> None:
    result = rank_normalize_by_query(["a", "a", "b", "b"], [10.0, 20.0, 100.0, 50.0])

    assert np.allclose(result, [0.0, 1.0, 1.0, 0.0])


def test_rank_normalization_preserves_ties() -> None:
    result = rank_normalize_by_query(["q", "q", "q"], [10.0, 10.0, 20.0])

    assert result[0] == result[1]
    assert result[2] > result[0]


def test_blend_combines_different_score_scales() -> None:
    result = blended_scores(
        ["q", "q", "q"],
        [[10.0, 20.0, 30.0], [0.9, 0.1, 0.2]],
        [0.5, 0.5],
    )

    assert result[2] > result[1]
    assert result[0] > result[1]


def test_runtime_hybrid_scores_unseen_entities_through_embedding_bridge(
    candidate, matching_job, unrelated_job
) -> None:
    embedding = LsaEmbeddingIndex.fit(
        {
            "warm-python": "python backend api database machine learning",
            "warm-sales": "retail sales customer store targets",
            "warm-data": "python data machine learning sql analytics",
        },
        {
            "warm-backend": "python backend api database engineer",
            "warm-retail": "retail sales customer service store",
            "warm-ml": "python machine learning recommendation sql",
        },
        dimensions=3,
        max_features=100,
    )
    collaborative = CollaborativeSvd.fit(
        user_ids=["warm-python", "warm-sales", "warm-data"],
        item_ids=["warm-backend", "warm-retail", "warm-ml"],
        interactions=[
            ("warm-python", "warm-backend", 2.0),
            ("warm-python", "warm-ml", 1.0),
            ("warm-data", "warm-ml", 2.0),
            ("warm-data", "warm-backend", 1.0),
            ("warm-sales", "warm-retail", 2.0),
        ],
        dimensions=2,
    )
    bundle = baseline_bundle(
        [
            TrainingPair(candidate, matching_job, 3, candidate.entity_id),
            TrainingPair(candidate, unrelated_job, 0, candidate.entity_id),
        ],
        "hybrid-fixture",
    )
    bundle.hybrid_runtime = HybridRuntime(embedding, collaborative)

    results = bundle.recommend_jobs(candidate, [unrelated_job, matching_job], limit=2)

    assert results[0].entity_id == matching_job.entity_id
    assert set(results[0].component_scores) == {
        "ranker",
        "embedding_cosine",
        "collaborative_latent",
        "hybrid_final",
    }
    assert results[0].component_modes == {
        "candidate_cf": "EMBEDDING_BRIDGED",
        "job_cf": "EMBEDDING_BRIDGED",
    }
