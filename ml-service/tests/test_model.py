from ad_recommender.model import (
    TrainingPair,
    guarded_score,
    load_bundle,
    save_bundle,
    train_bundle,
    train_guarded_pseudo_regressor_bundle,
    train_pseudo_classifier_bundle,
    train_pseudo_regressor_bundle,
)
from ad_recommender.prelabeling import PrelabelFeatureExtractorV2


def test_baseline_ranks_matching_job_first(baseline, candidate, matching_job, unrelated_job):
    results = baseline.recommend_jobs(candidate, [unrelated_job, matching_job], limit=2)

    assert [item.entity_id for item in results] == [matching_job.entity_id, unrelated_job.entity_id]
    assert results[0].score > results[1].score
    assert results[0].strong_matches
    assert results[1].gaps


def test_trained_bundle_can_be_saved_and_loaded(tmp_path, candidate, matching_job, unrelated_job):
    pairs = []
    for index in range(6):
        positive = matching_job.model_copy(update={"entity_id": f"positive-{index}"})
        negative = unrelated_job.model_copy(update={"entity_id": f"negative-{index}"})
        pairs.append(TrainingPair(candidate, positive, 3, f"query-{index}"))
        pairs.append(TrainingPair(candidate, negative, 0, f"query-{index}"))
    bundle = train_bundle(pairs, "dataset-hash")
    save_bundle(bundle, tmp_path)

    loaded = load_bundle(tmp_path / "model.joblib")
    score, _ = loaded.score(candidate, matching_job)

    assert loaded.manifest.model_version == "match-hgb-v1"
    assert 0 <= score <= 100
    assert (tmp_path / "manifest.json").exists()


def test_pseudo_classifier_bundle_can_be_served(
    tmp_path, candidate, matching_job, unrelated_job
):
    pairs = []
    for index in range(4):
        positive = matching_job.model_copy(update={"entity_id": f"pseudo-positive-{index}"})
        negative = unrelated_job.model_copy(update={"entity_id": f"pseudo-negative-{index}"})
        pairs.append(TrainingPair(candidate, positive, 3, f"pseudo-query-{index}"))
        pairs.append(TrainingPair(candidate, negative, 0, f"pseudo-query-{index}"))
    bundle = train_pseudo_classifier_bundle(pairs, "pseudo-dataset-hash")
    save_bundle(bundle, tmp_path)

    loaded = load_bundle(tmp_path / "model.joblib")
    matching_score, _ = loaded.score(candidate, matching_job)
    unrelated_score, _ = loaded.score(candidate, unrelated_job)

    assert loaded.manifest.model_version == "match-rf-pseudo-v2"
    assert loaded.manifest.label_source == "AI_TEACHER_PRELABELER_DISTILLED_V2"
    assert matching_score > unrelated_score


def test_pseudo_regressor_bundle_can_be_served(
    tmp_path, candidate, matching_job, unrelated_job
):
    pairs = []
    for index in range(4):
        positive = matching_job.model_copy(update={"entity_id": f"rank-positive-{index}"})
        negative = unrelated_job.model_copy(update={"entity_id": f"rank-negative-{index}"})
        pairs.append(TrainingPair(candidate, positive, 2.8, f"rank-query-{index}", 3))
        pairs.append(TrainingPair(candidate, negative, 0.2, f"rank-query-{index}", 0))
    bundle = train_pseudo_regressor_bundle(pairs, "continuous-dataset-hash")
    save_bundle(bundle, tmp_path)

    loaded = load_bundle(tmp_path / "model.joblib")
    matching_score, _ = loaded.score(candidate, matching_job)
    unrelated_score, _ = loaded.score(candidate, unrelated_job)

    assert loaded.manifest.model_version == "match-hgb-retrieval-v3"
    assert loaded.manifest.label_source == "AI_TEACHER_EXPECTED_RELEVANCE_V2"
    assert matching_score > unrelated_score


def test_trained_bundle_can_warm_up(candidate, matching_job, unrelated_job):
    pairs = []
    for index in range(4):
        pairs.append(TrainingPair(candidate, matching_job, 2.8, f"warm-query-{index}"))
        pairs.append(TrainingPair(candidate, unrelated_job, 0.2, f"warm-query-{index}"))

    bundle = train_pseudo_regressor_bundle(pairs, "warm-dataset-hash")

    bundle.warm_up()


def test_guardrail_caps_zero_skill_title_mismatch(candidate, matching_job):
    frontend = matching_job.model_copy(
        update={
            "entity_id": "frontend",
            "title": "Frontend React Engineer",
            "description": "Build React and TypeScript interfaces.",
            "skills": ["React", "TypeScript", "CSS"],
        }
    )
    values = PrelabelFeatureExtractorV2().transform(candidate, frontend).values

    assert guarded_score(100, candidate, frontend, values) <= 45


def test_guarded_v4_ranks_match_above_hard_negative(
    candidate, matching_job, unrelated_job
):
    hard_negative = matching_job.model_copy(
        update={
            "entity_id": "frontend-hard-negative",
            "title": "Frontend React Engineer",
            "description": "Build React and TypeScript interfaces.",
            "skills": ["React", "TypeScript", "CSS"],
        }
    )
    pairs = []
    for index in range(6):
        pairs.append(TrainingPair(candidate, matching_job, 3.0, f"guarded-{index}"))
        pairs.append(TrainingPair(candidate, hard_negative, 3.0, f"guarded-{index}"))
        pairs.append(TrainingPair(candidate, unrelated_job, 0.0, f"guarded-{index}"))

    bundle = train_guarded_pseudo_regressor_bundle(pairs, "guarded-hash")
    results = bundle.recommend_jobs(candidate, [hard_negative, matching_job], limit=2)

    assert bundle.manifest.model_version == "match-hgb-retrieval-v4"
    assert results[0].entity_id == matching_job.entity_id
    assert results[1].score <= 45
    assert not results[1].strong_matches[0].startswith("Skills matched: 0")
