from __future__ import annotations

import csv
import random
import re
import urllib.request
from collections import defaultdict
from pathlib import Path

import pandas as pd

from ad_recommender.features import extract_skills, normalize_text
from ad_recommender.model import TrainingPair
from ad_recommender.schemas import (
    CandidateInput,
    CandidatePreferences,
    JobInput,
    SalaryInput,
)

RESUME_DATASET_URL = (
    "https://huggingface.co/datasets/opensporks/resumes/resolve/main/Resume/Resume.csv"
    "?download=true"
)

CATEGORY_TERMS: dict[str, tuple[str, ...]] = {
    "ACCOUNTANT": ("accountant", "accounting", "auditor", "bookkeeper"),
    "ADVOCATE": ("attorney", "lawyer", "legal", "counsel", "advocate"),
    "AGRICULTURE": ("agriculture", "agronomist", "farm", "horticulture"),
    "APPAREL": ("apparel", "fashion", "garment", "merchandiser"),
    "ARTS": ("artist", "art director", "gallery", "creative"),
    "AUTOMOBILE": ("automotive", "automobile", "mechanic", "vehicle"),
    "AVIATION": ("aviation", "aircraft", "pilot", "flight"),
    "BANKING": ("banking", "banker", "loan officer", "mortgage"),
    "BPO": ("call center", "customer service", "support representative", "bpo"),
    "BUSINESS-DEVELOPMENT": ("business development", "partnership", "account executive", "growth"),
    "CHEF": ("chef", "cook", "culinary", "kitchen"),
    "CONSTRUCTION": ("construction", "site manager", "carpenter", "estimator"),
    "CONSULTANT": ("consultant", "consulting", "advisor"),
    "DESIGNER": ("designer", "ux", "ui", "graphic design", "product design"),
    "DIGITAL-MEDIA": ("digital media", "content", "social media", "video"),
    "ENGINEERING": ("engineer", "engineering", "developer", "architect"),
    "FINANCE": ("financial", "finance", "investment", "analyst"),
    "FITNESS": ("fitness", "trainer", "coach", "wellness"),
    "HEALTHCARE": ("healthcare", "nurse", "medical", "therapist", "clinical"),
    "HR": ("human resources", "recruiter", "talent", "hr "),
    "INFORMATION-TECHNOLOGY": (
        "software",
        "information technology",
        "developer",
        "data",
        "systems",
        "it ",
    ),
    "PUBLIC-RELATIONS": ("public relations", "communications", "publicist", "pr "),
    "SALES": ("sales", "account executive", "sales representative"),
    "TEACHER": ("teacher", "educator", "instructor", "professor"),
}

RELATED_CATEGORIES: dict[str, tuple[str, ...]] = {
    "ACCOUNTANT": ("FINANCE", "BANKING"),
    "ADVOCATE": ("CONSULTANT", "PUBLIC-RELATIONS"),
    "AGRICULTURE": ("CONSTRUCTION", "ENGINEERING"),
    "APPAREL": ("DESIGNER", "SALES"),
    "ARTS": ("DESIGNER", "DIGITAL-MEDIA"),
    "AUTOMOBILE": ("ENGINEERING", "CONSTRUCTION"),
    "AVIATION": ("ENGINEERING", "AUTOMOBILE"),
    "BANKING": ("FINANCE", "ACCOUNTANT"),
    "BPO": ("SALES", "HR"),
    "BUSINESS-DEVELOPMENT": ("SALES", "CONSULTANT"),
    "CHEF": ("FITNESS", "HEALTHCARE"),
    "CONSTRUCTION": ("ENGINEERING", "AUTOMOBILE"),
    "CONSULTANT": ("BUSINESS-DEVELOPMENT", "FINANCE"),
    "DESIGNER": ("ARTS", "DIGITAL-MEDIA"),
    "DIGITAL-MEDIA": ("PUBLIC-RELATIONS", "DESIGNER"),
    "ENGINEERING": ("INFORMATION-TECHNOLOGY", "CONSTRUCTION"),
    "FINANCE": ("ACCOUNTANT", "BANKING"),
    "FITNESS": ("HEALTHCARE", "TEACHER"),
    "HEALTHCARE": ("FITNESS", "TEACHER"),
    "HR": ("BUSINESS-DEVELOPMENT", "BPO"),
    "INFORMATION-TECHNOLOGY": ("ENGINEERING", "DIGITAL-MEDIA"),
    "PUBLIC-RELATIONS": ("DIGITAL-MEDIA", "BUSINESS-DEVELOPMENT"),
    "SALES": ("BUSINESS-DEVELOPMENT", "BPO"),
    "TEACHER": ("HR", "FITNESS"),
}


def download_resume_dataset(output: Path) -> Path:
    output.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        RESUME_DATASET_URL,
        headers={"User-Agent": "ad-project-recommender/0.1"},
    )
    with urllib.request.urlopen(request, timeout=120) as response, output.open("wb") as target:
        while chunk := response.read(1024 * 1024):
            target.write(chunk)
    return output


def load_resumes(path: Path) -> list[dict[str, str]]:
    frame = read_csv_resilient(path)
    expected = {"ID", "Resume_str", "Category"}
    if not expected.issubset(frame.columns):
        raise ValueError(f"Resume CSV is missing columns: {sorted(expected - set(frame.columns))}")
    frame = frame.loc[:, ["ID", "Resume_str", "Category"]].copy()
    frame.columns = ["resume_id", "resume_text", "category"]
    for column in frame.columns:
        frame[column] = frame[column].fillna("").astype(str).map(normalize_text)
    frame["category"] = frame["category"].str.upper()
    frame = frame[
        frame["resume_id"].str.fullmatch(r"\d+")
        & (frame["resume_text"].str.len() >= 100)
        & frame["category"].isin(CATEGORY_TERMS)
    ]
    frame = frame.drop_duplicates(subset=["resume_id"], keep="first")
    return frame.to_dict("records")


def load_jobs(path: Path) -> list[JobInput]:
    frame = read_csv_resilient(path)
    required = {"job_id", "title", "description", "location", "work_type"}
    if not required.issubset(frame.columns):
        raise ValueError(f"Job CSV is missing columns: {sorted(required - set(frame.columns))}")
    jobs: list[JobInput] = []
    seen: set[str] = set()
    for row in frame.fillna("").to_dict("records"):
        entity_id = str(row["job_id"]).removesuffix(".0").strip()
        title = clean_field(row["title"])
        description = clean_field(row["description"])
        if not entity_id or entity_id in seen or not title or not description:
            continue
        seen.add(entity_id)
        requirements_text = clean_field(row.get("skills_desc", ""))
        if not requirements_are_relevant(title, description, requirements_text):
            requirements_text = ""
        combined = f"{title} {description} {requirements_text}"
        work_type = clean_field(row.get("work_type", "")).upper()
        employment_type = (
            work_type if work_type in {"FULL_TIME", "INTERNSHIP", "PART_TIME"} else None
        )
        remote = str(row.get("remote_allowed", "")).strip() in {"1", "1.0", "true", "True"}
        salary = parse_salary(row)
        jobs.append(
            JobInput(
                entity_id=entity_id,
                title=title[:200],
                description=description[:50_000],
                requirements=[requirements_text[:1_000]] if requirements_text else [],
                skills=extract_skills(combined)[:100],
                location=clean_field(row.get("location", ""))[:200],
                workplace_type="REMOTE" if remote else "ONSITE",
                employment_type=employment_type,
                salary=salary,
                required_years_experience=extract_years(description),
            )
        )
    return jobs


def prepare_weak_pairs(
    resumes: list[dict[str, str]],
    jobs: list[JobInput],
    random_seed: int = 42,
    per_label: int = 10,
) -> list[TrainingPair]:
    if not resumes or not jobs:
        raise ValueError("Both resume and job datasets must contain valid records")
    randomizer = random.Random(random_seed)
    title_text = [normalize_text(job.title) for job in jobs]
    document_text = [normalize_text(f"{job.title} {job.description}") for job in jobs]
    title_pools: dict[str, list[int]] = {}
    document_pools: dict[str, list[int]] = {}
    for category, terms in CATEGORY_TERMS.items():
        title_pools[category] = [
            index for index, text in enumerate(title_text) if contains_term(text, terms)
        ]
        document_pools[category] = [
            index for index, text in enumerate(document_text) if contains_term(text, terms)
        ]

    all_indexes = list(range(len(jobs)))
    pairs: list[TrainingPair] = []
    for resume in resumes:
        category = resume["category"]
        positive_title = title_pools.get(category, [])
        positive_document = document_pools.get(category, [])
        positive_pool = positive_title or positive_document or all_indexes
        related_pool = list(
            {
                index
                for related in RELATED_CATEGORIES.get(category, ())
                for index in (title_pools.get(related, []) or document_pools.get(related, []))
            }
        )
        positive_indexes = sample_indexes(randomizer, positive_pool, per_label)
        hard_indexes = sample_indexes(
            randomizer,
            [index for index in related_pool if index not in positive_pool] or all_indexes,
            per_label,
        )
        excluded = set(positive_indexes) | set(hard_indexes)
        random_indexes = sample_excluding(
            randomizer, len(jobs), excluded, per_label
        )
        preference_job = jobs[positive_indexes[0]]
        candidate = candidate_from_resume(resume, preference_job)
        for index in positive_indexes:
            relevance = 3.0 if index in positive_title else 2.0
            pairs.append(TrainingPair(candidate, jobs[index], relevance, candidate.entity_id))
        for index in hard_indexes:
            pairs.append(TrainingPair(candidate, jobs[index], 1.0, candidate.entity_id))
        for index in random_indexes:
            pairs.append(TrainingPair(candidate, jobs[index], 0.0, candidate.entity_id))
    return pairs


def write_pairs(pairs: list[TrainingPair], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    candidates = {pair.candidate.entity_id: pair.candidate for pair in pairs}
    jobs = {pair.job.entity_id: pair.job for pair in pairs}
    candidate_path, job_path = sidecar_paths(output)
    with candidate_path.open("w", encoding="utf-8") as handle:
        for candidate in candidates.values():
            handle.write(candidate.model_dump_json() + "\n")
    with job_path.open("w", encoding="utf-8") as handle:
        for job in jobs.values():
            handle.write(job.model_dump_json() + "\n")
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle, fieldnames=["query_id", "candidate_id", "job_id", "relevance"]
        )
        writer.writeheader()
        for pair in pairs:
            writer.writerow(
                {
                    "query_id": pair.query_id,
                    "candidate_id": pair.candidate.entity_id,
                    "job_id": pair.job.entity_id,
                    "relevance": pair.relevance,
                }
            )


def read_pairs(path: Path) -> list[TrainingPair]:
    candidate_path, job_path = sidecar_paths(path)
    candidates = {
        item.entity_id: item
        for item in read_json_lines(candidate_path, CandidateInput.model_validate_json)
    }
    jobs = {
        item.entity_id: item for item in read_json_lines(job_path, JobInput.model_validate_json)
    }
    pairs: list[TrainingPair] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            pairs.append(
                TrainingPair(
                    candidate=candidates[row["candidate_id"]],
                    job=jobs[row["job_id"]],
                    relevance=float(row["relevance"]),
                    query_id=row["query_id"],
                    evaluation_relevance=(
                        float(row["teacher_label"])
                        if row.get("teacher_label", "").strip()
                        else None
                    ),
                )
            )
    return pairs


def sidecar_paths(path: Path) -> tuple[Path, Path]:
    return (
        path.with_name(f"{path.stem}_candidates.jsonl"),
        path.with_name(f"{path.stem}_jobs.jsonl"),
    )


def read_json_lines(path: Path, validator) -> list:
    with path.open("r", encoding="utf-8") as handle:
        return [validator(line) for line in handle if line.strip()]


def write_annotation_template(
    pairs: list[TrainingPair], output: Path, sample_size: int = 300, random_seed: int = 42
) -> set[tuple[str, str]]:
    randomizer = random.Random(random_seed)
    by_label: dict[int, list[TrainingPair]] = defaultdict(list)
    for pair in pairs:
        by_label[int(pair.relevance)].append(pair)
    selected: list[TrainingPair] = []
    base = sample_size // 4
    for label in range(4):
        selected.extend(sample_pairs(randomizer, by_label[label], base))
    selected.extend(sample_pairs(randomizer, pairs, max(0, sample_size - len(selected))))
    selected = selected[:sample_size]
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8-sig", newline="") as handle:
        fieldnames = [
            "direction",
            "query_id",
            "resume_id",
            "job_id",
            "resume_headline",
            "job_title",
            "job_location",
            "resume_excerpt",
            "job_excerpt",
            "weak_label_do_not_copy",
            "human_relevance_0_to_3",
            "reviewer_note",
        ]
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for index, pair in enumerate(selected):
            writer.writerow(
                {
                    "direction": "JOBS_FOR_CANDIDATE"
                    if index < sample_size // 2
                    else "CANDIDATES_FOR_JOB",
                    "query_id": pair.query_id,
                    "resume_id": pair.candidate.entity_id,
                    "job_id": pair.job.entity_id,
                    "resume_headline": pair.candidate.headline,
                    "job_title": pair.job.title,
                    "job_location": pair.job.location,
                    "resume_excerpt": pair.candidate.resume_text[:500],
                    "job_excerpt": pair.job.description[:500],
                    "weak_label_do_not_copy": pair.relevance,
                    "human_relevance_0_to_3": "",
                    "reviewer_note": "",
                }
            )
    return {(pair.candidate.entity_id, pair.job.entity_id) for pair in selected}


def read_csv_resilient(path: Path) -> pd.DataFrame:
    last_error: Exception | None = None
    for encoding in ("utf-8-sig", "cp1252"):
        try:
            return pd.read_csv(
                path,
                encoding=encoding,
                encoding_errors="replace",
                dtype=str,
                keep_default_na=False,
                on_bad_lines="skip",
                low_memory=False,
            )
        except (UnicodeDecodeError, pd.errors.ParserError) as error:
            last_error = error
    raise ValueError(f"Unable to read CSV {path}: {last_error}")


def candidate_from_resume(resume: dict[str, str], preference_job: JobInput) -> CandidateInput:
    category = resume["category"]
    text = resume["resume_text"]
    words = text.split()
    headline = " ".join(words[:12]).title()[:200]
    return CandidateInput(
        entity_id=resume["resume_id"],
        resume_text=text[:50_000],
        headline=headline,
        skills=extract_skills(text)[:100],
        years_experience=extract_years(text),
        preferences=CandidatePreferences(
            desired_titles=[category.replace("-", " ").title()],
            preferred_locations=[preference_job.location] if preference_job.location else [],
            workplace_types=[preference_job.workplace_type]
            if preference_job.workplace_type
            else [],
            employment_types=[preference_job.employment_type]
            if preference_job.employment_type
            else [],
        ),
    )


def parse_salary(row: dict) -> SalaryInput | None:
    minimum = parse_float(row.get("min_salary"))
    maximum = parse_float(row.get("max_salary"))
    if minimum is None and maximum is None:
        median = parse_float(row.get("med_salary"))
        minimum = median
        maximum = median
    currency = clean_field(row.get("currency", "")).upper()
    period = clean_field(row.get("pay_period", "")).upper()
    if (
        (minimum is None and maximum is None)
        or len(currency) != 3
        or period
        not in {
            "HOUR",
            "MONTH",
            "YEAR",
        }
    ):
        return None
    return SalaryInput(minimum=minimum, maximum=maximum, currency=currency, period=period)


def extract_years(text: str) -> float | None:
    matches = [
        int(value)
        for value in re.findall(r"\b(\d{1,2})\+?\s+years?\b", normalize_text(text))
        if 0 <= int(value) <= 50
    ]
    return float(max(matches)) if matches else None


def clean_field(value: object) -> str:
    return re.sub(r"\s+", " ", str(value or "").replace("\x00", " ")).strip()


def parse_float(value: object) -> float | None:
    try:
        parsed = float(str(value).strip())
        return parsed if parsed >= 0 else None
    except (TypeError, ValueError):
        return None


def contains_term(text: str, terms: tuple[str, ...]) -> bool:
    return any(re.search(rf"\b{re.escape(term.strip())}\b", text) for term in terms)


REQUIREMENT_STOPWORDS = {
    "ability", "candidate", "communication", "company", "detail", "excellent",
    "experience", "ideal", "including", "job", "knowledge", "position", "required",
    "responsibilities", "responsibility", "role", "skills", "strong", "team", "work",
    "written", "years",
}


def requirements_are_relevant(title: str, description: str, requirements: str) -> bool:
    """Reject source-side requirement text that was attached to the wrong job row.

    The source CSV contains occasional cross-row `skills_desc` values. Short skill lists are
    retained; longer prose must share a concrete skill or enough informative vocabulary with
    the job itself.
    """
    if not requirements:
        return False
    base = f"{title} {description}"
    requirement_skills = set(extract_skills(requirements))
    base_skills = set(extract_skills(base))
    if requirement_skills:
        return bool(requirement_skills & base_skills)
    if len(requirements) <= 120:
        return True
    base_tokens = informative_tokens(base)
    requirement_tokens = informative_tokens(requirements)
    if not requirement_tokens:
        return False
    overlap = len(base_tokens & requirement_tokens) / len(requirement_tokens)
    return overlap >= 0.08


def informative_tokens(value: str) -> set[str]:
    return {
        token
        for token in re.findall(r"[a-z][a-z+#.\-]{2,}", normalize_text(value))
        if token not in REQUIREMENT_STOPWORDS
    }


def sample_indexes(randomizer: random.Random, values: list[int], size: int) -> list[int]:
    unique = list(dict.fromkeys(values))
    if len(unique) >= size:
        return randomizer.sample(unique, size)
    if not unique:
        return []
    return unique + randomizer.choices(unique, k=size - len(unique))


def sample_excluding(
    randomizer: random.Random, upper_bound: int, excluded: set[int], size: int
) -> list[int]:
    """Sample indexes without rebuilding a full 25k-item list for every resume."""
    available = upper_bound - len(excluded)
    if available <= 0 or size <= 0:
        return []
    selected: set[int] = set()
    target = min(size, available)
    while len(selected) < target:
        index = randomizer.randrange(upper_bound)
        if index not in excluded:
            selected.add(index)
    values = list(selected)
    if len(values) < size:
        values.extend(randomizer.choices(values, k=size - len(values)))
    return values


def sample_pairs(
    randomizer: random.Random, values: list[TrainingPair], size: int
) -> list[TrainingPair]:
    if size <= 0 or not values:
        return []
    return randomizer.sample(values, min(size, len(values)))
