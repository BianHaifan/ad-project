from __future__ import annotations

import csv
import hashlib
import json
import math
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

import joblib
import numpy as np

from ad_recommender.schemas import CandidateInput, JobInput

if TYPE_CHECKING:
    from ad_recommender.model import TrainingPair

PRELABEL_FEATURE_VERSION = "prelabel-features-v3"
PRELABEL_MODEL_VERSION = "prelabeler-distilled-v2"

PRELABEL_FEATURE_NAMES = (
    "text_similarity",
    "title_overlap",
    "domain_overlap",
    "domain_related",
    "domain_mismatch",
    "candidate_domain_count",
    "job_domain_count",
    "structured_skill_coverage",
    "structured_skill_jaccard",
    "shared_structured_skill_count",
    "seniority_gap",
    "location_known",
    "location_match",
    "workplace_known",
    "workplace_match",
    "employment_known",
    "employment_match",
    "experience_known",
    "experience_ratio",
    "domain_overlap_count",
    "extracted_skill_coverage",
    "extracted_skill_jaccard",
    "shared_extracted_skill_count",
    "missing_extracted_skill_count",
    "title_shared_token_count",
    "candidate_seniority",
    "job_seniority",
    "known_constraint_count",
    "constraint_mismatch_count",
)

DOMAIN_TERMS: dict[str, tuple[str, ...]] = {
    "software_backend": (
        "backend", "back end", "server-side", "server side", "spring boot",
        "java developer", "api developer",
    ),
    "software_frontend": (
        "frontend", "front end", "react developer", "react engineer",
        "angular developer", "ui engineer",
    ),
    "software_mobile": (
        "mobile developer", "android developer", "ios developer", "flutter",
        "react native", "swift developer", "kotlin developer",
    ),
    "software_devops": (
        "devops", "site reliability", "sre", "platform engineer",
        "cloud engineer", "infrastructure engineer",
    ),
    "software_development": (
        "software",
        "developer",
        "programmer",
        "frontend",
        "front end",
        "backend",
        "back end",
        "full stack",
        "java",
        "python",
        "javascript",
        "react",
        "web developer",
        "application engineer",
        "devops",
        "cloud engineer",
        "systems engineer",
    ),
    "data_ai": (
        "data analyst",
        "data scientist",
        "data engineer",
        "database",
        "big data",
        "hadoop",
        "analytics",
        "business intelligence",
        "machine learning",
        "artificial intelligence",
        "statistician",
        "sql developer",
    ),
    "software_testing": (
        "quality assurance",
        " qa ",
        "qa engineer",
        "sdet",
        "test engineer",
        "automation tester",
        "software tester",
        "quality engineer",
    ),
    "human_resources": (
        "human resources",
        " hr ",
        "recruiter",
        "recruiting",
        "talent acquisition",
        "benefits",
        "employee relations",
        "personnel",
        "human resource",
    ),
    "education": (
        "teacher",
        "education",
        "educator",
        "school",
        "curriculum",
        "instructor",
        "professor",
        "classroom",
        "teaching",
        "tutor",
    ),
    "healthcare": (
        "nurse",
        "nursing",
        "medical",
        "clinical",
        "patient",
        "healthcare",
        "health care",
        "physician",
        "pharmacy",
        "therapist",
        "dental",
        "hospital",
        "caregiver",
    ),
    "finance_accounting": (
        "accountant",
        "accounting",
        "financial",
        "finance",
        "banking",
        "auditor",
        " audit ",
        "bookkeeper",
        "tax specialist",
        "accounts payable",
        "accounts receivable",
    ),
    "sales": (
        "sales",
        "business development",
        "account executive",
        "sales representative",
        "sales manager",
        "retail associate",
    ),
    "customer_service": (
        "customer service",
        "call center",
        "client service",
        "customer support",
        "support specialist",
        "customer care",
        "guest service",
    ),
    "administration": (
        "administrative",
        "executive assistant",
        "receptionist",
        "office manager",
        "secretary",
        "office assistant",
        "administration assistant",
        "administrative assistant",
    ),
    "mechanical_manufacturing": (
        "mechanical",
        "manufacturing engineer",
        "manufacturing",
        "solidworks",
        "aerospace",
        "automotive",
        "machinist",
        "mechanic",
        "industrial engineer",
        "cnc",
    ),
    "electrical_hardware": (
        "electrical",
        "electronics",
        "hardware engineer",
        "circuit",
        "semiconductor",
        "firmware",
        "embedded engineer",
        "rf engineer",
    ),
    "civil_construction": (
        "civil engineer",
        "construction",
        "architect",
        "structural",
        "autocad",
        "surveyor",
        "building engineer",
        "project architect",
    ),
    "design": (
        "graphic designer",
        "visual design",
        "ux designer",
        "ui designer",
        "illustrator",
        "photoshop",
        "creative designer",
        "art director",
        "interior designer",
    ),
    "marketing_communications": (
        "marketing",
        "seo",
        "social media",
        "communications",
        "public relations",
        "content writer",
        "copywriter",
        "brand manager",
        "digital marketing",
    ),
    "legal": ("attorney", "legal", "paralegal", "lawyer", "counsel", "litigation", "law clerk"),
    "security_law_enforcement": (
        "police",
        "detective",
        "security officer",
        "law enforcement",
        "military",
        "criminal investigator",
        "correctional",
        "firefighter",
    ),
    "hospitality": (
        "hotel",
        "restaurant",
        "food service",
        "chef",
        "cook",
        "bartender",
        "flight attendant",
        "server",
        "hospitality",
        "housekeeper",
    ),
    "logistics_supply_chain": (
        "warehouse",
        "logistics",
        "supply chain",
        "truck driver",
        "transportation",
        "shipping",
        "inventory",
        "forklift",
        "procurement",
        "dispatcher",
    ),
    "project_operations": (
        "operations manager",
        "project manager",
        "program manager",
        "business operations",
        "operations specialist",
        "product manager",
        "scrum master",
    ),
    "fitness_sports": (
        "fitness",
        "personal trainer",
        "athletic",
        "sports coach",
        "wellness",
        "group instructor",
    ),
    "research_lab": (
        "scientist",
        "chemistry",
        "chemist",
        "biology",
        "biologist",
        "laboratory",
        "researcher",
        "research scientist",
        "lab technician",
    ),
    "maintenance_skilled_trades": (
        "maintenance",
        "electrician",
        "plumber",
        "welder",
        "repair technician",
        "hvac",
        "field technician",
        "service technician",
    ),
    "insurance": ("insurance", "claims", "underwriter", "underwriting", "actuary", "adjuster"),
    "real_estate": ("real estate", "property manager", "realtor", "leasing", "mortgage"),
}

RELATED_DOMAINS = {
    frozenset(("software_backend", "software_frontend")),
    frozenset(("software_backend", "software_mobile")),
    frozenset(("software_backend", "software_devops")),
    frozenset(("software_frontend", "software_mobile")),
    frozenset(("software_frontend", "software_devops")),
    frozenset(("software_mobile", "software_devops")),
    frozenset(("software_backend", "data_ai")),
    frozenset(("software_devops", "data_ai")),
    frozenset(("software_development", "data_ai")),
    frozenset(("software_development", "software_testing")),
    frozenset(("data_ai", "software_testing")),
    frozenset(("human_resources", "administration")),
    frozenset(("human_resources", "customer_service")),
    frozenset(("administration", "customer_service")),
    frozenset(("sales", "marketing_communications")),
    frozenset(("sales", "customer_service")),
    frozenset(("finance_accounting", "data_ai")),
    frozenset(("mechanical_manufacturing", "maintenance_skilled_trades")),
    frozenset(("mechanical_manufacturing", "electrical_hardware")),
    frozenset(("mechanical_manufacturing", "civil_construction")),
    frozenset(("healthcare", "research_lab")),
    frozenset(("project_operations", "logistics_supply_chain")),
    frozenset(("project_operations", "software_development")),
    frozenset(("security_law_enforcement", "legal")),
    frozenset(("insurance", "finance_accounting")),
    frozenset(("real_estate", "sales")),
}

SKILL_TERMS = (
    "java",
    "python",
    "javascript",
    "typescript",
    "react",
    "angular",
    "node",
    "spring",
    "sql",
    "mysql",
    "oracle",
    "mongodb",
    "aws",
    "azure",
    "docker",
    "kubernetes",
    "linux",
    "hadoop",
    "spark",
    "tableau",
    "power bi",
    "excel",
    "salesforce",
    "autocad",
    "solidworks",
    "photoshop",
    "illustrator",
    "machine learning",
    "data analysis",
    "accounting",
    "recruiting",
    "customer service",
    "project management",
    "nursing",
    "seo",
    "c++",
    "c#",
    ".net",
    "unix",
)

STOPWORDS = {
    "summary",
    "career",
    "focus",
    "professional",
    "experienced",
    "experience",
    "years",
    "year",
    "job",
    "title",
    "role",
    "manager",
    "senior",
    "junior",
    "lead",
    "specialist",
    "associate",
    "looking",
    "work",
    "skills",
    "company",
    "team",
    "responsible",
    "description",
    "candidate",
    "developer",
    "engineer",
}


def normalized(value: str) -> str:
    return " " + re.sub(r"\s+", " ", value.lower().replace("/", " ")).strip() + " "


def detect_domains(title: str, excerpt: str) -> set[str]:
    title_text = normalized(title)
    excerpt_text = normalized(excerpt)
    result = {
        name for name, terms in DOMAIN_TERMS.items() if any(term in title_text for term in terms)
    }
    if not result:
        result = {
            name
            for name, terms in DOMAIN_TERMS.items()
            if sum(term in excerpt_text for term in terms) >= 2
        }
    software_subdomains = {
        value for value in result if value.startswith("software_")
        and value != "software_development"
    }
    if software_subdomains:
        result.discard("software_development")
    return result


def extracted_skills(value: str) -> set[str]:
    text = normalized(value)
    result = {
        skill
        for skill in SKILL_TERMS
        if re.search(rf"(?<![a-z0-9]){re.escape(skill)}(?![a-z0-9])", text)
    }
    if "react" in result and not any(
        term in text
        for term in (
            " react.js ", " reactjs ", " react developer ", " react engineer ",
            " frontend ", " front end ", " javascript ", " typescript ", " jsx ", " redux ",
        )
    ):
        result.remove("react")
    return result


def title_tokens(value: str) -> set[str]:
    return {
        token
        for token in re.findall(r"[a-z][a-z+#.\-]{2,}", value.lower())
        if token not in STOPWORDS
    }


def seniority(value: str) -> int:
    text = normalized(value)
    if any(term in text for term in (" director ", " vice president ", " vp ", " head of ")):
        return 4
    if any(
        term in text
        for term in (" senior ", " sr. ", " sr ", " lead ", " principal ", " architect ")
    ):
        return 3
    if any(term in text for term in (" manager ", " supervisor ")):
        return 2
    if any(
        term in text
        for term in (" junior ", " jr. ", " entry level ", " intern ", " trainee ")
    ):
        return 0
    return 1


def related_domains(left: set[str], right: set[str]) -> bool:
    return any(frozenset((a, b)) in RELATED_DOMAINS for a in left for b in right)


def jaccard(left: set[str], right: set[str]) -> float:
    return len(left & right) / len(left | right) if left or right else 0.0


def word_set_similarity(left: str, right: str) -> float:
    left_tokens = set(left.lower().split())
    right_tokens = set(right.lower().split())
    if not left_tokens or not right_tokens:
        return 0.0
    return len(left_tokens & right_tokens) / math.sqrt(len(left_tokens) * len(right_tokens))


def bool_feature(value: bool, known: bool) -> float:
    return float(value) if known else 0.5


@dataclass(frozen=True)
class PrelabelFeaturesV2:
    values: np.ndarray


@dataclass(frozen=True)
class CandidatePrelabelProfile:
    domains: set[str]
    structured_skills: set[str]
    preferred_locations: tuple[str, ...]
    workplace_types: frozenset[str]
    employment_types: frozenset[str]
    years_experience: float | None
    text_tokens: set[str]
    title_tokens: set[str]
    seniority: int
    extracted_skills: set[str]


@dataclass(frozen=True)
class JobPrelabelProfile:
    domains: set[str]
    structured_skills: set[str]
    location: str
    workplace_type: str | None
    employment_type: str | None
    required_years_experience: float | None
    text_tokens: set[str]
    title_tokens: set[str]
    seniority: int
    extracted_skills: set[str]


class PrelabelFeatureExtractorV2:
    feature_version = PRELABEL_FEATURE_VERSION
    feature_names = PRELABEL_FEATURE_NAMES

    def transform(self, candidate: CandidateInput, job: JobInput) -> PrelabelFeaturesV2:
        return self._from_profiles(candidate_profile(candidate), job_profile(job))

    def transform_many(
        self, candidates: list[CandidateInput], jobs: list[JobInput]
    ) -> list[PrelabelFeaturesV2]:
        if len(candidates) != len(jobs):
            raise ValueError("Candidates and jobs must have the same length")
        candidate_profiles = {
            item.entity_id: candidate_profile(item)
            for item in {item.entity_id: item for item in candidates}.values()
        }
        job_profiles = {
            item.entity_id: job_profile(item)
            for item in {item.entity_id: item for item in jobs}.values()
        }
        return [
            self._from_profiles(
                candidate_profiles[candidate.entity_id], job_profiles[job.entity_id]
            )
            for candidate, job in zip(candidates, jobs, strict=True)
        ]

    def _from_profiles(
        self, candidate: CandidatePrelabelProfile, job: JobPrelabelProfile
    ) -> PrelabelFeaturesV2:
        candidate_domains = candidate.domains
        job_domains = job.domains
        overlap = candidate_domains & job_domains
        domain_related = related_domains(candidate_domains, job_domains)

        candidate_structured_skills = candidate.structured_skills
        job_structured_skills = job.structured_skills
        shared_structured = candidate_structured_skills & job_structured_skills
        structured_union = candidate_structured_skills | job_structured_skills

        preferred_locations = candidate.preferred_locations
        job_location = job.location
        location_known = bool(preferred_locations and job_location)
        location_match = bool(
            location_known
            and any(value in job_location or job_location in value for value in preferred_locations)
        )
        workplace_known = bool(candidate.workplace_types and job.workplace_type)
        workplace_match = bool(job.workplace_type in candidate.workplace_types)
        employment_known = bool(candidate.employment_types and job.employment_type)
        employment_match = bool(job.employment_type in candidate.employment_types)
        experience_known = (
            candidate.years_experience is not None and job.required_years_experience is not None
        )
        experience_match = bool(
            experience_known
            and float(candidate.years_experience) >= float(job.required_years_experience)
        )
        if experience_known and float(job.required_years_experience) > 0:
            experience_ratio = min(
                float(candidate.years_experience) / float(job.required_years_experience), 1.0
            )
        elif experience_known:
            experience_ratio = 1.0
        else:
            experience_ratio = 0.5

        text_similarity = round(
            token_set_similarity(candidate.text_tokens, job.text_tokens),
            4,
        )
        candidate_title_tokens = candidate.title_tokens
        job_title_tokens = job.title_tokens
        title_overlap = round(jaccard(candidate_title_tokens, job_title_tokens), 4)
        candidate_level = candidate.seniority
        job_level = job.seniority

        candidate_text_skills = candidate.extracted_skills
        job_text_skills = job.extracted_skills
        shared_text_skills = candidate_text_skills & job_text_skills
        text_skill_union = candidate_text_skills | job_text_skills
        missing_text_skills = job_text_skills - candidate_text_skills

        constraints = (
            (location_known, location_match),
            (workplace_known, workplace_match),
            (employment_known, employment_match),
            (experience_known, experience_match),
        )
        known_constraint_count = sum(known for known, _ in constraints)
        mismatch_count = sum(known and not value for known, value in constraints)

        values = np.asarray(
            [
                text_similarity,
                title_overlap,
                float(bool(overlap)),
                float(domain_related),
                float(
                    bool(candidate_domains and job_domains and not overlap and not domain_related)
                ),
                min(len(candidate_domains), 3) / 3.0,
                min(len(job_domains), 3) / 3.0,
                len(shared_structured) / len(job_structured_skills)
                if job_structured_skills
                else 0.0,
                len(shared_structured) / len(structured_union) if structured_union else 0.0,
                min(len(shared_structured), 5) / 5.0,
                min(max(0, job_level - candidate_level), 4) / 4.0,
                float(location_known),
                bool_feature(location_match, location_known),
                float(workplace_known),
                bool_feature(workplace_match, workplace_known),
                float(employment_known),
                bool_feature(employment_match, employment_known),
                float(experience_known),
                experience_ratio,
                min(len(overlap), 3) / 3.0,
                len(shared_text_skills) / len(job_text_skills) if job_text_skills else 0.0,
                len(shared_text_skills) / len(text_skill_union) if text_skill_union else 0.0,
                min(len(shared_text_skills), 5) / 5.0,
                min(len(missing_text_skills), 10) / 10.0,
                min(len(candidate_title_tokens & job_title_tokens), 5) / 5.0,
                candidate_level / 4.0,
                job_level / 4.0,
                known_constraint_count / 4.0,
                mismatch_count / 4.0,
            ],
            dtype=np.float64,
        )
        return PrelabelFeaturesV2(values)


def candidate_profile(candidate: CandidateInput) -> CandidatePrelabelProfile:
    resume_excerpt = candidate.resume_text[:1500]
    preferences = candidate.preferences
    title_source = " ".join([candidate.headline, *preferences.desired_titles])
    return CandidatePrelabelProfile(
        domains=detect_domains(title_source, resume_excerpt),
        structured_skills={value.strip().lower() for value in candidate.skills if value.strip()},
        preferred_locations=tuple(
            value.lower() for value in preferences.preferred_locations
        ),
        workplace_types=frozenset(preferences.workplace_types),
        employment_types=frozenset(preferences.employment_types),
        years_experience=candidate.years_experience,
        text_tokens=set(f"{candidate.headline} {candidate.resume_text}".lower().split()),
        title_tokens=title_tokens(title_source),
        seniority=seniority(candidate.headline),
        extracted_skills=extracted_skills(f"{candidate.headline} {resume_excerpt}"),
    )


def job_profile(job: JobInput) -> JobPrelabelProfile:
    job_excerpt = job.description[:1500]
    return JobPrelabelProfile(
        domains=detect_domains(job.title, job_excerpt),
        structured_skills={value.strip().lower() for value in job.skills if value.strip()},
        location=job.location.lower(),
        workplace_type=job.workplace_type,
        employment_type=job.employment_type,
        required_years_experience=job.required_years_experience,
        text_tokens=set(f"{job.title} {job.description}".lower().split()),
        title_tokens=title_tokens(job.title),
        seniority=seniority(job.title),
        extracted_skills=extracted_skills(f"{job.title} {job_excerpt}"),
    )


def token_set_similarity(left_tokens: set[str], right_tokens: set[str]) -> float:
    if not left_tokens or not right_tokens:
        return 0.0
    return len(left_tokens & right_tokens) / math.sqrt(
        len(left_tokens) * len(right_tokens)
    )


@dataclass(frozen=True)
class PseudoLabelPrediction:
    label: int
    expected_relevance: float
    max_probability: float
    probability_margin: float


class PrelabelerV2:
    def __init__(self, model: object, model_version: str, feature_names: tuple[str, ...]) -> None:
        if feature_names != PRELABEL_FEATURE_NAMES:
            raise ValueError("The prelabel model feature list is incompatible with this service")
        self.model = model
        self.model_version = model_version
        self.extractor = PrelabelFeatureExtractorV2()

    @classmethod
    def load(cls, path: Path) -> PrelabelerV2:
        payload = joblib.load(path)
        if not isinstance(payload, dict) or "model" not in payload:
            raise TypeError("The prelabel artifact does not contain the expected model payload")
        model_version = str(payload.get("model_version", ""))
        if model_version != PRELABEL_MODEL_VERSION:
            raise ValueError(f"Unsupported prelabel model version: {model_version}")
        return cls(payload["model"], model_version, tuple(payload.get("feature_names", ())))

    def predict_pairs(
        self, pairs: list[TrainingPair], batch_size: int = 4096
    ) -> list[PseudoLabelPrediction]:
        results: list[PseudoLabelPrediction] = []
        for start in range(0, len(pairs), batch_size):
            batch = pairs[start : start + batch_size]
            features = self.extractor.transform_many(
                [pair.candidate for pair in batch], [pair.job for pair in batch]
            )
            matrix = np.vstack([item.values for item in features])
            raw_probabilities = self.model.predict_proba(matrix)
            probabilities = np.zeros((len(batch), 4), dtype=np.float64)
            for source_index, label in enumerate(self.model.classes_):
                probabilities[:, int(label)] = raw_probabilities[:, source_index]
            for values in probabilities:
                order = np.argsort(values)[::-1]
                results.append(
                    PseudoLabelPrediction(
                        label=int(order[0]),
                        expected_relevance=float(values @ np.arange(4, dtype=np.float64)),
                        max_probability=float(values[order[0]]),
                        probability_margin=float(values[order[0]] - values[order[1]]),
                    )
                )
        return results


def read_query_ids(path: Path | None) -> set[str]:
    if path is None:
        return set()
    return {
        value.strip()
        for value in path.read_text(encoding="utf-8").splitlines()
        if value.strip() and not value.lstrip().startswith("#")
    }


def sidecar_paths(path: Path) -> tuple[Path, Path]:
    return (
        path.with_name(f"{path.stem}_candidates.jsonl"),
        path.with_name(f"{path.stem}_jobs.jsonl"),
    )


def sha256_path(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_pseudo_labeled_pairs(
    pairs: list[TrainingPair],
    output: Path,
    teacher: PrelabelerV2,
    excluded_query_ids: set[str] | None = None,
    report: Path | None = None,
    batch_size: int = 4096,
    target: str = "label",
) -> dict[str, object]:
    if target not in {"label", "expected"}:
        raise ValueError("target must be 'label' or 'expected'")
    excluded = excluded_query_ids or set()
    source_query_ids = {pair.query_id for pair in pairs}
    missing_excluded_ids = excluded - source_query_ids
    if missing_excluded_ids:
        preview = ", ".join(sorted(missing_excluded_ids)[:5])
        raise ValueError(
            "Excluded query IDs were not found in the source pairs; "
            f"refusing to continue without the frozen holdout: {preview}"
        )
    selected = [pair for pair in pairs if pair.query_id not in excluded]
    predictions = teacher.predict_pairs(selected, batch_size=batch_size)
    output.parent.mkdir(parents=True, exist_ok=True)
    candidate_path, job_path = sidecar_paths(output)
    candidates = {pair.candidate.entity_id: pair.candidate for pair in selected}
    jobs = {pair.job.entity_id: pair.job for pair in selected}
    with candidate_path.open("w", encoding="utf-8") as handle:
        for candidate in candidates.values():
            handle.write(candidate.model_dump_json() + "\n")
    with job_path.open("w", encoding="utf-8") as handle:
        for job in jobs.values():
            handle.write(job.model_dump_json() + "\n")

    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "query_id",
                "candidate_id",
                "job_id",
                "relevance",
                "teacher_label",
                "teacher_expected_relevance",
                "teacher_max_probability",
                "teacher_probability_margin",
                "label_source",
            ],
        )
        writer.writeheader()
        for pair, prediction in zip(selected, predictions, strict=True):
            writer.writerow(
                {
                    "query_id": pair.query_id,
                    "candidate_id": pair.candidate.entity_id,
                    "job_id": pair.job.entity_id,
                    "relevance": (
                        prediction.label
                        if target == "label"
                        else round(prediction.expected_relevance, 6)
                    ),
                    "teacher_label": prediction.label,
                    "teacher_expected_relevance": round(prediction.expected_relevance, 6),
                    "teacher_max_probability": round(prediction.max_probability, 6),
                    "teacher_probability_margin": round(prediction.probability_margin, 6),
                    "label_source": "AI_TEACHER_PRELABELER_DISTILLED_V2",
                }
            )

    label_distribution = Counter(str(item.label) for item in predictions)
    confidence_distribution = Counter(
        "HIGH"
        if item.max_probability >= 0.9 and item.probability_margin >= 0.5
        else "REVIEW"
        for item in predictions
    )
    relevant_query_ids = {
        pair.query_id
        for pair, prediction in zip(selected, predictions, strict=True)
        if prediction.label >= 2
    }
    training_query_groups = len({pair.query_id for pair in selected})
    result: dict[str, object] = {
        "teacher_model_version": teacher.model_version,
        "source_pairs": len(pairs),
        "pseudo_labeled_pairs": len(selected),
        "excluded_pairs": len(pairs) - len(selected),
        "source_query_groups": len(source_query_ids),
        "training_query_groups": training_query_groups,
        "excluded_query_groups": len(excluded & {pair.query_id for pair in pairs}),
        "training_target": target,
        "relevant_query_groups": len(relevant_query_ids),
        "relevant_query_coverage": round(
            len(relevant_query_ids) / training_query_groups if training_query_groups else 0.0,
            6,
        ),
        "label_distribution": dict(label_distribution),
        "confidence_distribution": dict(confidence_distribution),
        "output_sha256": sha256_path(output),
    }
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(result, indent=2, sort_keys=True), encoding="utf-8")
    return result
