from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy import sparse
from sklearn.feature_extraction.text import TfidfVectorizer

from ad_recommender.schemas import CandidateInput, JobInput


def retrieve_top_jobs(
    candidates: list[CandidateInput],
    jobs: list[JobInput],
    output: Path,
    report: Path | None = None,
    top_k: int = 300,
    batch_size: int = 64,
    max_features: int = 60_000,
) -> dict[str, object]:
    if not candidates or not jobs:
        raise ValueError("Retrieval requires at least one candidate and one job")
    if top_k < 1 or top_k > len(jobs):
        raise ValueError("top_k must be between 1 and the number of jobs")

    vectorizer = TfidfVectorizer(
        lowercase=True,
        strip_accents="unicode",
        stop_words="english",
        ngram_range=(1, 2),
        min_df=2 if len(jobs) >= 100 else 1,
        max_df=0.98 if len(jobs) >= 100 else 1.0,
        max_features=max_features,
        sublinear_tf=True,
        norm="l2",
        dtype=np.float32,
    )
    job_matrix = vectorizer.fit_transform([job_document(job) for job in jobs])
    job_transpose = sparse.csc_matrix(job_matrix.T)

    output.parent.mkdir(parents=True, exist_ok=True)
    candidate_path = output.with_name(f"{output.stem}_candidates.jsonl")
    job_path = output.with_name(f"{output.stem}_jobs.jsonl")
    with candidate_path.open("w", encoding="utf-8") as handle:
        for candidate in candidates:
            handle.write(candidate.model_dump_json() + "\n")
    with job_path.open("w", encoding="utf-8") as handle:
        for job in jobs:
            handle.write(job.model_dump_json() + "\n")

    retrieved_pairs = 0
    zero_similarity_pairs = 0
    minimum_top_score = 1.0
    maximum_top_score = 0.0
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "query_id",
                "candidate_id",
                "job_id",
                "relevance",
                "retrieval_score",
                "retrieval_rank",
                "label_source",
            ],
        )
        writer.writeheader()
        for start in range(0, len(candidates), batch_size):
            batch = candidates[start : start + batch_size]
            candidate_matrix = vectorizer.transform(
                [candidate_document(candidate) for candidate in batch]
            )
            similarities = sparse.csr_matrix(candidate_matrix @ job_transpose)
            for row_index, candidate in enumerate(batch):
                row = similarities.getrow(row_index)
                ranked = top_indexes(row.indices, row.data, len(jobs), top_k)
                for rank, (job_index, similarity) in enumerate(ranked, start=1):
                    writer.writerow(
                        {
                            "query_id": candidate.entity_id,
                            "candidate_id": candidate.entity_id,
                            "job_id": jobs[job_index].entity_id,
                            "relevance": 0,
                            "retrieval_score": round(similarity, 8),
                            "retrieval_rank": rank,
                            "label_source": "TFIDF_TOP_K_V1",
                        }
                    )
                    retrieved_pairs += 1
                    zero_similarity_pairs += int(similarity == 0.0)
                    minimum_top_score = min(minimum_top_score, similarity)
                    maximum_top_score = max(maximum_top_score, similarity)

    result: dict[str, object] = {
        "retrieval_version": "TFIDF_TOP_K_V1",
        "candidate_count": len(candidates),
        "job_count": len(jobs),
        "top_k": top_k,
        "retrieved_pairs": retrieved_pairs,
        "zero_similarity_pairs": zero_similarity_pairs,
        "minimum_retrieval_score": round(minimum_top_score, 8),
        "maximum_retrieval_score": round(maximum_top_score, 8),
        "vocabulary_size": len(vectorizer.vocabulary_),
        "output_sha256": sha256_path(output),
    }
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(result, indent=2, sort_keys=True), encoding="utf-8")
    return result


def candidate_document(candidate: CandidateInput) -> str:
    desired_titles = " ".join(candidate.preferences.desired_titles)
    skills = " ".join(candidate.skills)
    return " ".join(
        [desired_titles] * 3
        + [candidate.headline] * 2
        + [skills] * 2
        + [candidate.resume_text]
    )


def job_document(job: JobInput) -> str:
    skills = " ".join(job.skills)
    requirements = " ".join(job.requirements)
    return " ".join([job.title] * 3 + [skills] * 2 + [requirements, job.description])


def top_indexes(
    nonzero_indexes: np.ndarray,
    nonzero_scores: np.ndarray,
    job_count: int,
    top_k: int,
) -> list[tuple[int, float]]:
    if len(nonzero_indexes) > top_k:
        selected = np.argpartition(nonzero_scores, -top_k)[-top_k:]
        indexes = nonzero_indexes[selected]
        scores = nonzero_scores[selected]
    else:
        indexes = nonzero_indexes
        scores = nonzero_scores
    ranked = sorted(
        ((int(index), float(score)) for index, score in zip(indexes, scores, strict=True)),
        key=lambda item: (-item[1], item[0]),
    )
    if len(ranked) < top_k:
        used = {index for index, _ in ranked}
        for index in range(job_count):
            if index not in used:
                ranked.append((index, 0.0))
                if len(ranked) == top_k:
                    break
    return ranked


def sha256_path(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
