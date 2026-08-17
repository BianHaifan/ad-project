FROM python:3.12-slim

WORKDIR /app/ml-service

COPY ml-service/pyproject.toml ./
COPY ml-service/src ./src

RUN pip install --no-cache-dir .

COPY ml-service/artifacts/active ./artifacts/active

ENV ML_MODEL_PATH=/app/ml-service/artifacts/active/model.joblib
EXPOSE 8000

CMD ["uvicorn", "ad_recommender.api:app", "--host", "0.0.0.0", "--port", "8000"]
