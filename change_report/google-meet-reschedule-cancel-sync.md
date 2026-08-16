# 修改报告：Google Meet 面试「重排与取消」后端同步

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 修改范围：`backend/`（`integration.google` 会议更新/取消实现 + `application` 面试服务两段式同步 + 对应回归测试）、`docs/openapi-v1.yaml`、`tasks/google-meet-integration-*.md`、`change_report/`
- 明确禁止且未改动：Web/Android UI、后台定时任务、独立重试 endpoint、真实 Google 授权、邮件邀请、Teams、ML、Agent、Admin、无关重构、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 安全复核）；未启动真实授权、未填入真实密钥；测试使用 fake transport，从未请求 Google。

## 目标与结果

当已连接的招聘者重排或取消一个已成功建会的 Google Meet 面试时，后端同步更新/删除招聘者 Google `primary` 日历上的对应 event，并把最终状态写回面试记录。MANUAL 流程与 GOOGLE_MEET 的「完成（COMPLETED）」完全不变（COMPLETED 为纯本地操作，无外部 counterpart）。

核心结果：

- **重排**：仅 `PATCH` 已有 event（更新 summary/start/end/timeZone），**绝不发送 conferenceData**，因此永不产生第二个 Meet 链接；`mode` 恒为 `ONLINE`，最终链接保持服务端已有链接，客户端不得提交/修改 `locationOrMeetingUrl`。
- **取消**：`DELETE` 已有 event；2xx **或 404（远端已不存在）** 都视为外部取消完成，随后本地置 `CANCELLED` 并清空链接。
- **远端失败**：本地面试保持 `SCHEDULED` 与原有时间/链接，仅 `meetingSyncStatus=FAILED` + 安全错误码，同一 PATCH 可重试。

## 架构与事务边界

`InterviewService.update` 由原先单 `@Transactional` 方法重构为按 provider 分派：

- `update`（入口）→ `findById` 判 provider → `updateLocal`（MANUAL，及 GOOGLE_MEET 的 COMPLETED）或 `updateGoogleMeet`（重排/取消）。
- `updateLocal` 与 GOOGLE_MEET COMPLETED 均为**单短事务**，逻辑与旧 `update` 完全一致（乐观版本、状态机、审计）。
- `updateGoogleMeet` 采用**两段式事务**，Google HTTP 永不持有 MySQL 事务或悲观锁：
  1. **事务一（短）**：`findByIdForUpdate` 悲观锁 → 校验归属/`expectedVersion`/`status=SCHEDULED`/`meetingProvider`/`meetingEventId`/非 PENDING → 校验 ONLINE/无客户端链接 → 保留候选人可见的旧时间与链接快照 → `beginSync()`（`meetingSyncStatus=PENDING`、清空错误、`version++` 作为同步预留）→ 提交。
  2. **无事务外部调用**：`meetingProvisioning.updateMeeting(...)` 或 `cancelMeeting(...)`（PATCH/DELETE 在锁与事务之外）。
  3. **事务二（短）**：按 interview ID 重新 `findByIdForUpdate`，**仅当**仍是 `GOOGLE_MEET` + `version == 预留版本` + `meetingSyncStatus == PENDING` 才写回；重排成功 → 应用新时间/时区/时长/note、保留 URL 与 eventId、`READY`；取消成功 → `CANCELLED` + 清空 URL、`READY`；失败 → 保留旧时间/链接/状态、`FAILED` + 安全码 → 审计 → 提交。

并发保护：`PENDING` 时再次更新 → 409 `GOOGLE_MEET_SYNC_IN_PROGRESS`；事务二发现预留版本被并发改动 → 同样 409 `GOOGLE_MEET_SYNC_IN_PROGRESS`，防止覆盖外部调用期间的并发写。

## 业务/契约规则

- GOOGLE_MEET 重排/取消要求：`status=SCHEDULED` + 已有 `meetingEventId`（缺 eventId → 409 `GOOGLE_MEET_RECONNECT_REQUIRED`，表示无外部 event 可同步）。
- `mode` 必须保持 `ONLINE`（提交非 ONLINE → 422 `VALIDATION_ERROR`）。
- 客户端不得提交/修改 `locationOrMeetingUrl`（提交非空链接 → 422 `VALIDATION_ERROR`；最终链接仅由服务端管理）。
- 远程失败错误码安全：连接缺失 → `GOOGLE_MEET_NOT_CONNECTED`；连接已吊销 → `GOOGLE_MEET_RECONNECT_REQUIRED`；401 刷新重试一次后再失败 / 429 / 5xx / 超时 → `GOOGLE_MEET_PROVISIONING_UNAVAILABLE`；`invalid_grant` → 标记 `REVOKED` + `GOOGLE_MEET_RECONNECT_REQUIRED`。绝不暴露 Google 响应体。

## 修改/新增文件

### 新增（`integration.google`）

- `MeetingSyncOutcome.java`（`SYNCED`/`FAILED`）、`MeetingUpdateRequest.java`、`MeetingCancelRequest.java`、`MeetingSyncResult.java` — provider 中立的「更新/取消已有会议」契约。
- `application/CalendarEventPatch.java` — 仅携带 summary/start/end/timezone，明确无 conferenceData。

### 修改

- `application/GoogleCalendarClient.java` — 增加 `patchEvent` / `deleteEvent` 声明（patch 永不发送 conference data；delete 将 404 视为成功）。
- `application/HttpGoogleCalendarClient.java` — 实现 `patchEvent`（`PATCH .../events/{id}?conferenceDataVersion=1&sendUpdates=none`，body 仅 summary/start/end）与 `deleteEvent`（`DELETE ...?sendUpdates=none`，2xx 或 404 返回成功，401→UNAUTHORIZED，其余→TRANSIENT）；`@Autowired` 主构造 + package-private 可注入 transport 构造（供测试）。
- `MeetingProvisioningPort.java` — 增加 `updateMeeting` / `cancelMeeting`，明确失败不抛远程异常、返回安全码。
- `application/MeetingProvisioningService.java` — 实现 `updateMeeting` / `cancelMeeting`，共享 `mutate` 编排（连接校验 → 临近过期刷新 → `withAuthRetry` 401 刷新重试一次 → `invalid_grant` 吊销 → 安全错误码）。
- `application/infrastructure/InterviewEntity.java` — 增加 `beginSync` / `completeGoogleReschedule` / `completeGoogleCancel` / `failSyncPreservingInvitation`（失败时**不清空** URL，与创建失败的 `markFailed` 区分）。
- `application/domain/InterviewAuditAction.java` — 增加 `SYNC_FAILED`。
- `application/application/InterviewService.java` — `update` 分派 + `updateLocal` + `updateGoogleMeet`（两段式）+ `SyncPlan` 记录；`snapshot` 增加 `meetingProvider`/`meetingSyncStatus`/`meetingSyncError`；`reasonFor` 增加 `SYNC_FAILED`。
- `docs/openapi-v1.yaml` — 更新 `MeetingSyncStatus`（PENDING/READY/FAILED 语义，取消后 READY 表示外部取消完成）、`MeetingProvider`（重排/取消同步）、`UpdateInterviewRequest.locationOrMeetingUrl`（服务端管理）、PATCH 端点描述（两段式同步、`GOOGLE_MEET_SYNC_IN_PROGRESS`、失败保留原邀请可重试）。
- `tasks/google-meet-integration-plan.md` / `tasks/google-meet-integration-todo.md` — Task 4 后端完成标注，Android 展示与 Web UI 剩余。

### 测试

- `GoogleMeetProvisioningIntegrationTest` — 新增：update 仅 PATCH 不 insert、patch 仅时间字段、cancel 仅 DELETE 不 insert、update/cancel 瞬态失败→UNAVAILABLE、401 刷新重试一次、`invalid_grant`→REVOKED、未连接→NOT_CONNECTED、已吊销连接→RECONNECT_REQUIRED。
- `HttpGoogleCalendarClientTest`（新增）— 聚焦 HTTP 层：DELETE 404 视为成功、500→TRANSIENT、401→UNAUTHORIZED、PATCH 使用 `conferenceDataVersion=1&sendUpdates=none` 且 body **不含** conferenceData/createRequest。
- `RecruiterInterviewIntegrationTest` — 新增：重排成功（PATCH 仅一次、链接保留、READY、版本精确返回）、取消成功（DELETE 一次、链接清空）、重排远端失败（SCHEDULED + 旧时间/链接 + FAILED + `SYNC_FAILED` 审计 + 同 PATCH 重试成功）、取消远端失败（保持 SCHEDULED + 链接）、PENDING→409 `GOOGLE_MEET_SYNC_IN_PROGRESS`、非 ONLINE/客户端链接→422、缺 eventId→409、COMPLETED 纯本地（无 update/cancel 调用）、外部调用前已提交 PENDING（`thenAnswer` 内断言 DB 已 PENDING）。MANUAL 重排/完成/取消由既有用例回归覆盖。

## 测试与验证

### 命令

```
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\...\bin\mvn.cmd' -o test
```

（Maven 3.9.16 wrapper dist + Microsoft OpenJDK 21.0.8，离线模式，`backend/` 目录下执行）

### 结果

- 全量后端：`Tests run: 154, Failures: 0, Errors: 0, Skipped: 6`，`BUILD SUCCESS`。
- 6 个跳过为 `MySqlFlywayIntegrationTest`（本机未检测到 Docker/Testcontainers，故跳过；本次未新增 Flyway 迁移）。

## API / 数据库

- 无新增端点、无新增 Flyway 迁移；复用 V9 `interviews` 的 `meeting_provider/meeting_sync_status/meeting_event_id/meeting_sync_error/meeting_correlation_id` 与 V10 加密 Google token。
- 新增/明确错误码：`GOOGLE_MEET_SYNC_IN_PROGRESS`（同步进行中，重排/取消拒绝）；`GOOGLE_MEET_RECONNECT_REQUIRED`（连接吊销/失效，或该面试无外部 event 可同步）。
- 审计新增 `SYNC_FAILED` action；`Interview` DTO 仍只暴露 `meetingProvider`/`meetingSyncStatus`，`meetingEventId`/`meetingSyncError` 不通过 API 暴露。

## 限制

- 未接入真实 Google 凭据，未执行真实授权/PATCH/DELETE；Calendar/token 调用均由测试 fake 模拟。
- Web 招聘者 UI 的同步状态渲染、Android 最终状态展示、后台定时补齐、独立重试 endpoint 均不在本包范围（见 `tasks/google-meet-integration-plan.md` Task 4）。
- 一个已 `FAILED` 且**无 `meetingEventId`** 的 GOOGLE_MEET 面试（创建期建会失败）无法通过本同步路径重排或取消（缺 eventId 被拒）；招聘者需重新建立 Meet（连接或重排前需先获得有效 event），此为当前契约的既定边界。
- `MySqlFlywayIntegrationTest` 因本机 Docker 环境不可用而跳过，建议在具备 Docker 的环境补跑端到端迁移校验。
