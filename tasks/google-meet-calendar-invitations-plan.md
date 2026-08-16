# Implementation Plan: Google Calendar Interview Invitations

## Goal

When a recruiter schedules a Google Meet interview, add the candidate's existing application contact email to the Google Calendar event. Google Calendar sends the initial invitation and later reschedule/cancellation updates. The in-app interview detail remains the fallback; no separate email service or Messages change is introduced.

## Scope and design

- Use only `applications.contact_email`, captured when the candidate applied. Do not add a recruiter-entered recipient field, database table, migration, or public API field.
- Applies only to `GOOGLE_MEET`; manual interviews, OAuth, Android UI, Messages, ML, Agent, and Admin stay untouched.
- Thread this server-side email through the internal `ProvisionRequest` and `CalendarEventSpec`.
- Calendar create serializes `attendees: [{"email": ...}]` and uses `sendUpdates=all`.
- Calendar reschedule/cancel also use `sendUpdates=all`; PATCH must never include attendees or conference data, preserving the existing recipient and Meet conference.
- Existing stable event ID, retry, failure, and no-sensitive-logging behaviour must remain intact.

## Tasks

### Task 1: Carry the application contact email to Calendar

**Acceptance criteria**

- [ ] `InterviewService` uses the selected application's `contactEmail`, never a browser-supplied address.
- [ ] Internal provisioning models carry that address to event creation, including retry paths.
- [ ] Manual interview DTOs and public OpenAPI remain unchanged.

**Likely files**

- `backend/src/main/java/com/adproject/application/application/InterviewService.java`
- `backend/src/main/java/com/adproject/integration/google/ProvisionRequest.java`
- `backend/src/main/java/com/adproject/integration/google/application/CalendarEventSpec.java`

### Task 2: Serialize attendee and enable Google notifications

**Acceptance criteria**

- [ ] Event-create payload contains exactly the server-sourced candidate attendee and query parameters include `conferenceDataVersion=1&sendUpdates=all`.
- [ ] Reschedule/cancel use `sendUpdates=all`, without serializing `attendees`, `conferenceData`, or a second conference request.
- [ ] Provider errors preserve the existing retryable, non-leaking failure semantics.

**Likely files**

- `backend/src/main/java/com/adproject/integration/google/application/HttpGoogleCalendarClient.java`
- `backend/src/test/java/com/adproject/integration/google/application/HttpGoogleCalendarClientTest.java`

### Task 3: Regression and live verification

**Acceptance criteria**

- [ ] Focused tests cover attendee payload, notification query parameters, retry, reschedule, cancellation, and manual regression.
- [ ] Full backend tests pass.
- [ ] A two-account demo confirms the candidate receives the Calendar invitation and updates, while Android shows the same Meet link.
- [ ] `change_report/` documents scope/tests/limitations without a real email, token, or secret.

## Risks

| Risk | Mitigation |
|---|---|
| A mail is not delivered | In-app detail remains available; delivery is Google's responsibility. |
| A patch clears attendees | PATCH only schedule fields and test its payload. |
| Retry duplicates invitations | Reuse the current stable Calendar event ID and conflict recovery. |
| Personal data leaks | Never log Calendar bodies/responses; use dummy test addresses only. |

## Completion definition

Focused plus full backend tests pass, and a real Google test-user recruiter plus candidate mailbox verify create, reschedule, and cancel notifications.
