from __future__ import annotations

import csv

from ad_recommender.retrieval import retrieve_top_jobs


def test_retrieval_writes_matching_job_first(
    tmp_path, candidate, matching_job, unrelated_job
):
    output = tmp_path / "retrieved.csv"
    result = retrieve_top_jobs(
        [candidate],
        [unrelated_job, matching_job],
        output,
        top_k=2,
        max_features=1_000,
    )

    with output.open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    assert [row["job_id"] for row in rows] == [matching_job.entity_id, unrelated_job.entity_id]
    assert float(rows[0]["retrieval_score"]) > float(rows[1]["retrieval_score"])
    assert result["retrieved_pairs"] == 2
    assert output.with_name("retrieved_candidates.jsonl").exists()
    assert output.with_name("retrieved_jobs.jsonl").exists()
