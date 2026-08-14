# 修改报告：招聘者 Web 端接入真实 Messages API

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-13
- 对应计划/任务：`tasks/plan.md`「Task A：招聘者 Web 端接入真实 Messages API」与 `tasks/todo.md`「Messages Web 包」
- 修改范围：仅 `web/`（新增会话 HTTP 客户端与轮询 Hook、替换 mock 会话路径、重写 Messages 页、样式与测试）、`change_report/conversations-web.md`、`tasks/todo.md`
- 明确禁止且未改动：`backend/`、`android/`、`ml-service/`、Agent、Admin、OpenAPI 契约、数据库迁移，以及 Web 中 Dashboard / Jobs / Applications 等无关页面

## 完成内容

- 以真实招聘者 Conversations/Messages HTTP API 完全替换 Messages 页的 mock 会话路径：
  - `web/src/api/conversationHttpClient.ts` 新增严格响应解析的 `ConversationHttpClient`，覆盖列表、详情、消息历史、发送、标记已读五个接口。
  - `web/src/api/repository.ts` 的会话方法全部委托到真实 HTTP 客户端；`mockRecruiterRepository` 与 `mocks/data.ts` 删除全部会话/消息 mock 数据与方法，仅保留认证 mock。
- 支持真实列表、详情、消息历史、发送文本、标记已读与服务端 `unreadCount`：
  - 列表用 `GET /recruiter/conversations?page=1&pageSize=100`；详情 `GET .../{id}`；消息 `GET .../{id}/messages`；发送 `POST`；已读 `PUT .../read-state`。
  - `unreadCount` 只读后端返回，页面不再从消息推断或硬编码未读数。
- 发送幂等与失败保留输入：
  - 发送时生成两个 UUID：`clientMessageId` 放入请求体、`Idempotency-Key` 作为必填请求头（`ConversationHttpClient` 注入 UUID 工厂，便于测试断言）。
  - 发送失败不清理输入框（`onSuccess` 才清空），可原样重试。
- 仅前台可见时轮询：
  - `web/src/api/polling.ts` 新增 `usePollingQuery` / `useForeground` / `nextPollDelay`。
  - 会话列表每 3 秒、详情每 1 秒；`visibilitychange` + `blur`/`focus` 控制启停，离开 Messages 路由（组件卸载）自动清理定时器。
  - 同一请求不并发堆积（`isFetching` 期间跳过 tick）；连续失败按 3/10/30 秒退避，成功后恢复默认频率；返回前台立即刷新。
- 补齐 loading / empty / error / retry / sending / disabled 状态。
- 移除未实现的附件 `+` 按钮与 ML 匹配演示文案；修复会话列表候选人姓名单行截断（`.truncate` + 列表项 flex 列布局）。
- 打开会话、发送成功、标记已读后立即刷新（`useSendMessage` / `useMarkConversationRead` 的 `onSuccess` 触发 `invalidateQueries`）。

## 修改文件

### Web（新增）

- `web/src/api/conversationHttpClient.ts`
  - 主要变化：`ConversationHttpClient` + 私有严格解析（`parseSummary` / `parseMessage` / `parseDetail` / `parseParticipant`）+ `unexpectedResponse()`；对外导出单例 `conversationHttpClient`。
- `web/src/api/polling.ts`
  - 主要变化：`LIST_INTERVAL_MS=3000`、`DETAIL_INTERVAL_MS=1000`、`nextPollDelay`（3/10/30 秒退避）、`useForeground`、`usePollingQuery`（`retry:false` + `refetchOnWindowFocus:false`，用 queryFn 包装计数连续失败，前台返回即时刷新，飞行中跳过 tick）。
- `web/src/api/conversationHttpClient.test.ts`
  - 主要变化：7 个测试——列表/详情/消息解析、空列表与 null lastMessage、发送 UUID `Idempotency-Key` 与 `clientMessageId`、已读 PUT 仅含 `lastReadMessageId`、畸形成功响应拒绝、网络/业务错误透传。
- `web/src/api/polling.test.tsx`
  - 主要变化：7 个测试——`nextPollDelay` 退避、按间隔轮询、飞行中不堆积、连续失败计数与成功后复位、失焦停止/聚焦立即刷新、禁用时不轮询、`useForeground` 焦点追踪。
- `web/src/pages/MessagesPage.test.tsx`
  - 主要变化：8 个测试——列表/头部/消息渲染与仅后端未读、姓名单行截断、空态、错误态、有未读且末条来自候选人时标记已读、无未读不标记、发送成功后清空输入并刷新、发送失败保留输入。

### Web（修改）

- `web/src/models/recruiter.ts`
  - 主要变化：新增 `ConversationDetail`、`InterviewContext`、`MessageListMeta`、`MessageListResult`、`ConversationListResult`；删除 `ConversationView`。
- `web/src/api/recruiterRepository.ts`
  - 主要变化：会话方法签名改为 `listConversations/getConversation/listMessages/sendMessage/markRead`；移除 `MessageResult`。
- `web/src/api/repository.ts`
  - 主要变化：会话五个方法委托到 `conversationHttpClient`。
- `web/src/api/queries.ts`
  - 主要变化：新增 `messages` query key、`useConversations/useConversation/useMessages`（`usePollingQuery`）、`useSendMessage`（发送成功刷新）、`useMarkConversationRead`（已读后刷新）。
- `web/src/mocks/mockRecruiterRepository.ts`
  - 主要变化：删除会话/消息 mock 方法，仅保留 `signIn/register/getMe`。
- `web/src/mocks/data.ts`
  - 主要变化：删除 `message()` 与 `conversations` mock 数据及相关 import。
- `web/src/pages/MessagesPage.tsx`
  - 主要变化：改为真实数据驱动；自动跳到第一条会话；列表/详情/消息/发送/已读全部接入真实 hook；头部展示候选人姓名与「Applied to {jobTitle}」及「View application」；移除 `+` 附件按钮与 ML 文案。
- `web/src/theme/global.css`
  - 主要变化：新增 `.truncate` 单行截断与 `.conversation-list .grow` 纵向 flex 布局。
- `web/src/api/repository.test.ts`
  - 主要变化：断言从「仅 messages 为 mock」改为「仅 auth 为 mock，会话/岗位/申请/看板均走真实客户端」。

## API / 数据库变化

- API：无变化。本任务只消费已冻结的招聘者会话接口，不新增/修改任何后端端点、契约或错误码。
- 数据库：无变化。不新增/修改迁移、表、索引或约束。
- 前端契约一致性：`ConversationHttpClient` 的解析字段与 `docs/openapi-v1.yaml`（`Message`、`ConversationSummary`、`ConversationDetail`、`MessageListResponse`、`SendMessageRequest`、`ReadStateRequest`）逐一对应；`deliveryStatus` 仅接受枚举、`context` 仅接受 `INTERVIEW_INVITATION` 或 `null`。

## 测试与验证

运行环境：`web/` 目录，Node 环境（Vitest 3.2.7 / Vite 7.3.6）。

- `npm run lint`：通过（0 错误、0 警告）。
- `npm run typecheck`（`tsc -b`）：通过。
- `npm test`（`vitest run`）：`14` 个测试文件、`97` 个用例全部通过。
  - 新增：`conversationHttpClient.test.ts`（7）、`polling.test.tsx`（7）、`MessagesPage.test.tsx`（8）；更新 `repository.test.ts`（1）。
  - 覆盖：API 响应解析、发送幂等请求头、发送后刷新、已读、空态/错误态、轮询启停与退避、姓名单行截断。
- `npm run build`：通过（`tsc -b && vite build`，114 模块，产物 `dist/assets/index-By3n6CXq.js`）。

## 已知限制

- **未完成真实浏览器验收（Checkpoint A）**：本环境无法驱动真实浏览器并连接运行中的后端/Docker 来核对 3 秒/1 秒轮询、失焦停止与双账号互发。因此仅完成了代码与自动化测试；`tasks/todo.md` 中「Messages Web 包」勾选项保持未勾选，待真实双账号验证后勾选。
- `detail.context` 当前恒为 `null`（后端契约预留），页面不使用该字段；`deliveryStatus` 由后端返回，前端仅原样解析显示，不做乐观伪造「已发送」。
- 页面通过 `page=1&pageSize=100` 一次性拉取会话列表，未实现列表分页/`unreadOnly`/`q` 的 UI 入口（后端接口已支持，但本轮 UI 未接入）。
- 未实现附件、编辑、撤回、删除、群聊、WebSocket 及任何新后端接口（按任务要求明确排除）。

## 下一步建议

- 在真实 MySQL + Docker + 两个真实账号（Candidate ↔ Recruiter）下执行 `tasks/plan.md` 的 Checkpoint A 实机验证，确认 3 秒/1 秒轮询、失焦停止、返回立即刷新、未读与已读、双向发送后勾选 todo。
- 继续 `Task B / C`（Android 真实会话数据层与页面轮询、跨端验收）。
- 如后续产品需要，可将招聘者会话列表的分页、`q` 搜索、`unreadOnly` 过滤接入 UI。
