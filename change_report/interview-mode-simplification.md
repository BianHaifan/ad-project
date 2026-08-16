# 修改报告：面试排期模式简化（Online / On-site / Phone）

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-15
- 修改范围：后端面试创建校验与测试、Recruiter Web 排期界面与测试、OpenAPI / 中文 API 指南 / 计划文档
- 明确禁止且未改动：Android、Messages、OAuth、Google Calendar 邀请链路、ML、Agent、Admin、数据库迁移、Flyway
- 未提交、未推送（等待 Codex 安全复核）；未使用真实 Google 凭据/网络/密钥/token/Meet 链接；未读取真实 `.env`；未记录真实邮箱、token、密钥、Meet 链接或 Google 响应体。

## 目标与结果

把面试排期收敛为「先选模式，再填该模式所需的字段」，后端成为规则权威：

- **Online**：仅 Google Meet，服务端自动创建会议链接与日历邀请，客户端不得提供任何链接。
- **On-site**：必须填写面试地点。
- **Phone**：必须填写电话号码或拨号说明。

删除了「手动粘贴 Zoom/Teams/自定义链接」的新建排期流程。招聘者未连接 Google 或授权失效时，Online 无法创建并给出跳转 Integrations 的明确提示；On-site 与 Phone 仍可正常创建。

核心结果：

- **后端**：`InterviewService.create` 改为「先按 mode 校验，再计算 provider」。
  - `ONLINE`：`meetingProvider` 必须为 `GOOGLE_MEET`（省略或 `MANUAL` 均拒绝），`locationOrMeetingUrl` 必须为空；随后执行既有的 Google 连接预检（未连接 → `GOOGLE_MEET_NOT_CONNECTED`，授权失效 → `GOOGLE_MEET_RECONNECT_REQUIRED`）。
  - `ONSITE` / `PHONE`：`meetingProvider` 不得为 `GOOGLE_MEET`（省略则默认 `MANUAL`），`locationOrMeetingUrl` 必填（沿用既有 `validateLocation`）。
  - 时区校验 `validateTimezone` 对所有模式继续生效。
- **Web**：新建排期表单删除 Meeting Provider 选择器与在线链接输入；Mode 置顶；Online 显示「Google Meet — meeting link and Calendar invitation will be created automatically.」说明与连接 CTA；On-site 显示 `Interview location`，Phone 显示 `Phone number or calling instructions`；时区改为只读说明文字 `Your browser timezone: <IANA timezone>`。重排表单与历史卡片渲染保持不变。
- **历史兼容**：保留 `MANUAL` 枚举、既有数据库字段与历史 `ONLINE + MANUAL` 记录，不做迁移、不删除历史链接；`updateLocal` / `updateGoogleMeet` / `validateLocation` 未改动，历史记录仍可重排与展示。

## 修改/新增文件

### 后端

- `backend/src/main/java/com/adproject/application/application/InterviewService.java`
  - `create()` 重写为 mode-first 校验（见上）；用独立的 `requestedProvider` 局部量做校验，最终 `provider` 只计算一次并保持 effectively final 以被事务 lambda 捕获。
  - `update` / `updateLocal` / `updateGoogleMeet` / `validateLocation` / `validateTimezone` / `ensureMeetingConnectionUsable` / `SyncPlan` 均未改动（保留历史重排兼容与 Google Calendar 邀请链路）。
- `backend/src/test/java/com/adproject/application/RecruiterInterviewIntegrationTest.java`
  - `SCHEDULE` 常量由 `ONLINE` + https 链接改为 `ONSITE` + "12 Marina Blvd, Singapore"（因 `ONLINE + MANUAL` 现被拒绝）。
  - `createSchedulesInterviewTransitionsApplicationAndWritesAudit`、`googleMeetRejectedWhenNotConnectedAndManualProviderAccepted`、`rejectsBlankOrNonHttpLocationAndAllowsStatusOnlyUpdates`、`createAndRescheduleRejectInvalidTimezoneButAcceptValidIanaZones` 的首个请求改到 `ONSITE`/`PHONE`，断言相应更新。
  - `googleMeetRequiresOnlineMode` 更名 `onsiteAndPhoneRejectGoogleMeet`，新增 `PHONE` 用例，字段由 `fieldErrors.mode` 改为 `fieldErrors.meetingProvider`。
  - 新增：`onlineRequiresGoogleMeetAndRejectsManualOrOmittedProvider`（ONLINE+MANUAL → 422 `meetingProvider`；ONLINE+省略 → 422 `meetingProvider`）、`phoneInterviewCreationSucceedsWithManualProvider`（PHONE → 201，`MANUAL` / `NOT_APPLICABLE`）、`legacyOnlineManualInterviewRemainsReadable`（直接把库内记录改为 `ONLINE`+`MANUAL`+历史链接后读取，断言仍可读且 `NOT_APPLICABLE`）。

### Web

- `web/src/pages/ApplicationDetailPage.tsx`
  - 移除新建表单的 `MeetingProvider` 类型导入与 `meetingProvider` 状态、`selectProvider` / `selectMode`、`openSchedule` 里的 `setMeetingProvider('MANUAL')`。
  - `googleConnectNote`：已连接 → `null`；错误分支改为「We could not verify your Google connection. Go to Integrations.」（删除「You can schedule manually, or」）。
  - `validSchedule`：`ONLINE` 只看 `googleMeetAvailable`，其余看 `validLocation(mode, location)`。
  - `submitSchedule`：`ONLINE` 提交 `meetingProvider: 'GOOGLE_MEET'`（不带 location），否则提交 `locationOrMeetingUrl = location.trim()`（不带 provider）。
  - 新建表单：`MODE` 作为第一个可交互字段，排在日期时间、时区、时长之前；选项 `Online — Google Meet` / `On-site — in-person location` / `Phone — call details`；时区改为 `<p>Your browser timezone: {timezone}</p>`；ONLINE 分支显示说明文字 + `{googleConnectNote}`；ONSITE/PHONE 分支按 `scheduleLocationField(mode)` 显示地点/拨号说明输入。
  - 新增 `scheduleLocationField(mode)` 辅助函数；`presentInterviewError` 的 `GOOGLE_MEET_PROVISIONING_UNAVAILABLE` 文案改为「Google Meet is unavailable right now. Please try again.」（删除「schedule manually」）。
  - 重排表单保持不变（仍用 `locationField`/`locationLabel`/`isGoogleMeet` 与只读时区输入 `aria-label="Timezone"`）。
- `web/src/pages/ApplicationPages.test.tsx`
  - 重写在线/现场/电话相关用例：`schedules an on-site interview…`（时区说明、`Interview location`、ONSITE payload 无 `meetingProvider`）、`shows mode-specific fields and never offers a manual online link`（断线时在线说明 + 无 `Location or meeting link` / `Meeting provider` 控件 + ONSITE/PHONE 标签）、`schedules a Google Meet without a link input when connected`（payload 含 `meetingProvider: 'GOOGLE_MEET'`）、`blocks online scheduling with a connect entry when DISCONNECTED/REVOKED`、错误映射用例（删除 provider 选择器与「schedule manually」断言）。
  - 新增 `assertPrecedes` 辅助断言（基于 `compareDocumentPosition`），并在 `schedules an on-site interview…` 中断言新建表单中 `Mode` 位于 `Date and time`、`Your browser timezone: …`、`Duration minutes` 之前。

### 文档

- `docs/openapi-v1.yaml`
  - `MeetingProvider` 描述：`MANUAL` 仅用于 ONSITE/PHONE；`GOOGLE_MEET` 为 ONLINE 必需、ONSITE/PHONE 拒绝。
  - `CreateInterviewRequest.locationOrMeetingUrl`：改为「ONSITE/PHONE 必填（地点或拨号说明），ONLINE 必须省略」。
  - `CreateInterviewRequest.meetingProvider`：改为「ONLINE 必需 GOOGLE_MEET，ONSITE/PHONE 拒绝 GOOGLE_MEET；省略时对 ONSITE/PHONE 默认 MANUAL」。
- `docs/BACKEND_API_GUIDE.zh-CN.md`
  - 3.3 Interview：新增 mode→provider 绑定规则与服务端拒绝组合。
  - 5.7 Interviews：JSON 示例由 `ONLINE` + `https://meet.example.com/interview` 改为 `ONSITE` + "12 Marina Blvd, Singapore"，并补充说明。
- `tasks/interview-mode-simplification-plan.md`、`tasks/interview-mode-simplification-todo.md`：勾选已完成验收项。

## API 行为变化（破坏性）

仅影响**新建**面试；读取与重排行为不变。

| 组合 | 之前 | 现在 |
|---|---|---|
| `ONLINE + MANUAL` | 允许（手动在线链接） | 拒绝，`422 VALIDATION_ERROR`，`fieldErrors.meetingProvider` |
| `ONLINE` 省略 provider | 默认 MANUAL | 拒绝，`422`，`fieldErrors.meetingProvider` |
| `ONLINE + 自定义 locationOrMeetingUrl` | 允许 | 拒绝，`422`，`fieldErrors.locationOrMeetingUrl` |
| `ONSITE/PHONE + GOOGLE_MEET` | 拒绝（原仅限 mode 校验） | 拒绝，`422`，`fieldErrors.meetingProvider` |
| `ONLINE` 未连接 Google | 拒绝 `GOOGLE_MEET_NOT_CONNECTED` | 不变 |
| `ONLINE` 授权失效 | 拒绝 `GOOGLE_MEET_RECONNECT_REQUIRED` | 不变 |

## 数据库

- **无新增/修改迁移**；无新端点、无 DTO 字段增删。复用既有 V9 `interviews` 会议字段与 V10 Google 连接。`MANUAL` 枚举与 `meeting_provider` / `location_or_meeting_url` 列保持不变，历史记录不被改写。

## 测试与验证

> 本仓库**未提供 Maven Wrapper**（根目录与 `backend/` 下均无 `mvnw` / `mvnw.cmd`），且 `mvn` 未必在 PATH 中。下面是 Windows PowerShell 下可复现的命令：先显式指定 JDK 21，再在 Maven 缓存目录（`~/.m2/wrapper/dists`）下查找 `mvn.cmd`，切到 `backend` 后执行聚焦测试；命令不打印任何密钥或环境变量值。

### 后端（聚焦）

```powershell
$env:JAVA_HOME = 'C:\Users\14188\.jdks\ms-21.0.8'
$mvn = (Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter 'mvn.cmd' | Select-Object -First 1).FullName
Set-Location 'C:\Users\14188\Desktop\ad-project\backend'
& $mvn -o -Dtest=RecruiterInterviewIntegrationTest test
```

结果：`RecruiterInterviewIntegrationTest` → `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

### Web

```powershell
Set-Location 'C:\Users\14188\Desktop\ad-project\web'
npm run lint
npm run typecheck
npm run test
npm run build
```

结果：

- `lint`：通过（无输出）。
- `typecheck`：通过（无输出）。
- `test`：`18 files / 160 tests` 全部通过（`ApplicationPages.test.tsx` 25 个用例）。
- `build`：成功（118 modules，产物正常）。

## 限制

- **日历邀请投递由 Google 负责**：本改动不涉及邀请链路的收发；Online 创建仍沿用既有 OAuth / Calendar 邀请 / 同步重试路径，是否送达由 Google 完成。
- 未接入真实 Google 凭据、未执行真实授权/建会/重排/取消；相关调用由测试 fake 模拟。
- 历史 `ONLINE + MANUAL` 记录仍可读、可重排（重排不强制改 provider），但其「手动链接」仅作为历史展示，不再作为新建排期的可选路径。

## 下一步：手工验证矩阵

需在具备真实 Google 账号与候选人的环境（不含真实 `.env`/密钥/token 提交）下验证：

| 模式 | Google 连接状态 | 预期结果 |
|---|---:|---|
| Online | 已连接 | 自动创建 Google Meet 事件与日历邀请，候选人收到邀请 |
| Online | 未连接 / 授权失效 | 无法提交，显示跳转 Integrations 的提示 |
| On-site | 任意 | 仅地点输入，创建为手动面试 |
| Phone | 任意 | 仅电话号码/拨号说明输入，创建为手动面试 |

同时确认：新建表单不再出现 Meeting Provider 选择器与在线链接输入；时区显示为 `Your browser timezone: <IANA>` 而非可编辑输入框。
