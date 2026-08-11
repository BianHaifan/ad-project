# 后端 API v1 开发指南

> 面向 Spring Boot 后端开发人员的快速参考。本文用于提高阅读效率，**不是新的契约来源**。字段必填性、格式、响应码和 schema 细节发生分歧时，一律以 [`openapi-v1.yaml`](./openapi-v1.yaml) 为准。

## 1. 开发入口

- OpenAPI 唯一权威契约：[`openapi-v1.yaml`](./openapi-v1.yaml)
- 完整中文接口目录：[`API_CATALOG.zh-CN.md`](./API_CATALOG.zh-CN.md)
- 英文接口目录：[`API_CATALOG.en.md`](./API_CATALOG.en.md)
- 前端覆盖关系：[`API_COVERAGE.csv`](./API_COVERAGE.csv)
- API 基础路径：`/api/v1`
- 本地 Web 地址：`http://localhost:8080/api/v1`
- Android 模拟器访问宿主机：`http://10.0.2.2:8080/api/v1`

## 2. 全局规则

### 2.1 JSON、ID 与时间

- JSON 字段统一使用 `camelCase`。
- 所有对外 ID 均为不可解析的 `string`，不要向客户端暴露数据库自增语义。
- 使用明确 ID 名称：`userId`、`companyId`、`jobId`、`applicationId`、`conversationId`、`messageId`、`interviewId`、`resumeId`、`snapshotId`。
- 用户真实姓名统一使用 `fullName`；`Company.name` 只表示公司名称。
- 时间统一返回 ISO-8601 UTC 字符串，且必须以 `Z` 结尾，例如 `2026-08-09T01:42:00Z`。
- 数据库中也应统一按 UTC 保存，时区转换由客户端展示层完成。

### 2.2 认证

除 OpenAPI 明确标记为 Public 的注册、登录和刷新接口外，默认使用 JWT Bearer Token：

```http
Authorization: Bearer <accessToken>
```

- Access Token 有效期：`7200` 秒。
- Refresh Token 有效期：`2592000` 秒。
- Refresh Token 单次使用并轮换；刷新成功后旧 Token 必须失效。
- Candidate 与 Recruiter 的角色和资源所有权必须同时验证。
- 跨公司 Recruiter 访问资源时按契约返回 `404`，避免泄漏资源存在性。

### 2.3 成功响应 envelope

单对象响应：

```json
{
  "data": {
    "applicationId": "app_001"
  }
}
```

分页列表响应：

```json
{
  "data": [],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 0,
    "hasNext": false
  }
}
```

消息游标列表响应：

```json
{
  "data": [],
  "meta": {
    "nextCursor": null,
    "hasMore": false
  }
}
```

`204 No Content` 接口不返回 JSON body。

### 2.4 错误响应 envelope

所有错误统一返回：

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "fieldErrors": {
      "fullName": "must not be blank"
    },
    "requestId": "req_01J..."
  }
}
```

后端必须保证：

- `error.code`：稳定、可供客户端分支处理的业务错误码。
- `error.message`：可理解的错误摘要，不泄漏堆栈或数据库细节。
- `error.fieldErrors`：没有字段错误时返回空对象 `{}`，不要省略。
- `error.requestId`：与日志、审计记录中的请求 ID 一致。

常用 HTTP 状态：

| 状态码 | 用途 |
| --- | --- |
| `400` | 请求结构或协议错误 |
| `401` | 未认证、Token 无效或过期 |
| `403` | 已认证但角色或权限不允许 |
| `404` | 资源不存在，或为防止越权泄漏而隐藏资源 |
| `409` | 状态冲突、版本冲突、重复投递、幂等键冲突 |
| `422` | 字段或业务校验失败 |

### 2.5 乐观锁、幂等和审计

- 更新 Company、Job、Application、Interview、Resume 时按 OpenAPI 接收 `expectedVersion`。
- 创建面试时使用 `expectedApplicationVersion`。
- 服务端必须原子比较版本；不匹配返回 `409 VERSION_CONFLICT`。
- 成功修改后实体 `version + 1`。
- 提交申请和发送消息必须读取 `Idempotency-Key` 请求头。
- 相同幂等键和相同 payload 返回原结果，不重复写入。
- 相同幂等键但 payload 不同返回 `409 IDEMPOTENCY_KEY_REUSED`。
- 消息还需以会话内唯一 `clientMessageId` 去重。
- 职位发布/状态、申请状态/负责人、面试创建/更新必须记录审计事件，包括 `actorId`、`companyId`、前后状态、`occurredAt`、`reason` 和 `requestId`。

## 3. 冻结枚举与状态机

### 3.1 ApplicationStatus

```text
APPLIED | IN_REVIEW | INTERVIEW | REJECTED | WITHDRAWN
```

允许流转：

```text
APPLIED   -> IN_REVIEW | REJECTED | WITHDRAWN
IN_REVIEW -> INTERVIEW | REJECTED | WITHDRAWN
INTERVIEW -> REJECTED | WITHDRAWN
REJECTED  -> 终态
WITHDRAWN -> 终态
```

- Recruiter 只能通过 transition 接口设置 `IN_REVIEW`、`INTERVIEW`、`REJECTED`。
- Recruiter 不能设置 `WITHDRAWN`。
- Candidate 只能通过自己的 withdraw 接口进入 `WITHDRAWN`。
- `NOT_APPLIED` 不属于 `ApplicationStatus`，只属于职位详情中的 `CandidateJobApplicationState`。
- `ACTIVE`、`INTERVIEW`、`ARCHIVED` 只是 Candidate 申请列表筛选分组，不是申请状态。
- MVP 不包含 `OFFERED` 或 `HIRED`；未来应通过独立 Offer 资源扩展。

### 3.2 JobStatus

```text
DRAFT | ACTIVE | PAUSED | CLOSED
```

```text
DRAFT  -> ACTIVE
ACTIVE -> PAUSED | CLOSED
PAUSED -> ACTIVE | CLOSED
CLOSED -> 终态
```

Candidate 公共职位接口只返回可见的 `ACTIVE` 职位。

### 3.3 Interview

```text
InterviewStatus = SCHEDULED | COMPLETED | CANCELLED
InterviewMode   = ONLINE | ONSITE | PHONE
```

```text
SCHEDULED -> COMPLETED | CANCELLED
COMPLETED -> 终态
CANCELLED -> 终态
```

改期只修改 `scheduledAt`，状态仍保持 `SCHEDULED`。

### 3.4 Message

```text
SenderType     = CANDIDATE | RECRUITER | SYSTEM
DeliveryStatus = SENDING | SENT | DELIVERED | READ | FAILED
```

## 4. 核心模型速查

### User 与 AuthUser

`User` 是统一账号身份：

```text
userId, role, fullName, email, avatarUrl, createdAt, updatedAt
```

`AuthUser` 在 User 基础上携带 `company`：Recruiter 有 Company，Candidate 为 `null`。

### Company

```text
companyId, name, logoUrl, stage, employeeRange, verificationStatus,
website, description, location, version, createdAt, updatedAt
```

- 公司响应只返回 `logoUrl`。
- 公司修改请求使用 `logoAssetId`。
- MVP 尚未实现 Asset API，客户端应省略 `logoAssetId`，响应 `logoUrl` 可为 `null`。

### recruiter 与 owner

- `recruiter`：Candidate 看到的外部联系招聘者，类型为 `RecruiterContact`。
- `owner`：公司内部负责该职位或申请的人员，类型为 `User | null`。
- 两种语义不能共用同一个数据库字段或 DTO 字段。

### Job

```text
jobId, title, company, employmentType, workplaceType, location, salary,
description, requirements[], skills[], deadline, visibility, status,
publishedAt, version, createdAt, updatedAt
```

工资规则：

```json
{
  "min": 5000,
  "max": 8000,
  "currency": "SGD",
  "period": "MONTH"
}
```

MVP 仅支持 `SGD`，金额使用整数主币单位。

### Resume 与 ResumeSnapshot

Resume：

```text
resumeId, fullName, age, location, headline, summary, experiences[],
version, createdAt, updatedAt
```

Experience：

```text
experienceId, title, company, description, startDate(YYYY-MM), endDate(YYYY-MM|null)
```

`ResumeSnapshot` 是完整 Resume 的不可变副本，并增加：

```text
snapshotId, capturedAt
```

提交申请时必须在同一事务内生成快照。之后修改 Resume 不能改变历史申请的 ResumeSnapshot。

### Application

基础 Application：

```text
applicationId, jobId, status, appliedAt, updatedAt, version
```

- Candidate 详情包含 Candidate 安全的 timeline、完整 ResumeSnapshot、Interview 和 nextSteps，不得包含 RecruiterNote。
- Recruiter 详情包含 CandidateSummary、owner、ResumeSnapshot、AuditEvent timeline、MatchAnalysis、Interview 和私有 notes。
- `RecruiterNote` 永远不能从 Candidate API 返回。

### Interview

```text
interviewId, applicationId, scheduledAt, timezone, durationMinutes, mode,
locationOrMeetingUrl, note, status, version, createdAt, updatedAt
```

统一字段只能使用 `scheduledAt` 和 `mode`。

### Conversation 与 Message

Conversation：

```text
conversationId, applicationId, jobId, createdAt, updatedAt
```

Message：

```text
messageId, conversationId, body, senderType, sentAt,
clientMessageId, deliveryStatus
```

会话的 `participant` 始终表示当前查看者的对端用户。

### MatchAnalysis

```text
score, evidence[], strongMatches[], gaps[], modelVersion, generatedAt
```

MatchAnalysis 仅用于辅助展示，不能用于鉴权，也不能自动触发录用、拒绝或状态流转。ML 不可用时允许返回 `null`，核心 CRUD 流程仍须工作。

## 5. 接口清单

以下 Path 均相对于 `/api/v1`。

### 5.1 Auth

| Method | Path | 权限 | 请求 | 成功 |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | Public | `RegisterRequest` | `201 AuthResponse` |
| POST | `/auth/login` | Public | `LoginRequest` | `200 AuthResponse` |
| POST | `/auth/refresh` | Public | `RefreshTokenRequest` | `200 TokenResponse` |
| POST | `/auth/logout` | Authenticated | `RefreshTokenRequest` | `204` |

Candidate 注册：

```json
{
  "role": "CANDIDATE",
  "fullName": "Yan Bohao",
  "email": "bohao.yan@example.com",
  "password": "password123",
  "acceptedTermsVersion": "2026-08"
}
```

Recruiter 注册额外包含 `companyName`；MVP 中注册会创建新公司，并让该 Recruiter 成为公司唯一管理员。

### 5.2 Candidate Profile、Resume 与 Feature

| Method | Path | 请求/参数 | 成功 |
| --- | --- | --- | --- |
| GET | `/candidate/profile` | — | `200 CandidateProfile` |
| PATCH | `/candidate/profile` | `UpdateProfileRequest` | `200 CandidateProfile` |
| GET | `/candidate/resume` | — | `200 Resume` |
| PUT | `/candidate/resume` | `SaveResumeRequest` | `200 Resume` |
| GET | `/features/learning` | — | `200 LearningFeature` |

首次创建 Resume 时 `expectedVersion=0`；后续保存必须发送当前版本。

### 5.3 Candidate Jobs 与 Applications

| Method | Path | 请求/参数 | 成功 |
| --- | --- | --- | --- |
| GET | `/jobs` | `q`, `employmentType`, `page`, `pageSize` | `200 CandidateJobSummary[] + meta` |
| GET | `/jobs/{jobId}` | `jobId` | `200 CandidateJobDetail` |
| POST | `/jobs/{jobId}/applications` | `Idempotency-Key`, `SubmitApplicationRequest` | `201 CandidateApplicationDetail` |
| GET | `/candidate/applications` | `filter`, `page`, `pageSize` | `200 CandidateApplicationSummary[] + meta` |
| GET | `/candidate/applications/{applicationId}` | `applicationId` | `200 CandidateApplicationDetail` |
| POST | `/candidate/applications/{applicationId}/withdraw` | `WithdrawApplicationRequest` | `200 CandidateApplicationDetail` |

提交申请请求：

```json
{
  "resumeId": "resume_001",
  "contactEmail": "bohao.yan@example.com",
  "shareProfile": true
}
```

提交申请必须在同一事务中完成：

1. 验证职位为可投递的 `ACTIVE` 状态；
2. 验证 Resume 属于当前 Candidate；
3. 检查同一 Candidate + Job 是否已存在申请；
4. 创建不可变 ResumeSnapshot；
5. 创建 `APPLIED` Application；
6. 写入首次审计/时间线事件；
7. 保存幂等结果。

### 5.4 Recruiter Profile、Company 与 Dashboard

| Method | Path | 请求/参数 | 成功 |
| --- | --- | --- | --- |
| GET | `/recruiter/me` | — | `200 RecruiterProfile` |
| GET | `/recruiter/company` | — | `200 Company` |
| PATCH | `/recruiter/company` | `UpdateCompanyRequest` | `200 Company` |
| GET | `/recruiter/dashboard` | `from`, `to` | `200 RecruiterDashboard` |

所有结果均限定当前 Recruiter 所属公司。

### 5.5 Recruiter Jobs

| Method | Path | 请求/参数 | 成功 |
| --- | --- | --- | --- |
| GET | `/recruiter/jobs` | `q`, `status`, `employmentType`, `location`, `ownerId`, `page`, `pageSize` | `200 RecruiterJobSummary[] + meta` |
| POST | `/recruiter/jobs` | `CreateJobRequest` | `201 RecruiterJobDetail` |
| GET | `/recruiter/jobs/{jobId}` | `jobId` | `200 RecruiterJobDetail` |
| PATCH | `/recruiter/jobs/{jobId}` | `UpdateJobRequest` | `200 RecruiterJobDetail` |
| POST | `/recruiter/jobs/{jobId}/publish` | `PublishJobRequest` | `200 RecruiterJobDetail` |
| POST | `/recruiter/jobs/{jobId}/status` | `ChangeJobStatusRequest` | `200 RecruiterJobDetail` |

创建职位只创建 `DRAFT`。发布和状态变更使用独立命令接口，不要在普通 PATCH 中绕过状态机。

### 5.6 Recruiter Applications 与 Notes

| Method | Path | 请求/参数 | 成功 |
| --- | --- | --- | --- |
| GET | `/recruiter/applications` | `status`, `jobId`, `q`, `ownerId`, `minMatchScore`, `page`, `pageSize`, `sort` | `200 RecruiterApplicationSummary[] + meta` |
| GET | `/recruiter/applications/{applicationId}` | `applicationId` | `200 RecruiterApplicationDetail` |
| POST | `/recruiter/applications/{applicationId}/transitions` | `ApplicationTransitionRequest` | `201 ApplicationTransitionResult` |
| PUT | `/recruiter/applications/{applicationId}/owner` | `ApplicationOwnerRequest` | `200 RecruiterApplicationDetail` |
| GET | `/recruiter/applications/{applicationId}/notes` | `page`, `pageSize` | `200 RecruiterNote[] + meta` |
| POST | `/recruiter/applications/{applicationId}/notes` | `CreateNoteRequest` | `201 RecruiterNote` |
| GET | `/recruiter/applications/{applicationId}/resume-snapshot` | `applicationId` | `200 ResumeSnapshot` |
| GET | `/recruiter/applications/{applicationId}/resume-snapshot/pdf` | `applicationId` | `200/302 DownloadResponse`，P1 延期 |

状态流转请求：

```json
{
  "toStatus": "IN_REVIEW",
  "reason": "Initial recruiter review started",
  "expectedVersion": 1
}
```

响应 `data` 同时包含更新后的 `application` 和本次 `event`。

MVP owner 只能设置为当前 Recruiter 的 `userId` 或 `null`，跨用户分配延期。

### 5.7 Interviews

| Method | Path | 请求 | 成功 |
| --- | --- | --- | --- |
| POST | `/recruiter/applications/{applicationId}/interviews` | `CreateInterviewRequest` | `201 Interview` |
| PATCH | `/recruiter/interviews/{interviewId}` | `UpdateInterviewRequest` | `200 Interview` |

创建面试请求：

```json
{
  "scheduledAt": "2026-08-11T06:00:00Z",
  "timezone": "Asia/Singapore",
  "durationMinutes": 30,
  "mode": "ONLINE",
  "locationOrMeetingUrl": "https://meet.example.com/interview",
  "note": "Technical interview",
  "expectedApplicationVersion": 2
}
```

创建面试必须原子完成面试创建、Application 进入 `INTERVIEW`、Application 版本递增和审计事件写入。

### 5.8 Candidate Conversations 与 Messages

| Method | Path | 请求/参数 | 成功 |
| --- | --- | --- | --- |
| GET | `/candidate/conversations` | `page`, `pageSize` | `200 ConversationSummary[] + meta` |
| GET | `/candidate/conversations/{conversationId}` | — | `200 ConversationDetail` |
| GET | `/candidate/conversations/{conversationId}/messages` | `before`, `limit` | `200 Message[] + cursor meta` |
| POST | `/candidate/conversations/{conversationId}/messages` | `Idempotency-Key`, `SendMessageRequest` | `201 Message` |
| PUT | `/candidate/conversations/{conversationId}/read-state` | `ReadStateRequest` | `204` |

### 5.9 Recruiter Conversations 与 Messages

| Method | Path | 请求/参数 | 成功 |
| --- | --- | --- | --- |
| GET | `/recruiter/conversations` | `q`, `unreadOnly`, `page`, `pageSize` | `200 ConversationSummary[] + meta` |
| GET | `/recruiter/conversations/{conversationId}` | — | `200 ConversationDetail` |
| GET | `/recruiter/conversations/{conversationId}/messages` | `before`, `limit` | `200 Message[] + cursor meta` |
| POST | `/recruiter/conversations/{conversationId}/messages` | `Idempotency-Key`, `SendMessageRequest` | `201 Message` |
| PUT | `/recruiter/conversations/{conversationId}/read-state` | `ReadStateRequest` | `204` |

发送消息请求：

```json
{
  "body": "Are you available for an interview?",
  "clientMessageId": "615c8eb7-dcd3-4f3c-8416-abf4b23f5620"
}
```

只有会话参与者能读写消息。Recruiter 还必须属于会话关联职位的公司。

## 6. 推荐的后端模块边界

```text
com.adproject
├── auth
├── user
├── company
├── resume
├── job
├── application
├── interview
├── conversation
├── message
├── recommendation
└── common
```

每个模块建议按能力组织 `api`、`application`、`domain`、`infrastructure`，不要把全部 Controller、Service、Repository 分别堆在全局目录中。

### common 至少包含

- JWT 认证与当前用户上下文；
- `DataResponse<T>`、分页响应和 `ErrorResponse`；
- requestId 过滤器；
- 全局异常到 HTTP/error.code 的映射；
- 乐观锁和幂等基础设施；
- UTC 时间与 JSON 序列化配置；
- 权限和资源所有权检查的公共能力。

## 7. 建议实现顺序

1. 通用 envelope、异常映射、requestId、JWT 和角色隔离。
2. Auth 注册、登录、刷新、退出。
3. Candidate Profile 与单份 Resume。
4. Recruiter Company 与 Job 创建、发布、状态机。
5. Candidate 职位浏览、投递事务和 ResumeSnapshot。
6. Recruiter Application 查询、状态流转、owner 和私有 notes。
7. Interview 创建、更新及审计。
8. Conversation、Message、已读状态和幂等去重。
9. Dashboard、MatchAnalysis 适配与 ML 失败降级。
10. OpenAPI 契约测试、权限测试和端到端联调。

## 8. 最低测试清单

每个受保护接口至少验证：

- 未登录返回 `401`；
- 错误角色返回 `403`；
- 错误所有权或跨公司访问返回契约规定的 `404/403`；
- 字段校验返回 `422` 和 `fieldErrors`；
- 版本不匹配返回 `409 VERSION_CONFLICT`；
- 状态机非法流转返回 `409 INVALID_APPLICATION_TRANSITION` 或相应职位/面试错误；
- 返回 JSON 字段、必填性和枚举与 OpenAPI 一致。

关键业务测试：

- 同一 Candidate 不能重复投递同一 Job；
- 非 `ACTIVE` 职位不能投递；
- 投递和首次状态事件必须事务一致；
- Resume 后续修改不影响历史 ResumeSnapshot；
- RecruiterNote 不出现在任何 Candidate 响应；
- Recruiter 不能设置 `WITHDRAWN`；
- Candidate 不能调用 Recruiter transition；
- 创建面试时 Application 和 Interview 状态事务一致；
- 幂等重试不会重复创建申请或消息；
- ML 不可用时职位、投递和申请处理仍可运行。

## 9. MVP 延期项

以下内容不应阻塞当前后端实现：

- 邮箱验证、忘记密码；
- 加入已有公司、邀请成员和复杂公司权限；
- 多份 Resume；
- Logo Asset API；
- ResumeSnapshot PDF 的真实生成与下载；
- WebSocket、推送和高级消息保留策略；
- Offer/Hire 资源；
- 多币种；
- MatchAnalysis 刷新策略和高级 ML 降级文案。

实现延期项时应先更新并评审 OpenAPI，不能直接在后端增加第二套字段或状态。
