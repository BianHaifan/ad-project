# Recruiter API v1 Draft

Base path: `/api/v1`. JSON uses `camelCase`, IDs are opaque, times are ISO-8601 UTC, lists use the existing `{data, meta}` envelope, and errors use the existing `{error:{code,message,fieldErrors,requestId}}` envelope. All protected endpoints require `Authorization: Bearer` and role `RECRUITER` unless noted.

## Auth and company

### `POST /auth/register`

Public. Request: `{"role":"RECRUITER","companyName":"Moonshot AI","fullName":"Mia Chen","email":"mia@moonshot.ai","password":"...","acceptedTermsVersion":"2026-08-01"}`. Response `201`: existing `AuthResponse`, with `user.role=RECRUITER`, plus `company:{id,name,verificationStatus}`. Errors: `EMAIL_ALREADY_REGISTERED`, `WEAK_PASSWORD`, `TERMS_NOT_ACCEPTED`, `COMPANY_NAME_REQUIRED`.

### `POST /auth/login`, `/auth/refresh`, `/auth/logout`

Reuse Candidate contracts. Login is public and returns the role in `AuthUser`; refresh is public with a valid refresh token; logout requires an authenticated session. Errors: `INVALID_CREDENTIALS`, `TOKEN_EXPIRED`, `TOKEN_REVOKED`.

### `GET /recruiter/me`

Response: `{"data":{"id":"rec_001","role":"RECRUITER","fullName":"Mia Chen","email":"mia@moonshot.ai","company":{"id":"company_001","name":"Moonshot AI","verificationStatus":"APPROVED"}}}`. Permission: recruiter self.

### `GET|PATCH /recruiter/company`

PATCH request may contain `name`, `website`, `description`, `logoAssetId`, `location`. Response returns the complete company. Errors: `COMPANY_NOT_FOUND`, `COMPANY_UPDATE_FORBIDDEN`, `VALIDATION_ERROR`. Permission: recruiter company member; updates may require company-admin capability.

## Dashboard

### `GET /recruiter/dashboard`

Query: optional `from`, `to` ISO dates. Response: `{"data":{"metrics":{"openRoles":12,"newMatches":248,"pendingResumes":73,"interviews":18,"verificationStatus":"APPROVED"},"recommendedApplications":[ApplicationSummary],"recentJobs":[RecruiterJobSummary]}}`. Permission: aggregates are limited to the recruiter company.

## Jobs

### `GET /recruiter/jobs`

Query: `q`, `status`, `employmentType`, `location`, `ownerId`, `page`, `pageSize`. Response: paged `RecruiterJobSummary[]` with `id,title,employmentType,workplaceType,location,status,publishedAt,applicantCount,owner`. Permission: own company only.

### `POST /recruiter/jobs`

Request: `{"title":"AI Backend Engineer","employmentType":"FULL_TIME","workplaceType":"HYBRID","location":"Shanghai","salary":{"min":25000,"max":40000,"currency":"CNY","period":"MONTH"},"description":"...","requirements":["..."],"skills":["Python"],"deadline":"2026-09-30T15:59:59Z","visibility":"PUBLIC"}`. Response `201`: complete job with `status:"DRAFT"`. Errors: `COMPANY_NOT_VERIFIED`, `INVALID_SALARY_RANGE`, `VALIDATION_ERROR`.

### `GET|PATCH /recruiter/jobs/{jobId}`

Path: `jobId`. PATCH accepts the fields from create and returns the complete job. Errors: `JOB_NOT_FOUND`, `JOB_OWNERSHIP_REQUIRED`, `JOB_EDIT_CONFLICT`, `VALIDATION_ERROR`.

### `POST /recruiter/jobs/{jobId}/publish`

Request: optional `{"expectedVersion":3}`. Response: job with `status:"ACTIVE"` and `publishedAt`. Errors: `JOB_NOT_PUBLISHABLE`, `COMPANY_NOT_VERIFIED`, `JOB_VERSION_CONFLICT`.

### `POST /recruiter/jobs/{jobId}/status`

Request: `{"status":"PAUSED","reason":"Reviewing job description","expectedVersion":4}`. Response: updated job. Allowed values: `ACTIVE`, `PAUSED`, `CLOSED`; `DRAFT` is created/edited, not a backward transition from active. Errors: `INVALID_JOB_TRANSITION`, `JOB_NOT_FOUND`, `JOB_VERSION_CONFLICT`.

Job enums: `JobStatus=DRAFT|ACTIVE|PAUSED|CLOSED`; existing `EmploymentType=FULL_TIME|INTERNSHIP|PART_TIME`; existing `WorkplaceType=ONSITE|HYBRID|REMOTE`; `Visibility=PUBLIC|PRIVATE`.

## Applications and resume snapshots

### `GET /recruiter/applications`

Query: `status`, `jobId`, `q`, `ownerId`, `minMatchScore`, `page`, `pageSize`, `sort`. Response: `{"data":{"counts":{"applied":18,"inReview":9,"interview":6,"rejected":12},"items":[ApplicationSummary]},"meta":PageMeta}`. `ApplicationSummary` reuses Candidate identifiers/status and adds candidate summary, owner and match score. Permission: applications to own-company jobs.

### `GET /recruiter/applications/{applicationId}`

Response: complete application, candidate summary, immutable `resumeSnapshot`, timeline/audit entries, `matchAnalysis:{score,evidence,modelVersion,generatedAt}`, interview and recruiter-only notes. Errors: `APPLICATION_NOT_FOUND`, `APPLICATION_OWNERSHIP_REQUIRED`.

### `POST /recruiter/applications/{applicationId}/transitions`

Request: `{"toStatus":"IN_REVIEW","reason":"Meets required backend experience","expectedVersion":2}`. Response `201`: `{"data":{"application":ApplicationDetail,"event":{"fromStatus":"APPLIED","toStatus":"IN_REVIEW","actorId":"rec_001","occurredAt":"..."}}}`. Errors: `INVALID_APPLICATION_TRANSITION`, `APPLICATION_VERSION_CONFLICT`, `APPLICATION_NOT_FOUND`. Allowed status enum is shared: `APPLIED|IN_REVIEW|INTERVIEW|REJECTED|WITHDRAWN`. Recruiter cannot set `WITHDRAWN`; Candidate cannot set recruiter decision states.

### `PUT /recruiter/applications/{applicationId}/owner`

Request: `{"ownerId":"rec_001"}` or `{"ownerId":null}`. Response: updated application. Errors: `OWNER_NOT_IN_COMPANY`, `APPLICATION_NOT_FOUND`.

### `GET|POST /recruiter/applications/{applicationId}/notes`

GET returns recruiter-only notes. POST request: `{"body":"Strong backend projects..."}`; response `201` includes `id,author,body,createdAt`. Errors: `NOTE_EMPTY`, `APPLICATION_NOT_FOUND`. Never include these notes in Candidate APIs.

### `GET /recruiter/applications/{applicationId}/resume-snapshot`

Response: the immutable snapshot using the existing Resume fields plus `snapshotId,capturedAt`. Optional `GET .../resume-snapshot/pdf` returns a short-lived download response or redirect. Errors: `RESUME_SNAPSHOT_NOT_FOUND`.

## Interviews

### `POST /recruiter/applications/{applicationId}/interviews`

Request: `{"scheduledAt":"2026-08-11T06:00:00Z","timezone":"Asia/Shanghai","durationMinutes":30,"mode":"ONLINE","locationOrMeetingUrl":"https://meet.example.com/...","note":"Technical interview"}`. Response `201`: `{"data":{"id":"int_001","applicationId":"app_001","status":"SCHEDULED",...}}`; application transitions to `INTERVIEW` in the same transaction and a system conversation event is created. Errors: `INVALID_APPLICATION_TRANSITION`, `INTERVIEW_TIME_IN_PAST`, `INTERVIEW_CONFLICT`, `MEETING_LOCATION_REQUIRED`.

### `PATCH /recruiter/interviews/{interviewId}`

Accepts schedule fields or `status`. Response: full interview. Enum: `InterviewStatus=SCHEDULED|COMPLETED|CANCELLED`; `InterviewMode=ONLINE|ONSITE|PHONE`. Errors: `INTERVIEW_NOT_FOUND`, `INVALID_INTERVIEW_TRANSITION`, `INTERVIEW_OWNERSHIP_REQUIRED`.

## Conversations and messages

### `GET /recruiter/conversations`

Query: `q`, `unreadOnly`, `page`, `pageSize`. Response reuses `ConversationSummary` with the participant representing Candidate and adds `applicationId`, `jobId`, `jobTitle`. Permission: own-company job conversations.

### `GET /recruiter/conversations/{conversationId}`

Response reuses `ConversationDetail` and `InterviewContext`. Errors: `CONVERSATION_NOT_FOUND`, `CONVERSATION_OWNERSHIP_REQUIRED`.

### `GET|POST /recruiter/conversations/{conversationId}/messages`

GET query: `before`, `limit`; response reuses cursor-paged `Message[]`. POST request reuses `{"body":"...","clientMessageId":"uuid"}` and returns `201 Message` with `senderType:"RECRUITER"`. Errors: `EMPTY_MESSAGE`, `DUPLICATE_CLIENT_MESSAGE_ID`, `CONVERSATION_CLOSED`.

### `PUT /recruiter/conversations/{conversationId}/read-state`

Request/response reuse Candidate read-state contract. Permission: current recruiter participant and company ownership.

## Common authorization and errors

All protected endpoints return `401 UNAUTHORIZED` for missing/invalid tokens, `403 ROLE_FORBIDDEN` for a non-Recruiter, and `404` (preferred to avoid resource enumeration) for a resource outside the recruiter company. Mutations also support `422 VALIDATION_ERROR`, `409` state/version conflicts, `429 RATE_LIMITED`, and `500 INTERNAL_ERROR`. Every job/application/interview mutation records actor, company, before/after state, timestamp and request ID.
