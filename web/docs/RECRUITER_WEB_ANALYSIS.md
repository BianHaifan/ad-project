# Recruiter Web Analysis

## 1. Figma scope and route map

| Figma frame | Node | Route | Primary transitions |
|---|---:|---|---|
| Sign in | `2093:2` | `/recruiter/sign-in` | success → dashboard; create account |
| Create Account | `2094:2` | `/recruiter/create-account` | success → dashboard; sign in |
| Dashboard | `1:346` | `/recruiter/dashboard` | jobs, application detail, create job |
| Open Roles | `2106:2` | `/recruiter/jobs` | create/edit job; application filter |
| Create Job | `2014:2` | `/recruiter/jobs/new`, `/recruiter/jobs/:jobId/edit` | save/publish → jobs |
| Applications | `2038:2` | `/recruiter/applications` | stage filter; application detail |
| Messages | `2111:2` | `/recruiter/messages/:conversationId?` | application detail; conversation selection |
| Application Details | `2044:2` | `/recruiter/applications/:applicationId` | message; resume review; status/interview actions |
| Resume Reviews | `1:424` | `/recruiter/applications/:applicationId/review` | decision actions; back to detail |
| New Applications | `2156:2` | `/recruiter/applications?stage=APPLIED` | application detail |
| In review | `2156:218` | `/recruiter/applications?stage=IN_REVIEW` | application detail |
| Interview | `2156:434` | `/recruiter/applications?stage=INTERVIEW` | application detail |
| Reject | `2156:650` | `/recruiter/applications?stage=REJECTED` | application detail |

The four stage frames are one resource-oriented page with a query filter, not four independent implementations.

## 2. Dynamic data, forms, and operations

- Auth: recruiter identity, company, tokens; fields are company name, work email, password, remember-me. Operations: register, sign in/out.
- Dashboard: open role, match, resume, interview, verification metrics; candidate recommendations and recent job postings.
- Jobs: job status, employment/workplace type, location, salary, published date, applicant count, owner. Fields: title, types, location, structured salary, description, requirements, skills, company, deadline and visibility. Operations: search/filter/page, create/edit, save draft, publish, pause/resume/close.
- Applications: candidate identity/contact, role, match score, applied time, status, owner, resume snapshot, match evidence, timeline and private notes. Operations: search/filter/page, assign owner, change stage, reject, open/download resume and add note.
- Interview: scheduled time, timezone, duration, mode, location/link and status. Operation: create/update/cancel invitation.
- Messages: conversations, participants, application/job context, messages, sender, timestamps, delivery/read state and unread count. Operations: select/search conversation, send message, mark read, open application.

## 3. Static copy versus business data

Static UI: navigation labels, headings, form labels, help copy, empty/error messages, button labels and ML disclaimer. API-owned business data: identities, companies, jobs, candidates, applications, counts, statuses, owners, notes, resume content, match scores/evidence/model metadata, interviews, conversations/messages, timestamps and pagination totals.

## 4. Candidate API impact

- Keep frozen `ApplicationStatus`: `APPLIED`, `IN_REVIEW`, `INTERVIEW`, `REJECTED`, `WITHDRAWN`; Figma “New” is a display label for `APPLIED`. `SCREENING` in the repository testing plan conflicts and should be corrected to `IN_REVIEW` in a separately reviewed documentation change.
- Add job lifecycle `DRAFT`, `ACTIVE`, `PAUSED`, `CLOSED`; Candidate public job endpoints must return only `ACTIVE` and accepting jobs.
- Reuse Candidate `Conversation`, `Message` and `SenderType`; Recruiter endpoints expose the same schema from the opposite participant perspective.
- Extend application detail with a reusable interview object, resume snapshot, audit history and recruiter-only notes. Do not expose private notes to Candidate.
- Auth must issue role claims and enforce company ownership. Recruiters may only manage jobs and applications belonging to their company.
- Match analysis is advisory and must include model version/generated time. It cannot authorize an application decision.
- No Android source changes are required for this Mock Web implementation.

## 5. Frontend structure

```text
src/
  api/            repository contract, selected implementation, Query hooks
  components/     shared shell, state and visual components
  features/       reserved for larger domain-specific components
  mocks/          all business fixtures and Mock Repository
  models/         shared business models and enums
  pages/          route-level views
  router/         route configuration
  theme/          design tokens and responsive CSS
docs/             analysis and API draft
```

## 6. Visual baseline

The inspected Figma frames use a 1440×1024 desktop baseline, Inter typography, `#f4f7f8`/`#f5f7f8` canvas, white cards, primary teal around `#00a6a8`, 32px horizontal page padding and 14–16px card radii. The implementation uses fluid grids with a 1024px minimum desktop width and a 1600px content cap.
