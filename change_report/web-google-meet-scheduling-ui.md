# 修改报告：招聘者 Web 的 Google Meet 面试排期与同步状态展示（Task 4 Web 部分）

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-15
- 修改范围：`web/`、`tasks/google-meet-integration-*.md`、`change_report/`
- 明确禁止且未改动：`backend/`、Android、Admin、ML、Agent、数据库迁移、认证方式、OpenAPI、依赖版本、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 复核）；未启动真实授权，未写入任何真实 Google 密钥 / token / redirect URL / 个人账号信息
- 仅消费既有后端接口与既有 DTO 字段，未新增 mock 字段，未自行定义后端行为

## 背景

后端已完成 Google Meet 建会（`change_report/google-meet-calendar-provisioning.md`）与改期/取消同步（`change_report/google-meet-reschedule-cancel-sync.md`），招聘者 Web 已具备连接状态页（`change_report/web-google-oauth-connection-ui.md`）。本包补齐招聘者 Application Detail 页的排期 UI：在面试弹窗中选择自动创建 Google Meet，并按 `meetingSyncStatus` 渲染 `PENDING / READY / FAILED` 同步状态，同时保留手动排期流程。

## 完成内容

### 1. 排期弹窗：会议提供方选择（`web/src/pages/ApplicationDetailPage.tsx`）

- 新增 `MEETING PROVIDER` 选择，默认 `MANUAL`（保留手动 link / location / phone）。
- 仅当 `mode === 'ONLINE'` 且连接状态为 `CONNECTED` 时 `GOOGLE_MEET` 可选（`googleMeetAvailable`），否则该选项 `disabled`。
- 选择 `GOOGLE_MEET`：强制并锁定 `ONLINE`（MODE 选择禁用），隐藏并清空手动会议链接输入，提交 `meetingProvider: 'GOOGLE_MEET'`，**不发送** `locationOrMeetingUrl`；切回 `MANUAL` 恢复既有校验与字段。
- 弹窗内读取 Google 连接状态（`useGoogleConnection`，`refetchOnMount: 'always'`）：
  - `CONNECTED` + 已选 `GOOGLE_MEET`：显示「Connected to Google Calendar — a Meet link will be created automatically.」
  - `DISCONNECTED`：解释并给出 `/recruiter/google-oauth` 入口（Go to Integrations）。
  - `REVOKED`：解释授权已失效并给出重连入口（Reconnect Google）。
  - 状态读取失败：安全降级为仅手动排期，`GOOGLE_MEET` 不可用，并给出检查 Integrations 的引导。
- 全程不渲染 token / OAuth 参数 / 原始 Google 错误 / 后端内部异常。

### 2. 可操作的后端错误文案（`presentInterviewError`）

新增四个安全、可操作的映射（不影响既有 version-conflict / permission / 404 / network 处理）：

- `GOOGLE_MEET_NOT_CONNECTED` → 提示先在 Integrations 连接 Google Calendar。
- `GOOGLE_MEET_RECONNECT_REQUIRED` → 授权已失效，去 Integrations 重连。
- `GOOGLE_MEET_PROVISIONING_UNAVAILABLE` → Google Meet 暂不可用，可稍后重试或手动排期。
- `GOOGLE_MEET_SYNC_IN_PROGRESS` → 已有同步进行中，等待完成后再操作。

均只引导到 `/recruiter/google-oauth`，不做自动跳转或认证改动。

### 3. 面试卡：同步状态渲染

- `MANUAL` → 沿用既有展示与可编辑字段，行为不变。
- `GOOGLE_MEET + PENDING` → 显示「Creating or syncing the Google Meet. The link appears once it is ready.」，不渲染任何虚假可点击链接，并禁用改期 / 标记完成 / 取消。
- `GOOGLE_MEET + READY` → 仅当 `SCHEDULED` 且为合法 `https://meet.google.com/…` 链接时，渲染可点击 Meet 链接并显示「Synced」。
- `GOOGLE_MEET + FAILED` → 显示「Google Meet sync failed. The candidate still sees the original meeting details.」，不显示「Synced」。
  - 若后端仍返回合法 `https://meet.google.com/...` 链接（曾成功建会，但后续改期 / 取消同步失败，旧链接仍有效），显示该链接并明确标注「Existing Google Meet link (unchanged)」。
  - 若后端无链接（首次建会失败），不显示链接。
  - 两种情况下，仍可重试的 `SCHEDULED` 面试都保留「Retry Google Meet」（复用 update 端点 + 当前 `expectedVersion`，绝不创建第二个面试）。

> 更正（2026-08-15，见 `google-meet-initial-provisioning-retry.md`）：本报告初版曾表述「FAILED 一律无链接」，不准确。实际行为为「已有事件 / 旧链接仍有效时展示旧链接并标注 unchanged；首次建会失败无链接时不展示」，Retry 始终复用 update 端点。
- `CANCELLED` → 无 Meet 链接；`COMPLETED` → 与终态行为一致（无操作，仅提示不可再变更）。
- 新增 `MeetingSyncBadge`（Syncing / Synced / Sync failed）与 `googleMeetLink()`（严格判定：`GOOGLE_MEET` + `READY` + `SCHEDULED` + 合法 HTTPS Meet URL 才返回链接）。

### 4. 改期弹窗：锁定提供方与链接 + 重试文案

- `GOOGLE_MEET` 面试的改期弹窗：MODE 与 MEETING PROVIDER 为只读（Online / Google Meet），不提供提供方或手动链接编辑；提交仅发送后端允许的改期字段 + `expectedVersion`（`scheduledAt / timezone / durationMinutes / note / expectedVersion`，不含 `mode` / `locationOrMeetingUrl` / `meetingProvider`）。
- `MANUAL` 面试保持既有可编辑 link / location / contact。
- `FAILED` 重试复用同一弹窗，但标题 / 描述 / 按钮文案切换为「Retry Google Meet sync / Retrying updates the existing Google Meet — no new interview is created. / Retry sync」，明确是重试同步而非新建面试。

### 5. 视觉（`web/src/theme/global.css`）

追加最小样式 `.badge.sync_pending / .badge.sync_ready / .badge.sync_failed`，复用既有 badge 语言与配色变量，未引入新设计系统或依赖。

### 6. 测试（`web/src/pages/ApplicationPages.test.tsx`）

新增 11 个用例，覆盖：

- 连接状态为 `CONNECTED` 时选择 `GOOGLE_MEET`、不发送手动链接、提交 `meetingProvider: 'GOOGLE_MEET'`（断言 API 输入，非仅内部状态）。
- `DISCONNECTED` / `REVOKED` 时 `GOOGLE_MEET` 不可用，且给出对应连接入口。
- `PENDING`：无链接、禁用改期/完成/取消。
- `READY`：渲染可点击 HTTPS Meet 链接 +「Synced」。
- `FAILED`：安全重试（无第二面试、`createInterview` 未被调用、`updateInterview` 复用当前 `expectedVersion`）。
- `CANCELLED`：无 Meet 链接。
- 4 个 `GOOGLE_MEET_*` 错误码映射为安全文案（不含后端私有错误文字）。

既有手动排期用例保持通过（未回归）。

## 影响文件

- `web/src/pages/ApplicationDetailPage.tsx`（修改）：排期弹窗提供方选择 + 连接门控、同步状态渲染、改期锁定与重试文案、4 个错误码映射。
- `web/src/theme/global.css`（修改）：追加 3 个同步状态 badge 样式。
- `web/src/pages/ApplicationPages.test.tsx`（修改）：新增 11 个 Task 4 Web 用例。
- `tasks/google-meet-integration-plan.md` / `tasks/google-meet-integration-todo.md`（修改）：仅标记本包完成的 Task 4 Web 项。
- `change_report/web-google-meet-scheduling-ui.md`（本报告，新增）。

## API 与数据库变化

- 无。未改动后端 API、OpenAPI、数据库迁移或任何前端 API 契约。本包仅消费既有 `Interview.meetingProvider / meetingSyncStatus`、`CreateInterviewRequest.meetingProvider?`、`UpdateInterviewRequest`（无 `meetingProvider`）与四个既有 `GOOGLE_MEET_*` 错误码。

## 测试命令与结果

在 `web/` 执行：

```powershell
npm run typecheck   # 通过（tsc -b）
npm run lint        # 通过（eslint .）
npm test            # 18 个测试文件、159 个用例全部通过（新增 11 个）
npm run build       # 通过（tsc -b && vite build）
```

本包相关新增用例（`pages/ApplicationPages.test.tsx`，24 个用例，其中 11 个新增）：

- 已连接 → 选择 `GOOGLE_MEET` 提交且不含手动链接（断言 `meetingProvider: 'GOOGLE_MEET'`、无 `locationOrMeetingUrl`）。
- `DISCONNECTED` / `REVOKED` → `GOOGLE_MEET` 禁用 + 连接入口（Go to Integrations / Reconnect Google）。
- `PENDING` → 无链接 + 禁用改期/完成/取消。
- `READY` → 可点击 HTTPS Meet 链接 +「Synced」。
- `FAILED` → 「Sync failed」+ 安全重试（复用 update、`createInterview` 未调用）。
- `CANCELLED` → 无 Meet 链接。
- 3 个建会路径错误码（`GOOGLE_MEET_NOT_CONNECTED` / `GOOGLE_MEET_RECONNECT_REQUIRED` / `GOOGLE_MEET_PROVISIONING_UNAVAILABLE`）+ 1 个改期路径错误码（`GOOGLE_MEET_SYNC_IN_PROGRESS`）→ 安全文案，不泄露后端私有错误。

> 注：`npm run build` 会重新生成 `web/dist/`（构建产物）。本包未手工编辑 `web/dist` / `web/node_modules`；构建输出为任务要求运行 build 的必然产物。

## 未做事项

- 未接入真实 Google 凭据；真实授权仍需人工配置 `GOOGLE_OAUTH_WEB_RETURN_URI` 与 Google Cloud OAuth 客户端。**不得**在代码或本报告中写入任何真实密钥。
- 未做真实两账号演示（属 Task 5）。
- 未改动后端、Android、Admin、ML、Agent、数据库迁移、认证方式、OpenAPI、依赖版本。

## 下一步

- 待 Codex 复核通过后，进入 Task 5：用获批的 Google 测试账号做真实两账号演示（recruiter 连接 Google → 排期自动建会 → 候选人 Android 看到 Meet 链接）。
