# 修改报告：真实站内会话后端包

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-13
- 对应计划/任务：`tasks/todo.md`「下一版本：真实站内会话（短轮询）」中「Claude 后端执行包」相关条目
- 修改范围：`backend/`（新增 `conversation` 模块 + 投递事务钩子 + V6 迁移）、`docs/`（OpenAPI、API_COVERAGE、API_CATALOG 会话行）、`tasks/todo.md`、`change_report/`
- 明确禁止且未改动：`web/`、`android/`、`ml-service/`、Agent、Admin 及其他无关模块

## 完成内容

- 在成功投递事务内自动创建唯一会话：`CandidateApplicationService.submit()` 在同一事务中调用 `ConversationProvisioningService.provision(...)`；一次投递最多一个会话（`conversations.application_id` 唯一），幂等重放不会重复创建。
- 新增 Flyway `V6` 迁移 `V6__create_conversations_and_messages.sql`，建立三张表：
  - `conversations`（`application_id` 唯一、`job_id`、`candidate_id`、`company_id`、时间字段、候选/公司索引）
  - `messages`（`conversation_id`、`sender_id`、`sender_type`、`body`、`sent_at`、`client_message_id`、`idempotency_key`、`payload_hash`；`(conversation_id, client_message_id)` 与 `(sender_id, idempotency_key)` 唯一约束；`body` 长度 1–5000 的 CHECK）
  - `conversation_read_states`（`conversation_id + user_id` 复合主键、`last_read_message_id`、`updated_at`）
- 新增独立 `conversation` 模块（domain / infrastructure / application / api），跨模块仅通过公开 Service / DTO 交互，不在控制器层直接操作其他模块 Repository。
- 实现并在 OpenAPI 标记为 `IMPLEMENTED` 的双端会话 API：
  - 候选人端（`/api/v1/candidate/conversations`）：列表、详情、消息历史（`before` 游标 + `limit`）、发送文本、更新已读。
  - 招聘者端（`/api/v1/recruiter/conversations`）：列表（`q`、`unreadOnly`、`page`、`pageSize`）、详情、消息历史、发送文本、更新已读。
- 权限与所有权：
  - 候选人只能访问自己的投递会话；招聘者只能访问本公司的会话。
  - 错误角色 403，跨用户 / 跨公司 404；未登录 401。
  - 已读状态按用户独立存储与计算。
- 消息约束：
  - 仅文本，最大 5000 字符；不支持附件 / 编辑 / 删除 / 撤回 / 群聊。
  - 会话进入 `REJECTED` 或 `WITHDRAWN` 后只读，发送返回明确业务冲突 `CONVERSATION_CLOSED`（409）。
  - 相同 `Idempotency-Key` + 相同请求返回原始消息；相同 key + 不同负载返回 `IDEMPOTENCY_KEY_REUSED`（409）。
  - 同一会话内重复 `clientMessageId` 不会重复入库（去重返回原始消息）。
- 会话列表 `unreadCount` 由真实消息 + 当前用户已读状态实时计算（排除自己发送、且 `sent_at > lastReadAt` 的消息），非硬编码。

## 修改文件

### 后端（新增 conversation 模块）

- `backend/src/main/java/com/adproject/conversation/domain/SenderType.java`（新增）
  - 主要变化：`CANDIDATE` / `RECRUITER` / `SYSTEM` 枚举。
- `backend/src/main/java/com/adproject/conversation/infrastructure/ConversationEntity.java`（新增）
  - 主要变化：会话实体，含 `applicationId`（唯一）、`jobId`、`candidateId`、`companyId`、时间字段与 `touch(Instant)`。
- `backend/src/main/java/com/adproject/conversation/infrastructure/MessageEntity.java`（新增）
  - 主要变化：消息实体，含 `senderId`、`senderType`、`body`、`clientMessageId`、`idempotencyKey`、`payloadHash`。
- `backend/src/main/java/com/adproject/conversation/infrastructure/ConversationReadStateId.java`、`ConversationReadStateEntity.java`（新增）
  - 主要变化：`@IdClass` 复合主键（`conversation_id + user_id`）与 `update(lastReadMessageId, now)`。
- `backend/src/main/java/com/adproject/conversation/infrastructure/ConversationRepository.java`（新增）
  - 主要变化：`findByApplicationId`、`findByCandidateId(pageable)`，扩展 `JpaSpecificationExecutor`（供招聘者 `q` 过滤）。
- `backend/src/main/java/com/adproject/conversation/infrastructure/MessageRepository.java`（新增）
  - 主要变化：`findBySenderIdAndIdempotencyKey`、`findByConversationIdAndClientMessageId`、`findLatest`、`pageBefore`（游标，`(sentAt,id)` 降序）、`countUnread`。
- `backend/src/main/java/com/adproject/conversation/infrastructure/ConversationReadStateRepository.java`（新增）
  - 主要变化：`JpaRepository<ConversationReadStateEntity, ConversationReadStateId>`。
- `backend/src/main/java/com/adproject/conversation/api/ConversationDtos.java`（新增）
  - 主要变化：全部对外 DTO（`Summary`、`Detail`、`Message`、`PageMeta`、`ListResponse`、`DetailResponse`、`MessageListMeta`、`MessageListResponse`、`MessageResponse`、`SendMessageRequest`、`ReadStateRequest` 等），不暴露 JPA Entity。
- `backend/src/main/java/com/adproject/conversation/application/ConversationProvisioningService.java`（新增）
  - 主要变化：公开 `provision(...)`，幂等（`findByApplicationId` 命中即复用，否则新建），供投递服务跨模块调用。
- `backend/src/main/java/com/adproject/conversation/application/ConversationService.java`（新增）
  - 主要变化：核心服务，含候选人/招聘者双端列表、详情、消息历史、发送、已读更新；所有权校验（返回 404）、`send` 幂等/去重/关闭会话校验、`unreadCount` 实时计算。
- `backend/src/main/java/com/adproject/conversation/api/CandidateConversationController.java`（新增）
  - 主要变化：候选人端 5 个端点。
- `backend/src/main/java/com/adproject/conversation/api/RecruiterConversationController.java`（新增）
  - 主要变化：招聘者端 5 个端点。

### 后端（既有文件修改）

- `backend/src/main/java/com/adproject/application/application/CandidateApplicationService.java`
  - 修改原因：在投递事务内自动创建唯一会话。
  - 主要变化：注入 `ConversationProvisioningService`，在 `submit()` 保存申请后调用 `provision(applicationId, job.getId(), candidate.getId(), job.getCompanyId(), now)`。
- `backend/src/main/resources/db/migration/V6__create_conversations_and_messages.sql`（新增）
  - 主要变化：三张表 + 唯一约束/CHECK/索引（见上）。

### 测试

- `backend/src/test/java/com/adproject/conversation/ConversationIntegrationTest.java`（新增）
  - 主要变化：8 个集成测试——投递自动建唯一会话、候选人列表/详情、候选人发消息且招聘者见未读、公司隔离、角色与未登录/403 强制、幂等与 `clientMessageId` 去重、关闭会话拒绝发送、已读按用户隔离。

### 契约与文档

- `docs/openapi-v1.yaml`：10 个会话端点 `x-status` 由 `DRAFT` → `IMPLEMENTED`，描述同步为 `IMPLEMENTED`。
- `docs/API_COVERAGE.csv`：`Conversations`、`Messages` 相关行状态由 `DRAFT` → `IMPLEMENTED`。
- `docs/API_CATALOG.zh-CN.md`、`docs/API_CATALOG.en.md`：会话相关行状态由 `DRAFT` → `IMPLEMENTED`。

## API 变化

- 是否变化：是
- 状态：会话/消息/已读契约由 DRAFT 冻结为 IMPLEMENTED。
- 候选人端（均为 `/api/v1/candidate/conversations` 下）：
  - `GET /` 列表；`GET /{conversationId}` 详情；`GET /{conversationId}/messages?before=&limit=` 消息历史；`POST /{conversationId}/messages`（`Idempotency-Key` 可选）发送；`PUT /{conversationId}/read-state` 已读。
- 招聘者端（均为 `/api/v1/recruiter/conversations` 下）：
  - `GET /?q=&unreadOnly=&page=&pageSize=` 列表；`GET /{conversationId}` 详情；`GET /{conversationId}/messages` 消息历史；`POST /{conversationId}/messages` 发送；`PUT /{conversationId}/read-state` 已读。
- 字段变化：无破坏性变更；`unreadCount` 由后端实时计算；`deliveryStatus` 当前恒为 `SENT`；`detail.context` 当前恒为 `null`。
- 与 OpenAPI 是否一致：是（已同步更新 `docs/openapi-v1.yaml` 与 API 目录/覆盖表）。

## 数据库变化

- 是否变化：是
- 新增迁移：`V6__create_conversations_and_messages.sql`
- 新增表：`conversations`、`messages`、`conversation_read_states`
- 新增约束/索引：`uk_conversations_application`（`application_id` 唯一）、`uk_messages_conversation_client`（`conversation_id, client_message_id`）、`uk_messages_sender_idempotency`（`sender_id, idempotency_key`）、`messages.body` 长度 CHECK、`idx_conversations_candidate`、`idx_conversations_company`、`idx_messages_conversation_sent`。
- 未改动既有表结构、既有迁移文件、数据库类型。

## 权限与安全

- 涉及角色：Candidate、Recruiter。
- 认证检查：路由统一 JWT 过滤器（`anyRequest().authenticated()`），未登录返回 401。
- 角色检查：候选人端点仅 `CANDIDATE`，招聘者端点仅 `RECRUITER`；错误角色返回 403。
- 资源所有权检查：
  - 候选人：会话 `candidate_id` 必须等于当前用户，否则 404。
  - 招聘者：通过 `company_members` 解析当前招聘者所属公司，会话 `company_id` 必须匹配，否则 404（跨公司不可见）。
- 敏感信息风险：低。仅返回本会话元数据与消息文本，不暴露 JPA Entity、简历原文、其他公司/用户数据。
- 幂等与去重：`Idempotency-Key` + `payload_hash`（SHA-256）防止重复发送；`client_message_id` 防客户端重复入库；已读状态按用户独立。

## 测试与验证

### 后端（IntelliJ 内置 Maven + JBR 21；环境 `JAVA_HOME` 原为 JDK 1.8、`mvn` 不在 PATH，已显式指定）

- 命令：`mvn -q -Dtest=ConversationIntegrationTest test`
  - 结果：`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`（自动建会话、列表/详情、发消息与未读、公司隔离、角色/未登录 403、幂等与去重、关闭会话拒绝发送、已读按用户隔离）。
- 命令：`mvn -q test`（全量）
  - 结果：`Tests run: 73, Failures: 2, Errors: 0, Skipped: 2`
  - `ConversationIntegrationTest` 8/8 通过；`CandidateApplicationIntegrationTest` 5/5 通过（确认投递服务改动无回归）。
  - 2 个既有失败：`RecruiterJobIntegrationTest` 中 `publishedAt` 的 H2 纳秒（9 位）vs 微秒（6 位）精度断言（约第 231、332 行）——与本任务无关，非本次改动引入。
  - 2 个跳过：`MySqlFlywayIntegrationTest`（Docker 不可用）。

## 已知限制

- `ConversationService` 复用了既有 `ApplicationRepository`、`JobRepository`、`UserRepository`、`CandidateProfileRepository`、`CompanyRepository`、`CompanyMemberRepository` 做只读查询——这与既有 `RecruiterApplicationService` 的跨模块只读约定一致，但严格意义上 conversation 模块直接注入了其他模块的 Repository。未引入循环依赖（`CandidateApplicationService → ConversationProvisioningService` 为叶子，`ConversationService → ApplicationRepository` 为叶子）。
- 招聘者列表 `unreadOnly` 过滤在内存中完成（先按会话分页取回，再按未读状态过滤），在会话量很大时存在分页边界上的未读过滤不完整风险；后续可下沉为数据库查询优化。
- `deliveryStatus` 当前恒为 `SENT`；`detail.context` 当前恒为 `null`（契约预留字段，本轮未使用）。
- 未实现客户端轮询（按任务要求，轮询由后续 Web 与 Android 包实现）。
- 附件 / 编辑 / 删除 / 撤回 / 群聊 / 系统消息均不在本版本范围（契约已冻结为不含附件）。
- 未进行真实 MySQL 端到端验证（本环境 Docker 不可用），由 H2（MySQL 模式）集成测试覆盖迁移与行为。

## 风险与注意事项

- 投递事务内新增 `conversationProvisioning.provision(...)` 会引入 conversation 模块依赖；`CandidateApplicationIntegrationTest` 5/5 已通过，但请 Codex 复核确认投递主链路无副作用。
- `ConversationReadStateEntity` 使用 `@IdClass` 复合主键，`update()` 会修改 `updatedAt` 与 `lastReadMessageId`；请确认 `ConversationReadStateRepository` 的 `save` 合并语义符合预期。
- V6 迁移为新增表，未改动已发布迁移；请确认目标环境 Flyway 版本兼容（沿用既有迁移命名与风格）。
- `unreadCount` 依赖 `lastReadAt`（由 `last_read_message_id` 的 `sent_at` 推导）；用户从未读过（无已读记录）时按 0 时刻计数，符合「全部消息未读」预期。

## 下一步建议

- 在真实 MySQL + 双账号登录态下做一次端到端验收（投递 → 自动建会话 → 候选人发消息 → 招聘者见未读 → 更新已读）。
- 修复既有 `RecruiterJobIntegrationTest` 的 H2 纳秒精度断言（与本任务无关，可单独处理）。
- 由 Web / Android 包接续实现真实 Messages 客户端与 1 秒（详情）/ 3 秒（列表）短轮询，并替换各自 mock。
- 后续可将招聘者 `unreadOnly` 过滤下沉到数据库查询，消除大数据量分页边界风险。
