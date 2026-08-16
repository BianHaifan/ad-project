# Implementation Plan: Interview Scheduling MVP

## Overview

Turn the existing `INTERVIEW` application label into a real, demo-ready
interview workflow. A recruiter schedules one interview for an application;
the application atomically enters `INTERVIEW`; the candidate can view the
invitation in Android; and the recruiter can reschedule, mark it completed, or
cancel it. This work excludes video calls, calendar integrations, ML, Agent,
and Admin changes.

## Architecture decisions

- Keep the OpenAPI interview contract as the source of truth. Change its two
  interview operations from `DRAFT` to `IMPLEMENTED` only when the backend and
  both clients are delivered together.
- Use a Flyway V7 migration with one `interviews` row per application. This
  matches the current singular `interview` field in application details and
  keeps the demo simple.
- Scheduling is a single transaction: verify recruiter ownership, lock the
  application, check its version and `IN_REVIEW` status, create the interview,
  and transition the application to `INTERVIEW` with an audit event.
- Store instants in UTC; retain the selected IANA timezone only for display.
  The recruiter enters date/time in their browser timezone, which is converted
  before the API request.
- `SCHEDULED` may be rescheduled, completed, or cancelled. `COMPLETED` and
  `CANCELLED` are terminal. Cancelling an interview does not silently change
  the application stage.
- Do not invent a new calendar page. Put a scheduling modal and the resulting
  interview card in the existing recruiter application-detail screen. Android
  is read-only and shows the scheduled interview on its existing application
  detail screen.

## Task list

### Task 1: Database and server interview slice

**Description:** Add V7, interview domain/entity/repository/service, and the
two recruiter endpoints already described in OpenAPI.

**Acceptance criteria:**
- [x] An `IN_REVIEW` application can create exactly one interview with UTC
  date-time, timezone, duration, mode, location/meeting URL, and optional note.
- [x] Creation changes the application to `INTERVIEW` and records the
  recruiter, previous/current stages, timestamp, and a schedule reason.
- [x] Only the owning recruiter may create or update it; other roles and other
  companies cannot discover its existence.
- [x] Rescheduling and status changes enforce `expectedVersion`; stale writes
  return `409 VERSION_CONFLICT`.

**Verification:** Backend tests cover success, unauthenticated, Candidate,
cross-company recruiter, invalid source stage, duplicate interview, stale
application/interview versions, and validation failures.

**Dependencies:** None.

**Likely modules:** `backend/`, `docs/openapi-v1.yaml`, API coverage docs.

### Task 2: Recruiter scheduling and management UI

**Description:** Replace the generic “Move to interview” option with a
schedule-interview modal. Add an interview card after scheduling with edit,
complete, and cancel actions.

**Acceptance criteria:**
- [x] The scheduling form requires local date/time, timezone, duration, mode,
  and meeting link/location; note is optional.
- [x] Only `IN_REVIEW` shows the schedule action. A successful submission
  refetches the application and immediately shows its interview card.
- [x] The card clearly distinguishes scheduled, completed, and cancelled;
  loading, validation, error, and disabled-submit states are present.
- [x] A completed/cancelled interview has no further edit actions.

**Verification:** Vitest covers request conversion, field validation,
successful creation, 409 recovery, and terminal-state UI behavior.

**Dependencies:** Task 1.

**Likely modules:** `web/src/api`, `web/src/pages`, `web/src/models`, tests.

### Task 3: Candidate interview visibility

**Description:** Extend the existing candidate application DTO mapping and
Android detail screen to show the interviewer’s invitation data without
exposing recruiter-only notes beyond the API contract.

**Acceptance criteria:**
- [x] Candidate list/detail requests return the interview only for the
  candidate’s own application.
- [x] Android shows scheduled time in the saved timezone, duration, mode,
  location/link, and current interview status.
- [x] It also handles no interview, cancelled, loading, and request-error
  states; it does not offer Candidate-side editing in this MVP.

**Verification:** Mapper/repository/ViewModel tests plus `assembleDebug`.

**Dependencies:** Task 1.

**Likely modules:** `backend/`, `android/`, Android tests.

### Task 4: Contract, regression, and demo acceptance

**Description:** Align the OpenAPI status and coverage documents, run focused
tests/builds, manually verify the full Web-to-Android flow, and record the
handoff in `change_report/`.

**Acceptance criteria:**
- [x] API documentation describes the implemented behavior and error cases.
- [ ] A recruiter can schedule, reschedule, complete, and cancel an interview;
  the candidate can see each resulting state after refresh. (requires manual
  two-account demo; not performed in this environment)
- [x] Existing application status, message, and job flows remain working.
- [x] A report identifies changed modules, migration version, tests run,
  limitations, and the safe next task.

**Verification:** Relevant backend tests, Web lint/typecheck/test/build,
Android test/lint/assembleDebug, and a real two-account demo.

**Dependencies:** Tasks 1–3.

## Checkpoints

1. After Task 1, validate the real API against MySQL before either client is
   changed.
2. After Tasks 2–3, run both client builds and demonstrate a recruiter
   scheduling an interview that the candidate can view.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| A status change succeeds but interview creation fails | Keep both operations in one transaction. |
| Time is displayed incorrectly | Store UTC plus IANA timezone; test a non-UTC example. |
| Stale pages overwrite a newer schedule | Require and check optimistic versions. |
| Scope grows into a calendar product | No calendar integration, invitations, accept/decline, or video call in this slice. |
| UI has no dedicated Figma frame | Reuse the existing application-detail layout; request a Figma frame before visual redesign. |
