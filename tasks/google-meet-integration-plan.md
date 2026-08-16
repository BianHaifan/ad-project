# Implementation Plan: Automatic Google Meet for Interviews

## Goal

Allow an approved recruiter who has explicitly connected a Google account to
schedule an online interview and receive a unique Google Meet link. The
candidate sees and opens that final link in the existing Android application
detail view. The scope is for a local/demo Google OAuth application and does
not change project login, ML, Agent, Admin, email, or Microsoft Teams.

## Preconditions and boundaries

- The manual interview workflow in `tasks/interview-plan.md` must be complete
  first. It owns the interview record, application transition, candidate view,
  and state machine.
- A Google Meet connection is optional. Recruiters without it retain the
  existing manual meeting-link flow.
- Only recruiters connect Google; Android never receives Google OAuth tokens.
- The repository never contains a Google client secret, OAuth token, or real
  redirect URL. Runtime secrets remain local environment configuration.
- The Google Cloud owner must enable Calendar API, register the exact callback
  URI, configure the OAuth consent screen as Testing, and add demo accounts as
  test users before live verification.

## Architecture decisions

- Add an isolated backend `integration.google` module. It exposes an interface
  used by the interview service; no Google SDK types leak into application DTOs.
- Use OAuth authorization-code flow with a single Google account connection per
  recruiter. Persist refresh tokens encrypted at rest and never log them.
- Use narrow Calendar event access rather than project authentication changes.
  The backend creates a Calendar event with conference-data creation enabled,
  then stores only the Google event ID and the generated join URL.
- Extend the draft interview contract with `meetingProvider` (`MANUAL` or
  `GOOGLE_MEET`). The later product decision in
  `tasks/interview-mode-simplification-plan.md` supersedes the new-online
  manual-link flow: new `ONLINE` interviews require `GOOGLE_MEET`; `MANUAL`
  remains for on-site/phone details and historical compatibility.
- External APIs cannot participate in the MySQL transaction. Commit the local
  interview and a provisioning record first, then create the Google event. A
  conference state of `PENDING`, `READY`, or `FAILED` makes retries explicit
  and prevents a false “scheduled with link” result.
- Use the persisted Google event ID and a request correlation ID to make retry
  safe. Google conference creation may be asynchronous, so fetch/poll only the
  specific event until its join URL is ready, with bounded retry/backoff.
- Reschedule and cancellation update Google only after the local optimistic
  version check. If Google sync fails, retain a visible retryable sync error;
  do not silently claim that the external meeting changed.
- Do not automatically post Messages in this integration package. Candidate
  visibility through the application detail remains sufficient and avoids
  coupling to the conversation module. Google Calendar attendee invitations are
  handled separately in `tasks/google-meet-calendar-invitations-plan.md` and
  use only the existing application contact email.

## Dependency graph

```text
Manual interview API + V7 migration + candidate display
  -> Google OAuth connection storage and callback
    -> Provider client and provisioning state
      -> Recruiter “auto-create Meet” UI
        -> Reschedule/cancel synchronization and demo verification
```

## Tasks

### Task 1: Freeze contract and local data model

**Description:** Update the draft OpenAPI interview request/response and
introduce Flyway V9 columns for provider state, Google event mapping,
encrypted recruiter connections, and short-lived OAuth state. (V8 is already
taken by `interview_audit_events`; the next Google-related migration starts at
V9.)

**Acceptance criteria:**
- [ ] The contract preserves manual links and defines Google-specific pending,
  ready, failed, and retryable behavior without exposing OAuth token fields.
- [ ] Each recruiter has at most one Google connection; each interview has at
  most one external calendar event mapping.
- [ ] All new schema is created only by Flyway and supports optimistic version
  checks and audit correlation.

**Verification:** Migration runs on a clean MySQL database and backend DTO
validation tests reject incompatible provider/link combinations.

**Dependencies:** Core manual interview workflow.

**Estimated scope:** Medium.

### Task 2: Secure recruiter Google connection slice

**Status:** Backend complete, including the safe browser handoff for the
callback (`change_report/google-oauth-web-handoff.md`), and the recruiter Web
connection page complete (`change_report/web-google-oauth-connection-ui.md`).
The Web scheduling UI ("auto-create Meet" selection and sync-state rendering)
is complete in Task 4 (`change_report/web-google-meet-scheduling-ui.md`).

**Description:** Add connect, callback, connection-status, and disconnect
operations in a new backend integration module, plus a compact Web connection
entry point.

**Acceptance criteria:**
- [x] Only an authenticated recruiter can begin or inspect their own connection.
- [x] OAuth `state` is single-use, short-lived, and bound to the initiating
  recruiter; callback rejects replay, expiry, and mismatched state.
- [x] Access and refresh tokens are encrypted at rest, excluded from API
  responses and logs, and permanently removed on disconnect.
- [x] The callback hands the browser back with 303 See Other to the single
  server-configured web return URI using only `googleOAuth=connected|denied|failed`,
  never a token, code, state, error detail, or client-supplied URL.
- [x] The Web UI has connecting, connected, denied, expired, and retry states.

**Verification:** Backend tests cover unauthenticated, Candidate, different
recruiter, state replay/expiry, provider-denied authorization, and disconnect.
Web tests cover every connection UI state.

**Dependencies:** Task 1; Google Cloud credentials supplied locally by the
project owner.

**Estimated scope:** Medium.

### Checkpoint: Connection safety

- [ ] No secret is tracked by Git or included in `change_report/`.
- [ ] A permitted demo recruiter can connect and disconnect a test Google
  account, while an unconnected recruiter cannot create an automatic meeting.

### Task 3: Provision a Meet while scheduling an interview

**Status:** Backend implementation complete (`change_report/google-meet-calendar-provisioning.md`).
The recruiter Web UI ("auto-create Meet" selection) and the reschedule/cancel
sync are complete in Task 4 (`change_report/web-google-meet-scheduling-ui.md`
and `change_report/google-meet-reschedule-cancel-sync.md`).

**Description:** Implement a provider client that creates a uniquely keyed
Calendar event and requests Google Meet conference data. The existing
interview scheduling flow selects `MANUAL` or `GOOGLE_MEET`.

**Acceptance criteria:**
- [x] A connected recruiter can schedule with `GOOGLE_MEET`; the application
  reaches `INTERVIEW` and the interview stores the verified join URL, Google
  event id, and sync status (server side; the Web "Creating Meet" UI is Task 4).
- [x] Provider failures leave a retryable failed state with no fabricated link;
  retry does not create duplicate internal interviews or duplicate Calendar
  events.
- [x] An unconnected, expired, or revoked connection returns a clear action to
  reconnect Google (`GOOGLE_MEET_NOT_CONNECTED` / `GOOGLE_MEET_RECONNECT_REQUIRED`)
  instead of a generic server error.
- [x] Manual link scheduling continues to work unchanged.

**Verification:** Provider-integration tests use a fake Google transport for
success, pending conference creation, token refresh, 401 refresh+retry, quota/
transient error, invalid-grant revocation, non-HTTPS link rejection, and
conflict recovery. Interview integration tests cover GOOGLE_MEET validation
(ONLINE-only, no client link), revoked reconnect, and that a provisioning
failure still commits the local interview.

**Dependencies:** Tasks 1–2.

**Estimated scope:** Medium.

### Task 4: Synchronize interview updates and expose final state

**Status:** Backend implementation complete
(`change_report/google-meet-reschedule-cancel-sync.md`), including the
review-fix pass for PENDING priority / completion-blocking / external-exception
safety (`change_report/google-meet-reschedule-cancel-sync-review-fixes.md`).
Android candidate display of the safe final state is complete
(`change_report/android-interview-meeting-sync-state.md`), and the recruiter
Web scheduling UI (auto-create Meet selection + sync-state rendering) is
complete (`change_report/web-google-meet-scheduling-ui.md`).

**Description:** When a scheduled Google Meet interview is rescheduled or
cancelled, update the mapped Calendar event and render the resulting sync state
in the recruiter UI. Candidate Android continues to display only the safe,
final invitation details.

**Acceptance criteria:**
- [x] Successful reschedule updates both local interview data and the Calendar
  event; cancellation updates/cancels the external event according to the
  chosen contract (backend, Web, and Android complete).
- [x] A remote sync failure is visible and retryable to the recruiter, while
  the candidate never receives an incorrect new time or link (backend leaves the
  interview SCHEDULED with its original time/link and `meetingSyncStatus=FAILED`;
  Web retry UI and Android final-state display complete).
- [x] Android displays the `READY` Meet link and the final interview status;
  it neither handles Google credentials nor exposes recruiter-only details.

**Verification:** Backend focused tests cover reschedule/cancel success,
DELETE-404-as-success, remote failure, PENDING-in-progress, 401 refresh/retry,
invalid-grant revocation, and manual regression. Web and Android regression
tests cover loading, no link, ready link, failed sync, cancelled, and terminal
states. Run backend focused tests, Web lint/typecheck/test/build, and Android
test/lint/assembleDebug.

**Dependencies:** Task 3.

**Estimated scope:** Medium.

### Task 5: Live demo and handoff

**Description:** Verify a real two-account demonstration using the approved
Google test users, then document the integration and its limitations.

**Acceptance criteria:**
- [ ] Recruiter connects Google, schedules an interview, receives a working
  Meet URL, reschedules once, and cancels or completes it.
- [ ] Candidate refreshes Android and sees the correct invitation state and
  opens the Meet URL.
- [ ] `change_report/` lists API/database changes, tests run, Google setup
  required, and that production OAuth verification is intentionally deferred.

**Dependencies:** Tasks 1–4 and Google Cloud owner configuration.

## Explicitly out of scope

- Google sign-in as project authentication
- Google Calendar free/busy lookup, calendar selection, recurring interviews,
  standalone email delivery guarantees, recordings, transcripts, and Meet
  attendance data. Google Calendar attendee invitations are in scope as a
  follow-up package; actual delivery remains Google's responsibility.
- Microsoft Teams integration
- Admin approval of third-party connections
- Changes to ML, Agent, or the existing message API

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Google rejects/withdraws consent | Persist connection state and guide the recruiter to reconnect. |
| External call succeeds but local write fails | Persist a provisioning operation before the call; use stable correlation and event mapping for recovery. |
| Duplicate meetings after retry | Enforce one mapping per interview and reuse the stored remote event ID. |
| Tokens leak | Encrypt at rest; mask logs; secrets only in environment variables. |
| Test OAuth cannot be used by a teammate | Add their account to the Google test-user list; do not publish the app merely for a demo. |
| No dedicated Figma frame | Reuse the application-detail interview card and scheduling modal; obtain design review before visual expansion. |

## Required external authority before Task 2

The project owner must provide, through local environment configuration only:

- Google Cloud OAuth client ID and client secret
- Approved HTTPS callback URL for the deployed demo, plus the local callback
  URL used during development
- The two Google test-user accounts
- A randomly generated token-encryption key
