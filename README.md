# AD Project

An intelligent recruitment platform capstone project for candidates, recruiters, and administrators.

## Technology baseline

- Candidate client: Kotlin + Jetpack Compose (Android)
- Recruiter and administrator clients: React + TypeScript
- Core backend: Java + Spring Boot
- Database: MySQL
- Machine learning: a team-trained job recommendation model, with training and inference handled by a standalone Python service
- AI Agent: performs operations that the current user is authorized to execute through controlled Spring Boot tools
- Agent orchestration: an internal Python + LangGraph service generates plans only; Spring Boot handles authorization, tools, previews, and auditing

## Current goal

First, complete one fully functional end-to-end MVP flow:

```text
Recruiter publishes a job
→ The job appears in the Android app
→ Candidate views the job details and applies
→ Recruiter reviews the application and updates its status
→ Candidate views the application progress
```

ML and the AI Agent are key features of the capstone project, but core functionality such as signing in, browsing, and applying must not depend on them.

## Documentation

- [Product requirements](docs/product-requirements.md)
- [User flows](docs/user-flows.md)
- [System architecture](docs/architecture.md)
- [Database design](docs/database-design.md)
- [API design](docs/api-design.md)
- [Authorization rules](docs/permissions.md)
- [Testing plan](docs/testing-plan.md)
- [Development plan](docs/development-plan.md)
- [Figma MVP design review](docs/figma-mvp-audit.md)
- [Graduation thesis outline](docs/graduation-thesis-outline.md)

Design reference: [AD project Copy](https://www.figma.com/design/ellcZx2GjomKwCQNxuryri/AD_project--Copy-?node-id=0-1)

## Run and test the backend

The backend requires Java 21, Maven, and MySQL 8. Copy the variables from `.env.example` into your local environment configuration (do not commit real values), then run:

```bash
cd backend
mvn spring-boot:run
```

By default, the service listens on `http://localhost:8080`, and the API prefix is `/api/v1`. Flyway automatically applies database migrations at startup; Hibernate only validates the schema and does not create tables.

### Password reset emails with Resend

Password reset emails are sent through Resend SMTP. First verify a sending domain in Resend and create an API key, then set the following values in your local, untracked `.env` file:

```dotenv
SMTP_HOST=smtp.resend.com
SMTP_PORT=587
SMTP_USERNAME=resend
SMTP_PASSWORD=re_your_api_key
SMTP_FROM_ADDRESS=no-reply@your-verified-domain.example
SMTP_STARTTLS=true
```

The root `.env` file is ignored by Git. Never write a real API key to `.env.example` or any tracked file.

Production CD uses the GitHub `production` Environment configuration:

- Secret `RESEND_API_KEY`: the Resend API key.
- Variable `RESEND_FROM_ADDRESS`: a sender address under a verified domain, such as `no-reply@example.com`.
- Secret `DEEPSEEK_API_KEY`: the DeepSeek API key used by the Agent Planner.

After a push to `main`, CD passes these three values to the server containers as runtime environment variables without writing secrets to the Git repository. If the values are not configured in GitHub, the deployment continues to use `SMTP_PASSWORD`, `SMTP_FROM_ADDRESS`, and `DEEPSEEK_API_KEY` from `/opt/adproject/infra/docker/.env` on the server; all other parameters have safe defaults. If `DEEPSEEK_API_KEY` is missing from both locations, the Agent Planner returns 503 and Spring Boot saves the run as `FAILED` without affecting core functionality.

Run all tests and build the package:

```bash
cd backend
mvn test
mvn package
```

The test suite always runs Auth HTTP integration tests against an isolated H2 database in MySQL compatibility mode. When Docker is available, it also validates clean-database migrations with Testcontainers and MySQL 8.4.

## Run the administrator system locally

Administrator is not a third business role. First register a normal Candidate or Recruiter account, then set
`ADMIN_BOOTSTRAP_EMAIL` to that account's email address after the initial startup. The system grants
`PLATFORM_ADMIN` only when no active administrator exists. It does not create an account or store a default
password; the environment variable can be removed after the authorization has been persisted.

```powershell
$env:ADMIN_BOOTSTRAP_EMAIL="your-registered-email@example.com"
cd backend
mvn spring-boot:run
```

Start the frontend in another terminal:

```powershell
cd web
npm install
npm run dev
```

Open `http://localhost:5173/admin/sign-in` in a browser. The administrator workspace includes users and permissions, company reviews, basic community moderation, and audit logs. `/admin/me` revalidates authorization with the server whenever the workspace is opened. See [OpenAPI](docs/openapi-v1.yaml) for the complete API contract.

## Run and test the Agent Planner

Before starting the Agent API, start the internal Planner in another terminal:

```bash
cd agent-service
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
uvicorn agent_service.main:app --host 127.0.0.1 --port 8090
```

Run the Planner tests with `pytest -q`. The current MVP supports querying or updating the default resume's age, summary, skills, and experience. The Planner uses `deepseek-v4-pro` to generate a constrained, structured plan and requires `DEEPSEEK_API_KEY` in the process environment. If the key is not configured or the provider call fails, the endpoint returns 503; Spring Boot saves the run as `FAILED` and returns a safe error. Spring Boot reads the current user's resume and generates a field-level preview, which Android displays as a plan. Spring Boot executes `apply_resume_patch` only after the user explicitly confirms through the confirmation endpoint. Confirmation validates a one-time confirmation ID, run version, resume version, expiration time, and `Idempotency-Key`; retries with the same idempotency key do not create duplicate writes.

In the Android app, open **Profile → AI Agent** to access the feature directly. See [`docs/agent-design.md`](docs/agent-design.md) and [`docs/openapi-v1.yaml`](docs/openapi-v1.yaml) for the public Agent API, request examples, and security boundaries.
