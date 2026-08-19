# Agent resume content tools

## Completed

- Added read requests for resume Summary, Skills, and Experience.
- Added confirmed Summary replacement.
- Added confirmed Skills add, delete, and rename operations.
- Added confirmed Experience add, delete, and field update operations.
- Kept age updates compatible with the existing preview and confirmation flow.
- Added typed read results and complex field previews to the Android Agent conversation.
- Added exact tool whitelist, ownership, preview expiry, idempotency, and resume version checks to every write.

## Modules changed

- `agent-service`: deterministic LangGraph planner and planner tests.
- `backend`: Agent orchestration, generic resume content patch service, integration tests.
- `android`: Agent contract models, query result rendering, list preview rendering, UI tests.
- `docs`: Agent design and OpenAPI contract.

## API and database

- `AgentFieldChange` now supports `age`, `summary`, `skills`, and `experiences` values.
- `AgentExecutionResult` supports `READ_RESUME` and an optional typed `queryResult`.
- `read_resume_section` was added to the documented Agent tool list.
- No database schema migration was required; previews and results remain JSON in existing Agent columns.

## Verification

- Python planner: 10 tests passed.
- Spring Agent integration: 12 tests passed.
- Existing Candidate Resume/Profile/Onboarding integration: 11 tests passed.
- Android Agent UI/ViewModel tests passed; `lintDebug` and `assembleDebug` passed.
- OpenAPI YAML parsed successfully as OpenAPI 3.1.0.
- Emulator read request returned the real account Skills through Android, Spring, Python, and MySQL.
- Emulator Summary write generated a preview and was cancelled; resume version and Summary remained unchanged.

## Current limitations

- Planning is deterministic pattern parsing, not an external LLM adapter.
- One Agent run handles one resume section operation at a time.
- Experience creation uses labeled fields and `YYYY-MM` dates.
- Agent age updates still target the resume age field rather than the separate Candidate Profile age field.
