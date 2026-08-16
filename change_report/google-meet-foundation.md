# 修改报告：Google Meet 自动建会第一包（Provider-aware interview foundation）

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 对应计划/任务：`tasks/google-meet-integration-plan.md`、`tasks/google-meet-integration-todo.md`「Provider-aware interview foundation」第一包
- 修改范围：`backend/`（application/interview 数据模型 + DTO/Service、V9 迁移、`integration.google` 端口、测试）、`web/`（模型类型、API 客户端解析、mock、测试）、`docs/openapi-v1.yaml`、`tasks/`（V8→V9 修正）、`change_report/`
- 明确禁止且未改动：真实 OAuth 跳转、Google API 调用、Google 密钥；`ml-service/`、Agent、Admin、认证/JWT/刷新令牌、既有 Messages API/表/轮询、Android；未提交/未推送（等待 Codex 复核）

## 完成内容

- 在 `interviews` 表上建立最小、可演进的第三方会议状态：`meeting_provider`（`MANUAL`/`GOOGLE_MEET`）、`meeting_sync_status`（`NOT_APPLICABLE`/`PENDING`/`READY`/`FAILED`），以及未来 Google Calendar 事件 ID、失败原因、稳定请求关联键三个安全字段；**不存储任何 OAuth token**。
- 手动面试在迁移后安全落为 `MANUAL` + `NOT_APPLICABLE`，`location_or_meeting_url` 原样保留。
- 用约束/索引保证：每个面试至多映射一个外部日历事件（`meeting_event_id` 唯一索引），重试复用关联键时不会产生重复外部会议（`meeting_correlation_id` 索引）。
- 扩展 Interview 请求/响应 DTO、OpenAPI、Web 类型以表达 provider + sync 状态；新字段向后兼容既有手动调用。
- 手动排程完全不变，响应显式返回 `MANUAL` + `NOT_APPLICABLE`。
- 保留 `GOOGLE_MEET` 请求值，但后端在未连接时用稳定业务错误码 `GOOGLE_MEET_NOT_CONNECTED`（409）拒绝，绝不伪造链接、绝不标记 `READY`。
- 新增最小 provider 端口 `MeetingProvisioningPort`（含未来 `createMeeting`/`reschedule`/`cancel` 扩展说明），无任何 Google SDK 类型；当前回退实现恒返回 `isConnected=false`。

## 为什么 V9（而非 V8）

- `V8__create_interview_audit_events.sql` 已被 `interview_audit_events` 占用；Google 相关下一条迁移必须从 **V9** 开始。已同步修正 `tasks/google-meet-integration-plan.md`、`tasks/google-meet-integration-todo.md` 中旧的 V8 表述。

## 修改文件

### 后端（新增）

- `backend/src/main/resources/db/migration/V9__add_interview_meeting_provider.sql`
  - 主要变化：`ALTER TABLE interviews` 新增 5 列（`meeting_provider`、`meeting_sync_status` 非空带默认值；`meeting_event_id`、`meeting_sync_error`、`meeting_correlation_id` 可空）；2 个 CHECK 约束（provider/sync 枚举）、`uk_interviews_meeting_event` 唯一索引、`idx_interviews_meeting_correlation` 普通索引。不改动 V1–V8。
- `backend/src/main/java/com/adproject/application/domain/MeetingProvider.java`、`MeetingSyncStatus.java`
  - 主要变化：两个枚举（见上）。
- `backend/src/main/java/com/adproject/integration/google/MeetingProvisioningPort.java`、`UnavailableMeetingProvisioningPort.java`
  - 主要变化：端口接口 `boolean isConnected(String recruiterId)` + `@Component` 回退实现恒返回 `false`；接口 javadoc 注明未来 `createMeeting`/`reschedule`/`cancel` 将扩展。

### 后端（既有文件修改）

- `backend/src/main/java/com/adproject/application/infrastructure/InterviewEntity.java`
  - 主要变化：新增 5 个字段映射；构造函数签名由 `InterviewStatus, Instant now` 变为 `InterviewStatus, MeetingProvider, Instant now`，并按 provider 推导 sync 状态（`MANUAL→NOT_APPLICABLE`，否则 `PENDING`）；新增 getter。
- `backend/src/main/java/com/adproject/application/api/InterviewDtos.java`
  - 主要变化：`Interview` 记录追加 `meetingProvider`、`meetingSyncStatus`；`CreateInterviewRequest` 新增可选 `meetingProvider`，`locationOrMeetingUrl` 由 `@NotBlank @Size` 改为 `@Size`（可选，供未来 GOOGLE_MEET 分支使用）。
- `backend/src/main/java/com/adproject/application/application/InterviewService.java`
  - 主要变化：注入 `MeetingProvisioningPort`；`create()` 解析 provider（缺省 `MANUAL`），`GOOGLE_MEET` 且未连接时抛 `GOOGLE_MEET_NOT_CONNECTED`（409）；仅 `MANUAL` 校验 location；`toDto()` 追加两个新字段名。
- `backend/src/main/java/com/adproject/application/application/CandidateApplicationResponseMapper.java`
  - 主要变化：`interviewDto()` 追加 `meetingProvider`/`meetingSyncStatus`。
- `backend/src/main/java/com/adproject/application/application/RecruiterApplicationService.java`
  - 主要变化：`interviewDto()` 追加 `meetingProvider`/`meetingSyncStatus`（补齐 `Interview` 记录新参数）。

### 契约与文档

- `docs/openapi-v1.yaml`：新增 `MeetingProvider`/`MeetingSyncStatus` schema；`Interview` 追加两个 `$ref` 字段；`CreateInterviewRequest` 移除 `locationOrMeetingUrl` 必填、新增可选 `meetingProvider` 及其说明。
- `tasks/google-meet-integration-plan.md`、`tasks/google-meet-integration-todo.md`：V8→V9 表述修正。

### 招聘者 Web

- `web/src/models/recruiter.ts`：新增 `MeetingProvider`/`MeetingSyncStatus` 类型；`Interview` 追加 `meetingProvider`/`meetingSyncStatus`；`CreateInterviewRequest` 追加可选 `meetingProvider`，`locationOrMeetingUrl` 改为可选。
- `web/src/api/applicationHttpClient.ts`：`parseInterview` 校验两个新字段；新增 `isMeetingProvider`/`isMeetingSyncStatus` 助手。
- `web/src/mocks/data.ts`、`web/src/pages/ApplicationPages.test.tsx`：面试字面量补齐 `meetingProvider: 'MANUAL'`、`meetingSyncStatus: 'NOT_APPLICABLE'`。

### 测试

- `backend/src/test/java/com/adproject/application/RecruiterInterviewIntegrationTest.java`（修改）
  - 主要变化：既有创建成功测试追加 `meetingProvider=MANUAL`、`meetingSyncStatus=NOT_APPLICABLE` 断言；新增 `googleMeetRejectedWhenNotConnectedAndManualProviderAccepted`（GOOGLE_MEET→409 `GOOGLE_MEET_NOT_CONNECTED` 且不落库；显式 MANUAL→201 且落库为 MANUAL+NOT_APPLICABLE）。
- `backend/src/test/java/com/adproject/auth/MySqlFlywayIntegrationTest.java`（修改）
  - 主要变化：新增 `v9MigrationAddsMeetingProviderColumnsAndManualDefaults`（校验 5 列 + 默认值 + 2 CHECK + 2 索引）、`v9MigrationMigratesExistingInterviewsToManualDefaults`（原始链插入 users→companies→jobs→resumes→resume_snapshots→applications→interviews，省略新列后校验默认 MANUAL+NOT_APPLICABLE 且 `location_or_meeting_url` 保留）。

## API 变化

- 是否变化：是（非破坏性，向后兼容）
- `POST /api/v1/recruiter/applications/{applicationId}/interviews`：请求体新增可选 `meetingProvider`（`MANUAL`/`GOOGLE_MEET`，缺省 `MANUAL`），`locationOrMeetingUrl` 由必填改为可选；`MANUAL` 仍需有效 location（服务层校验）；`GOOGLE_MEET` 且未连接返回 409 `GOOGLE_MEET_NOT_CONNECTED`。
- 响应 `Interview`（招聘者详情/创建/更新、候选人详情）新增 `meetingProvider`、`meetingSyncStatus`（均为枚举字符串）。
- 手动排程流程不变，响应显式 `MANUAL` + `NOT_APPLICABLE`。
- 与 OpenAPI 是否一致：是（已同步 schema 与字段）。

## 数据库变化

- 是否变化：是
- 新增迁移：`V9__add_interview_meeting_provider.sql`（仅 `ALTER TABLE`，不建新表，不改 V1–V8）
- 新增列：`meeting_provider`、`meeting_sync_status`（非空默认值）、`meeting_event_id`、`meeting_sync_error`、`meeting_correlation_id`（可空）
- 新增约束/索引：`chk_interviews_meeting_provider`、`chk_interviews_meeting_sync`、`uk_interviews_meeting_event`、`idx_interviews_meeting_correlation`

## 权限与安全

- 涉及角色：Recruiter（写）、Candidate（只读）。
- 认证/角色/所有权检查：复用既有 JWT 过滤器与 `InterviewService` 的 RECRUITER + 本公司校验，未新增认证逻辑。
- 敏感信息风险：低。`meeting_event_id`/`meeting_sync_error`/`meeting_correlation_id` 当前仅存库、不在响应中暴露；无任何 OAuth token 落库或进出 API。
- 并发：沿用 `PESSIMISTIC_WRITE` + 乐观锁 `expectedVersion`/`expectedApplicationVersion`。

## 测试与验证

### 后端（IntelliJ 内置 Maven + JBR 21，离线 `mvn -o test`）

- 全量：`Tests run: 94, Failures: 0, Errors: 0, Skipped: 5`
  - `RecruiterInterviewIntegrationTest`：`Tests run: 11`（含新增 provider 用例）。
  - 5 个跳过为 `MySqlFlywayIntegrationTest`（Testcontainers 离线、Docker 不可用；V9 两个新迁移测试随该类跳过）。
  - 说明：`ddl-auto: validate` + H2（MySQL 模式）下 Flyway V1–V9 全量迁移与实体映射校验通过，V9 SQL 与实体映射一致。

### Web

- `npm run typecheck`：通过。
- `npm run lint`：通过。
- `npm test`：`15 files, 122 tests` 全部通过。
- `npm run build`：`tsc -b && vite build` 成功。

### Android

- 未改动、未重跑。本包只改后端数据模型/契约与 Web 类型/解析；Android 面试详情仅读取既有字段（`mode`/`locationOrMeetingUrl`/`status`/`scheduledAt` 等），新增 `meetingProvider`/`meetingSyncStatus` 不影响其解析。Android 的 Google Meet 展示留待后续包（Task 4）处理。

## 已知限制

- 本包仅建立契约、数据模型与手动流程兼容性；**未实现**真实 OAuth 跳转、Google API/Calendar/Meet 调用、token 存储、email、消息推送、候选人 accept/reject、Microsoft Teams。
- `GOOGLE_MEET` 目前只有「未连接」分支；`MeetingProvisioningPort` 的回退实现恒返回 `isConnected=false`，未来 `integration.google` 模块接入真实连接后再扩展 `createMeeting`/`reschedule`/`cancel`。
- `meeting_event_id`/`meeting_sync_error`/`meeting_correlation_id` 暂只在数据库层存在，未在 API 响应暴露（后续包按需暴露）。

## Google Cloud 前置条件（未完成，需项目负责人本地配置）

- Google Cloud OAuth client ID / client secret（仅本地环境变量，不进仓库）
- 已审批的 HTTPS 回调 URL（部署演示 + 本地开发各一）
- 两个 Google 测试账号
- 随机 token 加密密钥
- 启用 Calendar API、将回调 URI 精确登记、OAuth 同意屏设为 Testing 并加入测试账号

## 风险与注意事项

- V9 为追加式 `ALTER TABLE`，不改动已发布 V1–V8 迁移；请确认目标环境 Flyway 兼容（沿用既有命名与风格）。
- `uk_interviews_meeting_event` 建立在可空列 `meeting_event_id` 上：MySQL/H2 允许多个 NULL，符合「未映射时无唯一约束、映射后至多一个外部事件」的语义。
- `locationOrMeetingUrl` 由必填改可选后，`MANUAL` 的必填语义改由 `InterviewService.validateLocation` 保证（服务层 422），DTO 层校验随之放宽——请 Codex 复核该语义是否符合预期。

## 下一步建议

- 下一包（Task 2）接真实 Google OAuth 连接存储与回调，并让 `MeetingProvisioningPort` 返回真实连接状态。
- 在真实 MySQL（Docker 可用时）补跑 `MySqlFlywayIntegrationTest`，端到端确认 V1–V8→V9 升级对既有面试行给出正确默认值。
