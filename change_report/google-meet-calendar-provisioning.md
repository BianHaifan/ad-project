# 修改报告：Google Calendar / Google Meet 后端实际建会

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 修改范围：`backend/`（`integration.google` 建会实现 + `application` 面试服务事务边界 + 对应回归测试）、`docs/openapi-v1.yaml`、`tasks/google-meet-integration-*.md`、`change_report/`
- 明确禁止且未改动：Web OAuth UI、Android UI、面试重排/取消同步、后台定时任务、重试接口、邮件邀请、Teams、ML、Agent、Admin、项目登录改造、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 安全复核）；未启动真实授权、未填入真实 OAuth 密钥/回调 URL/加密密钥；测试使用 fake transport，从未请求 Google。

## 目标与结果

当已连接的招聘者以 `meetingProvider=GOOGLE_MEET` 创建面试时，后端在招聘者的 Google `primary` 日历上创建带 Meet 会议数据的 event，并把**服务端校验过的最终 HTTPS `meet.google.com` 链接**、Google event ID、以及同步状态写回面试记录。MANUAL 流程不变。

## 架构与事务边界

- `MeetingProvisioningPort` 仍为业务边界，`InterviewService` 不依赖 Google HTTP/token/JSON。Calendar 解析、token 刷新、连接状态全部收敛在 `integration.google`。
- 新增 `MeetingProvisioningService.provision`（真实实现）：加载连接 → 解密 token → 需要时刷新 → 创建/恢复 event → 校验并轮询 Meet 链接 → 返回 provider 中立的 `ProvisionResult(READY|PENDING|FAILED)`。**远程失败不抛异常**，转为安全错误码。
- `InterviewService.create` 去掉类级 `@Transactional`，改用 `TransactionTemplate` 两段提交：
  1. **事务一**：锁定 application，提交本地面试（`PENDING`、无链接、稳定 correlation ID）+ application→INTERVIEW 状态事件 + 审计，提交。
  2. **无事务外部调用**：`meetingProvisioning.provision(...)`（Google HTTP 永不持有 MySQL 事务或悲观锁）。
  3. **事务二**：按 interview ID + correlation ID 短事务写回 `READY`/`PENDING`/`FAILED`。
  外部失败不回滚本地面试；即使 provision 意外抛异常，面试也以 `FAILED`(UNAVAILABLE) 落库而非 500。
- Google 侧：JDK `HttpClient` + Jackson + `SecretCipher` + Google OAuth 配置，固定 host 仅 `oauth2.googleapis.com/token` 与 `www.googleapis.com/calendar/v3/...`。event 创建于 `primary`，`conferenceDataVersion=1`、`conferenceSolutionKey.type=hangoutsMeet`；标题为通用 `Recruitment interview`（无 PII），无 attendee、无 Calendar 邮件。

## 业务/契约规则

- `GOOGLE_MEET` 仅允许 `mode=ONLINE`（否则 422 `VALIDATION_ERROR`）。
- `GOOGLE_MEET` 不得携带 `locationOrMeetingUrl`（伪造链接被拒，最终链接仅来自 Google 响应）。
- 未连接 → 409 `GOOGLE_MEET_NOT_CONNECTED`；连接已吊销/失效 → 409 `GOOGLE_MEET_RECONNECT_REQUIRED`；配置缺失/不可用 → 409 `GOOGLE_MEET_PROVISIONING_UNAVAILABLE`。MANUAL 规则不变。

## token 生命周期与幂等

- 临近过期（<5 分钟）先刷新；刷新后的 token 重新 AES-GCM 加密落库；Google 未轮换 refresh token 时保留旧值。
- Calendar 401 → 刷新 + 重试**至多一次**。`invalid_grant` → `GoogleConnectionStatus` 扩展 `REVOKED`（`markRevoked`，无新表）并返回 `GOOGLE_MEET_RECONNECT_REQUIRED` 而非 500。
- 429/超时/5xx → 安全同步状态/错误码，绝不暴露 Google 响应体。
- 每个面试的 correlation ID 保留为 conference `requestId` 与 DB `meeting_correlation_id`；Calendar event ID 由 `GoogleCalendarEventId` 值对象确定性派生（去连字符 + `gmeet` 前缀，符合 Google 合法 ID `^[a-v0-9]{5,1024}$`），同一 correlation ID 恒得同一 event ID，供 409 冲突 GET 恢复与轮询 GET 幂等使用；409 冲突走 GET 恢复（不重复插入）；会议异步时最多轮询 3 次（约 ≤2s），否则 `PENDING`（不伪造 URL）。
- 仅接受 HTTPS `meet.google.com` 链接；非规范链接 → `FAILED(GOOGLE_MEET_LINK_INVALID)`。
- 全程不记录 access token/refresh token/verifier/state/完整第三方错误响应。

## 修改/新增文件

### 新增（`integration.google`）

- `ProvisionOutcome.java` / `ProvisionRequest.java` / `ProvisionResult.java` — provider 中立契约类型。
- `RefreshedToken.java` / `GoogleTokenRefreshException.java` / `GoogleTokenClient.java` / `HttpGoogleTokenClient.java` — 刷新 token grant（固定 endpoint、超时、`invalid_grant` 分类）。
- `CalendarEventSpec.java` / `CalendarEvent.java` / `GoogleCalendarException.java` / `GoogleCalendarClient.java` / `HttpGoogleCalendarClient.java` — Calendar create/get（固定 host、`conferenceDataVersion=1`、401/409/429/5xx 分类、`hangoutLink` 与 conferenceData video 解析）。
- `GoogleConnectionStore.java` — `@Transactional` 的 `updateTokens`/`markRevoked`，短事务持久化 token 刷新与吊销。

### 修改

- `integration/google/domain/GoogleConnectionStatus.java` — 增加 `REVOKED`。
- `integration/google/infrastructure/GoogleRecruiterConnectionEntity.java` — 增加 `markRevoked(now)`。
- `integration/google/MeetingProvisioningPort.java` — 增加 `requiresReconnect`、`ensureConnectionUsable` 与 `provision`，并明确 `provision` 不抛远程异常、`ensureConnectionUsable` 在任何本地事务/锁之前运行。
- `integration/google/application/MeetingProvisioningService.java` — 从"恒 false"重写为真实建会编排。
- `application/infrastructure/InterviewEntity.java` — 增加 `assignCorrelationId` / `markReady` / `markPending` / `markFailed` 领域方法。
- `application/application/InterviewService.java` — `create` 改为 `TransactionTemplate` 两段提交 + GOOGLE_MEET 预校验 + 失败兜底。
- `docs/openapi-v1.yaml` — 更新 `MeetingProvider` 描述（移除"尚不可用"）、`GoogleConnectionStatus` 增加 `REVOKED`、`locationOrMeetingUrl` 描述。
- `tasks/google-meet-integration-todo.md` / `tasks/google-meet-integration-plan.md` — 标记 Task 3 后端完成，剩余 UI/同步归入 Task 4。

### 测试

- 新增 `backend/src/test/java/com/adproject/integration/google/application/GoogleMeetProvisioningIntegrationTest.java` — fake Calendar/token transport 覆盖：成功、pending→轮询→ready、pending 无 URL、409 恢复不重复插入、过期 token 刷新、401 刷新重试一次、`invalid_grant` 吊销→RECONNECT_REQUIRED、瞬态失败→UNAVAILABLE、非 HTTPS 链接拒绝、未连接。
- 修改 `GoogleOAuthIntegrationTest` — `connectedRecruiterStillCannotProvisionMeeting` → `connectedRecruiterProvisionsMeetingAfterConnect`（连接后可建会，mock Calendar）。
- 修改 `RecruiterInterviewIntegrationTest` — 新增 GOOGLE_MEET 仅 ONLINE、拒绝客户端链接、吊销→RECONNECT_REQUIRED、成功写 READY+链接、失败仍提交本地面试（FAILED 状态、application 保持 INTERVIEW）。

## 测试与验证

### 命令

```
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\...\bin\mvn.cmd' -o -f backend\pom.xml test
```

（Maven 3.9.16 wrapper dist + Microsoft OpenJDK 21.0.8，离线模式）

### 结果

- 全量后端：`Tests run: 131, Failures: 0, Errors: 0, Skipped: 6`，`BUILD SUCCESS`（含本包后续验收问题复核修正，详见 `google-meet-calendar-provisioning-review-fixes.md`）。
- 6 个跳过为 `MySqlFlywayIntegrationTest`（本机未检测到 Docker/Testcontainers，故跳过；本次未新增 Flyway 迁移）。

## API / 数据库

- 无新增端点、无新增 Flyway 迁移；现有 `interviews` 的 `meeting_provider/meeting_sync_status/meeting_event_id/meeting_sync_error/meeting_correlation_id`（V9）与加密的 Google token（V10）复用。
- `Interview` DTO 仍只暴露 `meetingProvider` 与 `meetingSyncStatus`；`meetingEventId`/`meetingSyncError`/`meetingCorrelationId` 仅落库供后续同步使用，不通过 API 暴露。
- `GoogleConnectionStatus` 域枚举新增 `REVOKED`，`/status` 现在可能返回 `REVOKED`；OpenAPI 已同步。

## 限制

- 未接入真实 Google 凭据，未执行真实授权/建会；Calendar/token 调用均由测试 fake 模拟。
- 面试重排/取消的 Google 同步、后台定时补齐、Web 招聘者建会 UI、Android 最终状态展示均不在本包范围（见 `tasks/google-meet-integration-plan.md` Task 4）。
- `MySqlFlywayIntegrationTest` 因本机 Docker 环境不可用而跳过，建议在具备 Docker 的环境补跑端到端迁移校验。
