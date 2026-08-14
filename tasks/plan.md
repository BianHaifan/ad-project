# 当前执行计划：真实 Messages 客户端接入（2026-08-13）

## 已完成基线

- Dashboard 已从 mock 切换为真实数据，报告见 `change_report/2026-08-13_1604_claude_dashboard-real-data.md`。
- Messages 后端、V6 Flyway 迁移和修复包已完成；本机 Docker MySQL 已在 V6，真实 Candidate/Recruiter 会话列表均已验证返回 200，报告见 `change_report/conversations-backend-remediation.md`。
- 本轮不再改动 ML、Agent、Admin、认证方案或消息数据库结构；附件继续明确不在范围内。

## 依赖顺序

```text
已完成的 Messages API + MySQL V6
  -> Web 真实会话客户端与前台轮询
  -> Android debug 默认地址修正
  -> Android 真实会话客户端与前台轮询
  -> Web <-> Android 双账号端到端验收
  -> 认证与创建岗位入口的小改动
```

## 下一步任务

### Task A：招聘者 Web 端接入真实 Messages API

**范围：** 只修改 `web/`、必要的 Web 测试和 `change_report/`；不修改后端契约或 Android。

**内容：** 新建严格的 conversations/messages HTTP client，替换 `mockRecruiterRepository` 的会话路径；以服务端数据完成列表、详情、历史、发送和已读。列表页仅在浏览器可见且仍在 Messages 路由时每 3 秒刷新，详情页每 1 秒刷新；同一查询不得并发堆积，失败按 3/10/30 秒退避。发送、进入详情和写入已读后立即刷新。移除附件 `+` 按钮和 ML 匹配演示文案，并修复候选人姓名单行截断。

**验收：**
- [ ] 不再从 mock 读取或写入会话/消息；发送请求携带 UUID `Idempotency-Key` 与 `clientMessageId`。
- [ ] 服务端 `unreadCount`、空态、错误态、重试、发送中禁用和发送失败保留输入内容均正确。
- [ ] Vitest 覆盖响应解析、发送后的刷新、轮询启停/退避、已读及姓名样式；`lint`、`typecheck`、测试、构建通过。

**规模：** 拆成 API/Repository 与页面/轮询两个连续小包，避免同时改动其它 Web 页面。

### Checkpoint A：Web 实机验证

- [ ] 以真实招聘者账号打开现有会话；列表和详情分别按 3 秒、1 秒更新。
- [ ] 在浏览器失焦或离开 Messages 页面后不再轮询；返回页面立即加载。
- [ ] 写入 `change_report/conversations-web.md`，列出 API、测试、未完成限制和 Docker/Flyway 版本。

### Task B：Android 调试地址与真实 Messages 数据层

**范围：** 只修改 `android/`、Android 测试和 `change_report/`。

**内容：** 先将 debug 默认 API 地址改为 `10.0.2.2:8081`（保留 `AD_API_BASE_URL` 覆盖），再为 Candidate 会话 API 添加 Retrofit DTO、Repository、错误映射、UUID 幂等标识与 ViewModel 状态。此包移除消息流程对 `FakeCandidateRepository` 的依赖，不改动其它 fake 功能。

**验收：**
- [ ] Android Studio 默认 Debug 能调用本项目后端而非主机 8080 的其它服务。
- [ ] 真实会话列表、详情、消息历史、发送和已读请求均使用 Candidate API，并处理 loading/empty/error/submitting。
- [ ] Repository/ViewModel 测试覆盖发送成功、失败重试、已读和 API 错误；`test`、`lint`、`assembleDebug` 通过。

### Task C：Android 页面轮询与跨端验收

**范围：** 只修改 `android/` 消息 UI/导航、测试和 `change_report/`。

**内容：** 让 Compose 消息列表和详情由 ViewModel 驱动，且仅在页面可见、应用前台时轮询（列表 3 秒，详情 1 秒）；离开、后台或已有请求未结束时停止/跳过。连续失败使用 3/10/30 秒退避，成功恢复正常频率。

**验收：**
- [ ] Android 不显示假会话或本地伪造已发送消息；服务端成功后才刷新到页面。
- [ ] 使用已投递的候选人与招聘者真实账号完成：投递生成会话 → Web 发信 → Android 约 1 秒内收到并显示未读 → Android 回复 → Web 更新已读。
- [ ] 写入 `change_report/conversations-android.md` 与一次双端验收结果；Messages 才可标记整体完成。

### 后续小改动（Messages 完整验收后）

1. Android 登录 401 文案、注册失败后修改信息再提交的回归测试，以及 Web 注册确认密码。
2. 移除 Android 登录/注册的身份选择，并保留未实现的 Forgot password 文案。
3. 统一创建岗位入口：只保留 Jobs 页及空态入口；不改草稿状态机。
4. 单独处理既有 `RecruiterJobIntegrationTest` 的 H2 时间精度断言，避免与业务功能包混合。

---

# 下一执行批次：认证与招聘入口小改动

## 范围与顺序

此批次只处理此前已确认的低风险体验问题；不新增忘记密码、公司审批或消息功能。Android 认证相关改动必须串行，避免同时修改 `AuthViewModel` 和 `AuthScreens`。

1. **Android 认证体验包（先执行）**
   - 移除登录、注册页的 Candidate/Recruiter 身份选择；Android 始终固定 Candidate。
   - 保留 `Forgot password?` 文案，不赋予点击行为、不新增页面或 API。
   - `/auth/login` 的 401 显示“邮箱或密码不正确”；受保护请求刷新 Token 失败才显示会话过期并清会话。
   - 回归覆盖：注册被服务端拒绝后，按钮恢复可用；修改邮箱/密码后错误清除，下一次请求可成功发出。这个测试针对此前的“失败后修改信息仍失败”疑问。
   - 不改注册 API、密码规则、后端认证、数据库或角色模型。

2. **Web 招聘者注册体验包**
   - 注册页新增仅客户端使用的 Confirm Password；登录页不显示。
   - 两次密码不一致时阻止请求；一致后错误立即清除；请求体仍仅发送 `password`。
   - 服务器页面级注册错误在用户修改相关字段时立即清除，避免视觉上保留旧错误；补“失败 → 修改 → 成功重试”测试。
   - 不实现网页忘记密码，且不改变后端或 OpenAPI。

3. **创建岗位入口统一包**
   - 仅保留 Jobs 页页头与空态的 `Create job` 入口。
   - 移除 Dashboard、Applications 中重复的 `Create Job Posting`。
   - 表单继续保留 `Create job draft` / `Save draft`，因为新建后的真实状态是 Draft。
   - 无 API、数据库或状态机变化。

## 验收检查点

- Android：默认 Android Studio Debug 登录、注册均请求本项目 `10.0.2.2:8081`；失败后修正输入可再次提交。
- Web：确认密码与重试错误状态通过 Vitest；登录页不出现确认密码。
- Web：Dashboard/Applications 不再出现创建岗位入口，Jobs 入口仍通向 `/recruiter/jobs/new`。
- 每个包独立测试、构建并写入 `change_report/`；不与 Messages 跨端验收混合。

---

# 招聘者 Dashboard 真实数据改造计划

## 目标

让招聘者 Dashboard 只展示当前招聘者所属公司的真实数据，并保证从下方申请列表点击进入详情时使用真实、可访问的申请 ID。

## 产品边界

- 本次不实现或调用 ML/Agent 推荐能力；该模块由其他成员负责。
- 原「Talent Pool Recommendations」改为「Recent applications（最近申请）」；不展示虚构匹配分数，也不使用「ML 推荐」文案。
- 不修改管理员审批流程、认证机制、数据库结构或 Android 客户端。

## API 设计

实现受认证保护的 `GET /api/v1/recruiter/dashboard`，仅聚合当前招聘者所属公司数据：

- `activeJobs`：状态为 `ACTIVE` 的岗位数量；
- `appliedApplications`：状态为 `APPLIED` 的申请数量；
- `inReviewApplications`：状态为 `IN_REVIEW` 的申请数量；
- `interviewApplications`：状态为 `INTERVIEW` 的申请数量；
- `companyVerificationStatus`：公司当前认证状态；
- `recentApplications`：最近更新的最多 3 条本公司申请摘要；
- `recentJobs`：最近创建的最多 3 个本公司岗位摘要。

现有 OpenAPI 中该接口仍为 `DRAFT`，应先将其调整为上述明确 DTO，再标为 `IMPLEMENTED`。不保留未实现的 `from`、`to` 查询参数。

## 实施顺序

1. 先更新 OpenAPI 和 API 文档，明确真实数据语义与响应字段。
2. 在 Spring Boot 中实现只读 Dashboard 聚合服务、控制器和 DTO，并沿用已有招聘者角色、成员关系和公司所有权校验。
3. 在 Web 端新增真实 HTTP 客户端与严格响应解析，替换 Dashboard 的 mock repository 接入。
4. 修改 Dashboard 页面：状态卡跳转到已有真实列表筛选；最近申请及岗位均使用后端返回的真实 ID；补齐 loading、empty、error 状态。
5. 增加后端权限/所有权回归测试及 Web 显示/跳转测试，完成后写入 `change_report/`。

## 验收

- 登录招聘者后，Dashboard 数字与同公司岗位、申请列表数据一致。
- 未登录、求职者角色、其他公司招聘者均不能读取该公司的汇总或最近申请。
- 没有申请时显示明确空态，不显示演示候选人。
- 最近申请点击可正常打开该招聘者有权查看的申请详情，不再出现 “Something went wrong”。
- 页面没有 “Demo data”“Dashboard mock”“Recommended by ML algorithm” 或虚构百分比分数。

---

# 下一版本：真实站内会话（短轮询）实施计划

## 概述

将 Web 招聘者端与 Android 求职者端现有的演示会话替换为 Spring Boot + MySQL 支撑的真实一对一求职申请会话。该版本使用 HTTP 短轮询，不引入 WebSocket、推送通知、音视频或文件附件。

## 已确认的产品和架构决策

- 一次成功投递在同一事务内自动创建一条会话；一条申请最多对应一条会话。
- 会话参与者是申请人和岗位所属公司。候选人只能访问自己的会话；该公司成员可访问公司岗位对应的会话；跨公司资源统一返回 404。
- 每个用户持有独立已读状态，因此一名招聘者阅读后不会替其他招聘者清除未读数。
- 消息正文最大 5,000 字符；本版本只支持文本，不支持编辑、撤回、删除、附件或群聊。
- 求职申请被 `REJECTED` 或 `WITHDRAWN` 后，会话保留可读，但双方不能继续发送新消息。
- 演示模式下，Web 与 Android 仅在消息页前台可见时轮询：会话详情页每 1 秒、会话列表页每 3 秒。页面失焦、进入后台或离开消息页时停止；发送及标记已读后立即刷新，不等待下一次轮询。每个客户端同一时刻最多一个轮询请求；连续失败时退避为 3 秒、10 秒、30 秒，成功后恢复默认频率。
- 已存在的 OpenAPI Conversations/Messages 路径是本功能的契约起点，但仍是 DRAFT。实现前补全其响应语义、分页、已读和幂等规则，再标为 IMPLEMENTED。
- 当前 Dashboard 实现任务与本任务都会修改 Web repository/model 文件，必须先完成并记录 Dashboard 任务，再开始本任务的 Web 阶段；不得并行修改这些共享文件。

## 数据模型

Flyway 新迁移建立以下表及索引：

```text
applications (existing)
  └─ conversations (application_id UNIQUE, job_id, candidate_id, company_id,
                   created_at, updated_at, last_message_at)
       ├─ messages (conversation_id, sender_id, sender_type, body, sent_at,
       │            client_message_id, idempotency_key, payload_hash)
       └─ conversation_read_states (conversation_id, user_id,
                                    last_read_message_id, updated_at)
```

`messages` 对 `(conversation_id, client_message_id)` 和 `(sender_id, idempotency_key)` 建立唯一性约束；同一幂等键但不同负载返回 `409 IDEMPOTENCY_KEY_REUSED`。所有时间为 UTC。

## 接口与轮询流程

```text
Candidate submits application
  → application transaction provisions conversation
  → Candidate / Recruiter list conversations (3s while visible)
  → open conversation → load metadata + latest messages
  → PUT read-state with last visible message ID
  → POST text message with Idempotency-Key + clientMessageId
  → invalidate/reload list and detail immediately
```

保留并实现已有的候选人、招聘者各自的 conversations / messages / read-state 路径；消息历史使用 `before` 游标、升序返回，并以 `limit` 限制单次数据量。前端不得从消息数据推断或伪造未读数。

## 任务列表

## 当前执行包：先交给 Claude 的后端任务

本包只允许修改 `backend/` 和会话相关 `docs/`，不修改 `web/`、`android/`、`ml-service/`、Agent 或 Admin。这样可与尚未完成的 Dashboard 前端改动隔离。

1. 新建会话规范文档，记录本版本文本消息范围、关闭申请后的只读规则、1 秒/3 秒演示轮询策略及“附件不支持”。
2. 更新既有 OpenAPI 的 Candidate/Recruiter Conversations 与 Messages 契约：列表、详情、游标消息、发消息和已读状态均改为 `IMPLEMENTED`；不改变路径，不增加附件。
3. 加入 `V6` 迁移及 conversation 模块持久化模型；为消息幂等键、客户端消息 ID 和已读状态建立约束。
4. 在申请成功创建的数据库事务内调用 conversation 模块公开的 provisioning service。
5. 实现候选人与招聘者端的全部读取、发送、已读 API，并补齐权限、资源所有权、消息幂等和关闭会话测试。

后端包的完成信号是：两个真实账号可通过 HTTP 完成候选人 → 招聘者 → 候选人的双向发信，且任一方只读取自己有权的会话。完成后 Claude 必须写 `change_report/conversations-backend.md`，再开始客户端包。

## 当前优先级调整：Messages 完善与接入

在继续 Android 认证、招聘者注册确认密码、岗位入口统一或其他任务前，先闭环真实 Messages。Claude 必须按以下三个连续包执行，并在每包完成后写独立报告：

1. **后端修复与真实 MySQL 验证**：将发送 Controller 的 `Idempotency-Key` 设为必填并与 OpenAPI 一致；修正 OpenAPI 中残留的 DRAFT 描述；更新 MySQL/Flyway 集成测试，显式断言 V6 三张表、关键索引与约束；重建/重启本项目后端使本机 Docker MySQL 应用 V6，并用真实登录态验证会话端点不再返回 500。
2. **招聘者 Web 接入**：以真实 HTTP API 完全替换 conversations/messages mock；实现详情页 1 秒、列表页 3 秒的仅前台轮询，真实未读、标记已读、发送、空态/错误/重试、姓名单行截断；移除消息页的 `+` 附件死按钮和 ML 匹配演示文案。
3. **求职者 Android 接入**：以 Retrofit/Repository/ViewModel 接入真实会话 API，移除消息页面对 `FakeCandidateRepository` 的依赖；实现相同轮询、已读、发送、UI 状态及 Android ↔ Web 双账号演示验证。

在三个包全部完成前，Messages 不得在 `change_report/` 中标为已完成；每包报告需明确列出运行中的 Docker 环境是否已更新到对应源代码和 Flyway 版本。

## 下一批 Web 小任务：统一创建岗位入口

该任务在 Dashboard 真实数据改动完成后执行，且必须与 Messages Web 客户端改造串行，防止并发修改 `DashboardPage.tsx`、repository 或共享样式。

**目标：** 将“创建岗位”作为岗位管理的职责，而非 Dashboard 或申请管理的重复操作。

- 保留 Jobs 页面页头和“无岗位”空态中的入口，按钮文案统一为 `Create job`；
- 移除 Dashboard 与 Applications 页面页头的 `Create Job Posting` 入口；
- 新建表单页继续使用 `Create job draft`、`Save draft` 等文案，因为后端创建操作确实先生成 `DRAFT`，并不直接发布岗位；
- 所有保留入口仍跳转 `/recruiter/jobs/new`，不改变 API、数据库或岗位状态机；
- 为保留/移除的入口及表单草稿文案增加 Web 回归测试。

**验收：** 从 Jobs 页面可创建并保存草稿；Dashboard 与 Applications 页面不再出现重复创建按钮；用户不会误以为点击按钮即发布岗位。

## 客户端包接口约定

| 客户端动作 | 调用 | 成功后的本地行为 |
|---|---|---|
| 打开列表 | `GET .../conversations` | 显示服务端计算的 `unreadCount`、最后消息和更新时间；列表前台每 3 秒刷新。 |
| 打开详情 | `GET .../conversations/{id}` + `GET .../messages` | 立即加载最近消息，随后详情前台每 1 秒刷新。 |
| 阅读到最新消息 | `PUT .../read-state` | 用最后可见消息 ID 更新已读；随后刷新列表。 |
| 发送文本 | `POST .../messages` + 两个 UUID 幂等标识 | 按 API 成功响应插入/刷新；失败时保留输入内容并可重试。 |

客户端不做乐观伪造的“已发送”消息；只有服务端持久化成功后显示消息，从而保证演示页面与两端数据一致。轮询控制必须取消旧请求或跳过尚未完成的请求，防止慢网络下请求堆积。

### Phase 1：契约和持久化基础

## Task 1：固定会话契约与数据库迁移

**Description:** 将短轮询、参与者权限、文本范围、已读和幂等规则写入 OpenAPI/API 文档；通过 Flyway 创建真实会话、消息、已读状态表和索引。

**Acceptance criteria:**

- [ ] OpenAPI 的候选人和招聘者会话、消息、已读接口具有明确请求/响应、游标和错误语义，状态为 IMPLEMENTED。
- [ ] `SendMessageRequest` 保持文本与 `clientMessageId`，不加入附件字段；写请求继续要求 `Idempotency-Key`。
- [ ] 新迁移可在空库和现有 V1–V5 数据库上运行，且不改写现有业务数据。

**Verification:**

- [ ] OpenAPI 校验通过。
- [ ] 后端启动时 Flyway 成功应用迁移。

**Dependencies:** None.

**Files likely touched:** `docs/openapi-v1.yaml`, `docs/API_COVERAGE.csv`, `docs/API_CATALOG.zh-CN.md`, `backend/src/main/resources/db/migration/V6__create_conversations_and_messages.sql`.

**Estimated scope:** Medium.

## Task 2：建立会话领域模型并在投递时创建会话

**Description:** 新建独立 `conversation` 后端模块的 Entity、Repository 和公共 provisioning service；在成功创建申请的同一事务内创建唯一会话。

**Acceptance criteria:**

- [ ] 每个成功申请恰好创建一条会话；同一投递的幂等重试不会产生第二条会话。
- [ ] 创建会话失败时，申请和会话均不提交，保持事务一致性。
- [ ] 模块间通过公开服务或 DTO 调用，不直接跨模块操作对方 Repository。

**Verification:**

- [ ] Spring Boot 集成测试覆盖首次投递和幂等重试。
- [ ] `backend` 定向测试通过。

**Dependencies:** Task 1.

**Files likely touched:** `backend/src/main/java/com/adproject/conversation/**`, `backend/src/main/java/com/adproject/application/application/CandidateApplicationService.java`, 对应集成测试。

**Estimated scope:** Medium.

### Checkpoint：后端基础

- [ ] Flyway、申请创建和现有申请回归测试全部通过。
- [ ] 人工检查：真实申请存在对应会话，未生成演示数据。

### Phase 2：真实会话 API

## Task 3：实现候选人会话读取、发送和已读 API

**Description:** 实现候选人自己的会话列表、详情、分页消息、发送文本和已读状态接口，并在服务端强制候选人所有权。

**Acceptance criteria:**

- [ ] 候选人只能读取和发送自己申请对应的会话；他人会话返回 404。
- [ ] 发送消息具有正文校验和双重幂等保护；`REJECTED`、`WITHDRAWN` 会话写入被拒绝。
- [ ] 已读状态只能指向本会话存在的消息；列表的 `unreadCount` 由真实数据计算。

**Verification:**

- [ ] 覆盖成功、401、错误角色 403、跨用户 404、无效已读消息 422、重复消息重试和关闭会话写入冲突。

**Dependencies:** Task 2.

**Files likely touched:** `backend/src/main/java/com/adproject/conversation/api/**`, `backend/src/main/java/com/adproject/conversation/application/**`, 候选人会话集成测试。

**Estimated scope:** Medium.

## Task 4：实现招聘者会话读取、发送和已读 API

**Description:** 在相同会话领域服务上实现招聘者端接口，按当前公司成员关系筛选，并返回候选人、岗位和真实未读摘要。

**Acceptance criteria:**

- [ ] 招聘者仅能读取本公司申请会话；其他公司会话返回 404。
- [ ] 同公司不同招聘者拥有各自的已读状态。
- [ ] 招聘者发送的消息在候选人会话中可见，且候选人未读数在下次轮询中增加。

**Verification:**

- [ ] 覆盖成功、401、候选人角色 403、跨公司 404、每用户已读隔离和幂等重试。

**Dependencies:** Task 3.

**Files likely touched:** 招聘者会话 Controller/DTO、共享领域服务、招聘者会话集成测试。

**Estimated scope:** Medium.

### Checkpoint：真实 API

- [ ] `backend` 全量测试通过。
- [ ] 两个账号在本地真实 API 环境互发消息；聊天详情页对方在 1 秒轮询周期内看到消息并正确更新未读数。

### Phase 3：客户端替换与轮询

## Task 5：将招聘者 Web Messages 替换为真实 API

**Description:** 删除 Messages mock 接入，新增严格 HTTP client、查询缓存失效和仅前台的 10 秒轮询；修复会话名称单行省略和未读显示。

**Acceptance criteria:**

- [ ] Web 不再从 `mockRecruiterRepository` 读取 conversations 或 messages。
- [ ] 列表、详情、发送、空态、错误、重试与发送中禁用状态均可用。
- [ ] 姓名单行截断，未读数来自 API；移除未实现的附件 `+` 按钮与 ML 匹配演示文案。

**Verification:**

- [ ] Vitest 覆盖真实解析、发送后的缓存刷新、未读显示、空态和轮询启动/停止。
- [ ] `npm run lint && npm run typecheck && npm test && npm run build` 通过。

**Dependencies:** Task 4；Dashboard 真实数据任务完成并已写入 `change_report/`。

**Files likely touched:** `web/src/api/**`, `web/src/models/recruiter.ts`, `web/src/pages/MessagesPage.tsx`, `web/src/theme/global.css`, 对应测试。

**Estimated scope:** Medium.

## Task 6：将 Android Messages 替换为真实 API

**Description:** 以现有 Compose 消息页面为视觉基础，新增 Retrofit contract、Repository、ViewModel 和生命周期感知的轮询，删除 `FakeCandidateRepository` 对消息页的依赖。

**Acceptance criteria:**

- [ ] 求职者可查看自己的真实会话、打开历史、发送文本并将会话标记已读。
- [ ] 轮询仅在 Messages/Chat 页面 `STARTED` 且可见时运行：详情页 1 秒、列表页 3 秒；离开页面或应用后台时取消，连续失败时退避。
- [ ] loading、empty、error、发送中和重试状态完整；不实现附件或顶部新建会话按钮。

**Verification:**

- [ ] Repository/ViewModel 单元测试覆盖轮询生命周期、发送成功/失败和已读刷新。
- [ ] Android `test`、`lint`、`assembleDebug` 通过，并在模拟器上与 Web 完成一轮互发验证。

**Dependencies:** Task 4.

**Files likely touched:** `android/app/src/main/java/**/data/api/**`, `data/contract/**`, `feature/messages/**`, `AdCandidateApp.kt`, 对应测试。

**Estimated scope:** Large；实施时拆分为网络层与 Compose/导航两个连续提交，避免单次修改过大。

## Task 7：修正 Android 登录 401 的错误提示

**Description:** 修复 Candidate 登录页将所有 HTTP 401 显示为“会话已过期”的误导性文案；保持后端对无效登录统一返回 `401 UNAUTHORIZED` 的安全策略不变。

**Acceptance criteria:**

- [ ] `/auth/login` 返回 401 时显示“邮箱或密码不正确”（不泄露账号是否存在）。
- [ ] 受保护请求在刷新 Token 失败后才显示“会话已过期，请重新登录”，并清除本地会话。
- [ ] 注册校验、网络错误和错误角色提示维持原有行为。

**Verification:**

- [ ] 更新 Repository/AuthViewModel 单元测试，分别覆盖错误密码和刷新 Token 失效。
- [ ] Android `test`、`lint`、`assembleDebug` 通过；在模拟器上手动验证两种提示。

**Dependencies:** None；可在会话 Android 客户端包之前独立完成。

**Files likely touched:** `android/app/src/main/java/**/data/api/RealRepositories.kt`、认证/网络相关测试。

**Estimated scope:** Small.

## Task 8：补齐招聘者注册确认密码

**Description:** 为 Web 招聘者注册表单增加仅客户端使用的确认密码字段和提交前一致性校验，防止用户在首次注册时输入错误密码。

**Acceptance criteria:**

- [ ] 仅注册页显示 `CONFIRM PASSWORD`，登录页不显示该字段。
- [ ] 两次密码不一致时阻止提交，并在确认密码字段下显示明确提示；密码一致后清除提示。
- [ ] 请求体仍只向现有 `/auth/register` 发送一个 `password`，不修改 OpenAPI、Spring Boot 认证接口、数据库或密码存储。

**Verification:**

- [ ] Web 测试覆盖：字段显示、两次密码不一致不发请求、一致时正常提交、修正输入后错误消失。
- [ ] `npm run lint && npm run typecheck && npm test && npm run build` 通过。

**Dependencies:** None.

**Files likely touched:** `web/src/pages/AuthPage.tsx`, `web/src/pages/AuthPage.test.tsx`。

**Estimated scope:** Small.

## Task 9：清理 Android 求职者认证页的误导入口

**Description:** Android 客户端是固定的 Candidate 端，因此移除登录页和注册页的身份选择组件。`Forgot password?` 文案按当前产品决定暂时保留，但不新增未评审的重置流程。

**Acceptance criteria:**

- [ ] Candidate 登录和注册页不再显示 Candidate/Recruiter 身份选择；注册请求继续固定发送 `role: CANDIDATE`。
- [ ] 登录页继续保留 `Forgot password?` 文案，但不为其添加伪造页面或网络请求。
- [ ] 现有登录、注册、字段校验及错误显示行为不受影响。

**Verification:**

- [ ] Compose/UI 测试覆盖登录和注册页不包含上述误导元素。
- [ ] Android `test`、`lint`、`assembleDebug` 通过，并在模拟器上核对截图。

**Dependencies:** None.

**Files likely touched:** `android/app/src/main/java/**/feature/auth/AuthScreens.kt`、对应认证 UI 测试。

**Estimated scope:** Small.

## 后续认证功能：真实忘记密码（暂不实施）

真正的密码找回不属于本次 UI 清理，也不能仅在 Android 端做一个表单。它需要先由认证模块负责人确认并评审：重置 Token 的签名/哈希与有效期、单次使用和撤销机制、邮件投递服务及密钥管理、频率限制、防账号枚举文案、重置确认页、审计与测试。完成这些规格后，才可新增 OpenAPI 与后端迁移；在此之前只保留现有入口文案。

## Task 10：修正 Android 本地调试 API 地址

**Description:** 将 Android debug 构建的默认 API 地址从 `http://10.0.2.2:8080/api/v1/` 改为本项目 Docker 后端实际暴露的 `http://10.0.2.2:8081/api/v1/`。当前主机 8080 被无关的 WeKnora 容器占用，导致 Android Studio 默认 Run 将认证请求发往错误服务并显示通用 “Request failed”。

**Acceptance criteria:**

- [ ] 未传入 `-PAD_API_BASE_URL` 时，Android Studio debug Run 自动连接本项目后端的 8081 映射端口。
- [ ] `-PAD_API_BASE_URL` 仍可覆盖默认值；release 地址不变。
- [ ] 求职者可以在模拟器上通过真实 `/auth/register` 和 `/auth/login` 完成注册、登录。

**Verification:**

- [ ] `assembleDebug` 通过。
- [ ] 从 Android Studio 的默认 Run 配置在模拟器验证一次新邮箱注册和登录；不依赖手工 Gradle 参数。

**Dependencies:** None.

**Files likely touched:** `android/app/build.gradle.kts`、必要的开发说明或配置测试。

**Estimated scope:** Small.

### Checkpoint：完整交付

- [ ] Web 招聘者与 Android 求职者使用两个真实账户完成双向收发和已读验证。
- [ ] 页面离开/失焦后没有继续轮询；重新进入立即刷新。
- [ ] 所有 mock conversations/messages 与死附件入口均已从生产路径移除。
- [ ] 每个实施阶段写入 `change_report/`，并更新本待办。

## 风险和控制

| 风险 | 影响 | 控制方式 |
|---|---|---|
| 消息功能触及两端和数据库 | 高 | 先冻结 OpenAPI 和迁移；后端 API 通过后再接客户端。 |
| 10 秒轮询造成无效请求 | 中 | 仅前台可见时轮询，发送/已读即时刷新，不在后台轮询。 |
| 多招聘者误共享已读状态 | 中 | `conversation_read_states` 以用户维度存储并写隔离测试。 |
| Mock 与真实路径混用 | 高 | Web/Android 分别移除消息 mock 依赖，并以真实双账号验收。 |
| 当前无独立 Messages Figma Frame | 中 | 复用现有消息页结构，不扩展视觉功能；实现后截图与现有设计核对。 |
