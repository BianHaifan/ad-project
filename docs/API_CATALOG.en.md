# Unified API Catalog v1

> Status: **DRAFT**. Unique key: normalized **HTTP Method + Path**. Base path: `/api/v1`. This document does not authorize frontend or backend code changes.

## Summary

| Metric | Count |
| --- | --- |
| Unique operations | 44 |
| Candidate operations | 19 |
| Recruiter operations | 29 |
| Shared operations | 4 |
| Exact-compatible shared duplicates | 2 |
| Shared operations with contract conflicts | 2 |

Counting formula: `19 + 29 - 4 = 44`. “Candidate/Recruiter operations” include shared operations; “Unique operations” deduplicates by Method + Path.

## Sources and limitations

- Candidate: `API_V1.md` and `openapi-v1.yaml`; 19 operations agree by Method + Path.
- Recruiter: `RECRUITER_API_DRAFT.md` and `RECRUITER_WEB_ANALYSIS.md`; 29 operations were derived from the API draft and checked against the route/impact analysis.
- The requested source `/Users/yezhian/code/adproject/web/docs/RECRUITER_WEB_ANALYSIS.yaml` was not present. No Recruiter YAML claims are made; the merged OpenAPI formalizes the two Recruiter Markdown sources.
- Query strings in Markdown examples are normalized out of Path; for example `GET /jobs?q=...` is keyed as `GET /jobs`.

## Duplicate, shared, and conflicting operations

| Method + Path | Classification | Request comparison | Response comparison | Resolution |
| --- | --- | --- | --- | --- |
| POST /auth/register | Shared + conflict | Candidate requires role=CANDIDATE; Recruiter requires role=RECRUITER and companyName | Recruiter response adds company; Candidate role enum excluded RECRUITER | RegisterRequest oneOf by role; AuthUser uses UserRole; company is Recruiter-only/nullable |
| POST /auth/login | Shared + response conflict | Same LoginRequest | Candidate AuthUser.role enum only allowed CANDIDATE | UserRole=CANDIDATE\|RECRUITER |
| POST /auth/refresh | Shared exact-compatible duplicate | Same RefreshTokenRequest | Same TokenResponse | Reuse one operation/contract |
| POST /auth/logout | Shared exact-compatible duplicate | Same RefreshTokenRequest | Same 204 response | Reuse one operation/contract |

No other cross-client duplicate exists under the required Method + Path identity rule. Candidate and Recruiter conversation/message operations are structurally shared but have different audience-prefixed paths, so they remain distinct catalog operations.

## Field, enum, and type conflicts

| Area | Source conflict | Unified decision |
| --- | --- | --- |
| AuthUser.role | Candidate YAML: CANDIDATE only; Recruiter draft: RECRUITER | UserRole = CANDIDATE \| RECRUITER |
| RegisterRequest | Recruiter adds companyName and a different role const | Discriminated oneOf CandidateRegisterRequest / RecruiterRegisterRequest |
| ApplicationStatus | Candidate YAML includes NOT_APPLIED; analysis freezes lifecycle without it | ApplicationStatus = APPLIED \| IN_REVIEW \| INTERVIEW \| REJECTED \| WITHDRAWN; NOT_APPLIED moved to CandidateJobApplicationState |
| Candidate application list status | ACTIVE \| INTERVIEW \| ARCHIVED are UI groupings, not lifecycle values | Keep as endpoint-specific display filter; do not reuse ApplicationStatus |
| Repository test plan | SCREENING appears outside the supplied API contract | Use IN_REVIEW; SCREENING is not in v1 |
| Job status | Candidate jobs had no lifecycle status; Recruiter requires DRAFT/ACTIVE/PAUSED/CLOSED | JobStatus added; Candidate public APIs expose only ACTIVE/accepting jobs |
| Interview date/name | Candidate context used interviewAt/interviewMode; Recruiter uses scheduledAt/mode | Canonical Interview uses scheduledAt and mode; InterviewContext is a projection |
| Resume snapshot ID | Candidate ApplicationDetail had resumeSnapshot.id/name; Recruiter requires full immutable Resume + snapshotId/capturedAt | ResumeSnapshot extends Resume with snapshotId/capturedAt; legacy id is Resume.id |
| Company logo | Candidate Company returns logoUrl; Recruiter company PATCH accepts logoAssetId | Treat logoAssetId as mutation input and logoUrl as resolved response URL; persistence mapping remains an implementation decision |
| Person display name | Embedded Candidate Recruiter uses name; Auth/Recruiter profile uses fullName | Use fullName for account/profile identity; keep name only as a legacy compact projection until clients migrate |
| Job responsibility | Candidate JobSummary calls the contact recruiter; Recruiter JobSummary calls workflow assignee owner | Keep recruiter and owner separate: external contact versus internal application/job owner |
| Application timestamps | Candidate exposes appliedAt and submittedAt without a stated distinction | appliedAt is the business event time; submittedAt is nullable transport/acceptance time until semantics are confirmed |
| Participant semantics | Candidate sees Recruiter; Recruiter sees Candidate; current Participant requires companyName | Same Participant schema, opposite-party semantics; companyName should be nullable for Candidate participant |
| Salary numeric type | Both drafts use whole-number examples and Candidate YAML uses integer | Keep integer minor/business units for v1; currency and period required |
| Read-state response | Candidate source is 204 while Recruiter says request/response reuse | Both use ReadStateRequest and 204 no content |

## Unified shared models

| Model | Canonical core | Audience-specific projection / privacy rule |
| --- | --- | --- |
| Job | id,title,company,employmentType,workplaceType,location,salary,description,requirements,skills,deadline,visibility,status,publishedAt,version | CandidateJob adds matchScore/recruiter/applicationState; RecruiterJob adds owner/applicantCount. Candidate only receives ACTIVE public jobs. |
| Application | id,jobId,status,appliedAt,submittedAt,updatedAt,version | Candidate view has company/timeline/nextSteps; Recruiter view adds candidate, owner, MatchAnalysis, ResumeSnapshot, audit, Interview and private notes. |
| Interview | id,applicationId,scheduledAt,timezone,durationMinutes,mode,locationOrMeetingUrl,note,status | Conversation InterviewContext adds jobId/jobTitle/type. Recruiter controls lifecycle; Candidate consumes context. |
| Conversation | id,participant,lastMessage/unreadCount or context; optional applicationId/jobId/jobTitle | participant means opposite party from current viewer; access always requires participant membership and, for Recruiter, company ownership. |
| Message | id,body,senderType,sentAt,clientMessageId,deliveryStatus | Same schema; senderType disambiguates CANDIDATE/RECRUITER/SYSTEM. clientMessageId supplies idempotency. |
| ResumeSnapshot | Resume + snapshotId + capturedAt | Immutable application-time copy; Candidate mutable Resume endpoints do not mutate historical snapshots. |

## Authorization policy

- Public: register, login, refresh (refresh token still required). Logout requires an authenticated session.
- Candidate resources: self/ownership or conversation participation.
- Recruiter resources: role RECRUITER plus company ownership; stronger capabilities apply to company update and selected job mutations. Cross-company resources should return 404 to reduce enumeration.
- Recruiter-only notes never appear in Candidate responses. MatchAnalysis is advisory and cannot authorize decisions. Mutations should record actor, company, before/after state, time, and requestId.

## Request and response comparison by operation

### Auth

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | POST | `/auth/register` | YES | YES | SHARED_CONFLICT | Public | oneOf CandidateRegisterRequest \| RecruiterRegisterRequest; discriminator=role | 201 AuthResponse; user.role=CANDIDATE | 201 AuthResponse; user.role=RECRUITER; company included; unified: 201 AuthResponse; role-aware user; company nullable/Recruiter-only |
| DRAFT | POST | `/auth/login` | YES | YES | SHARED_CONFLICT | Public | LoginRequest {email,password} | 200 AuthResponse; AuthUser.role only CANDIDATE in Candidate YAML | 200 AuthResponse; AuthUser.role=RECRUITER supported; unified: 200 AuthResponse; UserRole=CANDIDATE\|RECRUITER |
| DRAFT | POST | `/auth/refresh` | YES | YES | SHARED_COMPATIBLE | Public; valid refresh token | RefreshTokenRequest {refreshToken} | 200 TokenResponse | 200 TokenResponse (reused); unified: 200 TokenResponse |
| DRAFT | POST | `/auth/logout` | YES | YES | SHARED_COMPATIBLE | Authenticated session | RefreshTokenRequest {refreshToken} | 204 no content | 204 no content (reused); unified: 204 no content |

### Profile

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/candidate/profile` | YES | NO | CANDIDATE_ONLY | Candidate self | — | 200 CandidateProfile | 200 CandidateProfile |
| DRAFT | PATCH | `/candidate/profile` | YES | NO | CANDIDATE_ONLY | Candidate self | UpdateProfileRequest | 200 CandidateProfile | 200 CandidateProfile |
| DRAFT | GET | `/recruiter/me` | NO | YES | RECRUITER_ONLY | Recruiter self | — | — | 200 RecruiterProfile; unified: 200 RecruiterProfile |
| DRAFT | GET | `/recruiter/company` | NO | YES | RECRUITER_ONLY | Recruiter company member | — | — | 200 Company; unified: 200 Company |
| DRAFT | PATCH | `/recruiter/company` | NO | YES | RECRUITER_ONLY | Recruiter company admin capability | UpdateCompanyRequest | — | 200 Company; unified: 200 Company |
| DRAFT | GET | `/recruiter/dashboard` | NO | YES | RECRUITER_ONLY | Recruiter; own-company aggregate | from, to | — | 200 RecruiterDashboard; unified: 200 RecruiterDashboard |

### Jobs

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/jobs` | YES | NO | CANDIDATE_ONLY | Authenticated Candidate | q, employmentType, category, page, pageSize | 200 PageResponse<JobSummary> | 200 PageResponse<CandidateJobSummary> |
| DRAFT | GET | `/jobs/{jobId}` | YES | NO | CANDIDATE_ONLY | Authenticated Candidate; public ACTIVE job | jobId | 200 JobDetail | 200 CandidateJobDetail |
| DRAFT | GET | `/recruiter/jobs` | NO | YES | RECRUITER_ONLY | Recruiter; own company | q,status,employmentType,location,ownerId,page,pageSize | — | 200 PageResponse<RecruiterJobSummary>; unified: 200 PageResponse<RecruiterJobSummary> |
| DRAFT | POST | `/recruiter/jobs` | NO | YES | RECRUITER_ONLY | Recruiter; verified own company | CreateJobRequest | — | 201 RecruiterJobDetail(status=DRAFT); unified: 201 RecruiterJobDetail |
| DRAFT | GET | `/recruiter/jobs/{jobId}` | NO | YES | RECRUITER_ONLY | Recruiter; own-company job | jobId | — | 200 RecruiterJobDetail; unified: 200 RecruiterJobDetail |
| DRAFT | PATCH | `/recruiter/jobs/{jobId}` | NO | YES | RECRUITER_ONLY | Recruiter; own-company job + edit capability | UpdateJobRequest | — | 200 RecruiterJobDetail; unified: 200 RecruiterJobDetail |
| DRAFT | POST | `/recruiter/jobs/{jobId}/publish` | NO | YES | RECRUITER_ONLY | Recruiter; own company; verified company | PublishJobRequest | — | 200 RecruiterJobDetail(status=ACTIVE); unified: 200 RecruiterJobDetail |
| DRAFT | POST | `/recruiter/jobs/{jobId}/status` | NO | YES | RECRUITER_ONLY | Recruiter; own-company job | ChangeJobStatusRequest | — | 200 RecruiterJobDetail; unified: 200 RecruiterJobDetail |

### Applications

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | POST | `/jobs/{jobId}/applications` | YES | NO | CANDIDATE_ONLY | Candidate owner; Idempotency-Key required | SubmitApplicationRequest + Idempotency-Key | 201 ApplicationDetail | 201 CandidateApplicationDetail |
| DRAFT | GET | `/candidate/applications` | YES | NO | CANDIDATE_ONLY | Candidate self | status display filter, page, pageSize | 200 ApplicationListResponse | 200 CandidateApplicationListResponse |
| DRAFT | GET | `/candidate/applications/{applicationId}` | YES | NO | CANDIDATE_ONLY | Candidate application owner | applicationId | 200 ApplicationDetail | 200 CandidateApplicationDetail |
| DRAFT | GET | `/recruiter/applications` | NO | YES | RECRUITER_ONLY | Recruiter; applications to own-company jobs | status,jobId,q,ownerId,minMatchScore,page,pageSize,sort | — | 200 RecruiterApplicationListResponse; unified: 200 RecruiterApplicationListResponse |
| DRAFT | GET | `/recruiter/applications/{applicationId}` | NO | YES | RECRUITER_ONLY | Recruiter; own-company application | applicationId | — | 200 RecruiterApplicationDetail; unified: 200 RecruiterApplicationDetail |
| DRAFT | POST | `/recruiter/applications/{applicationId}/transitions` | NO | YES | RECRUITER_ONLY | Recruiter; own-company application; cannot set WITHDRAWN | ApplicationTransitionRequest | — | 201 ApplicationTransitionResult; unified: 201 ApplicationTransitionResult |
| DRAFT | PUT | `/recruiter/applications/{applicationId}/owner` | NO | YES | RECRUITER_ONLY | Recruiter; own-company owner assignment | ApplicationOwnerRequest | — | 200 RecruiterApplicationDetail; unified: 200 RecruiterApplicationDetail |
| DRAFT | GET | `/recruiter/applications/{applicationId}/notes` | NO | YES | RECRUITER_ONLY | Recruiter; own-company; private notes | applicationId | — | 200 RecruiterNote[]; unified: 200 RecruiterNote[] |
| DRAFT | POST | `/recruiter/applications/{applicationId}/notes` | NO | YES | RECRUITER_ONLY | Recruiter; own-company; private notes | CreateNoteRequest | — | 201 RecruiterNote; unified: 201 RecruiterNote |

### Interviews

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | POST | `/recruiter/applications/{applicationId}/interviews` | NO | YES | RECRUITER_ONLY | Recruiter; own-company application | CreateInterviewRequest | — | 201 Interview; application -> INTERVIEW atomically; unified: 201 Interview |
| DRAFT | PATCH | `/recruiter/interviews/{interviewId}` | NO | YES | RECRUITER_ONLY | Recruiter; own-company interview | UpdateInterviewRequest | — | 200 Interview; unified: 200 Interview |

### Conversations

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/candidate/conversations` | YES | NO | CANDIDATE_ONLY | Candidate participant | page, pageSize | 200 PageResponse<ConversationSummary> | 200 PageResponse<ConversationSummary> |
| DRAFT | GET | `/candidate/conversations/{conversationId}` | YES | NO | CANDIDATE_ONLY | Candidate participant | conversationId | 200 ConversationDetail | 200 ConversationDetail |
| DRAFT | PUT | `/candidate/conversations/{conversationId}/read-state` | YES | NO | CANDIDATE_ONLY | Candidate participant | ReadStateRequest | 204 no content | 204 no content |
| DRAFT | GET | `/recruiter/conversations` | NO | YES | RECRUITER_ONLY | Recruiter participant; own-company jobs | q,unreadOnly,page,pageSize | — | 200 PageResponse<ConversationSummary>; unified: 200 PageResponse<ConversationSummary> |
| DRAFT | GET | `/recruiter/conversations/{conversationId}` | NO | YES | RECRUITER_ONLY | Recruiter participant; own-company conversation | conversationId | — | 200 ConversationDetail; unified: 200 ConversationDetail |
| DRAFT | PUT | `/recruiter/conversations/{conversationId}/read-state` | NO | YES | RECRUITER_ONLY | Recruiter participant; own-company conversation | ReadStateRequest | — | 204 no content; unified: 204 no content |

### Messages

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/candidate/conversations/{conversationId}/messages` | YES | NO | CANDIDATE_ONLY | Candidate participant | before, limit | 200 MessageListResponse | 200 MessageListResponse |
| DRAFT | POST | `/candidate/conversations/{conversationId}/messages` | YES | NO | CANDIDATE_ONLY | Candidate participant | SendMessageRequest | 201 Message(senderType=CANDIDATE) | 201 Message |
| DRAFT | GET | `/recruiter/conversations/{conversationId}/messages` | NO | YES | RECRUITER_ONLY | Recruiter participant; own-company conversation | before,limit | — | 200 MessageListResponse; unified: 200 MessageListResponse |
| DRAFT | POST | `/recruiter/conversations/{conversationId}/messages` | NO | YES | RECRUITER_ONLY | Recruiter participant; own-company conversation | SendMessageRequest | — | 201 Message(senderType=RECRUITER); unified: 201 Message |

### Resume

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/candidate/resume` | YES | NO | CANDIDATE_ONLY | Candidate self | — | 200 Resume | 200 Resume |
| DRAFT | PUT | `/candidate/resume` | YES | NO | CANDIDATE_ONLY | Candidate self | SaveResumeRequest | 200 Resume | 200 Resume |
| DRAFT | GET | `/recruiter/applications/{applicationId}/resume-snapshot` | NO | YES | RECRUITER_ONLY | Recruiter; own-company application | applicationId | — | 200 ResumeSnapshot; unified: 200 ResumeSnapshot |
| DRAFT | GET | `/recruiter/applications/{applicationId}/resume-snapshot/pdf` | NO | YES | RECRUITER_ONLY | Recruiter; own-company application; short-lived URL | applicationId | — | 200 DownloadResponse or 302 redirect; unified: 200 DownloadResponse or 302 redirect |

### Features

| Status | Method | Path | Candidate | Recruiter | Sharing | Permission | Request | Candidate response | Recruiter response / unified response |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/features/learning` | YES | NO | CANDIDATE_ONLY | Authenticated Candidate | — | 200 LearningFeature | 200 LearningFeature |

## Open decisions before implementation

1. Confirm whether Company.logoAssetId should coexist with or replace Company.logoUrl in persisted contracts.
2. Confirm Participant.companyName nullability for Candidate participants in Recruiter conversations.
3. Confirm whether resume PDF uses a 200 DownloadResponse or 302 redirect; the merged spec documents 200 as the portable default.
4. Confirm salary integer unit semantics (major currency unit versus minor unit) before backend persistence.
5. Confirm version fields and optimistic-concurrency requirements on every mutation.
