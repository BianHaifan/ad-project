import random

import pytest

from ad_recommender.data import (
    candidate_from_resume,
    clean_field,
    contains_term,
    download_resume_dataset,
    extract_years,
    informative_tokens,
    load_jobs,
    load_resumes,
    parse_float,
    parse_salary,
    prepare_weak_pairs,
    read_json_lines,
    read_pairs,
    requirements_are_relevant,
    sample_excluding,
    sample_indexes,
    sample_pairs,
    sidecar_paths,
    write_annotation_template,
    write_pairs,
)
from ad_recommender.model import TrainingPair
from ad_recommender.schemas import CandidateInput, JobInput


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ("3.5", 3.5),
        ("0", 0.0),
        ("-1", None),
        ("abc", None),
        (None, None),
        ("", None),
    ],
)
def test_parse_float(value, expected):
    assert parse_float(value) == expected


def test_parse_salary_from_min_max():
    salary = parse_salary(
        {
            "min_salary": "5000",
            "max_salary": "8000",
            "currency": "SGD",
            "pay_period": "month",
        }
    )
    assert salary is not None
    assert salary.minimum == 5000.0
    assert salary.maximum == 8000.0
    assert salary.currency == "SGD"
    assert salary.period == "MONTH"


def test_parse_salary_falls_back_to_median():
    salary = parse_salary(
        {
            "min_salary": "",
            "max_salary": "",
            "med_salary": "7500",
            "currency": "usd",
            "pay_period": "YEAR",
        }
    )
    assert salary is not None
    assert salary.minimum == 7500.0
    assert salary.maximum == 7500.0


def test_parse_salary_rejects_invalid_values():
    assert parse_salary({"currency": "SG", "pay_period": "MONTH"}) is None
    assert (
        parse_salary(
            {"min_salary": "1000", "max_salary": "2000", "currency": "SGD", "pay_period": "WEEK"}
        )
        is None
    )
    assert (
        parse_salary(
            {"min_salary": "1000", "max_salary": "2000", "currency": "INR", "pay_period": ""}
        )
        is None
    )


def test_extract_years_returns_oldest_mention():
    assert extract_years("5 years of python and 3 years of sql") == 5.0
    assert extract_years("No experience requirement here") is None


def test_clean_field_collapses_whitespace_and_drops_null_bytes():
    assert clean_field("  a\x00   b \t c  ") == "a b c"
    assert clean_field(None) == ""
    assert clean_field(42) == "42"


def test_contains_term_matches_on_word_boundaries():
    assert contains_term("senior software engineer", ("software",))
    assert not contains_term("software", ("software engineer",))


def test_informative_tokens_removes_stopwords():
    tokens = informative_tokens("Python with strong written communication")
    assert "python" in tokens
    assert "strong" not in tokens
    assert "communication" not in tokens


def test_requirements_are_relevant_rejects_empty():
    assert not requirements_are_relevant("Title", "description", "")


def test_requirements_are_relevant_keeps_short_prose_without_skills():
    assert requirements_are_relevant(
        "Cashier", "Handle checkout and greet customers", "Friendly and reliable"
    )


def test_requirements_are_relevant_matches_via_skill_overlap():
    assert requirements_are_relevant(
        "Python Backend Engineer",
        "Build APIs with Python and FastAPI",
        "Python and FastAPI experience",
    )


def test_requirements_are_relevant_rejects_unrelated_skills():
    assert not requirements_are_relevant(
        "Python Backend Engineer",
        "Build APIs with Python and FastAPI",
        "Retail sales and marketing",
    )


def test_requirements_are_relevant_uses_token_overlap_for_long_prose():
    title = "Backend Engineer"
    description = "Design and operate reliable production services every day"
    long_requirements = (
        "The ideal candidate owns robust delivery and dependable operation of production systems. "
        "They plan careful rollout and thoughtful capacity ahead of every release window. "
        "We value clear documentation and disciplined review in our engineering team. "
    )

    assert requirements_are_relevant(title, description, long_requirements)


def test_requirements_are_relevant_rejects_long_prose_without_overlap():
    title = "Data Engineer"
    description = "Build data pipelines in the cloud"
    unrelated_requirements = "banana pineapple mango strawberry " * 30

    assert not requirements_are_relevant(title, description, unrelated_requirements)


def test_sample_indexes_handles_full_partial_and_empty_pools():
    rng = random.Random(42)
    assert len(sample_indexes(rng, [1, 2, 3, 4, 5], 3)) == 3

    small = sample_indexes(rng, [7], 3)
    assert len(small) == 3
    assert all(value == 7 for value in small)

    assert sample_indexes(rng, [], 3) == []


def test_sample_excluding_respects_bound_and_exclusions():
    rng = random.Random(7)
    values = sample_excluding(rng, 100, {1, 2, 3}, 5)

    assert len(values) == 5
    assert all(0 <= value < 100 for value in values)
    assert all(value not in {1, 2, 3} for value in values)


def test_sample_excluding_edge_cases():
    rng = random.Random(3)
    assert sample_excluding(rng, 3, {0, 1, 2}, 5) == []
    assert sample_excluding(rng, 10, set(), 0) == []

    oversized = sample_excluding(rng, 10, set(), 12)
    assert len(oversized) == 12
    assert all(0 <= value < 10 for value in oversized)


def test_sample_pairs_returns_empty_for_empty_or_zero_size(candidate, matching_job):
    rng = random.Random(1)
    pair = TrainingPair(candidate, matching_job, 3.0, "q1")

    assert sample_pairs(rng, [], 3) == []
    assert sample_pairs(rng, [pair], 0) == []


def test_sample_pairs_returns_subsample(candidate, matching_job, unrelated_job):
    rng = random.Random(2)
    pairs = [
        TrainingPair(candidate, matching_job, 3.0, "q1"),
        TrainingPair(candidate, unrelated_job, 0.0, "q1"),
    ]

    selected = sample_pairs(rng, pairs, 1)

    assert len(selected) == 1
    assert selected[0] in pairs


def test_sidecar_paths_names_companion_files(tmp_path):
    pairs_path = tmp_path / "pairs.csv"

    candidate_path, job_path = sidecar_paths(pairs_path)

    assert candidate_path.name == "pairs_candidates.jsonl"
    assert job_path.name == "pairs_jobs.jsonl"


def test_write_read_pairs_round_trip(candidate, matching_job, unrelated_job, tmp_path):
    pairs = [
        TrainingPair(candidate, matching_job, 3.0, "query-1"),
        TrainingPair(candidate, unrelated_job, 0.0, "query-1"),
    ]

    output = tmp_path / "eval" / "pairs.csv"
    write_pairs(pairs, output)
    restored = read_pairs(output)

    assert len(restored) == 2
    first, second = restored
    assert first.candidate.entity_id == candidate.entity_id
    assert first.job.entity_id == matching_job.entity_id
    assert first.relevance == 3.0
    assert first.query_id == "query-1"
    assert first.evaluation_relevance is None
    assert second.job.entity_id == unrelated_job.entity_id


def test_read_pairs_parses_teacher_label(candidate, matching_job, tmp_path):
    output = tmp_path / "pairs.csv"
    output.write_text(
        f"query_id,candidate_id,job_id,relevance,teacher_label\n"
        f"query-1,{candidate.entity_id},{matching_job.entity_id},3,2.0\n",
        encoding="utf-8",
    )
    (tmp_path / "pairs_candidates.jsonl").write_text(
        candidate.model_dump_json() + "\n", encoding="utf-8"
    )
    (tmp_path / "pairs_jobs.jsonl").write_text(
        matching_job.model_dump_json() + "\n", encoding="utf-8"
    )

    restored = read_pairs(output)

    assert len(restored) == 1
    assert restored[0].evaluation_relevance == 2.0


def test_read_json_lines_skips_blank_lines(candidate, tmp_path):
    path = tmp_path / "items.jsonl"
    path.write_text(
        candidate.model_dump_json() + "\n\n" + candidate.model_dump_json() + "\n",
        encoding="utf-8",
    )

    items = read_json_lines(path, CandidateInput.model_validate_json)

    assert len(items) == 2


def test_candidate_from_resume_builds_preferences():
    resume = {
        "resume_id": "42",
        "category": "INFORMATION-TECHNOLOGY",
        "resume_text": (
            "Python data engineer with 5 years building backend services and sql pipelines. "
            "Designing data systems across multiple teams. " * 3
        ),
    }
    job = JobInput(
        entity_id="job-it",
        title="Software Developer",
        description="Build software using python and data systems",
        location="Singapore",
        workplace_type="REMOTE",
        employment_type="FULL_TIME",
    )

    built = candidate_from_resume(resume, job)

    assert built.entity_id == "42"
    assert built.years_experience == 5.0
    assert "python" in built.skills
    assert built.preferences.desired_titles == ["Information Technology"]
    assert built.preferences.preferred_locations == ["Singapore"]
    assert built.preferences.workplace_types == ["REMOTE"]
    assert built.preferences.employment_types == ["FULL_TIME"]


def test_prepare_weak_pairs_requires_non_empty_datasets():
    with pytest.raises(ValueError):
        prepare_weak_pairs([], [])


def test_prepare_weak_pairs_generates_positive_hard_and_random():
    resume = {
        "resume_id": "1",
        "category": "INFORMATION-TECHNOLOGY",
        "resume_text": "Software engineer building python and data systems. " * 5,
    }
    jobs = [
        JobInput(
            entity_id="job-it",
            title="Software Developer",
            description="Build software using python and data systems",
            location="Singapore",
            workplace_type="ONSITE",
            employment_type="FULL_TIME",
        ),
        JobInput(
            entity_id="job-sales",
            title="Retail Sales Associate",
            description="Assist customers and meet sales targets",
            location="London",
            workplace_type="ONSITE",
            employment_type="PART_TIME",
        ),
        JobInput(
            entity_id="job-dev",
            title="Account Executive",
            description="Drive business development and partnerships",
            location="New York",
            workplace_type="ONSITE",
            employment_type="FULL_TIME",
        ),
        JobInput(
            entity_id="job-csr",
            title="Customer Service Representative",
            description="Support callers at the help desk",
            location="Sydney",
            workplace_type="HYBRID",
            employment_type="FULL_TIME",
        ),
        JobInput(
            entity_id="job-hr",
            title="HR Generalist",
            description="Support talent and people operations",
            location="Singapore",
            workplace_type="HYBRID",
            employment_type="FULL_TIME",
        ),
        JobInput(
            entity_id="job-eng",
            title="Site Engineer",
            description="Oversee construction and engineering work",
            location="Dubai",
            workplace_type="ONSITE",
            employment_type="FULL_TIME",
        ),
    ]

    pairs = prepare_weak_pairs([resume], jobs, random_seed=42, per_label=2)

    assert len(pairs) == 6
    assert all(pair.candidate.entity_id == "1" for pair in pairs)
    assert all(pair.query_id == "1" for pair in pairs)
    relevances = {pair.relevance for pair in pairs}
    assert 3.0 in relevances
    assert 1.0 in relevances
    assert 0.0 in relevances


def test_write_annotation_template_writes_sampled_rows(
    candidate, matching_job, unrelated_job, tmp_path
):
    pairs = []
    for index in range(8):
        positive = matching_job.model_copy(update={"entity_id": f"job-pos-{index}"})
        negative = unrelated_job.model_copy(update={"entity_id": f"job-neg-{index}"})
        pairs.append(TrainingPair(candidate, positive, 3.0, f"query-{index}"))
        pairs.append(TrainingPair(candidate, negative, 0.0, f"query-{index}"))

    output = tmp_path / "annotation.csv"
    selected = write_annotation_template(pairs, output, sample_size=6, random_seed=1)

    rows = output.read_text(encoding="utf-8-sig").strip().splitlines()
    assert len(selected) == 6
    assert len(rows) == 7
    assert "direction" in rows[0]
    assert "human_relevance_0_to_3" in rows[0]
    assert rows[1].startswith("JOBS_FOR_CANDIDATE,")


def test_download_resume_dataset_writes_streamed_chunks(tmp_path, monkeypatch):
    import urllib.request

    class FakeResponse:
        def __init__(self, chunks):
            self._chunks = iter(chunks)

        def read(self, _size):
            try:
                return next(self._chunks)
            except StopIteration:
                return b""

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

    chunks = [b"part-one", b"part-two", b""]
    monkeypatch.setattr(
        urllib.request, "urlopen", lambda request, timeout=120: FakeResponse(chunks)
    )

    output = tmp_path / "data" / "resumes.csv"
    result = download_resume_dataset(output)

    assert result == output
    assert output.read_bytes() == b"part-onepart-two"


def test_load_resumes_rejects_missing_columns(tmp_path):
    source = tmp_path / "resumes.csv"
    source.write_text("ID,Resume_str\n1,short text\n", encoding="utf-8")

    with pytest.raises(ValueError):
        load_resumes(source)


def test_load_jobs_rejects_missing_columns(tmp_path):
    source = tmp_path / "jobs.csv"
    source.write_text("job_id,title\n1,Some Title\n", encoding="utf-8")

    with pytest.raises(ValueError):
        load_jobs(source)
