from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import HistGradientBoostingRegressor, RandomForestClassifier
from sklearn.metrics import accuracy_score, f1_score, mean_absolute_error

from ad_recommender.features import FEATURE_VERSION, PairFeatureExtractor
from ad_recommender.prelabeling import (
    PRELABEL_FEATURE_NAMES,
    PRELABEL_FEATURE_VERSION,
    PrelabelFeatureExtractorV2,
)
from ad_recommender.schemas import CandidateInput, JobInput, RecommendationItem

BASELINE_VERSION = "match-baseline-v1"
TRAINED_VERSION = "match-hgb-v1"
PSEUDO_TRAINED_VERSION = "match-rf-pseudo-v2"
PSEUDO_RANKER_VERSION = "match-hgb-retrieval-v3"
GUARDED_RANKER_VERSION = "match-hgb-retrieval-v4"
PSEUDO_FEATURE_VERSION = f"{FEATURE_VERSION}+{PRELABEL_FEATURE_VERSION}"
LEGACY_PSEUDO_FEATURE_VERSION = f"{FEATURE_VERSION}+prelabel-features-v2"


@dataclass(frozen=True)
class TrainingPair:
    candidate: CandidateInput
    job: JobInput
    relevance: float
    query_id: str
    evaluation_relevance: float | None = None


@dataclass(frozen=True)
class ModelManifest:
    model_version: str
    feature_version: str
    algorithm: str
    dataset_sha256: str
    random_seed: int
    training_pairs: int
    metrics: dict[str, float]
    created_at: str
    label_source: str = "WEAK_SUPERVISION"
    training_query_groups: int = 0


@dataclass
class ModelBundle:
    extractor: PairFeatureExtractor
    regressor: HistGradientBoostingRegressor | RandomForestClassifier | None
    manifest: ModelManifest
    scoring_extractor: PrelabelFeatureExtractorV2 | None = None
    prediction_mode: str = "regression"

    def warm_up(self) -> None:
        if self.regressor is None:
            return
        feature_count = int(self.regressor.n_features_in_)
        sample = np.zeros((1, feature_count), dtype=np.float64)
        if self.prediction_mode == "classification":
            self.regressor.predict_proba(sample)
        else:
            self.regressor.predict(sample)

    def score(self, candidate: CandidateInput, job: JobInput) -> tuple[int, object]:
        features = self.extractor.transform(candidate, job)
        if self.regressor is None:
            raw_score = baseline_score(features.as_mapping()) * 3.0
        elif getattr(self, "prediction_mode", "regression") in {
            "classification",
            "prelabel_regression",
        }:
            scoring_extractor = getattr(self, "scoring_extractor", None)
            if scoring_extractor is None:
                raise ValueError("The classification model has no scoring feature extractor")
            scoring_features = scoring_extractor.transform(candidate, job)
            if self.prediction_mode == "classification":
                probabilities = self.regressor.predict_proba(
                    scoring_features.values.reshape(1, -1)
                )[0]
                raw_score = float(
                    probabilities @ np.asarray(self.regressor.classes_, dtype=float)
                )
            else:
                raw_score = float(self.regressor.predict(scoring_features.values.reshape(1, -1))[0])
        else:
            raw_score = float(self.regressor.predict(features.values.reshape(1, -1))[0])
        score = int(round(np.clip(raw_score / 3.0 * 100.0, 0.0, 100.0)))
        if self.scoring_extractor is not None:
            score = guarded_score(score, candidate, job, scoring_features.values)
        return score, features

    def score_pairs(
        self, candidates: list[CandidateInput], jobs: list[JobInput]
    ) -> list[tuple[int, object]]:
        features = self.extractor.transform_many(candidates, jobs)
        if self.regressor is None:
            raw_scores = np.array([baseline_score(item.as_mapping()) * 3.0 for item in features])
        elif getattr(self, "prediction_mode", "regression") in {
            "classification",
            "prelabel_regression",
        }:
            scoring_extractor = getattr(self, "scoring_extractor", None)
            if scoring_extractor is None:
                raise ValueError("The classification model has no scoring feature extractor")
            scoring_features = scoring_extractor.transform_many(candidates, jobs)
            matrix = np.vstack([item.values for item in scoring_features])
            if self.prediction_mode == "classification":
                probabilities = self.regressor.predict_proba(matrix)
                raw_scores = probabilities @ np.asarray(self.regressor.classes_, dtype=float)
            else:
                raw_scores = self.regressor.predict(matrix)
        else:
            matrix = np.vstack([item.values for item in features])
            raw_scores = self.regressor.predict(matrix)
        scores = np.rint(np.clip(raw_scores / 3.0 * 100.0, 0.0, 100.0)).astype(int)
        if self.scoring_extractor is not None:
            scores = np.asarray(
                [
                    guarded_score(int(score), candidate, job, item.values)
                    for score, candidate, job, item in zip(
                        scores, candidates, jobs, scoring_features, strict=True
                    )
                ],
                dtype=int,
            )
        return list(zip(scores.tolist(), features, strict=True))

    def recommend_jobs(
        self, candidate: CandidateInput, jobs: list[JobInput], limit: int
    ) -> list[RecommendationItem]:
        if self.regressor is not None and self.scoring_extractor is not None:
            scores = self._score_for_ranking([candidate] * len(jobs), jobs)
            selected = sorted(
                zip(jobs, scores, strict=True),
                key=lambda item: (-item[1], item[0].entity_id),
            )[:limit]
            selected_jobs = [job for job, _ in selected]
            explanations = self.extractor.transform_explanations(
                [candidate] * len(selected_jobs), selected_jobs
            )
            return rank_items(
                [
                    (job.entity_id, score, features, job)
                    for (job, score), features in zip(selected, explanations, strict=True)
                ],
                limit,
            )
        scored_pairs = self.score_pairs([candidate] * len(jobs), jobs)
        scored = [
            (job.entity_id, score, features, job)
            for job, (score, features) in zip(jobs, scored_pairs, strict=True)
        ]
        return rank_items(scored, limit)

    def recommend_candidates(
        self, job: JobInput, candidates: list[CandidateInput], limit: int
    ) -> list[RecommendationItem]:
        if self.regressor is not None and self.scoring_extractor is not None:
            scores = self._score_for_ranking(candidates, [job] * len(candidates))
            selected = sorted(
                zip(candidates, scores, strict=True),
                key=lambda item: (-item[1], item[0].entity_id),
            )[:limit]
            selected_candidates = [candidate for candidate, _ in selected]
            explanations = self.extractor.transform_explanations(
                selected_candidates, [job] * len(selected_candidates)
            )
            return rank_items(
                [
                    (candidate.entity_id, score, features, job)
                    for (candidate, score), features in zip(
                        selected, explanations, strict=True
                    )
                ],
                limit,
            )
        scored_pairs = self.score_pairs(candidates, [job] * len(candidates))
        scored = [
            (candidate.entity_id, score, features, job)
            for candidate, (score, features) in zip(candidates, scored_pairs, strict=True)
        ]
        return rank_items(scored, limit)

    def _score_for_ranking(
        self, candidates: list[CandidateInput], jobs: list[JobInput]
    ) -> list[int]:
        if self.regressor is None or self.scoring_extractor is None:
            raise ValueError("Fast ranking requires a trained scoring feature extractor")
        scoring_features = self.scoring_extractor.transform_many(candidates, jobs)
        matrix = np.vstack([item.values for item in scoring_features])
        if self.prediction_mode == "classification":
            probabilities = self.regressor.predict_proba(matrix)
            raw_scores = probabilities @ np.asarray(self.regressor.classes_, dtype=float)
        else:
            raw_scores = self.regressor.predict(matrix)
        scores = np.rint(np.clip(raw_scores / 3.0 * 100.0, 0.0, 100.0)).astype(int)
        return [
            guarded_score(int(score), candidate, job, item.values)
            for score, candidate, job, item in zip(
                scores, candidates, jobs, scoring_features, strict=True
            )
        ]


def train_bundle(
    pairs: list[TrainingPair],
    dataset_sha256: str,
    random_seed: int = 42,
    model_version: str = TRAINED_VERSION,
    label_source: str = "WEAK_SUPERVISION",
) -> ModelBundle:
    if len(pairs) < 8:
        raise ValueError("At least eight training pairs are required")
    candidates = [pair.candidate for pair in pairs]
    jobs = [pair.job for pair in pairs]
    extractor = PairFeatureExtractor().fit(candidates, jobs)
    features = np.vstack([item.values for item in extractor.transform_many(candidates, jobs)])
    labels = np.array([pair.relevance for pair in pairs], dtype=np.float64)
    regressor = HistGradientBoostingRegressor(
        loss="squared_error",
        learning_rate=0.06,
        max_iter=180,
        max_leaf_nodes=15,
        min_samples_leaf=max(2, min(20, len(pairs) // 20)),
        l2_regularization=0.2,
        early_stopping=len(pairs) >= 100,
        validation_fraction=0.15,
        random_state=random_seed,
    )
    regressor.fit(features, labels)
    predictions = np.clip(regressor.predict(features), 0.0, 3.0)
    manifest = ModelManifest(
        model_version=model_version,
        feature_version=FEATURE_VERSION,
        algorithm="TfidfVectorizer+HistGradientBoostingRegressor",
        dataset_sha256=dataset_sha256,
        random_seed=random_seed,
        training_pairs=len(pairs),
        metrics={"training_mae": round(float(mean_absolute_error(labels, predictions)), 6)},
        created_at=datetime.now(UTC).isoformat(),
        label_source=label_source,
        training_query_groups=len({pair.query_id for pair in pairs}),
    )
    return ModelBundle(extractor, regressor, manifest)


def train_pseudo_classifier_bundle(
    pairs: list[TrainingPair], dataset_sha256: str, random_seed: int = 42
) -> ModelBundle:
    if len(pairs) < 8:
        raise ValueError("At least eight training pairs are required")
    candidates = [pair.candidate for pair in pairs]
    jobs = [pair.job for pair in pairs]
    explanation_extractor = fit_explanation_extractor(pairs)
    scoring_extractor = PrelabelFeatureExtractorV2()
    scoring_features = scoring_extractor.transform_many(candidates, jobs)
    matrix = np.vstack([item.values for item in scoring_features])
    labels = np.rint([pair.relevance for pair in pairs]).astype(int)
    classifier = RandomForestClassifier(
        n_estimators=350,
        min_samples_leaf=2,
        max_features=None,
        class_weight="balanced_subsample",
        n_jobs=-1,
        random_state=random_seed,
    )
    classifier.fit(matrix, labels)
    predictions = classifier.predict(matrix)
    manifest = ModelManifest(
        model_version=PSEUDO_TRAINED_VERSION,
        feature_version=PSEUDO_FEATURE_VERSION,
        algorithm="PrelabelFeaturesV2+RandomForestClassifier",
        dataset_sha256=dataset_sha256,
        random_seed=random_seed,
        training_pairs=len(pairs),
        metrics={
            "training_accuracy": round(float(accuracy_score(labels, predictions)), 6),
            "training_macro_f1": round(
                float(f1_score(labels, predictions, average="macro", zero_division=0)), 6
            ),
        },
        created_at=datetime.now(UTC).isoformat(),
        label_source="AI_TEACHER_PRELABELER_DISTILLED_V2",
        training_query_groups=len({pair.query_id for pair in pairs}),
    )
    return ModelBundle(
        explanation_extractor,
        classifier,
        manifest,
        scoring_extractor=scoring_extractor,
        prediction_mode="classification",
    )


def train_pseudo_regressor_bundle(
    pairs: list[TrainingPair], dataset_sha256: str, random_seed: int = 42
) -> ModelBundle:
    if len(pairs) < 8:
        raise ValueError("At least eight training pairs are required")
    candidates = [pair.candidate for pair in pairs]
    jobs = [pair.job for pair in pairs]
    explanation_extractor = fit_explanation_extractor(pairs)
    scoring_extractor = PrelabelFeatureExtractorV2()
    scoring_features = scoring_extractor.transform_many(candidates, jobs)
    matrix = np.vstack([item.values for item in scoring_features])
    labels = np.asarray([pair.relevance for pair in pairs], dtype=np.float64)
    regressor = HistGradientBoostingRegressor(
        loss="squared_error",
        learning_rate=0.05,
        max_iter=300,
        max_leaf_nodes=31,
        min_samples_leaf=max(2, min(100, len(pairs) // 5000)),
        l2_regularization=0.2,
        early_stopping=len(pairs) >= 100,
        validation_fraction=0.1,
        random_state=random_seed,
    )
    regressor.fit(matrix, labels)
    predictions = np.clip(regressor.predict(matrix), 0.0, 3.0)
    manifest = ModelManifest(
        model_version=PSEUDO_RANKER_VERSION,
        feature_version=PSEUDO_FEATURE_VERSION,
        algorithm="PrelabelFeaturesV2+HistGradientBoostingRegressor",
        dataset_sha256=dataset_sha256,
        random_seed=random_seed,
        training_pairs=len(pairs),
        metrics={"training_mae": round(float(mean_absolute_error(labels, predictions)), 6)},
        created_at=datetime.now(UTC).isoformat(),
        label_source="AI_TEACHER_EXPECTED_RELEVANCE_V2",
        training_query_groups=len({pair.query_id for pair in pairs}),
    )
    return ModelBundle(
        explanation_extractor,
        regressor,
        manifest,
        scoring_extractor=scoring_extractor,
        prediction_mode="prelabel_regression",
    )


def train_guarded_pseudo_regressor_bundle(
    pairs: list[TrainingPair], dataset_sha256: str, random_seed: int = 42
) -> ModelBundle:
    """Train v4 with conservative hard-negative corrections and monotonic constraints."""
    if len(pairs) < 8:
        raise ValueError("At least eight training pairs are required")
    candidates = [pair.candidate for pair in pairs]
    jobs = [pair.job for pair in pairs]
    explanation_extractor = fit_explanation_extractor(pairs)
    scoring_extractor = PrelabelFeatureExtractorV2()
    scoring_features = scoring_extractor.transform_many(candidates, jobs)
    matrix = np.vstack([item.values for item in scoring_features])
    original_labels = np.asarray([pair.relevance for pair in pairs], dtype=np.float64)
    labels = original_labels.copy()
    sample_weight = np.ones(len(pairs), dtype=np.float64)
    corrected = 0
    for index, (pair, feature) in enumerate(zip(pairs, scoring_features, strict=True)):
        sanitized = guarded_training_target(labels[index], pair.job, feature.values)
        if sanitized < labels[index]:
            labels[index] = sanitized
            sample_weight[index] = 3.0
            corrected += 1
    monotonic = monotonic_constraints()
    regressor = HistGradientBoostingRegressor(
        loss="squared_error",
        learning_rate=0.05,
        max_iter=300,
        max_leaf_nodes=31,
        min_samples_leaf=max(2, min(100, len(pairs) // 5000)),
        l2_regularization=0.4,
        monotonic_cst=monotonic,
        early_stopping=len(pairs) >= 100,
        validation_fraction=0.1,
        random_state=random_seed,
    )
    regressor.fit(matrix, labels, sample_weight=sample_weight)
    predictions = np.clip(regressor.predict(matrix), 0.0, 3.0)
    manifest = ModelManifest(
        model_version=GUARDED_RANKER_VERSION,
        feature_version=PSEUDO_FEATURE_VERSION,
        algorithm="GuardedPrelabelFeaturesV3+MonotonicHistGradientBoostingRegressor",
        dataset_sha256=dataset_sha256,
        random_seed=random_seed,
        training_pairs=len(pairs),
        metrics={
            "training_mae_guarded_targets": round(
                float(mean_absolute_error(labels, predictions)), 6
            ),
            "hard_negative_corrections": float(corrected),
            "hard_negative_correction_rate": round(corrected / len(pairs), 6),
        },
        created_at=datetime.now(UTC).isoformat(),
        label_source="AI_TEACHER_EXPECTED_RELEVANCE_V2+CONSERVATIVE_HARD_NEGATIVES_V1",
        training_query_groups=len({pair.query_id for pair in pairs}),
    )
    return ModelBundle(
        explanation_extractor,
        regressor,
        manifest,
        scoring_extractor=scoring_extractor,
        prediction_mode="prelabel_regression",
    )


def fit_explanation_extractor(pairs: list[TrainingPair]) -> PairFeatureExtractor:
    candidates = {pair.candidate.entity_id: pair.candidate for pair in pairs}
    jobs = {pair.job.entity_id: pair.job for pair in pairs}
    return PairFeatureExtractor().fit(list(candidates.values()), list(jobs.values()))


def baseline_bundle(
    pairs: list[TrainingPair],
    dataset_sha256: str,
    extractor: PairFeatureExtractor | None = None,
) -> ModelBundle:
    fitted_extractor = extractor or PairFeatureExtractor().fit(
        [pair.candidate for pair in pairs], [pair.job for pair in pairs]
    )
    manifest = ModelManifest(
        model_version=BASELINE_VERSION,
        feature_version=FEATURE_VERSION,
        algorithm="weighted-tfidf-and-structured-features",
        dataset_sha256=dataset_sha256,
        random_seed=42,
        training_pairs=len(pairs),
        metrics={},
        created_at=datetime.now(UTC).isoformat(),
        label_source="RULE_BASELINE",
        training_query_groups=len({pair.query_id for pair in pairs}),
    )
    return ModelBundle(fitted_extractor, None, manifest)


def save_bundle(bundle: ModelBundle, artifact_directory: Path) -> None:
    artifact_directory.mkdir(parents=True, exist_ok=True)
    joblib.dump(bundle, artifact_directory / "model.joblib")
    (artifact_directory / "manifest.json").write_text(
        json.dumps(asdict(bundle.manifest), indent=2, sort_keys=True), encoding="utf-8"
    )


def load_bundle(path: Path) -> ModelBundle:
    bundle = joblib.load(path)
    if not isinstance(bundle, ModelBundle):
        raise TypeError("The model artifact does not contain a ModelBundle")
    if bundle.manifest.feature_version not in {
        FEATURE_VERSION,
        LEGACY_PSEUDO_FEATURE_VERSION,
        PSEUDO_FEATURE_VERSION,
    }:
        raise ValueError("The model feature version is incompatible with this service")
    return bundle


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def baseline_score(features: dict[str, float]) -> float:
    weighted = [
        ("text_similarity", 0.30, False),
        ("skill_coverage", 0.30, False),
        ("title_similarity", 0.15, False),
        ("experience_fit", 0.10, features["experience_missing"] == 1.0),
        ("location_match", 0.04, features["location_missing"] == 1.0),
        ("workplace_match", 0.04, features["workplace_missing"] == 1.0),
        ("employment_match", 0.03, features["employment_missing"] == 1.0),
        ("salary_fit", 0.04, features["salary_missing"] == 1.0),
    ]
    available = [(name, weight) for name, weight, missing in weighted if not missing]
    total_weight = sum(weight for _, weight in available)
    if total_weight == 0:
        return 0.0
    return sum(features[name] * weight for name, weight in available) / total_weight


def prelabel_mapping(values: np.ndarray) -> dict[str, float]:
    return dict(zip(PRELABEL_FEATURE_NAMES, values, strict=True))


def guarded_training_target(raw_target: float, job: JobInput, values: np.ndarray) -> float:
    mapping = prelabel_mapping(values)
    target = float(raw_target)
    if (
        job.skills
        and mapping["structured_skill_coverage"] == 0.0
        and mapping["title_overlap"] == 0.0
    ):
        target = min(target, 1.0)
    elif (
        job.skills
        and mapping["structured_skill_coverage"] < 0.25
        and mapping["title_overlap"] < 0.25
    ):
        target = min(target, 1.8)
    if mapping["domain_mismatch"] == 1.0:
        target = min(target, 0.9)
    return target


def guarded_score(score: int, candidate: CandidateInput, job: JobInput, values: np.ndarray) -> int:
    mapping = prelabel_mapping(values)
    guarded = score
    if (
        job.skills
        and mapping["structured_skill_coverage"] == 0.0
        and mapping["title_overlap"] == 0.0
    ):
        guarded = min(guarded, 45)
    elif (
        job.skills
        and mapping["structured_skill_coverage"] < 0.25
        and mapping["title_overlap"] < 0.25
    ):
        guarded = min(guarded, 60)
    if mapping["domain_mismatch"] == 1.0:
        guarded = min(guarded, 35)
    qualifies_for_exceptional = (
        (not job.skills or mapping["structured_skill_coverage"] >= 0.75)
        and mapping["title_overlap"] >= 0.5
        and mapping["constraint_mismatch_count"] == 0.0
    )
    if guarded >= 95 and not qualifies_for_exceptional:
        guarded = 94
    return max(0, min(100, guarded))


def monotonic_constraints() -> list[int]:
    positive = {
        "text_similarity", "title_overlap", "domain_overlap", "domain_related",
        "structured_skill_coverage", "structured_skill_jaccard",
        "shared_structured_skill_count", "location_match", "workplace_match",
        "employment_match", "experience_ratio", "domain_overlap_count",
        "extracted_skill_coverage", "extracted_skill_jaccard",
        "shared_extracted_skill_count", "title_shared_token_count",
    }
    negative = {
        "domain_mismatch", "seniority_gap", "missing_extracted_skill_count",
        "constraint_mismatch_count",
    }
    return [
        1 if name in positive else -1 if name in negative else 0
        for name in PRELABEL_FEATURE_NAMES
    ]


def rank_items(scored: list[tuple], limit: int) -> list[RecommendationItem]:
    ordered = sorted(scored, key=lambda item: (-item[1], item[0]))[:limit]
    results: list[RecommendationItem] = []
    for rank, (entity_id, score, features, job) in enumerate(ordered, start=1):
        strong, gaps, evidence = explain(features, job)
        results.append(
            RecommendationItem(
                entity_id=entity_id,
                score=score,
                rank=rank,
                strong_matches=strong,
                gaps=gaps,
                evidence=evidence,
            )
        )
    return results


def explain(features: object, job: JobInput) -> tuple[list[str], list[str], list[str]]:
    mapping = features.as_mapping()
    strong: list[str] = []
    gaps: list[str] = []
    matched = list(features.matched_skills)
    missing = list(features.missing_skills)
    total_required = len(matched) + len(missing)
    if matched:
        strong.append(f"Skills matched: {len(matched)} of {total_required}")
    if mapping["title_similarity"] >= 0.35:
        strong.append("Desired role aligns with the job title")
    if mapping["location_match"] == 1.0:
        strong.append("Preferred location matched")
    if mapping["workplace_match"] == 1.0:
        strong.append("Preferred workplace type matched")
    if mapping["employment_match"] == 1.0:
        strong.append("Preferred employment type matched")
    if mapping["salary_fit"] == 1.0 and mapping["salary_missing"] == 0.0:
        strong.append("Salary expectation is within the offered range")
    if total_required and not matched:
        gaps.append("No listed required skills matched")
    if missing:
        gaps.append("Missing listed skills: " + ", ".join(missing[:3]))
    if mapping["experience_missing"] == 0.0 and mapping["experience_fit"] < 1.0:
        gaps.append("Experience is below the stated requirement")
    if mapping["location_missing"] == 0.0 and mapping["location_match"] == 0.0:
        gaps.append("Location does not match the stated preference")
    if mapping["workplace_missing"] == 0.0 and mapping["workplace_match"] == 0.0:
        gaps.append("Workplace type does not match the stated preference")
    if mapping["employment_missing"] == 0.0 and mapping["employment_match"] == 0.0:
        gaps.append("Employment type does not match the stated preference")
    evidence = matched[:5]
    return strong[:3], gaps[:3], evidence
