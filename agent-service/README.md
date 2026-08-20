# AD Project Agent Planner

Internal LangGraph service that converts a Candidate instruction into a structured, whitelisted plan. It never receives JWTs, reads MySQL, or executes business writes.

## Run locally

```bash
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
export DEEPSEEK_API_KEY='set-this-in-your-local-shell'
uvicorn agent_service.main:app --host 127.0.0.1 --port 8090
```

Run tests with `pytest`.

When `DEEPSEEK_API_KEY` is set, the planner uses `deepseek-v4-pro` through DeepSeek's
OpenAI-compatible Chat Completions API. The API key is read only from the process environment.
If the provider is unavailable or the key is not configured, the endpoint returns 503 with a
safe error code and Spring Boot saves the run as `FAILED`. `GET /health` reports the configured
mode, model, provider used by the most recent plan, and the last error code without exposing
credentials or user content.

Supported requests query or maintain the default resume's `age`, `summary`, `skills`, and
`experiences`. Writes remain previews until Spring Boot authorizes and confirms them.

On Windows with Docker Desktop, run `scripts/restart-agent-deepseek.ps1` from PowerShell.
It securely prompts for the key when the current shell does not already have
`DEEPSEEK_API_KEY`, builds the Agent image, replaces only the stateless Agent container, and
prints health JSON. A successful configuration reports `"plannerMode":"DEEPSEEK"`
and `"model":"deepseek-v4-pro"`.
