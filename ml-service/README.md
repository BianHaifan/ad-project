# AD Project ML recommendation service

This service trains and serves one explainable candidate-job matching model. Spring Boot remains
the only public business API. The Python service neither authenticates end users nor reads MySQL.

> 团队成员在提交、拉取或首次运行前，请先阅读
> [TEAM_HANDOFF.zh-CN.md](TEAM_HANDOFF.zh-CN.md)。其中说明了哪些模型文件必须提交、哪些数据禁止提交、
> 如何配置共享令牌，以及从 GitHub 拉取后如何完成真实的前后端联调。

## Environment

```powershell
cd E:\githubitem\AD\ad-project\ml-service
conda env create -f environment.yml
conda activate ad-project-ml
```

If the environment already exists, update it with:

```powershell
conda env update -n ad-project-ml -f environment.yml
```

## Data preparation

The full resume source is CC0. The local job listing source has no explicit license, so raw data,
processed pairs, human labels, and model artifacts are intentionally ignored by Git.

```powershell
conda run -n ad-project-ml ad-recommender download-resumes `
  --output ..\datasets\resume_full.csv

conda run -n ad-project-ml ad-recommender prepare-data `
  --resumes ..\datasets\resume_full.csv `
  --jobs ..\datasets\company.csv `
  --output data\processed\training_pairs_v4.csv `
  --annotations data\annotations\human_review_v4.csv
```

The annotation file contains a machine-generated weak label only to help audit sampling. Reviewers
must independently fill `human_relevance_0_to_3`; the weak label must not be copied as the answer.

## Retrieval, pseudo-labeling, guarded v4 training, and serving

The active v4 model first cleans all 25,298 unique jobs, retrieves 300 jobs per candidate, then
learns from the continuous expected relevance produced by `prelabeler-distilled-v2` plus
conservative hard-negative corrections. Keep the 20 frozen
blind-test query groups out of both pseudo-labeling and final training:

```powershell
conda run -n ad-project-ml ad-recommender retrieve `
  --candidate-pairs data\processed\training_pairs_v4.csv `
  --jobs ..\datasets\company.csv `
  --output data\processed\training_pairs_retrieval_top300_v4.csv `
  --report reports\retrieval_top300_v4.json `
  --top-k 300

conda run -n ad-project-ml ad-recommender pseudo-label `
  --pairs data\processed\training_pairs_retrieval_top300_v4.csv `
  --teacher-model artifacts\teacher\prelabeler-distilled-v2.joblib `
  --output data\processed\training_pairs_retrieval_pseudo_v4.csv `
  --exclude-query-ids data\processed\frozen_blind_query_ids.txt `
  --report reports\pseudo_label_retrieval_v4.json `
  --target expected

conda run -n ad-project-ml ad-recommender train `
  --pairs data\processed\training_pairs_retrieval_pseudo_v4.csv `
  --algorithm hgb-guarded-regressor `
  --label-source AI_TEACHER_EXPECTED_RELEVANCE_V2+CONSERVATIVE_HARD_NEGATIVES_V1 `
  --artifact-dir artifacts\active `
  --report reports\training_retrieval_v4.json

$env:ML_INTERNAL_TOKEN='replace-with-a-local-secret'
$env:ML_MODEL_PATH='artifacts\active\model.joblib'
conda run -n ad-project-ml ad-recommender serve --port 8000
```

Pseudo-labeling writes the teacher class, expected relevance, probability, margin, and label
source for every retained pair. The v4 training set has 738,900 pairs across 2,463 candidate query
groups. Its guarded trainer corrected 238,009 conservative hard negatives, uses monotonic feature
constraints, and applies an online cap when explicit skills and role titles do not match. Final
training writes `model.joblib` and `manifest.json`. Online requests load and warm that
artifact at service startup and never train during a request.

The active `match-hgb-retrieval-v4` model has validation MAP@10 0.967368, eligible-query MAP@10
0.977280, and relevant-query coverage 0.989858. These values measure agreement with AI teacher labels;
they must not be described as real hiring accuracy. The frozen set remains reserved for
independently reviewed human labels.

Run the deterministic role/skill counterfactual checks after every model change:

```powershell
conda run -n ad-project-ml python scripts\evaluate_counterfactuals.py `
  --model artifacts\active\model.joblib `
  --report reports\counterfactual_v4.json
```

## Hybrid recommendation research

The active `match-hybrid-lsa-cf-v1` artifact now executes three components in the real inference
service: the guarded v4 ranker (85%), a 64-dimensional LSA embedding signal (10%), and a
32-dimensional implicit-feedback SVD collaborative signal (5%). The collaborative matrix contains
40,439 reproducible pseudo interactions across 284 warm candidates and 1,500 warm jobs. A new live
Candidate or Job is mapped to the nearest warm entity in the fitted LSA space before CF scoring;
the internal response reports this honestly as `EMBEDDING_BRIDGED` rather than direct history.

This runtime proves integration, not a production lift. Direct score blending did not provide stable
offline gains, CF feedback is teacher-derived rather than observed behavior, and the existing v4
metrics remain teacher-agreement metrics. Full-catalog neural retrieval was complementary, so a
future candidate architecture is a union of existing and neural Top-300 pools followed by reranking.
See
[HYBRID_RECOMMENDER_PLAN.zh-CN.md](HYBRID_RECOMMENDER_PLAN.zh-CN.md) for results, reproduction
commands, real-event requirements, and deployment gates.

Rebuild the hybrid runtime artifact from the processed v4 pairs with:

```powershell
conda run -n ad-project-ml python scripts\build_hybrid_runtime.py `
  --base-model artifacts\active\model.joblib `
  --training-pairs data\processed\training_pairs_retrieval_pseudo_v4.csv `
  --output artifacts\active `
  --warm-users 300 --warm-items 1500 `
  --embedding-dimensions 64 --cf-dimensions 32 --seed 42
```

The raw processed pairs and sidecar JSONL files are not committed. Team members must obtain the
versioned data package described in the import guide before rebuilding; normal service startup only
needs the committed `artifacts/active/model.joblib` and `manifest.json`.

Run a real-data in-process API smoke test with:

```powershell
conda run -n ad-project-ml python scripts\smoke_test_model.py `
  --model artifacts\active\model.joblib `
  --pairs data\processed\training_pairs_retrieval_top300_v4.csv `
  --jobs 50
```

Audit the 20 query groups excluded from student training with teacher labels:

```powershell
conda run -n ad-project-ml python scripts\evaluate_frozen_queries.py `
  --model artifacts\active\model.joblib `
  --teacher-model artifacts\teacher\prelabeler-distilled-v2.joblib `
  --retrieval-pairs data\processed\training_pairs_retrieval_top300_v4.csv `
  --training-pairs data\processed\training_pairs_retrieval_pseudo_v4.csv `
  --query-ids data\processed\frozen_blind_query_ids.txt `
  --report reports\frozen_teacher_audit_v4.json
```

The audit verifies zero frozen-query overlap with training and reports MAP@10 1.0 over 6,000
retrieved pairs. It is explicitly a teacher-agreement audit, not a human blind test.

## Checks

```powershell
conda run -n ad-project-ml ruff check .
conda run -n ad-project-ml pytest
```

## Import raw jobs for end-to-end testing

Use `ad-recommender import-jobs` to select raw jobs and send them through the real Spring Boot
recruiter APIs. The tool never writes MySQL directly, records resumable local progress, and skips
jobs already imported. Start with `--dry-run`, then provide the recruiter password through the
`AD_IMPORT_PASSWORD` environment variable. See the complete Chinese walkthrough in
[IMPORT_TEST_JOBS.zh-CN.md](IMPORT_TEST_JOBS.zh-CN.md).

For server deployment, data-transfer scope, remote imports, and production inference checks, see
[SERVER_DATA_IMPORT_AND_INFERENCE.zh-CN.md](SERVER_DATA_IMPORT_AND_INFERENCE.zh-CN.md).
