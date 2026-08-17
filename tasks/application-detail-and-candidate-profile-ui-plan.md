# Implementation Plan: Application Detail and Candidate Profile UI

## Goal

Make the recruiter application-detail page explain the real hiring flow at a glance, expose a reliable shortcut to the candidate conversation, and turn the Android "Me" page into a polished job-seeker profile hub. This is a presentation and navigation improvement; it must not change the existing application state machine, interview semantics, authentication, database schema, or Google Meet flow.

## Product and scope decisions

- Use the existing visual system: recruiter desktop 1440 × 1024, candidate Android 390 × 844, teal accents, pale background, white rounded cards. The desired BOSS Zhipin reference means a dense, job-seeker-oriented information hierarchy, not a visual copy or a new social feature.
- Keep the existing application statuses exactly as implemented: `APPLIED`, `IN_REVIEW`, `INTERVIEW`, `REJECTED`, and `WITHDRAWN`. Do not invent an offer/hired stage: the backend does not currently support it.
- Present application progression separately from interview lifecycle. Application progression is Submitted → Review → Interview → Outcome; interview lifecycle is Scheduled / Completed / Cancelled.
- A recruiter chat shortcut must use an exact, company-scoped conversation lookup by `applicationId`. Do not scan only the first page of the conversation list and do not create a conversation as a side effect of opening the application page.
- Candidate profile editing remains truthful to currently supported data: name, headline and location. Avatar and email are display-only; resume editing remains a separate existing screen. Do not add fake avatar upload, verification, followers, salary, or completion data.
- No Flyway migration, no Admin/ML/Agent changes, no Google Meet/OAuth changes, no new dependency, no secret/configuration change.

## Design specification

### Recruiter application detail

1. **Header and candidate card**
   - Preserve back navigation, candidate identity, job title and current badge.
   - Add a prominent `Message candidate` action beside the candidate summary; while lookup is loading show a disabled loading label; if no conversation exists, show a neutral unavailable explanation rather than creating one.
2. **Hiring progress panel**
   - Replace the current event-only horizontal row with a labelled four-stage rail: Submitted, Review, Interview, Outcome.
   - Completed stages show timestamp; current stage is highlighted; future stages are muted; rejected and withdrawn visibly terminate the rail with their actual reason/time from audit events.
   - Keep the full audit history below it as `Activity history`, including recruiter reason, instead of pretending every audit event is a standard step.
3. **Action panel**
   - `APPLIED`: show `Start review` as the main action and `Reject` as a secondary destructive action.
   - `IN_REVIEW` without an interview: show `Schedule interview` as the main action and `Reject` as a secondary action.
   - `INTERVIEW`: show the interview card/lifecycle and only actions permitted by existing APIs (reschedule, complete, cancel interview; reject application). Do not add an unsupported hire/offer action.
   - `REJECTED` / `WITHDRAWN`: read-only terminal card that explains no further recruiter application transition is available.
   - A required transition reason appears only in an explicit confirm panel/dialog for Start review or Reject. Preserve `expectedVersion`, the current mutation error handling, disabled/submitting states and server-side permission checks.
4. **Interview card**
   - Promote mode, date/time in the displayed timezone, duration, meeting/link/location, Google Meet sync state and candidate-facing note into a structured summary.
   - Keep scheduling/rescheduling in a modal; action text must match the actual effect. Never expose a manual meeting-link field for newly created online interviews.

### Candidate Android profile

1. **Top identity card**: avatar or initials, name, headline, location, email, and a clearly labelled edit affordance.
2. **Career snapshot**: use only existing profile statistics (applications, interviews, chats and saved jobs), with zero-state labels that remain useful when data is zero.
3. **Job-seeker action cards**: `Online resume`, `My applications`, and `Edit profile`; retain sign-out as a visually separate secondary action.
4. **Resume preview**: if a resume exists, show headline, short summary and up to two experience entries, with `View / edit resume`; if none exists, show an honest prompt to create one.
5. **State quality**: polished loading, retryable error, content, editing and submitting states. Preserve the bottom navigation and accessible labels; do not put repository/network work in composables.

## Work packages

### Package 1 — Reliable recruiter conversation lookup (backend + contract)

**Description:** Extend the existing recruiter conversation list with an optional `applicationId` filter and use it only for the application-detail shortcut. This is read-only and retains the existing company ownership check.

**Likely files:**
- `backend/src/main/java/com/adproject/conversation/api/RecruiterConversationController.java`
- `backend/src/main/java/com/adproject/conversation/application/ConversationService.java`
- `backend/src/test/java/com/adproject/conversation/...`
- `docs/openapi-v1.yaml`
- `docs/API_COVERAGE.csv`

**Acceptance criteria:**
- Authenticated recruiter receives only the conversation belonging to a requested application in the recruiter's company.
- Cross-company/nonexistent application yields an empty list or existing not-found policy without revealing another company’s data.
- Omitting the filter preserves current conversation-list behaviour.

**Verification:** focused backend success/401/403-or-404/cross-company tests; OpenAPI matches controller; no migration.

### Package 2 — Recruiter application-detail flow and chat entry (web)

**Description:** Refactor the existing application detail UI into clear application progression, interview lifecycle and context-aware action cards. Consume Package 1 for the message shortcut.

**Likely files:**
- `web/src/pages/ApplicationDetailPage.tsx`
- `web/src/pages/ApplicationDetailPage.test.tsx` (new if absent)
- `web/src/api/contract.ts`
- `web/src/api/recruiterRepository.ts`
- `web/src/api/conversationHttpClient.ts`
- `web/src/api/queries.ts`
- `web/src/theme/global.css`

**Acceptance criteria:**
- Each of the five real application statuses renders an unambiguous progress and action state.
- Interview lifecycle is visibly separate from application progression.
- `Message candidate` opens `/recruiter/messages/{conversationId}` for the exact application conversation; no-conversation and loading/error states are handled.
- Start review/reject require reason and preserve existing optimistic-concurrency behaviour; scheduling remains the only path to `INTERVIEW`.

**Verification:** component tests for all status/action combinations, message link and unavailable state; `npm run typecheck`, `npm run lint`, `npm test`, `npm run build`; manual recruiter flow APPLIED → IN_REVIEW → schedule interview → message.

### Package 3 — Candidate profile hub (Android)

**Description:** Recompose the real profile screen using existing profile, stats and resume data; keep profile and resume writes on their existing endpoints.

**Likely files:**
- `android/app/src/main/java/com/adproject/candidate/feature/profile/RealProfileScreens.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/profile/ProfileViewModels.kt`
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`
- `android/app/src/test/java/com/adproject/candidate/...Profile...Test.kt`
- potentially existing design-system primitives, only if a reusable primitive is genuinely needed

**Acceptance criteria:**
- Identity, real statistics, resume preview/empty prompt, actions and logout hierarchy are visible without changing API data.
- Edit profile saves name/headline/location only; email/avatar clearly remain display-only.
- Resume preview handles loading, absent resume and failed resume request without hiding the profile itself.
- Existing profile, resume and bottom-navigation paths continue to work.

**Verification:** ViewModel/Compose tests for content, no-resume, profile error, resume error and submitting states; `./gradlew :app:testDebugUnitTest lintDebug assembleDebug`; manual emulator screenshot at 390 × 844.

## Checkpoints and order

1. Complete and review Package 1 before Package 2; the chat link otherwise cannot be correct on paginated data.
2. Package 3 is independent and may be implemented in parallel only after no shared Android navigation file is being changed elsewhere.
3. After Packages 1–2, manually verify the recruiter flow against the real local backend.
4. After Package 3, manually verify the candidate screen on the emulator and compare the hierarchy to the existing theme.
5. Each package must update its own file in `change_report/`; do not commit or push until review approval.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| UI suggests transitions unsupported by the backend | Derive every action from the current state machine; do not add offer/hire. |
| Chat shortcut selects a wrong candidate on long lists | Add a server-side, company-scoped `applicationId` filter instead of client-side page scanning. |
| Profile redesign introduces fake data | Display only profile/stats/resume fields already returned by APIs; use explicit empty states. |
| Interview and application statuses become conflated again | Keep two labelled components, two badge types and action groups. |
| Concurrent work touches navigation/shared styles | Keep Package 3 scoped to the profile feature first; coordinate before changing shared primitives. |

## Completion definition

- No state-machine, migration, role, ownership or Google integration behaviour changes.
- New/revised API contract and backend tests cover authentication, wrong role and wrong company where Package 1 is added.
- Web and Android state handling covers loading, empty, error, content and submitting/disabled states.
- Relevant tests/builds pass and both flows are manually verified against the local backend.
