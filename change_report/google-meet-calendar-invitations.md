# 修改报告：Google Meet 面试「日历邀请」——候选人受邀自动通知

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-15
- 修改范围：`backend/`（Google Meet 日历建会邀请链路 + 对应后端测试）
- 明确禁止且未改动：数据库迁移、OpenAPI、React Web、Android、Messages、ML、Agent、Admin、auth；未新增独立邮件服务/队列/邮件模板/前端收件人输入框
- 未提交、未推送（等待 Codex 安全复核）；未启动真实授权、未填入真实密钥；测试使用 fake transport，从未请求 Google。

## 目标与结果

当招聘者创建 Google Meet 面试时，把候选人 `applications.contact_email` 作为**唯一受邀者（attendee）**写入 Google Calendar 建会请求，使 Google 自动发送「首次邀请 / 重排 / 取消」通知。收件人**仅来自服务端 `applications.contact_email`**，绝不接受/信任浏览器提交的邮箱。

核心结果：

- **首次建会**：Calendar 事件 body 写单个 `attendees: [{ email }]`，建会请求使用 `conferenceDataVersion=1&sendUpdates=all` → Google 自动发送初始邀请。
- **重排 / 取消**：PATCH / DELETE 请求改为 `sendUpdates=all`（原为 `none`），Google 自动发送变更/取消通知；PATCH body **绝不写入** `attendees`、`conferenceData` 或新的 `createRequest`，从而**保留既有会议链接与既有受邀者**。
- **收件人来源**：`create` 流程在事务一内读取 `application.getContactEmail()`，透过 `ProvisionRequest → CalendarEventSpec` 一路传入建会 body；重排/取消不重新读邮箱（PATCH 保留既有 attendees）。

## 架构与事务边界

未改变既有的两段式事务、稳定 event id、重试、冲突恢复、权限校验、错误降级逻辑。改动仅落在「建会请求参数的传递与 HTTP body/query 序列化」这两条链路：

1. `InterviewService.create`（事务一内）捕获 `application.getContactEmail()`（经 `String[]` 持有者带出 lambda），作为 `provisionMeeting(...)` 的第 6 个参数 `attendeeEmail` 构造 `ProvisionRequest`；重试路径 `SyncPlan.provision` 同样携带 `attendeeEmail`（`SyncPlan` 记录新增该字段，`cancel`/`reschedule`/`localCancel` 工厂传 `null`）。
2. `MeetingProvisioningService` 把 `request.attendeeEmail()` 透传进 `CalendarEventSpec`。
3. `HttpGoogleCalendarClient`：
   - `createEvent` 的 URI 由 `...?conferenceDataVersion=1` 改为 `...?conferenceDataVersion=1&sendUpdates=all`，并在 body 中仅当 `attendeeEmail` 非空/非空白时序列化 `attendees: [{ email }]`。
   - `patchEvent` URI 由 `sendUpdates=none` 改为 `sendUpdates=all`；`patchBody` 仍只写 summary/start/end，不含 attendees/conferenceData/createRequest。
   - `deleteEvent` URI 由 `sendUpdates=none` 改为 `sendUpdates=all`。

日志/安全约束保持：不打印 OAuth token、邮箱、Meet 链接、Google 响应体。

## 修改/新增文件

### 修改（`integration.google` 与 `application`）

- `integration/google/ProvisionRequest.java` — record 新增 `attendeeEmail`（第 6 字段）。
- `integration/google/application/CalendarEventSpec.java` — record 新增 `attendeeEmail`（第 7 字段）。
- `integration/google/application/MeetingProvisioningService.java` — 建会时把 `request.attendeeEmail()` 透传进 `CalendarEventSpec`。
- `integration/google/application/HttpGoogleCalendarClient.java` — `createEvent`/`patchEvent`/`deleteEvent` 三处 `sendUpdates=all`；`createEvent` body 序列化单个 `attendees`（email 空白时省略，避免无效请求）；`patchBody` 保持不含 attendees/conferenceData。
- `application/application/InterviewService.java` — `create` 捕获并透传 `contactEmail`；`provisionMeeting` 增参；`SyncPlan` record 增 `attendeeEmail`（`provision` 工厂增参，`cancel`/`reschedule`/`localCancel` 传 `null`）；重试路径复用 `application.getContactEmail()`。

### 测试

- `GoogleMeetProvisioningIntegrationTest` — 新增 `provisionThreadsAttendeeEmailIntoCalendarEventSpec`：捕获 `CalendarEventSpec`，断言 `attendeeEmail` 等于测试邮箱。
- `HttpGoogleCalendarClientTest` — 重命名 `patchEventUsesSyncQueryAndNeverSendsConferenceDataOrAttendees`（改断言 `sendUpdates=all` 且 body 不含 `attendees`）；新增 `createEventSerializesSingleAttendeeAndSendsNotifications`（POST + `conferenceDataVersion=1` + `sendUpdates=all` + body 含 `attendees`/邮箱/`conferenceData`/`createRequest`）、`createEventOmitsAttendeeWhenEmailBlank`（空白邮箱 → body 不含 `attendees`）、`deleteEventSendsNotificationsToAll`（URI 含 `sendUpdates=all`）。
- `RecruiterInterviewIntegrationTest` — 新增 `googleMeetProvisionReceivesApplicationContactEmailNotBrowserInput`：请求体**不含**任何邮箱，断言 `ProvisionRequest.attendeeEmail()` 等于 `applications.contact_email`（服务端来源，非浏览器输入）；新增 `googleMeetInitialFailureRetryCarriesContactEmailFromApplicationNotBrowserInput`：首次建会失败 → 面试进入 `FAILED` → 招聘者触发既有 Retry Google Meet 流程，断言重试的 `ProvisionRequest.attendeeEmail()` 仍等于该申请 `contact_email`（重试 PATCH body 亦无邮箱），且复用既有 correlation ID、不创建第二个内部 interview。

> 测试编写过程中发现并修复一处用例问题：新用例最初复用了与既有 `googleMeetSuccessfulProvisionStoresLinkAndMarksReady` 相同的硬编码 `meeting_event_id = "evt-1"`，在共享 H2 库上触发唯一索引 `uk_interviews_meeting_event` 冲突（HTTP 409 `RESOURCE_CONFLICT`）。改为 `"evt-" + UUID.randomUUID()` 后通过。此为测试隔离问题，非业务缺陷。

## 测试与验证

### 命令

```
$env:JAVA_HOME='<本机 JDK 21 安装目录，如 C:\Users\<you>\.jdks\ms-21.0.8>'
mvn -o test
```

> 本仓库**未提供 Maven Wrapper**（根目录与 `backend/` 下均无 `mvnw` / `mvnw.cmd`），且本机 `mvn` 亦不在 PATH。实际执行使用本机 Maven 3.9.16 发行版（由 Maven Wrapper 机制下载后缓存于 `~/.m2/wrapper/dists/apache-maven-3.9.16/<sha256>/bin/mvn.cmd`），配合 Microsoft OpenJDK 21.0.8。为可复现，建议将 Maven 加入 PATH 后直接运行 `mvn -o test`；如需锁定版本，可为仓库补加 Maven Wrapper（`mvn wrapper:wrapper`）。

### 结果

- 聚焦后端（本改动相关三类）：
  `mvn -o -Dtest=RecruiterInterviewIntegrationTest,GoogleMeetProvisioningIntegrationTest,HttpGoogleCalendarClientTest test`
  → `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`（RecruiterInterview 41 + GoogleMeetProvisioning 24 + HttpGoogleCalendarClient 7）。
- 全量后端：`mvn -o test` → `Tests run: 183, Failures: 0, Errors: 0, Skipped: 6`，`BUILD SUCCESS`。
- 6 个跳过为 `MySqlFlywayIntegrationTest`（本机未检测到 Docker/Testcontainers，故跳过；本次未新增 Flyway 迁移，与改动无关）。

未运行 Web/Android 测试（本次未改动二者）。

## API / 数据库

- 无新增端点、无新增 Flyway 迁移、无 DTO/OpenAPI 变更；复用既有 `applications.contact_email`、V9 `interviews` 会议字段与 V10 加密 Google token。
- 收件人仅由服务端读取，绝不出现在 API 请求契约中。

## 限制

- **邮件投递由 Google 负责**：本改动只在 Calendar 事件上写入受邀者并开启 `sendUpdates=all`，邀请/变更/取消邮件的实际生成与送达由 Google Calendar 完成，后端无法也不应保证送达结果。
- 未接入真实 Google 凭据、未执行真实授权/建会/PATCH/DELETE；Calendar/token 调用均由测试 fake 模拟。
- 未新增独立邮件服务、队列、邮件模板或前端收件人输入框；若候选人邮箱为空/未提供，则建会不写 attendees（防御性省略），此时不会收到自动邀请——与「收件人仅来自 `contact_email`」的约束一致。

## 下一步

**尚未完成真实双账号验证。** 仍需在具备两个真实 Google 账号（招聘者 + 候选人邮箱）的环境下，验证端到端闭环：招聘者授权并创建 Google Meet 面试 → 候选人邮箱收到 Google 自动邀请 → 招聘者重排/取消 → 候选人收到 Google 自动变更/取消通知，且会议链接保持稳定、无第二个 event。此验证不涉及真实 `.env`、密钥、token 或 Meet 链接的提交。
