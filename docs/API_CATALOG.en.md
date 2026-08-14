# Unified Candidate Android + Recruiter Web API v1 Contract

> Status: **DRAFT**. Base path: `/api/v1`. Unique operation identity: normalized **HTTP Method + Path**. OpenAPI is the sole machine-readable implementation and client-generation contract.

## Summary and counting

- Unique operations: **45**
- Candidate operations: **20**
- Recruiter operations: **29**
- Shared operations: **4**

Counting formula: `20 + 29 - 4 = 45`.

## Frozen unified rules

- JSON is camelCase only; every ID is an opaque string. Entity identifiers use userId/companyId/jobId/applicationId/conversationId/messageId/interviewId/resumeId/snapshotId.
- Times are ISO-8601 UTC date-time values ending in `Z`. Ordinary responses use `data`; lists use `data + meta`; errors use `error.code/message/fieldErrors/requestId`.
- ApplicationStatus=`APPLIED|IN_REVIEW|INTERVIEW|REJECTED|WITHDRAWN`; NOT_APPLIED belongs only to CandidateJobApplicationState; ACTIVE/INTERVIEW/ARCHIVED belong only to ApplicationListFilter.
- JobStatus=`DRAFT|ACTIVE|PAUSED|CLOSED`; InterviewStatus=`SCHEDULED|COMPLETED|CANCELLED`; InterviewMode=`ONLINE|ONSITE|PHONE`; SenderType=`CANDIDATE|RECRUITER|SYSTEM`.
- recruiter is the external recruiting contact; owner is the internal company assignee. A person's real name is fullName only.

## Authorization, privacy, concurrency, audit, and idempotency

- Candidate access is limited to their own Profile, Resume, Applications, and participated Conversations. Recruiter access is limited to their company's Jobs, Applications, Interviews, and Conversations; cross-company resources return 404.
- RecruiterNote never appears in a Candidate response. MatchAnalysis is advisory display data only and cannot authorize or automatically decide hire, rejection, or a status transition.
- Concurrent updates require expectedVersion (expectedApplicationVersion when creating an interview). The server compares atomically, increments version on success, and returns 409 VERSION_CONFLICT on mismatch.
- Job publish/status, application transition/owner, and interview create/update operations audit actorId, companyId, before/after values, occurredAt, reason, and requestId.
- Application submission and message sending require Idempotency-Key. Same key+payload returns the original result without a duplicate; a different payload returns 409 IDEMPOTENCY_KEY_REUSED. Messages also deduplicate clientMessageId per conversation; a new key for the same Candidate+job returns 409 APPLICATION_ALREADY_EXISTS.

## Resolved source-contract conflicts

- The four shared Auth Method+Paths now have one contract each: registration discriminates by role and AuthUser.role supports both clients.
- Generic resource/user id and user name fields were replaced by explicit `*Id` and fullName; Company.name remains the company name.
- interviewAt/interviewMode became scheduledAt/mode; SCREENING became IN_REVIEW.
- The legacy Resume Snapshot `{id,name}` projection was removed in favor of full ResumeSnapshot + snapshotId/capturedAt.
- submittedAt was removed; appliedAt is the sole application event time. List payloads are data arrays plus meta.
- logoAssetId is mutation-only and Company returns logoUrl; recruiter and owner are separate concepts.
- Every ID schema is string; concurrent mutations consistently use expectedVersion/version.

## Shared models

| Model | Canonical meaning |
| --- | --- |
| `User` | Canonical account identity: userId, role, fullName, email, timestamps. |
| `AuthUser` | Authenticated User plus role-dependent Company. |
| `Company` | companyId and returned logoUrl; logoAssetId exists only in update requests. |
| `Job` | Canonical job, JobStatus, version and timestamps. |
| `CandidateJobSummary` | Candidate projection; ACTIVE jobs only; recruiter is external contact. |
| `RecruiterJobSummary` | Recruiter projection; owner is an internal company assignee. |
| `Application` | applicationId, jobId, ApplicationStatus, appliedAt, updatedAt and version. |
| `CandidateApplicationDetail` | Candidate-safe detail; structurally excludes RecruiterNote. |
| `RecruiterApplicationDetail` | Company-scoped detail with snapshot, advisory analysis, audit and private notes. |
| `Interview` | interviewId, scheduledAt, mode, status, version and timestamps. |
| `InterviewContext` | Conversation projection using scheduledAt and mode only. |
| `Conversation` | conversationId with application/job ownership context. |
| `ConversationSummary` | List projection with opposite participant and last message. |
| `ConversationDetail` | Detail projection with optional InterviewContext. |
| `Message` | messageId, conversationId, SenderType and sentAt. |
| `Resume` | Mutable resume with resumeId, version and timestamps. |
| `ResumeSnapshot` | Immutable snapshotId/capturedAt application-time copy. |
| `MatchAnalysis` | Advisory display data; never an authorization or automatic decision input. |
| `RecruiterNote` | Recruiter-only private note; impossible in Candidate response graphs. |
| `PageMeta` | Shared page/pageSize/total/hasNext pagination metadata. |
| `ErrorResponse` | error.code/message/fieldErrors/requestId. |

## Operation contract table

### Auth

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | POST | `/auth/register` | `registerUser` | YES | YES | Public | body: RegisterRequest | 201 | 409, 422 |
| DRAFT | MVP | POST | `/auth/login` | `login` | YES | YES | Public | body: LoginRequest | 200 | 401 |
| DRAFT | MVP | POST | `/auth/refresh` | `refreshToken` | YES | YES | Public | body: RefreshTokenRequest | 200 | 401 |
| DRAFT | MVP | POST | `/auth/logout` | `logout` | YES | YES | Authenticated session | body: RefreshTokenRequest | 204 | 401, 403 |

### Profile

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/candidate/profile` | `getCandidateProfile` | YES | NO | Candidate self only | — | 200 | 401, 403 |
| DRAFT | MVP | PATCH | `/candidate/profile` | `updateCandidateProfile` | YES | NO | Candidate self only | body: UpdateProfileRequest | 200 | 422, 401, 403, 409 |
| DRAFT | MVP | GET | `/recruiter/me` | `getRecruiterMe` | NO | YES | Recruiter self | — | 200 | 401, 404, 403 |
| DRAFT | MVP | GET | `/recruiter/company` | `getRecruiterCompany` | NO | YES | Recruiter company member; current company scope only | — | 200 | 401, 404, 403 |
| DRAFT | MVP | PATCH | `/recruiter/company` | `updateRecruiterCompany` | NO | YES | Recruiter company admin capability; current company scope only | body: UpdateCompanyRequest | 200 | 401, 404, 403, 409, 422 |
| IMPLEMENTED | MVP | GET | `/recruiter/dashboard` | `getRecruiterDashboard` | NO | YES | Recruiter; own-company aggregate | — | 200 | 401, 404, 403 |

### Jobs

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/jobs` | `listJobs` | YES | NO | Candidate role; returns ACTIVE visible jobs only | params: q/employmentType/Page/PageSize | 200 | 401, 403 |
| DRAFT | MVP | GET | `/jobs/{jobId}` | `getJob` | YES | NO | Candidate role; ACTIVE visible job only | params: JobId | 200 | 404, 401, 403 |
| DRAFT | MVP | GET | `/recruiter/jobs` | `listRecruiterJobs` | NO | YES | Recruiter; own company | params: q/status/employmentType/location/ownerId/Page/PageSize | 200 | 401, 404, 403 |
| DRAFT | MVP | POST | `/recruiter/jobs` | `createRecruiterJob` | NO | YES | Recruiter; verified own company | body: CreateJobRequest | 201 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | GET | `/recruiter/jobs/{jobId}` | `getRecruiterJob` | NO | YES | Recruiter; own-company job; cross-company resources return 404 | params: JobId | 200 | 401, 404, 403 |
| DRAFT | MVP | PATCH | `/recruiter/jobs/{jobId}` | `updateRecruiterJob` | NO | YES | Recruiter; own-company job + edit capability; cross-company resources return 404 | params: JobId; body: UpdateJobRequest | 200 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | POST | `/recruiter/jobs/{jobId}/publish` | `publishRecruiterJob` | NO | YES | Recruiter; own company; verified company; cross-company resources return 404 | params: JobId; body: PublishJobRequest | 200 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | POST | `/recruiter/jobs/{jobId}/status` | `changeRecruiterJobStatus` | NO | YES | Recruiter; own-company job; cross-company resources return 404 | params: JobId; body: ChangeJobStatusRequest | 200 | 401, 404, 403, 409, 422 |

### Applications

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | POST | `/jobs/{jobId}/applications` | `submitApplication` | YES | NO | Candidate role; own Resume; target job must be ACTIVE and accepting applications | params: JobId/IdempotencyKey; body: SubmitApplicationRequest | 201 | 404, 409, 422, 401, 403 |
| DRAFT | MVP | GET | `/candidate/applications` | `listApplications` | YES | NO | Candidate self only | params: filter/Page/PageSize | 200 | 401, 403 |
| DRAFT | MVP | GET | `/candidate/applications/{applicationId}` | `getApplication` | YES | NO | Candidate application owner only | params: applicationId | 200 | 404, 401, 403 |
| DRAFT | MVP | GET | `/recruiter/applications` | `listRecruiterApplications` | NO | YES | Recruiter; applications to own-company jobs | params: status/jobId/q/ownerId/minMatchScore/Page/PageSize/sort | 200 | 401, 404, 403 |
| DRAFT | MVP | GET | `/recruiter/applications/{applicationId}` | `getRecruiterApplication` | NO | YES | Recruiter; own-company application; cross-company resources return 404 | params: applicationId | 200 | 401, 404, 403 |
| DRAFT | MVP | POST | `/recruiter/applications/{applicationId}/transitions` | `transitionRecruiterApplication` | NO | YES | Recruiter; own-company application; cannot set WITHDRAWN; cross-company resources return 404 | params: applicationId; body: ApplicationTransitionRequest | 201 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | PUT | `/recruiter/applications/{applicationId}/owner` | `assignRecruiterApplicationOwner` | NO | YES | Recruiter; own-company owner assignment; cross-company resources return 404 | params: applicationId; body: ApplicationOwnerRequest | 200 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | GET | `/recruiter/applications/{applicationId}/notes` | `listRecruiterApplicationNotes` | NO | YES | Recruiter; own-company; private notes; cross-company resources return 404 | params: applicationId/Page/PageSize | 200 | 401, 404, 403 |
| DRAFT | MVP | POST | `/recruiter/applications/{applicationId}/notes` | `createRecruiterApplicationNote` | NO | YES | Recruiter; own-company; private notes; cross-company resources return 404 | params: applicationId; body: CreateNoteRequest | 201 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | POST | `/candidate/applications/{applicationId}/withdraw` | `withdrawCandidateApplication` | YES | NO | Candidate application owner only | params: applicationId; body: WithdrawApplicationRequest | 200 | 401, 403, 404, 409, 422 |

### Interviews

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | POST | `/recruiter/applications/{applicationId}/interviews` | `createRecruiterInterview` | NO | YES | Recruiter; own-company application; cross-company resources return 404 | params: applicationId; body: CreateInterviewRequest | 201 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | PATCH | `/recruiter/interviews/{interviewId}` | `updateRecruiterInterview` | NO | YES | Recruiter; own-company interview; cross-company resources return 404 | params: interviewId; body: UpdateInterviewRequest | 200 | 401, 404, 403, 409, 422 |

### Conversations

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| IMPLEMENTED | MVP | GET | `/candidate/conversations` | `listConversations` | YES | NO | Candidate conversation participant only | params: Page/PageSize | 200 | 401, 403 |
| IMPLEMENTED | MVP | GET | `/candidate/conversations/{conversationId}` | `getConversation` | YES | NO | Candidate conversation participant only | params: ConversationId | 200 | 404, 401, 403 |
| IMPLEMENTED | MVP | PUT | `/candidate/conversations/{conversationId}/read-state` | `updateConversationReadState` | YES | NO | Candidate conversation participant only | params: ConversationId; body: ReadStateRequest | 204 | 404, 401, 403, 409, 422 |
| IMPLEMENTED | MVP | GET | `/recruiter/conversations` | `listRecruiterConversations` | NO | YES | Recruiter participant; own-company jobs | params: q/unreadOnly/Page/PageSize | 200 | 401, 404, 403 |
| IMPLEMENTED | MVP | GET | `/recruiter/conversations/{conversationId}` | `getRecruiterConversation` | NO | YES | Recruiter participant; own-company conversation; cross-company resources return 404 | params: conversationId | 200 | 401, 404, 403 |
| IMPLEMENTED | MVP | PUT | `/recruiter/conversations/{conversationId}/read-state` | `updateRecruiterConversationReadState` | NO | YES | Recruiter participant; own-company conversation; cross-company resources return 404 | params: conversationId; body: ReadStateRequest | 204 | 401, 403, 409, 422, 404 |

### Messages

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| IMPLEMENTED | MVP | GET | `/candidate/conversations/{conversationId}/messages` | `listMessages` | YES | NO | Candidate conversation participant only | params: ConversationId/before/limit | 200 | 404, 401, 403 |
| IMPLEMENTED | MVP | POST | `/candidate/conversations/{conversationId}/messages` | `sendMessage` | YES | NO | Candidate conversation participant only | params: ConversationId/IdempotencyKey; body: SendMessageRequest | 201 | 404, 422, 401, 403, 409 |
| IMPLEMENTED | MVP | GET | `/recruiter/conversations/{conversationId}/messages` | `listRecruiterMessages` | NO | YES | Recruiter participant; own-company conversation; cross-company resources return 404 | params: conversationId/before/limit | 200 | 401, 404, 403 |
| IMPLEMENTED | MVP | POST | `/recruiter/conversations/{conversationId}/messages` | `sendRecruiterMessage` | NO | YES | Recruiter participant; own-company conversation; cross-company resources return 404 | params: conversationId/IdempotencyKey; body: SendMessageRequest | 201 | 401, 404, 403, 409, 422 |

### Resume

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/candidate/resume` | `getResume` | YES | NO | Candidate self only | — | 200 | 404, 401, 403 |
| DRAFT | MVP | PUT | `/candidate/resume` | `saveResume` | YES | NO | Candidate self only | body: SaveResumeRequest | 200 | 422, 401, 403, 409 |
| DRAFT | MVP | GET | `/recruiter/applications/{applicationId}/resume-snapshot` | `getRecruiterResumeSnapshot` | NO | YES | Recruiter; own-company application; cross-company resources return 404 | params: applicationId | 200 | 401, 404, 403 |
| DRAFT | P1_DEFERRED | GET | `/recruiter/applications/{applicationId}/resume-snapshot/pdf` | `getRecruiterResumeSnapshotPdf` | NO | YES | Recruiter; own-company application; cross-company resources return 404 | params: applicationId | 200, 302 | 401, 404, 403 |

### Features

| Status | MVP scope | Method | Path | operationId | Candidate | Recruiter | Permission | Request | Success | Main errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/features/learning` | `getLearningFeature` | YES | NO | Candidate role | — | 200 | 401, 403 |

## Frozen MVP decisions and deferred scope

**Frozen now:** local Web/API uses `http://localhost:8080/api/v1`; Android Emulator uses `http://10.0.2.2:8080/api/v1`. Access tokens last 2 hours; refresh tokens last 30 days and rotate on refresh. Recruiter registration creates a company and makes that Recruiter its only admin; MVP owner is the current Recruiter or null. Salary supports Singapore dollars only: ISO 4217 `SGD`, with integer major-currency min/max. Each Candidate has one mutable Resume; submission captures an immutable snapshot. Job/Application/Interview transition matrices are frozen in OpenAPI enum descriptions. Application has no OFFERED/HIRED; Candidate withdrawal uses the withdraw operation.

**Deferred until after MVP:** email verification, password reset, production domain, existing-company membership/invites/complex permissions, multiple Resumes, PDF snapshot download implementation, WebSocket/push and retention policy, Offer/Hire resources, multiple currencies, advanced MatchAnalysis refresh/degradation policy, and Logo upload/Asset APIs. Deferred work does not block MVP; the PDF operation is explicitly `P1_DEFERRED`.
