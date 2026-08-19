from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass

import numpy as np
from scipy.sparse import csr_matrix
from scipy.stats import rankdata
from sklearn.decomposition import TruncatedSVD
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize

from ad_recommender.schemas import CandidateInput, JobInput


@dataclass
class LsaEmbeddingIndex:
    """Dense text embeddings learned with TF-IDF followed by truncated SVD."""

    candidate_ids: tuple[str, ...]
    job_ids: tuple[str, ...]
    candidate_vectors: np.ndarray
    job_vectors: np.ndarray
    vectorizer: TfidfVectorizer
    reducer: TruncatedSVD

    @classmethod
    def fit(
        cls,
        candidate_documents: dict[str, str],
        job_documents: dict[str, str],
        dimensions: int = 128,
        max_features: int = 30_000,
        random_seed: int = 42,
    ) -> LsaEmbeddingIndex:
        if not candidate_documents or not job_documents:
            raise ValueError("Candidate and job documents are required")
        candidate_ids = tuple(sorted(candidate_documents))
        job_ids = tuple(sorted(job_documents))
        documents = [candidate_documents[item] for item in candidate_ids]
        documents.extend(job_documents[item] for item in job_ids)
        vectorizer = TfidfVectorizer(
            lowercase=True,
            strip_accents="unicode",
            stop_words="english",
            ngram_range=(1, 2),
            min_df=2,
            max_df=0.98,
            max_features=max_features,
            sublinear_tf=True,
            norm="l2",
        )
        sparse_vectors = vectorizer.fit_transform(documents)
        component_count = min(
            dimensions,
            sparse_vectors.shape[0] - 1,
            sparse_vectors.shape[1] - 1,
        )
        if component_count < 2:
            raise ValueError("At least two LSA components are required")
        reducer = TruncatedSVD(n_components=component_count, random_state=random_seed)
        dense_vectors = normalize(reducer.fit_transform(sparse_vectors), norm="l2")
        candidate_count = len(candidate_ids)
        return cls(
            candidate_ids=candidate_ids,
            job_ids=job_ids,
            candidate_vectors=np.asarray(dense_vectors[:candidate_count]),
            job_vectors=np.asarray(dense_vectors[candidate_count:]),
            vectorizer=vectorizer,
            reducer=reducer,
        )

    def score_pairs(
        self, candidate_ids: Sequence[str], job_ids: Sequence[str]
    ) -> np.ndarray:
        if len(candidate_ids) != len(job_ids):
            raise ValueError("Candidate and job IDs must have the same length")
        candidate_index = {item: index for index, item in enumerate(self.candidate_ids)}
        job_index = {item: index for index, item in enumerate(self.job_ids)}
        candidate_rows = np.asarray([candidate_index[str(item)] for item in candidate_ids])
        job_rows = np.asarray([job_index[str(item)] for item in job_ids])
        return np.einsum(
            "ij,ij->i",
            self.candidate_vectors[candidate_rows],
            self.job_vectors[job_rows],
        )

    def encode_documents(self, documents: Sequence[str]) -> np.ndarray:
        """Encode unseen documents with the fitted LSA space."""
        sparse_vectors = self.vectorizer.transform(list(documents))
        return np.asarray(normalize(self.reducer.transform(sparse_vectors), norm="l2"))

    def nearest_candidate_ids(self, vectors: np.ndarray) -> tuple[str, ...]:
        indexes = np.argmax(np.asarray(vectors) @ self.candidate_vectors.T, axis=1)
        return tuple(self.candidate_ids[int(index)] for index in indexes)

    def nearest_job_ids(self, vectors: np.ndarray) -> tuple[str, ...]:
        indexes = np.argmax(np.asarray(vectors) @ self.job_vectors.T, axis=1)
        return tuple(self.job_ids[int(index)] for index in indexes)


@dataclass
class CollaborativeSvd:
    """A small implicit-feedback matrix-factorization baseline using scikit-learn."""

    user_ids: tuple[str, ...]
    item_ids: tuple[str, ...]
    user_vectors: np.ndarray
    item_vectors: np.ndarray
    reducer: TruncatedSVD

    @classmethod
    def fit(
        cls,
        user_ids: Iterable[str],
        item_ids: Iterable[str],
        interactions: Iterable[tuple[str, str, float]],
        dimensions: int = 64,
        random_seed: int = 42,
    ) -> CollaborativeSvd:
        ordered_users = tuple(sorted({str(item) for item in user_ids}))
        ordered_items = tuple(sorted({str(item) for item in item_ids}))
        if not ordered_users or not ordered_items:
            raise ValueError("Users and items are required")
        user_index = {item: index for index, item in enumerate(ordered_users)}
        item_index = {item: index for index, item in enumerate(ordered_items)}
        rows: list[int] = []
        columns: list[int] = []
        values: list[float] = []
        for user_id, item_id, weight in interactions:
            if weight <= 0:
                continue
            rows.append(user_index[str(user_id)])
            columns.append(item_index[str(item_id)])
            values.append(float(weight))
        matrix = csr_matrix(
            (values, (rows, columns)),
            shape=(len(ordered_users), len(ordered_items)),
            dtype=np.float64,
        )
        component_count = min(dimensions, min(matrix.shape) - 1)
        if matrix.nnz < 2 or component_count < 2:
            raise ValueError("At least two interactions and two latent dimensions are required")
        reducer = TruncatedSVD(n_components=component_count, random_state=random_seed)
        user_vectors = reducer.fit_transform(matrix)
        item_vectors = reducer.components_.T
        return cls(
            user_ids=ordered_users,
            item_ids=ordered_items,
            user_vectors=np.asarray(user_vectors),
            item_vectors=np.asarray(item_vectors),
            reducer=reducer,
        )

    def score_pairs(self, user_ids: Sequence[str], item_ids: Sequence[str]) -> np.ndarray:
        if len(user_ids) != len(item_ids):
            raise ValueError("User and item IDs must have the same length")
        user_index = {item: index for index, item in enumerate(self.user_ids)}
        item_index = {item: index for index, item in enumerate(self.item_ids)}
        user_rows = np.asarray([user_index[str(item)] for item in user_ids])
        item_rows = np.asarray([item_index[str(item)] for item in item_ids])
        return np.einsum(
            "ij,ij->i",
            self.user_vectors[user_rows],
            self.item_vectors[item_rows],
        )


@dataclass(frozen=True)
class HybridScoreBatch:
    final_scores: np.ndarray
    ranker_scores: np.ndarray
    embedding_scores: np.ndarray
    collaborative_scores: np.ndarray
    candidate_mode: str
    job_modes: tuple[str, ...]


@dataclass(frozen=True)
class HybridCandidateScoreBatch:
    final_scores: np.ndarray
    ranker_scores: np.ndarray
    embedding_scores: np.ndarray
    collaborative_scores: np.ndarray
    candidate_modes: tuple[str, ...]
    job_mode: str


@dataclass
class HybridRuntime:
    """Online hybrid reranker using LSA embeddings and implicit-feedback SVD.

    Unknown live IDs are mapped to their nearest warm entities in the fitted LSA
    space before the collaborative score is calculated. This makes CF executable
    for cold-start demonstrations without pretending that the new user has history.
    """

    embedding: LsaEmbeddingIndex
    collaborative: CollaborativeSvd
    ranker_weight: float = 0.85
    embedding_weight: float = 0.10
    collaborative_weight: float = 0.05
    feedback_source: str = "SYNTHETIC_IMPLICIT_FEEDBACK"

    def __post_init__(self) -> None:
        weights = np.asarray(
            [self.ranker_weight, self.embedding_weight, self.collaborative_weight],
            dtype=np.float64,
        )
        if np.any(weights < 0) or not np.isclose(weights.sum(), 1.0):
            raise ValueError("Hybrid weights must be non-negative and sum to one")

    def score_jobs(
        self,
        candidate: CandidateInput,
        jobs: Sequence[JobInput],
        ranker_scores: Sequence[float],
    ) -> HybridScoreBatch:
        if not jobs or len(jobs) != len(ranker_scores):
            raise ValueError("Jobs and ranker scores must have the same non-zero length")

        candidate_known = candidate.entity_id in self.collaborative.user_ids
        known_items = set(self.collaborative.item_ids)
        candidate_vector = self.embedding.encode_documents([candidate_document(candidate)])
        job_vectors = self.embedding.encode_documents([job_document(job) for job in jobs])
        embedding_raw = (candidate_vector @ job_vectors.T).reshape(-1)

        if candidate_known:
            collaborative_candidate_id = candidate.entity_id
            candidate_mode = "DIRECT"
        else:
            collaborative_candidate_id = self.embedding.nearest_candidate_ids(candidate_vector)[0]
            candidate_mode = "EMBEDDING_BRIDGED"

        nearest_job_ids = self.embedding.nearest_job_ids(job_vectors)
        collaborative_job_ids = tuple(
            job.entity_id if job.entity_id in known_items else nearest_id
            for job, nearest_id in zip(jobs, nearest_job_ids, strict=True)
        )
        job_modes = tuple(
            "DIRECT" if job.entity_id in known_items else "EMBEDDING_BRIDGED" for job in jobs
        )
        collaborative_raw = self.collaborative.score_pairs(
            [collaborative_candidate_id] * len(jobs),
            collaborative_job_ids,
        )

        query_ids = [candidate.entity_id] * len(jobs)
        embedding_rank = rank_normalize_by_query(query_ids, embedding_raw)
        collaborative_rank = rank_normalize_by_query(query_ids, collaborative_raw)
        ranker = np.asarray(ranker_scores, dtype=np.float64)
        final_scores = np.clip(
            self.ranker_weight * ranker
            + 100.0 * self.embedding_weight * embedding_rank
            + 100.0 * self.collaborative_weight * collaborative_rank,
            0.0,
            100.0,
        )
        return HybridScoreBatch(
            final_scores=np.rint(final_scores).astype(int),
            ranker_scores=ranker,
            embedding_scores=embedding_raw,
            collaborative_scores=collaborative_raw,
            candidate_mode=candidate_mode,
            job_modes=job_modes,
        )

    def score_candidates(
        self,
        job: JobInput,
        candidates: Sequence[CandidateInput],
        ranker_scores: Sequence[float],
    ) -> HybridCandidateScoreBatch:
        if not candidates or len(candidates) != len(ranker_scores):
            raise ValueError("Candidates and ranker scores must have the same non-zero length")

        known_users = set(self.collaborative.user_ids)
        job_known = job.entity_id in self.collaborative.item_ids
        candidate_vectors = self.embedding.encode_documents(
            [candidate_document(candidate) for candidate in candidates]
        )
        job_vector = self.embedding.encode_documents([job_document(job)])
        embedding_raw = (candidate_vectors @ job_vector.T).reshape(-1)

        nearest_candidate_ids = self.embedding.nearest_candidate_ids(candidate_vectors)
        collaborative_candidate_ids = tuple(
            candidate.entity_id if candidate.entity_id in known_users else nearest_id
            for candidate, nearest_id in zip(
                candidates, nearest_candidate_ids, strict=True
            )
        )
        candidate_modes = tuple(
            "DIRECT" if candidate.entity_id in known_users else "EMBEDDING_BRIDGED"
            for candidate in candidates
        )
        if job_known:
            collaborative_job_id = job.entity_id
            job_mode = "DIRECT"
        else:
            collaborative_job_id = self.embedding.nearest_job_ids(job_vector)[0]
            job_mode = "EMBEDDING_BRIDGED"
        collaborative_raw = self.collaborative.score_pairs(
            collaborative_candidate_ids,
            [collaborative_job_id] * len(candidates),
        )
        query_ids = [job.entity_id] * len(candidates)
        embedding_rank = rank_normalize_by_query(query_ids, embedding_raw)
        collaborative_rank = rank_normalize_by_query(query_ids, collaborative_raw)
        ranker = np.asarray(ranker_scores, dtype=np.float64)
        final_scores = np.clip(
            self.ranker_weight * ranker
            + 100.0 * self.embedding_weight * embedding_rank
            + 100.0 * self.collaborative_weight * collaborative_rank,
            0.0,
            100.0,
        )
        return HybridCandidateScoreBatch(
            final_scores=np.rint(final_scores).astype(int),
            ranker_scores=ranker,
            embedding_scores=embedding_raw,
            collaborative_scores=collaborative_raw,
            candidate_modes=candidate_modes,
            job_mode=job_mode,
        )


def candidate_document(candidate: CandidateInput) -> str:
    preferences = candidate.preferences
    return " ".join(
        [
            candidate.headline,
            " ".join(candidate.skills),
            " ".join(preferences.desired_titles),
            candidate.resume_text[:4_000],
        ]
    )


def job_document(job: JobInput) -> str:
    return " ".join(
        [
            job.title,
            " ".join(job.skills),
            " ".join(job.requirements),
            job.description[:4_000],
        ]
    )


def rank_normalize_by_query(query_ids: Sequence[str], scores: Sequence[float]) -> np.ndarray:
    """Convert arbitrary model scales to stable per-query percentiles in [0, 1]."""
    query_array = np.asarray([str(item) for item in query_ids])
    score_array = np.asarray(scores, dtype=np.float64)
    if len(query_array) != len(score_array):
        raise ValueError("Query IDs and scores must have the same length")
    normalized = np.zeros(len(scores), dtype=np.float64)
    for query_id in np.unique(query_array):
        indexes = np.flatnonzero(query_array == query_id)
        if len(indexes) == 1:
            normalized[indexes] = 1.0
            continue
        local_scores = score_array[indexes]
        ranks = rankdata(local_scores, method="average") - 1.0
        normalized[indexes] = ranks / (len(indexes) - 1)
    return normalized


def blended_scores(
    query_ids: Sequence[str], components: Sequence[Sequence[float]], weights: Sequence[float]
) -> np.ndarray:
    if len(components) != len(weights) or not components:
        raise ValueError("Every component must have one weight")
    weight_array = np.asarray(weights, dtype=np.float64)
    if np.any(weight_array < 0) or float(weight_array.sum()) <= 0:
        raise ValueError("Blend weights must be non-negative and have a positive sum")
    weight_array /= weight_array.sum()
    normalized_components = [rank_normalize_by_query(query_ids, item) for item in components]
    return sum(
        weight * component
        for weight, component in zip(weight_array, normalized_components, strict=True)
    )
