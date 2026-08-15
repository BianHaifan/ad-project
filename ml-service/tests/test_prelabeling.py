from __future__ import annotations

import csv

import numpy as np
import pytest

from ad_recommender.model import TrainingPair
from ad_recommender.prelabeling import (
    PRELABEL_FEATURE_NAMES,
    PRELABEL_MODEL_VERSION,
    PrelabelerV2,
    PrelabelFeatureExtractorV2,
    write_pseudo_labeled_pairs,
)
from ad_recommender.schemas import CandidatePreferences


class DomainAwareFakeClassifier:
    classes_ = np.asarray([0, 1, 2, 3])

    def predict_proba(self, matrix):
        probabilities = []
        for row in matrix:
            if row[2] == 1.0:
                probabilities.append([0.01, 0.04, 0.15, 0.80])
            else:
                probabilities.append([0.80, 0.15, 0.04, 0.01])
        return np.asarray(probabilities)


def test_v2_prelabel_features_have_expected_shape(candidate, matching_job):
    features = PrelabelFeatureExtractorV2().transform(candidate, matching_job)

    assert features.values.shape == (29,)
    assert len(PRELABEL_FEATURE_NAMES) == 29
    assert features.values[2] == 1.0


def test_v2_batch_features_equal_individual_features(
    candidate, matching_job, unrelated_job
):
    extractor = PrelabelFeatureExtractorV2()
    individual = [
        extractor.transform(candidate, matching_job).values,
        extractor.transform(candidate, unrelated_job).values,
    ]
    batch = extractor.transform_many(
        [candidate, candidate], [matching_job, unrelated_job]
    )

    np.testing.assert_array_equal(
        np.vstack(individual), np.vstack([features.values for features in batch])
    )


def test_desired_title_distinguishes_backend_from_frontend(
    candidate, matching_job
):
    candidate = candidate.model_copy(
        update={
            "headline": "Software Engineer",
            "preferences": CandidatePreferences(
                desired_titles=["Backend Engineer"],
                preferred_locations=["Singapore"],
            ),
        }
    )
    backend = matching_job.model_copy(
        update={"title": "Backend Engineer", "skills": ["Java", "Spring Boot"]}
    )
    frontend = matching_job.model_copy(
        update={"entity_id": "frontend", "title": "Frontend React Engineer", "skills": ["React"]}
    )
    extractor = PrelabelFeatureExtractorV2()
    backend_values = dict(
        zip(PRELABEL_FEATURE_NAMES, extractor.transform(candidate, backend).values, strict=True)
    )
    frontend_values = dict(
        zip(PRELABEL_FEATURE_NAMES, extractor.transform(candidate, frontend).values, strict=True)
    )

    assert backend_values["title_overlap"] > frontend_values["title_overlap"]
    assert backend_values["domain_overlap"] == 1.0
    assert frontend_values["domain_overlap"] == 0.0


def test_pseudo_label_writer_excludes_frozen_query(
    tmp_path, candidate, matching_job, unrelated_job
):
    teacher = PrelabelerV2(
        DomainAwareFakeClassifier(), PRELABEL_MODEL_VERSION, PRELABEL_FEATURE_NAMES
    )
    pairs = [
        TrainingPair(candidate, matching_job, 1, "train-query"),
        TrainingPair(candidate, unrelated_job, 1, "frozen-query"),
    ]
    output = tmp_path / "pseudo_pairs.csv"
    report = tmp_path / "report.json"

    result = write_pseudo_labeled_pairs(
        pairs,
        output,
        teacher,
        excluded_query_ids={"frozen-query"},
        report=report,
    )

    with output.open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    assert len(rows) == 1
    assert rows[0]["query_id"] == "train-query"
    assert rows[0]["teacher_label"] == "3"
    assert rows[0]["label_source"] == "AI_TEACHER_PRELABELER_DISTILLED_V2"
    assert result["excluded_pairs"] == 1
    assert report.exists()
    assert output.with_name("pseudo_pairs_candidates.jsonl").exists()
    assert output.with_name("pseudo_pairs_jobs.jsonl").exists()


def test_pseudo_label_writer_rejects_missing_frozen_query(
    tmp_path, candidate, matching_job
):
    teacher = PrelabelerV2(
        DomainAwareFakeClassifier(), PRELABEL_MODEL_VERSION, PRELABEL_FEATURE_NAMES
    )
    pairs = [TrainingPair(candidate, matching_job, 1, "train-query")]

    with pytest.raises(ValueError, match="frozen holdout"):
        write_pseudo_labeled_pairs(
            pairs,
            tmp_path / "pseudo_pairs.csv",
            teacher,
            excluded_query_ids={"missing-frozen-query"},
        )


def test_pseudo_label_writer_can_train_on_expected_relevance(
    tmp_path, candidate, matching_job
):
    teacher = PrelabelerV2(
        DomainAwareFakeClassifier(), PRELABEL_MODEL_VERSION, PRELABEL_FEATURE_NAMES
    )
    output = tmp_path / "continuous_pairs.csv"

    result = write_pseudo_labeled_pairs(
        [TrainingPair(candidate, matching_job, 0, "query")],
        output,
        teacher,
        target="expected",
    )

    with output.open("r", encoding="utf-8", newline="") as handle:
        row = next(csv.DictReader(handle))
    assert float(row["relevance"]) == pytest.approx(2.74)
    assert row["teacher_label"] == "3"
    assert result["training_target"] == "expected"
    assert result["relevant_query_coverage"] == 1.0
