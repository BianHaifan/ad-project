from __future__ import annotations

import argparse
import json
from dataclasses import replace
from pathlib import Path

import pandas as pd

from ad_recommender.hybrid import (
    CollaborativeSvd,
    HybridRuntime,
    LsaEmbeddingIndex,
    candidate_document,
    job_document,
)
from ad_recommender.model import load_bundle, save_bundle
from ad_recommender.schemas import CandidateInput, JobInput

MODEL_VERSION = "match-hybrid-lsa-cf-v1"


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the deployable LSA + CF hybrid bundle")
    parser.add_argument("--base-model", type=Path, required=True)
    parser.add_argument("--training-pairs", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--warm-users", type=int, default=300)
    parser.add_argument("--warm-items", type=int, default=1_500)
    parser.add_argument("--positive-threshold", type=float, default=2.0)
    parser.add_argument("--embedding-dimensions", type=int, default=64)
    parser.add_argument("--cf-dimensions", type=int, default=32)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    frame = pd.read_csv(
        args.training_pairs,
        usecols=["candidate_id", "job_id", "relevance"],
        dtype={"candidate_id": str, "job_id": str},
    )
    positive = frame[frame["relevance"] >= args.positive_threshold].copy()
    selected_users = set(
        positive["candidate_id"].value_counts().head(args.warm_users).index.astype(str)
    )
    user_rows = positive[positive["candidate_id"].isin(selected_users)]
    selected_items = set(
        user_rows["job_id"].value_counts().head(args.warm_items).index.astype(str)
    )
    warm = user_rows[user_rows["job_id"].isin(selected_items)].copy()
    warm_users = set(warm["candidate_id"].astype(str))
    warm_items = set(warm["job_id"].astype(str))
    if len(warm_users) < 3 or len(warm_items) < 3 or len(warm) < 10:
        raise ValueError("The selected warm subset is too small for hybrid training")

    candidate_path = args.training_pairs.with_name(
        f"{args.training_pairs.stem}_candidates.jsonl"
    )
    job_path = args.training_pairs.with_name(f"{args.training_pairs.stem}_jobs.jsonl")
    candidates = read_entities(candidate_path, warm_users, CandidateInput)
    jobs = read_entities(job_path, warm_items, JobInput)
    embedding = LsaEmbeddingIndex.fit(
        {entity_id: candidate_document(value) for entity_id, value in candidates.items()},
        {entity_id: job_document(value) for entity_id, value in jobs.items()},
        dimensions=args.embedding_dimensions,
        random_seed=args.seed,
    )
    interactions = [
        (
            str(row.candidate_id),
            str(row.job_id),
            1.0 + max(0.0, float(row.relevance) - args.positive_threshold),
        )
        for row in warm.itertuples(index=False)
    ]
    collaborative = CollaborativeSvd.fit(
        user_ids=warm_users,
        item_ids=warm_items,
        interactions=interactions,
        dimensions=args.cf_dimensions,
        random_seed=args.seed,
    )

    bundle = load_bundle(args.base_model)
    bundle.hybrid_runtime = HybridRuntime(
        embedding=embedding,
        collaborative=collaborative,
        ranker_weight=0.85,
        embedding_weight=0.10,
        collaborative_weight=0.05,
        feedback_source="AI_TEACHER_DERIVED_SYNTHETIC_IMPLICIT_FEEDBACK",
    )
    metrics = dict(bundle.manifest.metrics)
    metrics.update(
        {
            "hybrid_warm_users": float(len(warm_users)),
            "hybrid_warm_items": float(len(warm_items)),
            "hybrid_interactions": float(len(interactions)),
            "hybrid_ranker_weight": 0.85,
            "hybrid_embedding_weight": 0.10,
            "hybrid_collaborative_weight": 0.05,
            "hybrid_embedding_explained_variance": round(
                float(embedding.reducer.explained_variance_ratio_.sum()), 6
            ),
            "hybrid_cf_explained_variance": round(
                float(collaborative.reducer.explained_variance_ratio_.sum()), 6
            ),
        }
    )
    hybrid_suffix = "+lsa64+cf32"
    feedback_suffix = "+AI_TEACHER_DERIVED_SYNTHETIC_IMPLICIT_FEEDBACK"
    base_model_version = bundle.manifest.model_version
    base_feature_version = bundle.manifest.feature_version.removesuffix(hybrid_suffix)
    base_label_source = bundle.manifest.label_source.removesuffix(feedback_suffix)
    bundle.manifest = replace(
        bundle.manifest,
        model_version=MODEL_VERSION,
        feature_version=f"{base_feature_version}{hybrid_suffix}",
        algorithm=(
            "GuardedHGBRanker+TfidfTruncatedSvdLsaEmbedding+"
            "ImplicitFeedbackTruncatedSvdCF"
        ),
        metrics=metrics,
        label_source=f"{base_label_source}{feedback_suffix}",
    )
    save_bundle(bundle, args.output)
    summary = {
        "model_version": MODEL_VERSION,
        "base_model": base_model_version,
        "warm_users": len(warm_users),
        "warm_items": len(warm_items),
        "synthetic_interactions": len(interactions),
        "embedding_dimensions": int(embedding.reducer.n_components),
        "cf_dimensions": int(collaborative.reducer.n_components),
        "weights": {"ranker": 0.85, "embedding": 0.10, "collaborative": 0.05},
        "cf_limit": "No real behavior events; feedback is derived from teacher relevance.",
    }
    (args.output / "hybrid-build-summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True), encoding="utf-8"
    )
    print(json.dumps(summary, indent=2, sort_keys=True))


def read_entities(path: Path, required_ids: set[str], schema) -> dict[str, object]:
    entities: dict[str, object] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            raw = json.loads(line)
            entity_id = str(raw.get("entity_id", ""))
            if entity_id in required_ids:
                entities[entity_id] = schema.model_validate(raw)
                if len(entities) == len(required_ids):
                    break
    missing = required_ids - entities.keys()
    if missing:
        raise ValueError(f"Missing {len(missing)} selected entities in {path}")
    return entities


if __name__ == "__main__":
    main()
