# 统一 API 目录 v1

> 状态：**DRAFT**。唯一接口主键为规范化后的 **HTTP Method + Path**。基础路径：`/api/v1`。本文档不授权或包含任何前端、后端代码修改。

## 汇总

| 指标 | 数量 |
| --- | --- |
| 唯一接口总数 | 44 |
| Candidate 使用的接口数 | 19 |
| Recruiter 使用的接口数 | 29 |
| 两端共享接口数 | 4 |
| 完全兼容的共享重复接口 | 2 |
| 存在契约冲突的共享接口 | 2 |

计数公式：`19 + 29 - 4 = 44`。Candidate 和 Recruiter 接口数均包含共享接口；唯一接口总数按 Method + Path 去重。

## 来源与限制

- Candidate：`API_V1.md` 与 `openapi-v1.yaml`，两份材料按 Method + Path 核对后均为 19 个操作。
- Recruiter：`RECRUITER_API_DRAFT.md` 与 `RECRUITER_WEB_ANALYSIS.md`，从 API Draft 提取 29 个操作，并与页面路由和影响分析交叉核对。
- 指定的 `/Users/yezhian/code/adproject/web/docs/RECRUITER_WEB_ANALYSIS.yaml` 实际不存在。因此本文不声称读取过 Recruiter YAML；合并 OpenAPI 是对现有两份 Recruiter Markdown 的正式化。
- Markdown 示例中的 query string 不属于 Path 主键。例如 `GET /jobs?q=...` 统一记为 `GET /jobs`。

## 重复、共享与冲突接口

| Method + Path | 分类 | 请求对比 | 响应对比 | 统一方案 |
| --- | --- | --- | --- | --- |
| POST /auth/register | 共享且冲突 | Candidate 要求 role=CANDIDATE；Recruiter 要求 role=RECRUITER 并增加 companyName | Recruiter 响应增加 company；Candidate 的 role 枚举不允许 RECRUITER | 按 role 使用 CandidateRegisterRequest / RecruiterRegisterRequest 的 oneOf；AuthUser 使用 UserRole；company 仅 Recruiter 返回或为 null |
| POST /auth/login | 共享且响应冲突 | 两端均为 LoginRequest | Candidate AuthUser.role 原先仅允许 CANDIDATE | UserRole 统一为 CANDIDATE \| RECRUITER |
| POST /auth/refresh | 共享且完全兼容 | 相同 RefreshTokenRequest | 相同 TokenResponse | 复用同一个接口和契约 |
| POST /auth/logout | 共享且完全兼容 | 相同 RefreshTokenRequest | 相同 204 响应 | 复用同一个接口和契约 |

在 Method + Path 唯一性规则下，没有其他跨端重复接口。Candidate 与 Recruiter 的 Conversation/Message 接口虽然结构共享，但路径分别带有 `/candidate` 和 `/recruiter` 前缀，因此仍是不同接口。

## 字段名、状态枚举与数据类型冲突

| 范围 | 来源冲突 | 统一决定 |
| --- | --- | --- |
| AuthUser.role | Candidate YAML 仅允许 CANDIDATE；Recruiter Draft 需要 RECRUITER | UserRole = CANDIDATE \| RECRUITER |
| RegisterRequest | Recruiter 增加 companyName，并使用不同 role 常量 | 使用以 role 为 discriminator 的 CandidateRegisterRequest / RecruiterRegisterRequest oneOf |
| ApplicationStatus | Candidate YAML 包含 NOT_APPLIED；分析文档冻结的申请生命周期不包含它 | ApplicationStatus = APPLIED \| IN_REVIEW \| INTERVIEW \| REJECTED \| WITHDRAWN；NOT_APPLIED 移入 CandidateJobApplicationState |
| Candidate 申请列表 status | ACTIVE \| INTERVIEW \| ARCHIVED 是 UI 分组，不是申请生命周期状态 | 保留为接口专用展示筛选枚举，不复用 ApplicationStatus |
| SCREENING | 仓库测试计划出现 SCREENING，与 API 分析文档冲突 | v1 统一使用 IN_REVIEW，不引入 SCREENING |
| JobStatus | Candidate 职位没有生命周期状态；Recruiter 需要 DRAFT/ACTIVE/PAUSED/CLOSED | 新增 JobStatus；Candidate 公开职位接口只返回 ACTIVE 且可申请的职位 |
| Interview 时间/模式字段 | Candidate 使用 interviewAt/interviewMode；Recruiter 使用 scheduledAt/mode | 标准 Interview 使用 scheduledAt/mode；InterviewContext 为对话投影 |
| ResumeSnapshot | Candidate 仅有 resumeSnapshot.id/name；Recruiter 需要完整不可变 Resume 和 snapshotId/capturedAt | ResumeSnapshot 扩展 Resume，增加 snapshotId/capturedAt；原 id 作为 Resume.id |
| Company Logo | Candidate 返回 logoUrl；Recruiter PATCH 接收 logoAssetId | logoAssetId 作为修改输入，logoUrl 作为解析后的响应 URL；持久化映射待实现阶段确认 |
| 人员姓名 | 嵌入式 Recruiter 使用 name；Auth/Recruiter Profile 使用 fullName | 账户和资料统一使用 fullName；name 暂保留为兼容旧客户端的紧凑投影 |
| Job 负责人 | Candidate 的 recruiter 表示外部联系人；Recruiter 的 owner 表示内部负责人 | 两者语义不同，分别保留 recruiter 与 owner |
| Application 时间 | Candidate 同时返回 appliedAt 与 submittedAt，但未明确差异 | appliedAt 表示业务申请时间；submittedAt 暂定义为可空的服务端接收时间，语义仍需确认 |
| Participant | Candidate 看到 Recruiter；Recruiter 看到 Candidate；原 Participant 强制 companyName | participant 始终表示当前用户的对方；Recruiter 看到 Candidate 时 companyName 可为 null |
| Salary 数值类型 | 两端示例均为整数，Candidate YAML 也定义为 integer，但未明确货币单位 | v1 保持 integer，并强制 currency/period；主币单位或最小币种单位须在持久化前确认 |
| Read State 响应 | Candidate 明确为 204；Recruiter 表示复用 Candidate 请求/响应 | 两端统一使用 ReadStateRequest 和 204 无响应体 |

## 统一共享模型

| 模型 | 标准核心字段 | 端侧投影与隐私规则 |
| --- | --- | --- |
| Job | id,title,company,employmentType,workplaceType,location,salary,description,requirements,skills,deadline,visibility,status,publishedAt,version | Candidate 投影增加 matchScore/recruiter/applicationState；Recruiter 投影增加 owner/applicantCount。Candidate 只能收到公开 ACTIVE 职位。 |
| Application | id,jobId,status,appliedAt,submittedAt,updatedAt,version | Candidate 投影包含 company/timeline/nextSteps；Recruiter 投影增加 candidate、owner、MatchAnalysis、ResumeSnapshot、审计、Interview 和私有备注。 |
| Interview | id,applicationId,scheduledAt,timezone,durationMinutes,mode,locationOrMeetingUrl,note,status | Conversation 的 InterviewContext 增加 jobId/jobTitle/type。Recruiter 控制生命周期，Candidate 消费上下文。 |
| Conversation | id,participant,lastMessage/unreadCount 或 context；applicationId/jobId/jobTitle 可空 | participant 表示当前用户的对方；访问必须满足会话成员关系，Recruiter 还必须满足公司归属。 |
| Message | id,body,senderType,sentAt,clientMessageId,deliveryStatus | 两端共用；senderType 区分 CANDIDATE/RECRUITER/SYSTEM；clientMessageId 用于幂等。 |
| ResumeSnapshot | Resume + snapshotId + capturedAt | 提交申请时生成不可变快照；Candidate 后续修改在线 Resume 不得改变历史快照。 |

## 权限策略

- 注册、登录、刷新 Token 为公开接口，但刷新仍需有效 refresh token；退出登录要求已认证会话。
- Candidate 资源要求本人所有权或会话参与者身份。
- Recruiter 资源要求 RECRUITER 角色和公司归属；公司更新及部分职位修改还需要更强能力。跨公司资源建议返回 404，降低资源枚举风险。
- Recruiter 私有备注绝不能出现在 Candidate 响应中。MatchAnalysis 仅供参考，不能作为授权或自动决策依据。所有修改操作应记录 actor、company、修改前后状态、时间和 requestId。

## 按接口对比请求与响应

### 认证（Auth）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | POST | `/auth/register` | 使用 | 使用 | 共享-存在冲突 | 公开 | oneOf CandidateRegisterRequest \| RecruiterRegisterRequest; discriminator=role | 201 AuthResponse; user.role=CANDIDATE | 201 AuthResponse; user.role=RECRUITER; company included；统一：201 AuthResponse; role-aware user; company nullable/Recruiter-only |
| DRAFT | POST | `/auth/login` | 使用 | 使用 | 共享-存在冲突 | 公开 | LoginRequest {email,password} | 200 AuthResponse; AuthUser.role only CANDIDATE in Candidate YAML | 200 AuthResponse; AuthUser.role=RECRUITER supported；统一：200 AuthResponse; UserRole=CANDIDATE\|RECRUITER |
| DRAFT | POST | `/auth/refresh` | 使用 | 使用 | 共享-完全兼容 | 公开；须提供有效 refresh token | RefreshTokenRequest {refreshToken} | 200 TokenResponse | 200 TokenResponse (reused)；统一：200 TokenResponse |
| DRAFT | POST | `/auth/logout` | 使用 | 使用 | 共享-完全兼容 | 已认证会话 | RefreshTokenRequest {refreshToken} | 204 no content | 204 no content (reused)；统一：204 no content |

### 资料与公司（Profile）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/candidate/profile` | 使用 | 不使用 | 仅 Candidate | Candidate 本人 | — | 200 CandidateProfile | 200 CandidateProfile |
| DRAFT | PATCH | `/candidate/profile` | 使用 | 不使用 | 仅 Candidate | Candidate 本人 | UpdateProfileRequest | 200 CandidateProfile | 200 CandidateProfile |
| DRAFT | GET | `/recruiter/me` | 不使用 | 使用 | 仅 Recruiter | Recruiter 本人 | — | — | 200 RecruiterProfile；统一：200 RecruiterProfile |
| DRAFT | GET | `/recruiter/company` | 不使用 | 使用 | 仅 Recruiter | Recruiter 所属公司成员 | — | — | 200 Company；统一：200 Company |
| DRAFT | PATCH | `/recruiter/company` | 不使用 | 使用 | 仅 Recruiter | Recruiter 所属公司；需公司管理员能力 | UpdateCompanyRequest | — | 200 Company；统一：200 Company |
| DRAFT | GET | `/recruiter/dashboard` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司聚合数据 | 查询参数：from、to | — | 200 RecruiterDashboard；统一：200 RecruiterDashboard |

### 职位（Jobs）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/jobs` | 使用 | 不使用 | 仅 Candidate | 已认证 Candidate | 查询参数：q、employmentType、category、page、pageSize | 200 PageResponse<JobSummary> | 200 PageResponse<CandidateJobSummary> |
| DRAFT | GET | `/jobs/{jobId}` | 使用 | 不使用 | 仅 Candidate | 已认证 Candidate；仅公开且 ACTIVE 的职位 | 路径参数：jobId | 200 JobDetail | 200 CandidateJobDetail |
| DRAFT | GET | `/recruiter/jobs` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司 | 查询参数：q、status、employmentType、location、ownerId、page、pageSize | — | 200 PageResponse<RecruiterJobSummary>；统一：200 PageResponse<RecruiterJobSummary> |
| DRAFT | POST | `/recruiter/jobs` | 不使用 | 使用 | 仅 Recruiter | Recruiter；所属公司须已认证 | CreateJobRequest | — | 201 RecruiterJobDetail(status=DRAFT)；统一：201 RecruiterJobDetail |
| DRAFT | GET | `/recruiter/jobs/{jobId}` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司职位 | 路径参数：jobId | — | 200 RecruiterJobDetail；统一：200 RecruiterJobDetail |
| DRAFT | PATCH | `/recruiter/jobs/{jobId}` | 不使用 | 使用 | 仅 Recruiter | Recruiter；所属公司职位且具备编辑能力 | UpdateJobRequest | — | 200 RecruiterJobDetail；统一：200 RecruiterJobDetail |
| DRAFT | POST | `/recruiter/jobs/{jobId}/publish` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司且公司须已认证 | PublishJobRequest | — | 200 RecruiterJobDetail(status=ACTIVE)；统一：200 RecruiterJobDetail |
| DRAFT | POST | `/recruiter/jobs/{jobId}/status` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司职位 | ChangeJobStatusRequest | — | 200 RecruiterJobDetail；统一：200 RecruiterJobDetail |

### 申请（Applications）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | POST | `/jobs/{jobId}/applications` | 使用 | 不使用 | 仅 Candidate | Candidate 本人；必须提供 Idempotency-Key | SubmitApplicationRequest + Idempotency-Key | 201 ApplicationDetail | 201 CandidateApplicationDetail |
| DRAFT | GET | `/candidate/applications` | 使用 | 不使用 | 仅 Candidate | Candidate 本人 | status display filter, page, pageSize | 200 ApplicationListResponse | 200 CandidateApplicationListResponse |
| DRAFT | GET | `/candidate/applications/{applicationId}` | 使用 | 不使用 | 仅 Candidate | 申请所属 Candidate | 路径参数：applicationId | 200 ApplicationDetail | 200 CandidateApplicationDetail |
| DRAFT | GET | `/recruiter/applications` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司职位的申请 | 查询参数：status、jobId、q、ownerId、minMatchScore、page、pageSize、sort | — | 200 RecruiterApplicationListResponse；统一：200 RecruiterApplicationListResponse |
| DRAFT | GET | `/recruiter/applications/{applicationId}` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司申请 | 路径参数：applicationId | — | 200 RecruiterApplicationDetail；统一：200 RecruiterApplicationDetail |
| DRAFT | POST | `/recruiter/applications/{applicationId}/transitions` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司申请；不得设置 WITHDRAWN | ApplicationTransitionRequest | — | 201 ApplicationTransitionResult；统一：201 ApplicationTransitionResult |
| DRAFT | PUT | `/recruiter/applications/{applicationId}/owner` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅可在所属公司内分配负责人 | ApplicationOwnerRequest | — | 200 RecruiterApplicationDetail；统一：200 RecruiterApplicationDetail |
| DRAFT | GET | `/recruiter/applications/{applicationId}/notes` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司；私有备注 | 路径参数：applicationId | — | 200 RecruiterNote[]；统一：200 RecruiterNote[] |
| DRAFT | POST | `/recruiter/applications/{applicationId}/notes` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司；私有备注 | CreateNoteRequest | — | 201 RecruiterNote；统一：201 RecruiterNote |

### 面试（Interviews）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | POST | `/recruiter/applications/{applicationId}/interviews` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司申请 | CreateInterviewRequest | — | 201 Interview; application -> INTERVIEW atomically；统一：201 Interview |
| DRAFT | PATCH | `/recruiter/interviews/{interviewId}` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司面试 | UpdateInterviewRequest | — | 200 Interview；统一：200 Interview |

### 会话（Conversations）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/candidate/conversations` | 使用 | 不使用 | 仅 Candidate | Candidate 会话参与者 | 查询参数：page、pageSize | 200 PageResponse<ConversationSummary> | 200 PageResponse<ConversationSummary> |
| DRAFT | GET | `/candidate/conversations/{conversationId}` | 使用 | 不使用 | 仅 Candidate | Candidate 会话参与者 | 路径参数：conversationId | 200 ConversationDetail | 200 ConversationDetail |
| DRAFT | PUT | `/candidate/conversations/{conversationId}/read-state` | 使用 | 不使用 | 仅 Candidate | Candidate 会话参与者 | ReadStateRequest | 204 no content | 204 no content |
| DRAFT | GET | `/recruiter/conversations` | 不使用 | 使用 | 仅 Recruiter | Recruiter 会话参与者；仅限所属公司职位 | 查询参数：q、unreadOnly、page、pageSize | — | 200 PageResponse<ConversationSummary>；统一：200 PageResponse<ConversationSummary> |
| DRAFT | GET | `/recruiter/conversations/{conversationId}` | 不使用 | 使用 | 仅 Recruiter | Recruiter 会话参与者；仅限所属公司会话 | 路径参数：conversationId | — | 200 ConversationDetail；统一：200 ConversationDetail |
| DRAFT | PUT | `/recruiter/conversations/{conversationId}/read-state` | 不使用 | 使用 | 仅 Recruiter | Recruiter 会话参与者；仅限所属公司会话 | ReadStateRequest | — | 204 no content；统一：204 no content |

### 消息（Messages）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/candidate/conversations/{conversationId}/messages` | 使用 | 不使用 | 仅 Candidate | Candidate 会话参与者 | 查询参数：before、limit | 200 MessageListResponse | 200 MessageListResponse |
| DRAFT | POST | `/candidate/conversations/{conversationId}/messages` | 使用 | 不使用 | 仅 Candidate | Candidate 会话参与者 | SendMessageRequest | 201 Message(senderType=CANDIDATE) | 201 Message |
| DRAFT | GET | `/recruiter/conversations/{conversationId}/messages` | 不使用 | 使用 | 仅 Recruiter | Recruiter 会话参与者；仅限所属公司会话 | 查询参数：before、limit | — | 200 MessageListResponse；统一：200 MessageListResponse |
| DRAFT | POST | `/recruiter/conversations/{conversationId}/messages` | 不使用 | 使用 | 仅 Recruiter | Recruiter 会话参与者；仅限所属公司会话 | SendMessageRequest | — | 201 Message(senderType=RECRUITER)；统一：201 Message |

### 简历（Resume）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/candidate/resume` | 使用 | 不使用 | 仅 Candidate | Candidate 本人 | — | 200 Resume | 200 Resume |
| DRAFT | PUT | `/candidate/resume` | 使用 | 不使用 | 仅 Candidate | Candidate 本人 | SaveResumeRequest | 200 Resume | 200 Resume |
| DRAFT | GET | `/recruiter/applications/{applicationId}/resume-snapshot` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司申请 | 路径参数：applicationId | — | 200 ResumeSnapshot；统一：200 ResumeSnapshot |
| DRAFT | GET | `/recruiter/applications/{applicationId}/resume-snapshot/pdf` | 不使用 | 使用 | 仅 Recruiter | Recruiter；仅限所属公司申请；短期有效 URL | 路径参数：applicationId | — | 200 DownloadResponse or 302 redirect；统一：200 DownloadResponse or 302 redirect |

### 功能开关（Features）

| 状态 | Method | Path | Candidate | Recruiter | 共享分类 | 权限 | 请求 | Candidate 响应 | Recruiter 响应 / 统一响应 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DRAFT | GET | `/features/learning` | 使用 | 不使用 | 仅 Candidate | 已认证 Candidate | — | 200 LearningFeature | 200 LearningFeature |

## 实现前待确认事项

1. 确认 Company.logoAssetId 与 Company.logoUrl 是并存，还是由前者解析生成后者。
2. 确认 Recruiter 会话中 Candidate participant 的 companyName 是否允许为 null。
3. 确认简历 PDF 使用 200 DownloadResponse 还是 302 跳转；合并 OpenAPI 暂以 200 作为通用默认，并记录可选 302。
4. 在后端持久化前确认 Salary integer 表示主币单位还是最小币种单位。
5. 确认所有修改接口的 version 字段与乐观并发控制要求。
