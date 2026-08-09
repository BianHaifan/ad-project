# Candidate Android + Recruiter Web 统一 API v1 契约

> 状态：**DRAFT**。基础路径：`/api/v1`。唯一接口标识：规范化的 **HTTP Method + Path**。OpenAPI 是后端实现与客户端生成的唯一机器可读契约。

## 汇总与计数口径

- 唯一接口：**45**
- Candidate 使用：**20**
- Recruiter 使用：**29**
- 共享接口：**4**

计数公式：`20 + 29 - 4 = 45`。

## 已冻结的统一规则

- JSON 只使用 camelCase；所有 ID 都是不可解析的 string。实体标识使用 userId/companyId/jobId/applicationId/conversationId/messageId/interviewId/resumeId/snapshotId。
- 时间为 ISO-8601 UTC date-time 且必须以 `Z` 结尾。普通响应使用 `data`；列表使用 `data + meta`；错误使用 `error.code/message/fieldErrors/requestId`。
- ApplicationStatus=`APPLIED|IN_REVIEW|INTERVIEW|REJECTED|WITHDRAWN`；NOT_APPLIED 仅属于 CandidateJobApplicationState；ACTIVE/INTERVIEW/ARCHIVED 仅属于 ApplicationListFilter。
- JobStatus=`DRAFT|ACTIVE|PAUSED|CLOSED`；InterviewStatus=`SCHEDULED|COMPLETED|CANCELLED`；InterviewMode=`ONLINE|ONSITE|PHONE`；SenderType=`CANDIDATE|RECRUITER|SYSTEM`。
- 对外招聘联系人使用 recruiter；公司内部负责人使用 owner。用户真实姓名只使用 fullName。

## 权限、隐私、并发、审计与幂等

- Candidate 仅能访问自己的 Profile、Resume、Applications 及本人参与的 Conversations。Recruiter 仅能访问所属公司的 Jobs、Applications、Interviews 和 Conversations；跨公司资源返回 404。
- RecruiterNote 不会出现在任何 Candidate 响应中。MatchAnalysis 只供辅助展示，不参与授权，也不能自动决定录用、拒绝或状态流转。
- 有并发风险的更新请求必须提供 expectedVersion（创建面试使用 expectedApplicationVersion）；服务端原子比较，成功后 version+1，不匹配返回 409 VERSION_CONFLICT。
- 职位发布/状态、申请状态/负责人、面试创建/更新必须记录 actorId、companyId、变更前后值、occurredAt、reason、requestId。
- 提交申请和发送消息必须提供 Idempotency-Key。相同 key+相同 payload 返回原结果且不重复写入；不同 payload 返回409 IDEMPOTENCY_KEY_REUSED。消息同时以会话内唯一 clientMessageId 去重；同一 Candidate+job 的新 key 重复投递返回 409 APPLICATION_ALREADY_EXISTS。

## 已消除的源文档冲突

- 公共 Auth 四个 Method+Path 只保留一份：注册请求按 role 区分 Candidate/Recruiter，AuthUser.role 统一支持两种角色。
- 用户/资源的旧通用 id 和用户 name 已替换为明确的 `*Id` 与 fullName；公司名称继续使用 Company.name。
- interviewAt/interviewMode 已替换为 scheduledAt/mode；SCREENING 已替换为 IN_REVIEW。
- Resume Snapshot 的旧 `{id,name}` 投影已删除，统一为完整 ResumeSnapshot + snapshotId/capturedAt。
- submittedAt 已删除，投递业务时间只使用 appliedAt；列表数据统一为 data 数组和 meta。
- logoAssetId 只作为公司 Logo 修改字段，Company 只返回 logoUrl；recruiter 与 owner 已拆分。
- 所有 ID schema 均为 string；并发更新统一使用 expectedVersion/version。

## 共享模型

| 模型 | 统一含义 |
| --- | --- |
| `User` | 统一账号身份：userId、role、fullName、email、时间戳。 |
| `AuthUser` | 已认证 User，并按角色携带 Company。 |
| `Company` | 使用 companyId，响应返回 logoUrl；logoAssetId 仅用于修改请求。 |
| `Job` | 统一职位、JobStatus、version 与时间戳。 |
| `CandidateJobSummary` | Candidate 投影；仅 ACTIVE 职位；recruiter 是对外联系人。 |
| `RecruiterJobSummary` | Recruiter 投影；owner 是公司内部负责人。 |
| `Application` | applicationId、jobId、ApplicationStatus、appliedAt、updatedAt 与 version。 |
| `CandidateApplicationDetail` | Candidate 安全详情；结构上排除 RecruiterNote。 |
| `RecruiterApplicationDetail` | 公司范围详情，包含快照、辅助分析、审计和私有备注。 |
| `Interview` | interviewId、scheduledAt、mode、status、version 与时间戳。 |
| `InterviewContext` | 会话投影，仅使用 scheduledAt 和 mode。 |
| `Conversation` | conversationId 及申请/职位归属上下文。 |
| `ConversationSummary` | 列表投影，包含对端参与者和最后消息。 |
| `ConversationDetail` | 详情投影，可包含 InterviewContext。 |
| `Message` | messageId、conversationId、SenderType 与 sentAt。 |
| `Resume` | 可修改简历，包含 resumeId、version 与时间戳。 |
| `ResumeSnapshot` | 不可变的投递时快照，使用 snapshotId/capturedAt。 |
| `MatchAnalysis` | 仅供辅助展示；绝不用于授权或自动录用/拒绝。 |
| `RecruiterNote` | Recruiter 私有备注；不会进入 Candidate 响应结构。 |
| `PageMeta` | 统一 page/pageSize/total/hasNext 分页元数据。 |
| `ErrorResponse` | 统一 error.code/message/fieldErrors/requestId。 |

## 接口契约表

### Auth

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | POST | `/auth/register` | `registerUser` | YES | YES | Public | body: RegisterRequest | 201 | 409, 422 |
| DRAFT | MVP | POST | `/auth/login` | `login` | YES | YES | Public | body: LoginRequest | 200 | 401 |
| DRAFT | MVP | POST | `/auth/refresh` | `refreshToken` | YES | YES | Public | body: RefreshTokenRequest | 200 | 401 |
| DRAFT | MVP | POST | `/auth/logout` | `logout` | YES | YES | Authenticated session | body: RefreshTokenRequest | 204 | 401, 403 |

### Profile

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/candidate/profile` | `getCandidateProfile` | YES | NO | Candidate self only | — | 200 | 401, 403 |
| DRAFT | MVP | PATCH | `/candidate/profile` | `updateCandidateProfile` | YES | NO | Candidate self only | body: UpdateProfileRequest | 200 | 422, 401, 403, 409 |
| DRAFT | MVP | GET | `/recruiter/me` | `getRecruiterMe` | NO | YES | Recruiter self | — | 200 | 401, 404, 403 |
| DRAFT | MVP | GET | `/recruiter/company` | `getRecruiterCompany` | NO | YES | Recruiter company member; current company scope only | — | 200 | 401, 404, 403 |
| DRAFT | MVP | PATCH | `/recruiter/company` | `updateRecruiterCompany` | NO | YES | Recruiter company admin capability; current company scope only | body: UpdateCompanyRequest | 200 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | GET | `/recruiter/dashboard` | `getRecruiterDashboard` | NO | YES | Recruiter; own-company aggregate | params: from/to | 200 | 401, 404, 403 |

### Jobs

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/jobs` | `listJobs` | YES | NO | Candidate role; returns ACTIVE visible jobs only | params: q/employmentType/category/Page/PageSize | 200 | 401, 403 |
| DRAFT | MVP | GET | `/jobs/{jobId}` | `getJob` | YES | NO | Candidate role; ACTIVE visible job only | params: JobId | 200 | 404, 401, 403 |
| DRAFT | MVP | GET | `/recruiter/jobs` | `listRecruiterJobs` | NO | YES | Recruiter; own company | params: q/status/employmentType/location/ownerId/Page/PageSize | 200 | 401, 404, 403 |
| DRAFT | MVP | POST | `/recruiter/jobs` | `createRecruiterJob` | NO | YES | Recruiter; verified own company | body: CreateJobRequest | 201 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | GET | `/recruiter/jobs/{jobId}` | `getRecruiterJob` | NO | YES | Recruiter; own-company job; cross-company resources return 404 | params: JobId | 200 | 401, 404, 403 |
| DRAFT | MVP | PATCH | `/recruiter/jobs/{jobId}` | `updateRecruiterJob` | NO | YES | Recruiter; own-company job + edit capability; cross-company resources return 404 | params: JobId; body: UpdateJobRequest | 200 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | POST | `/recruiter/jobs/{jobId}/publish` | `publishRecruiterJob` | NO | YES | Recruiter; own company; verified company; cross-company resources return 404 | params: JobId; body: PublishJobRequest | 200 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | POST | `/recruiter/jobs/{jobId}/status` | `changeRecruiterJobStatus` | NO | YES | Recruiter; own-company job; cross-company resources return 404 | params: JobId; body: ChangeJobStatusRequest | 200 | 401, 404, 403, 409, 422 |

### Applications

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
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

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | POST | `/recruiter/applications/{applicationId}/interviews` | `createRecruiterInterview` | NO | YES | Recruiter; own-company application; cross-company resources return 404 | params: applicationId; body: CreateInterviewRequest | 201 | 401, 404, 403, 409, 422 |
| DRAFT | MVP | PATCH | `/recruiter/interviews/{interviewId}` | `updateRecruiterInterview` | NO | YES | Recruiter; own-company interview; cross-company resources return 404 | params: interviewId; body: UpdateInterviewRequest | 200 | 401, 404, 403, 409, 422 |

### Conversations

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/candidate/conversations` | `listConversations` | YES | NO | Candidate conversation participant only | params: Page/PageSize | 200 | 401, 403 |
| DRAFT | MVP | GET | `/candidate/conversations/{conversationId}` | `getConversation` | YES | NO | Candidate conversation participant only | params: ConversationId | 200 | 404, 401, 403 |
| DRAFT | MVP | PUT | `/candidate/conversations/{conversationId}/read-state` | `updateConversationReadState` | YES | NO | Candidate conversation participant only | params: ConversationId; body: ReadStateRequest | 204 | 404, 401, 403, 409, 422 |
| DRAFT | MVP | GET | `/recruiter/conversations` | `listRecruiterConversations` | NO | YES | Recruiter participant; own-company jobs | params: q/unreadOnly/Page/PageSize | 200 | 401, 404, 403 |
| DRAFT | MVP | GET | `/recruiter/conversations/{conversationId}` | `getRecruiterConversation` | NO | YES | Recruiter participant; own-company conversation; cross-company resources return 404 | params: conversationId | 200 | 401, 404, 403 |
| DRAFT | MVP | PUT | `/recruiter/conversations/{conversationId}/read-state` | `updateRecruiterConversationReadState` | NO | YES | Recruiter participant; own-company conversation; cross-company resources return 404 | params: conversationId; body: ReadStateRequest | 204 | 401, 403, 409, 422, 404 |

### Messages

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/candidate/conversations/{conversationId}/messages` | `listMessages` | YES | NO | Candidate conversation participant only | params: ConversationId/before/limit | 200 | 404, 401, 403 |
| DRAFT | MVP | POST | `/candidate/conversations/{conversationId}/messages` | `sendMessage` | YES | NO | Candidate conversation participant only | params: ConversationId/IdempotencyKey; body: SendMessageRequest | 201 | 404, 422, 401, 403, 409 |
| DRAFT | MVP | GET | `/recruiter/conversations/{conversationId}/messages` | `listRecruiterMessages` | NO | YES | Recruiter participant; own-company conversation; cross-company resources return 404 | params: conversationId/before/limit | 200 | 401, 404, 403 |
| DRAFT | MVP | POST | `/recruiter/conversations/{conversationId}/messages` | `sendRecruiterMessage` | NO | YES | Recruiter participant; own-company conversation; cross-company resources return 404 | params: conversationId/IdempotencyKey; body: SendMessageRequest | 201 | 401, 404, 403, 409, 422 |

### Resume

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/candidate/resume` | `getResume` | YES | NO | Candidate self only | — | 200 | 404, 401, 403 |
| DRAFT | MVP | PUT | `/candidate/resume` | `saveResume` | YES | NO | Candidate self only | body: SaveResumeRequest | 200 | 422, 401, 403, 409 |
| DRAFT | MVP | GET | `/recruiter/applications/{applicationId}/resume-snapshot` | `getRecruiterResumeSnapshot` | NO | YES | Recruiter; own-company application; cross-company resources return 404 | params: applicationId | 200 | 401, 404, 403 |
| DRAFT | P1_DEFERRED | GET | `/recruiter/applications/{applicationId}/resume-snapshot/pdf` | `getRecruiterResumeSnapshotPdf` | NO | YES | Recruiter; own-company application; cross-company resources return 404 | params: applicationId | 200, 302 | 401, 404, 403 |

### Features

| 状态 | MVP 范围 | Method | Path | operationId | Candidate | Recruiter | 权限 | 请求 | 成功 | 主要错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | MVP | GET | `/features/learning` | `getLearningFeature` | YES | NO | Candidate role | — | 200 | 401, 403 |

## 已冻结的 MVP 决策与延期范围

**现在冻结：**本地 Web/API 使用 `http://localhost:8080/api/v1`，Android 模拟器使用 `http://10.0.2.2:8080/api/v1`；Access Token 2 小时，Refresh Token 30 天并在刷新时轮换。Recruiter 注册即创建公司并成为唯一公司管理员；MVP owner 只能是当前 Recruiter 或 null。Salary 仅支持新加坡元，ISO 4217 代码为 `SGD`，min/max 使用整数主币单位。每位 Candidate 只有一份可修改 Resume，投递时生成不可变快照。Job/Application/Interview 转换矩阵以 OpenAPI enum description 为准。Application 不包含 OFFERED/HIRED；Candidate 通过 withdraw 接口进入 WITHDRAWN。

**延期到 MVP 之后：**邮箱验证、忘记密码、生产域名、加入已有公司/邀请成员/复杂权限、多份 Resume、PDF快照下载实现、WebSocket/推送和消息保留策略、Offer/Hire 资源、多币种、MatchAnalysis 刷新策略和高级降级文案、Logo 上传/资产 API。延期项不阻塞 MVP；PDF 接口明确标记为 `P1_DEFERRED`。
