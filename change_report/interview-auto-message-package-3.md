# Interview Auto-Message（Package 3）交付报告

> 状态：实现完成，等待 Codex/主协调人复核；未 commit / push。
> 目标：招聘者成功创建一场面试后，系统自动在该职位申请对应的会话中追加一条 SYSTEM 站内消息，求职者在现有消息页即可看到。

## 完成内容

### 后端

- 新增 `ConversationService.appendInterviewNotice(applicationId, recruiterId, noticeId, body)`：
  - 以 `SenderType.SYSTEM` 写入消息（发送者为发起面试的招聘者 id，仅用于未读计数归属，不参与前端渲染）。
  - 幂等：`noticeId`（= 面试 id）同时充当消息 id、`clientMessageId`、`idempotencyKey`；写入前 `messages.findById(noticeId)` 判重，重试/重复调用不会产生第二条消息。
  - 会话缺失时静默跳过（不抛错），确保面试创建绝不因通知缺口而失败或回滚。
  - 复用现有 `MessageEntity` / `ConversationRepository` / `MessageRepository`，不直连数据库、不新增临时 API。
- 修改 `InterviewService.create()`：
  - 注入 `ConversationService`；事务 1 中通过 `requireJob(...)` 捕获职位标题（与 `contactEmail` 同模式）。
  - **MANUAL（线下/电话）**：消息在事务 1 内写入（与面试、申请状态流转、审计事件同事务），位置信息在创建时即已知，保证「一条消息与会面原子提交」。
  - **ONLINE（Google Meet）**：消息在事务 2 回写阶段写入（与 `markReady/markPending/markFailed` 同事务），仅在链接已生成（READY）时在正文中附上 Meet 链接；FAILED/PENDING 时正文不含链接（符合「location or generated Meet link when applicable」）。
  - 一致性策略：面试行成功落库（HTTP 201，无论 Meet 同步态）⇔ 恰好一条消息；校验/冲突/连接预检失败在写消息之前即抛错，不产生消息；消息写入本身不抛错、不会反向回滚面试创建。
- 消息正文（多行，各字段固定标签）：
  ```
  Interview scheduled: {职位标题}
  Time: {yyyy-MM-dd HH:mm} ({时区名})
  Mode: {Online|On-site|Phone}
  {Meeting link|Location|Phone}: {链接/地点/电话}   // 仅当存在时
  ```
  - 时间按面试自身时区格式化（复用已验证的 `ZoneId`）。
  - 正文仅由职位标题、时间、时区、模式、地点/已验证的 `meet.google.com` 链接构成；**绝不包含** OAuth token、Google event id、correlation id 或任何供应商响应体。

### Web

- `queries.ts`：`useCreateInterview` 成功后追加 `invalidateQueries({queryKey: keys.conversations})`，使招聘者会话列表在创建面试后及时反映新系统消息（消息详情本身已有轮询）。
- `global.css`：`.message` 增加 `white-space: pre-line`，使多行 SYSTEM 正文换行正常显示（同时改善用户多行消息显示）。

### Android

- **无改动**：`SenderType` 已含 `SYSTEM`（`ApiContract.kt:50`）；`MessagesScreens.kt:269` 以 `message.senderType == SenderType.CANDIDATE` 判定方向，SYSTEM 天然左对齐；`MessagesScreens.kt:279` 正文为普通 `Text`，多行 `\n` 原生渲染。故本包无需 Android 兼容改动。

### 文档

- `docs/API_COVERAGE.csv`：更新 `createRecruiterInterview` 行的 Summary 为「Schedule interview, transition application, and append a SYSTEM message to the conversation」。
- `docs/openapi-v1.yaml`：无改动（`senderType` 枚举已含 `SYSTEM`，未新增端点/请求/响应结构，未触碰 Admin 未提交段落）。

## 实际修改文件

### 后端

- `backend/src/main/java/com/adproject/conversation/application/ConversationService.java`（新增 `appendInterviewNotice`）
- `backend/src/main/java/com/adproject/application/application/InterviewService.java`（注入 ConversationService、事务内写消息、正文构造助手）
- `backend/src/test/java/com/adproject/application/InterviewAutoMessageIntegrationTest.java`（新增，7 用例）

### Web

- `web/src/api/queries.ts`（面试创建后失效会话列表）
- `web/src/theme/global.css`（`.message` 增加 `white-space: pre-line`）

### 文档

- `docs/API_COVERAGE.csv`

## API / 数据库 / Flyway 变化

- API：无新增/变更端点、无请求/响应结构变化（复用既有会话与消息端点）。
- 数据库 / Flyway：无新增表、无新增迁移（复用 `conversations`、`messages`）。

## 测试

后端（在线 Maven，JDK 21，全量）：

```bash
export JAVA_HOME="C:/Users/14188/.jdks/ms-21.0.8"; export PATH="$JAVA_HOME/bin:$PATH"
cd backend
"/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.3/plugins/maven/lib/maven3/bin/mvn" test
```

结果：`Tests run: 204, Failures: 0, Errors: 0, Skipped: 6` / `BUILD SUCCESS`（跳过 6 例为 `MySqlFlywayIntegrationTest`，本机无 Docker 的 Testcontainers）。

新增 `InterviewAutoMessageIntegrationTest`（7 用例，全部通过）：

- `manualInterviewCreationAppendsExactlyOneSystemMessageCandidateCanRead` —— 恰好一条、`senderType=SYSTEM`、求职者经 `GET /candidate/conversations/{id}/messages` 可读。
- `manualMessageBodyContainsJobTitleTimeTimezoneModeAndLocationWithoutTokenOrLink` —— 正文含职位标题、`2026-08-20 17:00`、`Asia/Singapore`、`On-site`、地点；不含 meet 链接/token/event id。
- `googleMeetReadyInterviewMessageIncludesMeetLinkButNoEventIdOrToken` —— 正文含 `Online` 与 `Meeting link: https://meet.google.com/...`，不含 event id / token。
- `googleMeetProvisioningFailureStillWritesMessageWithoutLink` —— 供给失败仍 201 创建面试并写入一条无链接消息。
- `noMessageWhenInterviewCreationFails` —— 连接预检 409 与校验 422 均不产生面试、不产生消息。
- `crossCompanyRecruiterCannotScheduleAndProducesNoMessage` —— 他司招聘者 404，不产生面试与消息。
- `googleMeetRetryAfterInitialFailureDoesNotDuplicateMessage` —— 初始供给失败后的 PATCH 重试不产生第二条消息。

既有回归：`RecruiterInterviewIntegrationTest`（44）、`ConversationIntegrationTest`（9）、`CandidateApplicationIntegrationTest`（5）、`CandidatePublicProfileIntegrationTest`（6）等全量通过。

Web：

```bash
cd web && npm test        # 170 passed
cd web && npm run build   # tsc -b + vite build 成功
```

Android：本包无 Android 改动，未触发构建（`SYSTEM` 消息已被现有渲染路径兼容，见上文）。

## 未完成内容（按任务边界）

- 仅处理「创建面试成功」事件；未涉及改期/取消/邮件/推送/附件。
- 未修改 Google Meet / OAuth / Calendar 流程、Admin / ML / Agent、Android 登录注册、Google 鉴权配置。
- 未本地手测（未启动本地服务）。

## 关键一致性说明

- 「一条已创建面试」⇔「一条消息」由三层保证：消息 id = 面试 id（天然唯一、幂等）；MANUAL 在事务 1、ONLINE 在事务 2 内写入，与面试/最终同步态原子提交；写入前 `findById` 判重。
- 消息写入失败不会反向回滚面试：会话缺失时跳过、已存在时跳过，`appendInterviewNotice` 不抛业务异常。
- 无 token 泄露：正文字段白名单构造，ONLINE 仅透出 `isUsableMeetUrl` 校验通过的 `meet.google.com` 链接。
