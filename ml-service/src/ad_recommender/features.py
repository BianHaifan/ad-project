from __future__ import annotations

import re
from dataclasses import dataclass

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer

from ad_recommender.schemas import CandidateInput, JobInput

FEATURE_VERSION = "pair-features-v1"

TOKEN_PATTERN = re.compile(r"[a-z0-9][a-z0-9+#.\-]{1,}")
SPACE_PATTERN = re.compile(r"\s+")

COMMON_SKILLS = (
    "python",
    "java",
    "javascript",
    "typescript",
    "react",
    "node",
    "spring",
    "sql",
    "mysql",
    "postgresql",
    "mongodb",
    "aws",
    "azure",
    "docker",
    "kubernetes",
    "git",
    "linux",
    "fastapi",
    "pytorch",
    "tensorflow",
    "scikit-learn",
    "machine learning",
    "data analysis",
    "excel",
    "power bi",
    "tableau",
    "accounting",
    "sales",
    "marketing",
    "recruiting",
    "human resources",
    "project management",
    "customer service",
    "nursing",
    "healthcare",
    "autocad",
    "solidworks",
    "photoshop",
    "illustrator",
    "seo",
    "writing",
)

REACT_TECH_CONTEXT = (
    "react.js",
    "reactjs",
    "react developer",
    "react engineer",
    "frontend",
    "front end",
    "javascript",
    "typescript",
    "jsx",
    "redux",
)


def normalize_text(value: str) -> str:
    return SPACE_PATTERN.sub(" ", value.lower().replace("\x00", " ")).strip()


def tokens(value: str) -> set[str]:
    return set(TOKEN_PATTERN.findall(normalize_text(value)))


def normalize_values(values: list[str]) -> set[str]:
    return {normalize_text(value) for value in values if normalize_text(value)}


def extract_skills(text: str) -> list[str]:
    normalized = normalize_text(text)
    extracted: list[str] = []
    for skill in COMMON_SKILLS:
        pattern = rf"(?<![a-z0-9]){re.escape(skill)}(?![a-z0-9])"
        if not re.search(pattern, normalized):
            continue
        # "react" is also a common English verb. Only treat it as the JavaScript
        # library when nearby job text provides an unambiguous technical context.
        if skill == "react" and not any(term in normalized for term in REACT_TECH_CONTEXT):
            continue
        extracted.append(skill)
    return extracted


FEATURE_NAMES = (
    "text_similarity",
    "title_similarity",
    "skill_coverage",
    "skill_jaccard",
    "experience_fit",
    "location_match",
    "workplace_match",
    "employment_match",
    "salary_fit",
    "experience_missing",
    "location_missing",
    "workplace_missing",
    "employment_missing",
    "salary_missing",
)


@dataclass(frozen=True)
class PairFeatures:
    values: np.ndarray
    matched_skills: tuple[str, ...]
    missing_skills: tuple[str, ...]

    def as_mapping(self) -> dict[str, float]:
        return dict(zip(FEATURE_NAMES, self.values, strict=True))


@dataclass(frozen=True)
class CandidateProfile:
    desired_title_tokens: set[str]
    skills: set[str]
    preferred_locations: set[str]
    workplaces: set[str]
    employments: set[str]


@dataclass(frozen=True)
class JobProfile:
    title_tokens: set[str]
    skills: set[str]
    location: str


class PairFeatureExtractor:
    def __init__(self, vectorizer: TfidfVectorizer | None = None) -> None:
        self.vectorizer = vectorizer or TfidfVectorizer(
            lowercase=True,
            strip_accents="unicode",
            stop_words="english",
            ngram_range=(1, 2),
            min_df=1,
            max_df=0.98,
            max_features=20_000,
            sublinear_tf=True,
            norm="l2",
        )

    def fit(self, candidates: list[CandidateInput], jobs: list[JobInput]) -> PairFeatureExtractor:
        documents = list(
            dict.fromkeys(
                [candidate_document(candidate) for candidate in candidates]
                + [job_document(job) for job in jobs]
            )
        )
        if not documents:
            raise ValueError("At least one candidate or job document is required")
        self.vectorizer.fit(documents)
        return self

    def transform(self, candidate: CandidateInput, job: JobInput) -> PairFeatures:
        candidate_vector, job_vector = self.vectorizer.transform(
            [candidate_document(candidate), job_document(job)]
        )
        text_similarity = float(candidate_vector.multiply(job_vector).sum())

        return self._structured(
            candidate,
            job,
            text_similarity,
            candidate_profile(candidate),
            job_profile(job),
        )

    def transform_many(
        self, candidates: list[CandidateInput], jobs: list[JobInput]
    ) -> list[PairFeatures]:
        if len(candidates) != len(jobs):
            raise ValueError("Candidates and jobs must have the same length")
        if not candidates:
            return []
        unique_candidates = list({item.entity_id: item for item in candidates}.values())
        unique_jobs = list({item.entity_id: item for item in jobs}.values())
        candidate_indexes = {item.entity_id: index for index, item in enumerate(unique_candidates)}
        job_indexes = {item.entity_id: index for index, item in enumerate(unique_jobs)}
        candidate_matrix = self.vectorizer.transform(
            [candidate_document(item) for item in unique_candidates]
        )
        job_matrix = self.vectorizer.transform([job_document(item) for item in unique_jobs])
        candidate_profiles = {
            item.entity_id: candidate_profile(item) for item in unique_candidates
        }
        job_profiles = {item.entity_id: job_profile(item) for item in unique_jobs}
        candidate_rows = candidate_matrix[
            [candidate_indexes[item.entity_id] for item in candidates]
        ]
        job_rows = job_matrix[[job_indexes[item.entity_id] for item in jobs]]
        similarities = np.asarray(candidate_rows.multiply(job_rows).sum(axis=1)).reshape(-1)
        return [
            self._structured(
                candidate,
                job,
                float(similarity),
                candidate_profiles[candidate.entity_id],
                job_profiles[job.entity_id],
            )
            for candidate, job, similarity in zip(candidates, jobs, similarities, strict=True)
        ]

    def transform_explanations(
        self, candidates: list[CandidateInput], jobs: list[JobInput]
    ) -> list[PairFeatures]:
        """Build explanation fields without re-vectorizing full resume and job text."""
        if len(candidates) != len(jobs):
            raise ValueError("Candidates and jobs must have the same length")
        candidate_profiles = {
            item.entity_id: candidate_explanation_profile(item)
            for item in {item.entity_id: item for item in candidates}.values()
        }
        job_profiles = {
            item.entity_id: job_explanation_profile(item)
            for item in {item.entity_id: item for item in jobs}.values()
        }
        return [
            self._structured(
                candidate,
                job,
                0.0,
                candidate_profiles[candidate.entity_id],
                job_profiles[job.entity_id],
            )
            for candidate, job in zip(candidates, jobs, strict=True)
        ]

    def _structured(
        self,
        candidate: CandidateInput,
        job: JobInput,
        text_similarity: float,
        candidate_data: CandidateProfile,
        job_data: JobProfile,
    ) -> PairFeatures:
        title_similarity = jaccard(candidate_data.desired_title_tokens, job_data.title_tokens)

        matched_skills = tuple(sorted(candidate_data.skills & job_data.skills))
        missing_skills = tuple(sorted(job_data.skills - candidate_data.skills))
        skill_coverage = len(matched_skills) / len(job_data.skills) if job_data.skills else 0.0
        skill_jaccard = jaccard(candidate_data.skills, job_data.skills)

        experience_missing = float(
            candidate.years_experience is None or job.required_years_experience is None
        )
        experience_fit = ratio_fit(candidate.years_experience, job.required_years_experience)

        location_missing = float(
            not candidate_data.preferred_locations or not job_data.location
        )
        location_match = contains_match(candidate_data.preferred_locations, job_data.location)

        workplace_missing = float(not candidate_data.workplaces or job.workplace_type is None)
        workplace_match = exact_match(candidate_data.workplaces, job.workplace_type)

        employment_missing = float(not candidate_data.employments or job.employment_type is None)
        employment_match = exact_match(candidate_data.employments, job.employment_type)

        salary_fit, salary_missing = salary_compatibility(candidate, job)

        values = np.array(
            [
                text_similarity,
                title_similarity,
                skill_coverage,
                skill_jaccard,
                experience_fit,
                location_match,
                workplace_match,
                employment_match,
                salary_fit,
                experience_missing,
                location_missing,
                workplace_missing,
                employment_missing,
                salary_missing,
            ],
            dtype=np.float64,
        )
        return PairFeatures(values, matched_skills, missing_skills)


def candidate_profile(candidate: CandidateInput) -> CandidateProfile:
    return CandidateProfile(
        desired_title_tokens=tokens(" ".join(candidate.preferences.desired_titles)),
        skills=normalize_values(candidate.skills)
        | set(extract_skills(candidate_document(candidate))),
        preferred_locations=normalize_values(candidate.preferences.preferred_locations),
        workplaces=set(candidate.preferences.workplace_types),
        employments=set(candidate.preferences.employment_types),
    )


def candidate_explanation_profile(candidate: CandidateInput) -> CandidateProfile:
    return CandidateProfile(
        desired_title_tokens=tokens(" ".join(candidate.preferences.desired_titles)),
        skills=normalize_values(candidate.skills),
        preferred_locations=normalize_values(candidate.preferences.preferred_locations),
        workplaces=set(candidate.preferences.workplace_types),
        employments=set(candidate.preferences.employment_types),
    )


def job_profile(job: JobInput) -> JobProfile:
    return JobProfile(
        title_tokens=tokens(job.title),
        skills=normalize_values(job.skills) | set(extract_skills(job_document(job))),
        location=normalize_text(job.location),
    )


def job_explanation_profile(job: JobInput) -> JobProfile:
    return JobProfile(
        title_tokens=tokens(job.title),
        skills=normalize_values(job.skills),
        location=normalize_text(job.location),
    )


def candidate_document(candidate: CandidateInput) -> str:
    return normalize_text(" ".join([candidate.headline, candidate.resume_text, *candidate.skills]))


def job_document(job: JobInput) -> str:
    return normalize_text(" ".join([job.title, job.description, *job.requirements, *job.skills]))


def jaccard(left: set[str], right: set[str]) -> float:
    union = left | right
    return len(left & right) / len(union) if union else 0.0


def ratio_fit(actual: float | None, required: float | None) -> float:
    if actual is None or required is None:
        return 0.0
    if required <= 0:
        return 1.0
    return min(actual / required, 1.0)


def contains_match(preferred: set[str], actual: str) -> float:
    if not preferred or not actual:
        return 0.0
    return float(any(value in actual or actual in value for value in preferred))


def exact_match(preferred: set[str], actual: str | None) -> float:
    if not preferred or actual is None:
        return 0.0
    return float(actual in preferred)


def salary_compatibility(candidate: CandidateInput, job: JobInput) -> tuple[float, float]:
    expected = candidate.preferences.minimum_salary
    offered = job.salary
    if (
        expected is None
        or expected.minimum is None
        or offered is None
        or offered.maximum is None
        or expected.currency != offered.currency
        or expected.period != offered.period
    ):
        return 0.0, 1.0
    if expected.minimum <= 0:
        return 1.0, 0.0
    return min(offered.maximum / expected.minimum, 1.0), 0.0
