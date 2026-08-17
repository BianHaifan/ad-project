from __future__ import annotations

import argparse
import json
import os
import random
from dataclasses import asdict, replace
from pathlib import Path

import uvicorn

from ad_recommender.backend_import import BackendImportError, BackendJobImporter, select_import_jobs
from ad_recommender.data import (
    download_resume_dataset,
    load_jobs,
    load_resumes,
    prepare_weak_pairs,
    read_json_lines,
    read_pairs,
    sidecar_paths,
    write_annotation_template,
    write_pairs,
)
from ad_recommender.evaluation import evaluate_ranking
from ad_recommender.model import (
    baseline_bundle,
    save_bundle,
    sha256_file,
    train_bundle,
    train_guarded_pseudo_regressor_bundle,
    train_pseudo_classifier_bundle,
    train_pseudo_regressor_bundle,
)
from ad_recommender.prelabeling import (
    PrelabelerV2,
    read_query_ids,
    write_pseudo_labeled_pairs,
)
from ad_recommender.retrieval import retrieve_top_jobs
from ad_recommender.schemas import CandidateInput


def main() -> None:
    parser = argparse.ArgumentParser(description="AD Project recommendation model tools")
    subparsers = parser.add_subparsers(dest="command", required=True)

    download = subparsers.add_parser("download-resumes")
    download.add_argument("--output", type=Path, required=True)

    prepare = subparsers.add_parser("prepare-data")
    prepare.add_argument("--resumes", type=Path, required=True)
    prepare.add_argument("--jobs", type=Path, required=True)
    prepare.add_argument("--output", type=Path, required=True)
    prepare.add_argument("--annotations", type=Path, required=True)
    prepare.add_argument("--per-label", type=int, default=10)

    train = subparsers.add_parser("train")
    train.add_argument("--pairs", type=Path, required=True)
    train.add_argument("--artifact-dir", type=Path, required=True)
    train.add_argument("--report", type=Path, required=True)
    train.add_argument(
        "--algorithm",
        choices=(
            "hgb-regressor", "rf-pseudo-classifier", "hgb-pseudo-regressor",
            "hgb-guarded-regressor",
        ),
        default="hgb-regressor",
    )
    train.add_argument("--label-source", default="WEAK_SUPERVISION")

    pseudo_label = subparsers.add_parser("pseudo-label")
    pseudo_label.add_argument("--pairs", type=Path, required=True)
    pseudo_label.add_argument("--teacher-model", type=Path, required=True)
    pseudo_label.add_argument("--output", type=Path, required=True)
    pseudo_label.add_argument("--exclude-query-ids", type=Path)
    pseudo_label.add_argument("--report", type=Path, required=True)
    pseudo_label.add_argument("--batch-size", type=int, default=4096)
    pseudo_label.add_argument("--target", choices=("label", "expected"), default="label")

    retrieve = subparsers.add_parser("retrieve")
    retrieve.add_argument("--candidate-pairs", type=Path, required=True)
    retrieve.add_argument("--jobs", type=Path, required=True)
    retrieve.add_argument("--output", type=Path, required=True)
    retrieve.add_argument("--report", type=Path, required=True)
    retrieve.add_argument("--top-k", type=int, default=300)
    retrieve.add_argument("--batch-size", type=int, default=64)
    retrieve.add_argument("--max-features", type=int, default=60_000)

    import_jobs = subparsers.add_parser("import-jobs")
    import_jobs.add_argument("--jobs", type=Path, required=True)
    import_jobs.add_argument("--backend-url", default="http://127.0.0.1:8080")
    import_jobs.add_argument("--email")
    import_jobs.add_argument("--password-env", default="AD_IMPORT_PASSWORD")
    import_jobs.add_argument("--access-token-env", default="AD_IMPORT_ACCESS_TOKEN")
    import_jobs.add_argument(
        "--state-file", type=Path, default=Path("data/imports/company-jobs.json")
    )
    import_jobs.add_argument("--limit", type=int, default=20)
    import_jobs.add_argument("--seed", type=int, default=42)
    import_jobs.add_argument("--dry-run", action="store_true")
    import_jobs.add_argument("--no-publish", action="store_true")

    serve = subparsers.add_parser("serve")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8000)
    serve.add_argument("--reload", action="store_true")

    args = parser.parse_args()
    if args.command == "download-resumes":
        output = download_resume_dataset(args.output)
        print(f"Downloaded resume dataset to {output}")
    elif args.command == "prepare-data":
        resumes = load_resumes(args.resumes)
        jobs = load_jobs(args.jobs)
        pairs = prepare_weak_pairs(resumes, jobs, per_label=args.per_label)
        held_out = write_annotation_template(pairs, args.annotations)
        training_pairs = [
            pair for pair in pairs if (pair.candidate.entity_id, pair.job.entity_id) not in held_out
        ]
        write_pairs(training_pairs, args.output)
        print(
            f"Prepared {len(training_pairs)} training pairs from {len(resumes)} resumes and "
            f"{len(jobs)} jobs; held out {len(held_out)} pairs for human review"
        )
    elif args.command == "retrieve":
        candidate_path, _ = sidecar_paths(args.candidate_pairs)
        candidates = read_json_lines(candidate_path, CandidateInput.model_validate_json)
        jobs = load_jobs(args.jobs)
        result = retrieve_top_jobs(
            candidates,
            jobs,
            args.output,
            report=args.report,
            top_k=args.top_k,
            batch_size=args.batch_size,
            max_features=args.max_features,
        )
        print(json.dumps(result, indent=2, sort_keys=True))
    elif args.command == "pseudo-label":
        pairs = read_pairs(args.pairs)
        teacher = PrelabelerV2.load(args.teacher_model)
        excluded_query_ids = read_query_ids(args.exclude_query_ids)
        result = write_pseudo_labeled_pairs(
            pairs,
            args.output,
            teacher,
            excluded_query_ids=excluded_query_ids,
            report=args.report,
            batch_size=args.batch_size,
            target=args.target,
        )
        print(json.dumps(result, indent=2, sort_keys=True))
    elif args.command == "train":
        pairs = read_pairs(args.pairs)
        train_pairs, validation_pairs = group_split(pairs)
        digest = sha256_file(args.pairs)
        print(
            f"Loaded {len(train_pairs)} training and {len(validation_pairs)} validation pairs",
            flush=True,
        )
        print("Training validation model...", flush=True)
        if args.algorithm == "hgb-guarded-regressor":
            trained = train_guarded_pseudo_regressor_bundle(train_pairs, digest)
        elif args.algorithm == "rf-pseudo-classifier":
            trained = train_pseudo_classifier_bundle(train_pairs, digest)
        elif args.algorithm == "hgb-pseudo-regressor":
            trained = train_pseudo_regressor_bundle(train_pairs, digest)
        else:
            trained = train_bundle(
                train_pairs,
                digest,
                label_source=args.label_source,
            )
        baseline = baseline_bundle(train_pairs, digest, extractor=trained.extractor)
        print("Evaluating baseline and trained rankings...", flush=True)
        metrics = {
            "baseline": evaluate_ranking(baseline, validation_pairs),
            "trained": evaluate_ranking(trained, validation_pairs),
        }
        print("Training final model on all pairs...", flush=True)
        if args.algorithm == "hgb-guarded-regressor":
            final_model = train_guarded_pseudo_regressor_bundle(pairs, digest)
        elif args.algorithm == "rf-pseudo-classifier":
            final_model = train_pseudo_classifier_bundle(pairs, digest)
        elif args.algorithm == "hgb-pseudo-regressor":
            final_model = train_pseudo_regressor_bundle(pairs, digest)
        else:
            final_model = train_bundle(
                pairs,
                digest,
                label_source=args.label_source,
            )
        final_model.manifest = replace(
            final_model.manifest,
            metrics={
                **final_model.manifest.metrics,
                **{f"validation_{name}": value for name, value in metrics["trained"].items()},
            },
        )
        save_bundle(final_model, args.artifact_dir)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(
                {"comparison": metrics, "active_model": asdict(final_model.manifest)},
                indent=2,
                sort_keys=True,
            ),
            encoding="utf-8",
        )
        print(f"Saved {final_model.manifest.model_version} to {args.artifact_dir}")
    elif args.command == "import-jobs":
        try:
            jobs = select_import_jobs(load_jobs(args.jobs), args.limit, args.seed)
        except (FileNotFoundError, ValueError) as error:
            parser.error(str(error))
        if args.dry_run:
            print(
                json.dumps(
                    [
                        {
                            "sourceJobId": job.entity_id,
                            "title": job.title,
                            "employmentType": job.employment_type,
                            "workplaceType": job.workplace_type,
                            "location": job.location,
                        }
                        for job in jobs
                    ],
                    indent=2,
                )
            )
            return
        access_token = os.getenv(args.access_token_env, "").strip()
        importer = BackendJobImporter(args.backend_url, args.state_file)
        try:
            if not access_token:
                password = os.getenv(args.password_env, "")
                if not args.email or not password:
                    message = (
                        f"set {args.access_token_env}, or provide --email and "
                        f"set {args.password_env}"
                    )
                    parser.error(
                        message
                    )
                access_token = importer.login(args.email, password)
            result = importer.import_jobs(
                jobs,
                access_token,
                args.jobs,
                publish=not args.no_publish,
            )
        except BackendImportError as error:
            parser.error(str(error))
        print(json.dumps(asdict(result), indent=2, default=str))
    else:
        uvicorn.run("ad_recommender.api:app", host=args.host, port=args.port, reload=args.reload)


def group_split(
    pairs: list, validation_fraction: float = 0.2, random_seed: int = 42
) -> tuple[list, list]:
    query_ids = sorted({pair.query_id for pair in pairs})
    if len(query_ids) < 2:
        raise ValueError("Training data must contain at least two query groups")
    randomizer = random.Random(random_seed)
    randomizer.shuffle(query_ids)
    validation_count = max(1, round(len(query_ids) * validation_fraction))
    validation_ids = set(query_ids[:validation_count])
    training = [pair for pair in pairs if pair.query_id not in validation_ids]
    validation = [pair for pair in pairs if pair.query_id in validation_ids]
    return training, validation


if __name__ == "__main__":
    main()
