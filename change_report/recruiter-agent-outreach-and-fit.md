# Recruiter Agent Outreach and Candidate Fit

## Completed

- Added a normal message-conversation entry point to each Agent-ranked candidate, including candidates who have not applied.
- Added `RECRUITER_OUTREACH` conversations. A recruiter can create one only through a completed screening run they own, for a candidate actually returned by that run, and for a job still owned by their company.
- Made outreach creation idempotent for the recruiter + company + job + candidate combination. It reuses the normal Messages page, attachments, unread counts, and polling; it does not use the separate community direct-message tables.
- Outreach conversations permit text and attachment messages without an application record. The candidate sees the recruiter who initiated the contact.
- Added a live reverse-ranking fallback for recruiter application list/detail match score and Candidate Fit. A current candidate-side snapshot is still reused first; when it is missing or stale, the submitted resume snapshot is scored through the existing ranking service, which safely falls back to deterministic rules if ML is unavailable.

## API and database

- New recruiter-only endpoint: `POST /api/v1/agent/runs/{runId}/ranked-candidates/{candidateId}/conversation`.
- Conversation responses now include `conversationType`; `applicationId` can be `null` for `RECRUITER_OUTREACH`.
- Added Flyway migration `V32__add_recruiter_outreach_conversations.sql`. Existing application conversations remain unchanged; no data is deleted or rewritten.
- Updated `docs/openapi-v1.yaml`, recruiter Web client contracts, and Android conversation decoding compatibility.

## Tests and checks

- Web: `npm test -- --run src/api/agentHttpClient.test.ts src/pages/AgentPage.test.tsx src/api/conversationHttpClient.test.ts` — 32 passed.
- Web: `npm run lint` and `npm run typecheck` — passed.
- Android: JDK 21 `testDebugUnitTest --tests com.adproject.candidate.feature.messages.MessagesScreensUiTest` — passed.
- `git diff --check` — passed (line-ending notices only).
- Backend regression tests were added for Agent outreach idempotency/message sending and stale Candidate Fit fallback, but could not be run locally because Maven is not installed and this repository has no Maven wrapper. CI runs `mvn -B test` in `backend/`.

## Limits / next safe check

- The fallback score is advisory only and does not alter application state or hiring decisions.
- Before merging, run `mvn -B test -Dtest=ConversationIntegrationTest,RecruiterApplicationIntegrationTest` in `backend/` (or let CI do so) to validate Flyway V32 against the test schema.
