from __future__ import annotations

import hmac
import os
import time
from datetime import UTC, datetime
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status

from ad_recommender.model import ModelBundle, load_bundle
from ad_recommender.schemas import (
    HealthResponse,
    RecommendationResponse,
    RecommendCandidatesRequest,
    RecommendJobsRequest,
)

DEFAULT_MODEL_PATH = Path("artifacts/active/model.joblib")


def create_app(
    bundle: ModelBundle | None = None,
    model_path: Path | None = None,
    internal_token: str | None = None,
) -> FastAPI:
    application = FastAPI(
        title="AD Project Recommendation Service",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url="/internal/v1/openapi.json",
    )
    application.state.bundle = bundle
    application.state.internal_token = internal_token or os.getenv(
        "ML_INTERNAL_TOKEN", "local-ml-token"
    )
    if bundle is None:
        configured_path = model_path or Path(os.getenv("ML_MODEL_PATH", DEFAULT_MODEL_PATH))
        if configured_path.exists():
            application.state.bundle = load_bundle(configured_path)
    if application.state.bundle is not None:
        application.state.bundle.warm_up()

    def require_token(
        request: Request, x_internal_token: str | None = Header(default=None)
    ) -> None:
        expected = request.app.state.internal_token
        if not x_internal_token or not hmac.compare_digest(x_internal_token, expected):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid internal service token",
            )

    def require_bundle(request: Request) -> ModelBundle:
        loaded = request.app.state.bundle
        if loaded is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="No compatible model artifact is loaded",
            )
        return loaded

    @application.get("/internal/v1/health", response_model=HealthResponse)
    def health(request: Request) -> HealthResponse:
        loaded = request.app.state.bundle
        if loaded is None:
            return HealthResponse(status="not_ready", model_version=None, feature_version=None)
        return HealthResponse(
            status="ready",
            model_version=loaded.manifest.model_version,
            feature_version=loaded.manifest.feature_version,
        )

    @application.post(
        "/internal/v1/recommend/jobs",
        response_model=RecommendationResponse,
        dependencies=[Depends(require_token)],
    )
    def recommend_jobs(request: Request, body: RecommendJobsRequest) -> RecommendationResponse:
        loaded = require_bundle(request)
        started = time.perf_counter()
        items = loaded.recommend_jobs(body.candidate, body.jobs, body.limit)
        return RecommendationResponse(
            model_version=loaded.manifest.model_version,
            feature_version=loaded.manifest.feature_version,
            generated_at=datetime.now(UTC),
            inference_ms=max(0, round((time.perf_counter() - started) * 1000)),
            items=items,
        )

    @application.post(
        "/internal/v1/recommend/candidates",
        response_model=RecommendationResponse,
        dependencies=[Depends(require_token)],
    )
    def recommend_candidates(
        request: Request, body: RecommendCandidatesRequest
    ) -> RecommendationResponse:
        loaded = require_bundle(request)
        started = time.perf_counter()
        items = loaded.recommend_candidates(body.job, body.candidates, body.limit)
        return RecommendationResponse(
            model_version=loaded.manifest.model_version,
            feature_version=loaded.manifest.feature_version,
            generated_at=datetime.now(UTC),
            inference_ms=max(0, round((time.perf_counter() - started) * 1000)),
            items=items,
        )

    return application


app = create_app()
