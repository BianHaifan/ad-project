# 修改报告：Google Meet 首次建会失败后的真正重试（后端）+ FAILED 旧 Meet 链接展示（Web）

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-15
- 修改范围：`backend/src/main/java/com/adproject/application/**`、`backend/src/test/java/com/adproject/application/**`、`web/src/pages/ApplicationDetailPage.tsx`、`web/src/pages/ApplicationPages.test.tsx`、`change_report/`
- 明确禁止且未改动：Google OAuth、Google client/transport、数据库迁移、OpenAPI、Android、Admin、ML、Agent、认证方式、依赖版本、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 复核）；未新增 API 端点 / DTO 字段 / 数据库字段；继续复用既有 `PATCH /recruiter/interviews/{id}`；未使用任何真实 Google 凭据或网络调用

## 背景

两个相互关联的缺陷：

1. **后端**：首次建会失败后无法真正重试。当 `GOOGLE_MEET` 面试在创建阶段 provisioning 失败时，会停留在 `SCHEDULED + FAILED` 且 `meetingEventId` 为空。此时对该面试发起 `PATCH` 重试，旧逻辑直接返回 `GOOGLE_MEET_RECONNECT_REQUIRED`，而不是重新调用 provisioning 端口——导致「先失败、再重试成功」的正常路径被完全堵死。旧测试 `googleMeetUpdateRejectsMissingEventId` 恰好固化了这一错误行为。
2. **Web**：对 `FAILED` 状态的面试，Web 一律隐藏旧的仍有效的 Meet 链接。当一次改期 / 取消同步在**已有 Calendar 事件**的面试上失败时，后端会保留原时间与 Meet 链接，但 Web 之前把链接藏掉，招聘者无法再访问那个仍然有效的会议。

## 完成内容

### 1. 后端：无事件 ID 的 `FAILED` 面试可真正重试（`InterviewEntity` + `InterviewService`）

只针对 `GOOGLE_MEET + SCHEDULED + FAILED + meetingEventId 为空`（首次建会失败）这一情况：

- 同一面试的 `PATCH` 重试改为**重新调用既有 provisioning 端口**（`meetingProvisioning.provision(...)`），而非返回 `GOOGLE_MEET_RECONNECT_REQUIRED`。
- 复用该面试**已持久化的 correlation ID**（`interview.getMeetingCorrelationId()`），**绝不创建第二个内部面试**；重试成功时写回既有面试的 `meetingEventId`、Meet URL、`READY` 与合并后的排期信息。
- 重试再次失败时保持 `SCHEDULED + FAILED`，不伪造链接，不丢失失败状态与 `meetingSyncError`。
- 保留全部既有约束：owner / version（乐观锁）校验、`PENDING` 优先级检查、`ONLINE` 模式校验、不泄露 event id / correlation id / token / Google 原始错误。

关键改动：

- `InterviewEntity.completeGoogleProvisionRetry(eventId, meetingUrl, scheduledAt, timezone, durationMinutes, note, now)`：一次版本自增内写入恢复后的 event id + URL + `READY` + 合并排期。
- `InterviewService.provisionMeeting(...)` 下沉为低层签名（直接接收 recruiterId / correlationId / 排期参数），供「创建」与「重试」两条路径复用。
- `SyncPlan` 扩展 `correlationId / provision / localCancel` 字段，新增 `provision(...)` 与 `localCancel(...)` 工厂。
- `updateGoogleMeet` 第一阶段：先做 mode/location 校验，再把「无 event id 即拒绝」改为分支：
  - 无 event id 且非 `FAILED` → 仍返回 `GOOGLE_MEET_RECONNECT_REQUIRED`（保持既有语义）。
  - 无 event id 且 `FAILED` 且 `cancel` → **本地取消**（`interview.cancel(now)` + `CANCELLED` 审计），不调用外部 Google cancel，不要求重连。
  - 无 event id 且 `FAILED` 且非 cancel → `beginSync` 后走 `SyncPlan.provision(...)` 重新建会。
- 第一阶段后新增分支：`localCancel` 直接回读返回；`provision` 调用 provisioning 端口并通过 `writeBackProvisionRetry(...)` 写回（`READY` → `completeGoogleProvisionRetry` + `RESCHEDULED` 审计；`PENDING` → `markPending` 无审计；其余 → `failSyncPreservingInvitation` + `SYNC_FAILED` 审计；写回受 `provider==GOOGLE_MEET && version==reservedVersion && syncStatus==PENDING` 守卫）。

### 2. 后端：无事件 ID 首次失败的取消为本地取消

无 event id 的首次失败面试取消：仅本地 `CANCELLED`，不调用外部 Google cancel、不要求重连；**不影响**「已有 event id」面试的取消同步路径（既有 `cancelMeeting` 行为保持不变）。

### 3. Web：`FAILED` 状态保留旧 Meet 链接并标注「unchanged」（`ApplicationDetailPage.tsx`）

- 新增纯函数 `retainedGoogleMeetLink(interview)`：仅当 `GOOGLE_MEET + FAILED + SCHEDULED` 且 `locationOrMeetingUrl` 为合法 `https://meet.google.com/...` 时返回链接。
- 面试卡渲染新增：`retainedMeetLink` 存在时显示「Existing Google Meet link (unchanged)」+ 可点击链接，**不显示「Synced」**、不声称新时间 / 链接已生效。
- 若后端无链接（首次建会失败）则继续不显示链接，`Retry Google Meet` 按钮保留（复用 `updateInterview`，不调用 `createInterview`）。

### 4. 测试

- 后端（`RecruiterInterviewIntegrationTest`）：
  - 删除固化了旧错误行为的 `googleMeetUpdateRejectsMissingEventId`。
  - 新增 3 个用例 + 辅助 `scheduleGoogleMeetInitialFailure`：
    - 首次失败 → PATCH 重试成功 → `READY`（`meetingEventId`/Meet URL/`scheduledAt` 写回、version 4、**面试数仍为 1**、`provision` 被调用 2 次且重试的 correlationId 等于持久化的 correlationId）。
    - 首次失败 → PATCH 再次失败 → 保持 `SCHEDULED + FAILED`、空链接、`syncError` 保留、`provision` 被调用 2 次。
    - 首次失败 → 取消 → 本地 `CANCELLED`（`cancelMeeting`/`updateMeeting` 均 `never` 调用）。
  - 既有「有 event id 的 FAILED 继续走既有重试」「version 冲突 / PENDING 冲突 / 跨公司 / 错误角色」回归不受影响。
- Web（`ApplicationPages.test.tsx`）：
  - 更新 `offers a safe retry for a failed Google Meet...`：`FAILED + 保留链接` → 显示 `https://meet.google.com/abc-def` + 「Existing Google Meet link (unchanged)」+ 无「Synced」+ 无「Reschedule」；重试仍走 `updateInterview`（`createInterview` 不被调用）。
  - 新增 fixture `googleMeetFailedNoLink` 与用例「首次建会失败无链接 → 无链接 + 无 'unchanged' + 仍有 Retry」。

## 影响文件

- `backend/src/main/java/com/adproject/application/infrastructure/InterviewEntity.java`（修改）：新增 `completeGoogleProvisionRetry`。
- `backend/src/main/java/com/adproject/application/application/InterviewService.java`（修改）：`provisionMeeting` 下沉、`SyncPlan` 扩展、`updateGoogleMeet` 分支、新增 `writeBackProvisionRetry`。
- `backend/src/test/java/com/adproject/application/RecruiterInterviewIntegrationTest.java`（修改）：移除旧测试，新增 3 个用例。
- `web/src/pages/ApplicationDetailPage.tsx`（修改）：新增 `retainedGoogleMeetLink` 与「unchanged」链接渲染。
- `web/src/pages/ApplicationPages.test.tsx`（修改）：更新 1 个用例、新增 1 个用例与 fixture。
- `change_report/web-google-meet-scheduling-ui.md`（修改）：更正「FAILED 一律隐藏链接」的过时描述。
- `change_report/google-meet-initial-provisioning-retry.md`（本报告，新增）。

## API 与数据库变化

- 无。未新增 API 端点 / DTO 字段 / 数据库字段，未改动 OpenAPI / 数据库迁移。继续复用既有 `PATCH /recruiter/interviews/{id}` 与既有 `Interview` 字段；未向客户端暴露 event id / correlation id / token / Google 原始错误。

## 测试命令与结果

后端（`RecruiterInterviewIntegrationTest`，Maven surefire）：

```
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0  → BUILD SUCCESS
```

Web（在 `web/` 执行）：

```powershell
npm run typecheck   # 通过（tsc -b）
npm run lint        # 通过（eslint .）
npm test            # 18 个测试文件、160 个用例全部通过（ApplicationPages 25 个）
npm run build       # 通过（tsc -b && vite build）
```

> 注：`npm run build` 会重新生成 `web/dist/`（构建产物），本包未手工编辑 `web/dist` / `web/node_modules`。

## 未做事项

- 未接入真实 Google 凭据，未做任何真实网络调用；真实授权仍需人工配置。
- 未改动 Google OAuth / Google client / transport / 数据库迁移 / OpenAPI / Android / Admin / ML / Agent / 认证方式 / 依赖版本。
- 未提交、未推送（等待 Codex 复核）。
