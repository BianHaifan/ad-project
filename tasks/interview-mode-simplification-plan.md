# Implementation Plan: Simplify Interview Mode Selection

## Product decision

The recruiter first selects the interview mode:

- **Online** — always create a Google Meet and a Google Calendar invitation.
- **On-site** — require an interview location.
- **Phone** — require a phone number or calling instructions.

The new-schedule form must not offer a recruiter-supplied online meeting link
(Zoom, Teams, or custom URL). A recruiter without a usable Google connection
cannot schedule a new online interview and is directed to Integrations.

## Boundaries

- This removes only the **new online manual-link flow**. It does not remove the
  `MANUAL` database/API enum because it still represents on-site/phone details
  and protects historical interviews.
- No Flyway migration, Messages change, email provider, Android UI change, ML,
  Agent, or Admin work.
- Online Google Meet continues to use the existing OAuth, Calendar invitation,
  sync/retry, ownership, and candidate-display path.
- Existing historical `ONLINE + MANUAL` records remain readable. Do not rewrite
  stored records or destroy their existing links.

## Architecture decisions

- The backend is the authority: for **new** interviews it must reject
  `ONLINE + MANUAL` and any recruiter-supplied online URL, rather than relying
  only on a removed web control.
- The Web form derives provider from mode: `ONLINE` submits `GOOGLE_MEET` with
  no location field; `ONSITE` and `PHONE` submit the manual provider with the
  appropriate required detail field.
- `meetingProvider` may remain in the public DTO to preserve response/history
  compatibility, but its allowed value is constrained by `mode`. Update the
  OpenAPI text and API guide accordingly.
- Timezone remains automatically based on the recruiter's browser, but is
  presented as explanatory text instead of an input that appears editable.

## Task 1: Enforce the simplified mode/provider rules in the API

**Description:** Update new-interview validation so `ONLINE` requires
`GOOGLE_MEET`, requires no client location/link, and performs the existing
Google connection preflight. `ONSITE` and `PHONE` use manual details; they
cannot request Google Meet.

**Acceptance criteria:**

- [x] An authenticated, connected recruiter can create `ONLINE + GOOGLE_MEET`.
- [x] `ONLINE + MANUAL`, an omitted/wrong provider, or an online
  `locationOrMeetingUrl` is rejected with a clear validation error before any
  local application/interview mutation.
- [x] `ONSITE` requires a location and `PHONE` requires phone/calling details;
  both reject `GOOGLE_MEET`.
- [x] Existing interview reads remain compatible; no migration occurs.

**Likely files:**

- `backend/src/main/java/com/adproject/application/application/InterviewService.java`
- `backend/src/test/java/com/adproject/integration/RecruiterInterviewIntegrationTest.java`
- Google Meet integration tests affected by creation validation.

**Verification:** Focused MockMvc/integration tests cover success, unauthenticated,
Candidate role, other-company ownership, all invalid combinations, a disconnected
Google account, and manual on-site/phone regression.

## Task 2: Simplify the recruiter scheduling form

**Description:** Remove the Meeting Provider selector and online-link input
from the *new schedule* form. Put Mode first, render concise mode definitions,
then show only the relevant fields. Make browser timezone visibly descriptive.

**Acceptance criteria:**

- [x] Mode control appears first with labels: `Online — Google Meet`,
  `On-site — in-person location`, and `Phone — call details`.
- [x] Online shows a non-editable Google Meet explanation and a connection CTA
  when unavailable; it never renders a manual URL input or provider selector.
- [x] On-site shows `Interview location`; Phone shows `Phone number or calling
  instructions`; neither can choose Google Meet.
- [x] Timezone reads, for example, `Your browser timezone: Asia/Shanghai`, not
  as an apparently editable blank field.
- [x] Loading, disabled, validation-error, and submitting states remain clear.

**Likely files:**

- `web/src/pages/ApplicationDetailPage.tsx`
- `web/src/pages/ApplicationPages.test.tsx`
- Relevant CSS only if needed for layout.

**Verification:** Web unit tests assert mode-driven fields, payloads, connected
and disconnected online states, and on-site/phone submission. Run lint,
typecheck, tests, and build.

## Task 3: Update contract and regression documentation

**Description:** Align the OpenAPI descriptions, Chinese API guide, existing
Google Meet plan, and change report with the new rule. Do not claim that
external Calendar delivery is guaranteed.

**Acceptance criteria:**

- [x] OpenAPI documents that new online interviews require Google Meet and do
  not accept a custom link; manual details are for on-site/phone.
- [x] The Google Meet error copy no longer tells the recruiter to fall back to
  manual online scheduling.
- [x] `change_report/` clearly states API behaviour changed, database did not,
  and historical records are preserved.

## Checkpoint

Before handoff, verify this matrix against the real API:

| Mode | Google connected | Allowed result |
|---|---:|---|
| Online | Yes | Google Meet event and Calendar invitation |
| Online | No | Blocked with Integrations guidance |
| On-site | Either | Location-based manual interview |
| Phone | Either | Phone-details manual interview |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Existing API consumers send `ONLINE + MANUAL` | Document the breaking rule and return explicit validation error; current recruiter Web is updated in the same change. |
| Legacy manual online data breaks | Preserve enum and read/render compatibility; apply new rule only to creation. |
| Google configuration is absent at a demo | Online is intentionally blocked with a direct Integrations CTA; on-site/phone remain usable. |

## Completion definition

Backend and Web validations pass; a manual check proves each row in the matrix;
the calendar invitation package remains compatible; and the required change
report contains no credentials or personal data.
