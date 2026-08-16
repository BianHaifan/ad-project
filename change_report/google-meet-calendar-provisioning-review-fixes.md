# 修改报告：Google Calendar / Meet 建会包验收问题复核修正

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 修改范围：`backend/`（`integration.google` 建会实现 + `application` 面试服务预检/事务边界 + 对应回归测试）、`change_report/`
- 明确禁止且未改动：Web/Android UI、面试重排/取消同步、后台定时任务、重试 endpoint、真实 Google 授权、`web/dist`、`web/node_modules`；未新增 Flyway 迁移、公开 endpoint、Google SDK、真实密钥；未记录/泄露 token/verifier/state/第三方响应体。
- 未提交、未推送（等待 Codex 安全复核）。

## 本次修复的 4 个根因

### 根因 1：Calendar event ID 非法且不可幂等

原 `MeetingProvisioningService.provision` 把带连字符的 UUID correlation ID 直接作为 `spec.eventId()` 传给 Google Calendar，违反 Google event ID 仅允许小写 `a-v` 与数字 `0-9` 的格式约束。同时"correlation ID = event ID"是隐式约定，409 冲突恢复 GET 与轮询 GET 都依赖同一 ID；若该 ID 非法或随机变化，恢复/轮询无法稳定命中同一 event。

**修复**：新增纯值对象 `GoogleCalendarEventId`，`fromCorrelationId(correlationId)` 对 correlation ID 去连字符 + 小写 + 加 `gmeet` 前缀，并校验 `^[a-v0-9]{5,1024}$`。`provision` 用派生后的合法 event ID 作为 `spec.eventId()`，correlation ID 仍原样作为 conference `requestId` 与 DB `meeting_correlation_id` 落库。insert、409 恢复 GET、轮询 GET 三处统一使用该派生 ID；同一 correlation ID 恒得同一 event ID（幂等，非随机）。

### 根因 2：REVOKED 分支不可达 + OAuth 回调不恢复状态

`InterviewService.create` 原按 `isConnected()` → `requiresReconnect()` 顺序判断，而 REVOKED 状态并非 CONNECTED，导致 REVOKED 永远先命中 `GOOGLE_MEET_NOT_CONNECTED`，`requiresReconnect` 分支是死代码。且 OAuth 成功回调对已存在连接只 `replaceTokens`，不把 `status=REVOKED` 恢复为 `CONNECTED`，用户重连后状态仍卡在 REVOKED。

**修复**：
- `InterviewService.create` 改为单一 `ensureMeetingConnectionUsable(principal.userId())`（内部委托 `MeetingProvisioningPort.ensureConnectionUsable`），实现内**先判 REVOKED → `RECONNECT_REQUIRED`，再判非 CONNECTED → `NOT_CONNECTED`**。
- `GoogleRecruiterConnectionEntity` 新增 `reconnect(...)`：更新 token/expiry/updatedAt/version++ 并把 `status` 恢复为 `CONNECTED`；`GoogleOAuthService.handleCallback` 更新已存在连接时改用 `reconnect(...)`。
- `GET /api/v1/recruiter/google-oauth/status` 对 REVOKED 返回 `connected=false, status=REVOKED`。

### 根因 3：过期 refresh token 的 invalid_grant 判定晚于本地创建事务

原实现只在 `provision`（本地事务已提交之后）才尝试刷新并识别 `invalid_grant`；一旦命中，本地面试与 application 状态已创建/变更，无法在创建前拦截。且刷新 Google 调用可能落在本地业务事务/悲观锁之后。

**修复**：`MeetingProvisioningPort` 新增 `ensureConnectionUsable(recruiterId)`，`InterviewService.create` 在**任何本地事务/锁之前**调用。实现：access token 仍有效 → 不调 Google；临近过期 → 预检刷新（成功持久化并放行；`invalid_grant` → 短事务 `markRevoked` + 抛 `RECONNECT_REQUIRED`；瞬态 → 抛 `UNAVAILABLE`）。预检之后的 Calendar 瞬态失败属于真实外部状态，仍按 `FAILED`/`PENDING` 落库，不回滚本地面试。

### 根因 4：Calendar 401 刷新重试在三处散落重复

insert、409 恢复 GET、轮询 GET 各自实现"401 → 刷新 → 重试一次"，逻辑重复且不一致，存在多次刷新/无限重试风险；refresh 的 `invalid_grant` 未统一收敛到"标记 REVOKED + 安全 reconnect 结果"。

**修复**：抽取 `withAuthRetry(...)` 辅助，统一"执行 Calendar 调用，遇一个 401 刷新一次并重试一次"（无循环、无无限重试），insert/409 恢复 GET/轮询 GET 复用。refresh 命中 `invalid_grant` → `markRevoked` + 安全 reconnect 结果，不泄露 Google 响应体。

## 测试覆盖

- `GoogleMeetProvisioningIntegrationTest`（新增 4）：
  - `eventIdIsValidAndDeterministic` — 断言传给 `GoogleCalendarClient` 的 event ID 匹配 `^[a-v0-9]{5,1024}$`、不含 `-`、同一 correlation ID 恒得同一 ID、`requestId` 仍为原始 correlation ID。
  - `provisionRecoversViaGetOnConflictWhenRecoveryGetUnauthorized` — 409 恢复 GET 首次 401 → 刷新 + 成功。
  - `provisionPollsThroughUnauthorizedRefresh` — 轮询 GET 首次 401 → 刷新 + 成功。
  - `provisionDoesNotRefreshAgainWhenRetryStillUnauthorized` — 重试仍 401/失败 → 不再刷新，仅刷新一次。
- `GoogleOAuthIntegrationTest`（新增 3）：
  - `statusReportsRevokedAsDisconnected` — REVOKED 返回 `connected=false, status=REVOKED`。
  - `revokedConnectionRecoversAfterReconnectAndCanProvisionAgain` — REVOKED → 创建返回 `RECONNECT_REQUIRED` 且不建会、application 保持 IN_REVIEW → re-OAuth 回调恢复 CONNECTED → 可再建会（201 READY）。
  - `expiredTokenInvalidGrantPreflightRejectsBeforeInterviewCreation` — 过期 token + fake `invalid_grant` → 409 `RECONNECT_REQUIRED`，interviews 计数不变、application 仍 IN_REVIEW、connection 变 REVOKED。
- `RecruiterInterviewIntegrationTest`（更新 5）：GOOGLE_MEET 用例改用 `ensureConnectionUsable` stub（`NOT_CONNECTED` / `PROVISIONING_UNAVAILABLE` / `RECONNECT_REQUIRED` / 成功 / 失败）。

## 测试与验证

### 命令

```
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\...\bin\mvn.cmd' -o -f backend\pom.xml test
```

（Maven 3.9.16 wrapper dist + Microsoft OpenJDK 21.0.8，离线模式）

### 结果

- 全量后端：`Tests run: 131, Failures: 0, Errors: 0, Skipped: 6`，`BUILD SUCCESS`。
- 6 个跳过为 `MySqlFlywayIntegrationTest`（本机未检测到 Docker/Testcontainers；本次未新增 Flyway 迁移）。
- 修正了一个测试隔离问题：`GoogleOAuthIntegrationTest` 非 `@Transactional`（共享 H2），两个建会用例原先都 mock 固定 event ID `evt-123`，第二次写回触发 `uk_interviews_meeting_event` 唯一约束（`23505`）而 409；改为各自独立的 event ID（`evt-connected`/`evt-reconnect`）后消除碰撞。

## API / 数据库

- 无新增端点、无新增 Flyway 迁移、无新增 Google SDK/真实密钥。
- 新增 `MeetingProvisioningException`（运行时异常，携带安全 `code`），`InterviewService` 将其映射为 `ApiException`，绝不向客户端泄露 Google 响应体或 token。
- `interviews.meeting_event_id` 现在存 `GoogleCalendarEventId` 派生的合法 event ID；`meeting_correlation_id` 仍存原始 correlation ID，语义与之前一致（仅落库，不通过 API 暴露）。

## 限制

- 未接入真实 Google 凭据，未执行真实授权/建会；Calendar/token 调用均由测试 fake 模拟。
- 面试重排/取消的 Google 同步、后台定时补齐、Web 招聘者建会 UI、Android 最终状态展示仍不在本包范围。
- `MySqlFlywayIntegrationTest` 因本机 Docker 环境不可用而跳过，建议在具备 Docker 的环境补跑端到端迁移校验。
