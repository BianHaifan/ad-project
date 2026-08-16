# 修改报告：Google Meet「重排与取消同步」审查修复包

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 修改范围：`backend/`（`InterviewService` 校验顺序与外部调用兜底 + 回归测试）、`docs/openapi-v1.yaml`（仅契约说明）、`tasks/google-meet-integration-*.md`（仅修复状态）、`change_report/`
- 明确禁止且未改动：Web、Android、Admin、ML、Agent、数据库迁移、真实 Google 凭据、依赖版本、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 安全复核）；未执行真实 Google 授权或真实 Calendar 调用；测试使用 fake transport，从未请求 Google。

## 目标

修复已审查确认的三项同步一致性问题，确保任何 Google Meet 同步中的面试都不会被错误更新，也不会因异常永久卡在 `PENDING`：

1. `PENDING` 的优先级与错误码（并发下旧版本误报 `VERSION_CONFLICT`）。
2. 不允许在 `PENDING` 时完成面试（否则可能留下 `COMPLETED + PENDING` 的不一致状态）。
3. 外部调用异常必须安全落库（否则面试永久停在 `PENDING`，且无独立重试 endpoint）。

## 修复内容

### 1. `PENDING` 的优先级与错误码

`InterviewService.updateGoogleMeet` 的重排/取消第一阶段（Phase 1）原先先比较 `expectedVersion`、后检查 `meetingSyncStatus == PENDING`。真实并发下，第一请求完成版本递增并置 `PENDING`，第二请求携带旧版本时会被误报为 `VERSION_CONFLICT`，而非约定的 `GOOGLE_MEET_SYNC_IN_PROGRESS`。

调整后的校验顺序（在 Phase 1 短事务内）：

1. `findByIdForUpdate` + 应用归属校验（跨公司返回 404，`requireJob` 在任何业务校验之前）；
2. `status == SCHEDULED` 校验（`INVALID_INTERVIEW_TRANSITION`）；
3. **`meetingSyncStatus == PENDING` 校验（`GOOGLE_MEET_SYNC_IN_PROGRESS`，先于版本比较）**；
4. `expectedVersion` 乐观锁比较（`VERSION_CONFLICT`）；
5. `meetingEventId` 存在校验（`GOOGLE_MEET_RECONNECT_REQUIRED`）；
6. `mode == ONLINE`、客户端不得提交链接的校验（`VALIDATION_ERROR`）。

结果：对任何处于 `PENDING` 的 Google Meet 更新均返回 409 `GOOGLE_MEET_SYNC_IN_PROGRESS`；非 PENDING 场景保留原有乐观锁 `VERSION_CONFLICT` 语义。跨公司用户始终在 `requireJob` 处得到 404 `NOT_FOUND`，无法通过该错误码探测资源状态（该保护位于 PENDING 校验之前，未变）。

### 2. 不允许在 `PENDING` 时完成面试

`status == COMPLETED` 分支原先未检查 `meetingSyncStatus`。现已加入同样的 `PENDING` 校验（同样先于 `expectedVersion`）：当 `meetingSyncStatus == PENDING` 时，`COMPLETED` 同样返回 409 `GOOGLE_MEET_SYNC_IN_PROGRESS`，不修改本地 status、时间、链接、sync error 或 version，也不调用 `updateMeeting`/`cancelMeeting`。正常的非 PENDING Google Meet `COMPLETED` 仍是纯本地操作，不调用 Google Calendar。

### 3. 外部调用异常必须安全落库

第一阶段已提交 `PENDING` 后，若 `meetingProvisioning.updateMeeting(...)` / `cancelMeeting(...)` 抛出未预期 `RuntimeException`（或返回 `null` / 非法的 `MeetingSyncResult`），原先第二阶段不会执行，面试会永久停在 `PENDING`。

新增私有辅助方法 `syncExternal(SyncPlan)` 在外部阶段统一保护：

- 仅 `catch (RuntimeException)`，不捕获 `Error`；
- 异常、`null` 结果、`outcome() == null` 的非法结果统一转换为 `MeetingSyncResult(FAILED, "GOOGLE_MEET_PROVISIONING_UNAVAILABLE")`；
- 仅记录异常类别（`e.getClass().getSimpleName()`），不记录 Token、Google 原始响应、Meet 链接或候选人敏感信息；
- 无论重排还是取消，第二阶段都会执行 `failSyncPreservingInvitation(...)`，本地面试保持 `SCHEDULED`、旧时间与旧 Meet 链接保留、`meetingSyncStatus=FAILED`、审计 `SYNC_FAILED`，并返回成功业务响应；后续同一 PATCH 可使用返回的新版本再次重试。

## 状态机与异常兜底

- 外部调用仍在数据库事务与悲观锁之外（两段式事务不变：Phase 1 预留 `PENDING` + 版本递增，外部 HTTP，Phase 2 按 `provider == GOOGLE_MEET && version == 预留版本 && syncStatus == PENDING` 写回）。
- 重排/取消成功：`completeGoogleReschedule` / `completeGoogleCancel` → `READY`（取消清空链接）。
- 远端失败（返回 `FAILED`）：`failSyncPreservingInvitation` → `FAILED`，保留旧邀请。
- 未预期异常 / `null` / 非法结果：同上安全降级为 `FAILED + GOOGLE_MEET_PROVISIONING_UNAVAILABLE`，绝不留在 `PENDING`。
- MANUAL 更新路径、`updateLocal` 完全不变。

## 修改/新增文件

### 修改

- `backend/src/main/java/com/adproject/application/application/InterviewService.java`
  - COMPLETED 分支：新增 `status` 校验后的 `rejectIfSyncInProgress`（先于版本比较）。
  - 重排/取消 Phase 1：重排校验顺序，`PENDING` 先于 `expectedVersion`。
  - 外部阶段：改用 `syncExternal(SyncPlan)` 包装，新增 `safeSyncFailure()` 与 `rejectIfSyncInProgress(InterviewEntity)` 私有辅助方法。
- `docs/openapi-v1.yaml`
  - PATCH 端点描述与 `MeetingSyncStatus` 描述更新为：`PENDING` 期间拒绝所有 PATCH 更新（重排、取消、完成），且该拒绝先于乐观锁版本比较；未预期外部异常同样安全降级为 `FAILED`。
- `tasks/google-meet-integration-todo.md` / `tasks/google-meet-integration-plan.md` — 标注本修复包完成，引用本报告。

### 测试（`RecruiterInterviewIntegrationTest`，fake transport）

新增 6 个回归测试：

- `googleMeetRejectsStaleVersionAsSyncInProgressWhenPending` — 构造真实同步保留状态（`meeting_sync_status=PENDING` 且版本 2→3），以旧 `expectedVersion=2` 发起重排与取消，均断言 409 `GOOGLE_MEET_SYNC_IN_PROGRESS`，且不调用外部 port。
- `googleMeetRejectsCompletionWhenSyncInProgress` — `PENDING` 时 `COMPLETED` 返回 409 `GOOGLE_MEET_SYNC_IN_PROGRESS`，本地 status/version/sync error 均不变，不调用外部 port。
- `googleMeetRescheduleUnexpectedExceptionFailsSafelyAndIsRetryable` — 重排 port 抛 `RuntimeException`：断言 200、`SCHEDULED`、旧时间与链接保留、`meetingSyncStatus=FAILED`、`meeting_sync_error=GOOGLE_MEET_PROVISIONING_UNAVAILABLE`、`SYNC_FAILED` 审计、返回版本 4（可重试）。
- `googleMeetCancelUnexpectedExceptionKeepsScheduledWithLink` — 取消 port 抛 `RuntimeException`：本地仍 `SCHEDULED`、链接保留、`FAILED`、`SYNC_FAILED` 审计。
- `googleMeetNullSyncResultFailsSafely` — port 返回 `null`：安全降级为 `FAILED + GOOGLE_MEET_PROVISIONING_UNAVAILABLE`。
- `googleMeetInvalidSyncResultFailsSafely` — port 返回 `new MeetingSyncResult(null, null)`：同样安全降级。

既有测试（`googleMeetCompletionIsLocalOnly`、`googleMeetRejectsUpdateWhenSyncInProgress` 等）继续通过，未回归。

## 测试与验证

### 命令

```
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -o -f backend\pom.xml test
```

（Maven 3.9.16 wrapper dist + Microsoft OpenJDK 21.0.8，离线模式）

### 结果

- 全量后端：`Tests run: 160, Failures: 0, Errors: 0, Skipped: 6`，`BUILD SUCCESS`。
- 6 个跳过为 `MySqlFlywayIntegrationTest`（本机未检测到 Docker/Testcontainers，故跳过；本次未新增 Flyway 迁移）。

## 未做事项

- 未接入真实 Google 凭据，未执行真实授权/PATCH/DELETE；Calendar/token 调用均由测试 fake 模拟。
- 未新增 endpoint、migration 或后台补偿任务；未实现 Web/Android UI、后台定时补齐、独立重试 endpoint。
- 未通过 API 暴露 `meetingEventId`、`meetingSyncError`、Token 或 Google 原始错误。
- `MySqlFlywayIntegrationTest` 因本机 Docker 环境不可用而跳过，建议在具备 Docker 的环境补跑端到端迁移校验。
