# 修改报告：Android 调试地址与真实 Messages 数据层

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-13
- 对应计划/任务：`tasks/plan.md`「Task B：Android 调试地址与真实 Messages 数据层」与 `tasks/todo.md`「Messages Android 数据包」
- 修改范围：仅 `android/`（build 配置、契约 DTO、Retrofit API、真实仓库、ViewModel、Compose 页面、测试）、`change_report/conversations-android-data.md`、`tasks/todo.md`
- 明确禁止且未改动：`backend/`、`web/`、`ml-service/`、Agent、Admin、OpenAPI 契约、数据库迁移、认证协议与角色模型；未实现附件、群聊、编辑/撤回/删除、WebSocket、忘记密码流程；未删除其它尚未替换的 fake 功能。

## 完成内容

- 修正 debug 默认 API 地址为 `http://10.0.2.2:8081/api/v1/`（原 `8080`）；保留 `AD_API_BASE_URL` 覆盖，release 地址不变。
- 以真实 Candidate Conversations/Messages API 替换消息流程的 `FakeCandidateRepository` 路径，覆盖会话列表、详情、消息历史、发送文本、标记已读五个接口，严格对齐 OpenAPI：
  - `GET /candidate/conversations?page&pageSize` → `ListEnvelope<ConversationSummary, PageMeta>`。
  - `GET /candidate/conversations/{conversationId}` → `DataEnvelope<ConversationDetail>`。
  - `GET /candidate/conversations/{conversationId}/messages?before&limit` → `ListEnvelope<Message, CursorMeta>`。
  - `POST /candidate/conversations/{conversationId}/messages`（必填 `Idempotency-Key` 头 + 请求体 `clientMessageId`）→ `DataEnvelope<Message>`。
  - `PUT /candidate/conversations/{conversationId}/read-state` → `Response<Unit>`（204）。
- 发送幂等双标识：发送时生成两个 UUID——`Idempotency-Key` 作为请求头、`clientMessageId` 放入请求体；同一草稿的重试复用同一对标识（成功后重置），避免重复发送。
- 发送失败保留输入、可原样重试、不乐观伪造消息：`send()` 失败只回写 `message` 与 `sending=false`，不清空 `draft`，列表仅在服务端成功后追加返回的消息。
- 建立消息 UI 状态与错误处理：`MessagesUiState`（loading/refreshing/conversations/message）与 `ChatUiState`（loading/conversation/messages/message/notFound/sending/draft），页面呈现 loading / empty / error(retry) / content / sending 五种状态；会话 404 使用「This conversation is no longer available.」，`IDEMPOTENCY_KEY_REUSED`、`CONVERSATION_CLOSED` 分别映射为安全文案。
- 标记已读：详情加载消息成功后，以最后一条消息 id 调用 `read-state`（fire-and-forget，失败不影响内容展示）。
- 移除 Messages 页的 `+` 死按钮与无行为搜索框；删除旧的 `ChatDetailScreen.kt` 与 `MainScreens.kt` 中的 mock `MessagesScreen`。

## 修改文件

### Android（新增）

- `android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesViewModel.kt`
  - 主要变化：`MessagesUiState`/`MessagesViewModel`（列表加载、refresh、retry）与 `ChatUiState`/`ChatViewModel`（详情+消息加载、标记已读、`send()` 双幂等标识并复用、发送失败保留草稿、`retry`）；各带 `companion object factory`。
- `android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesScreens.kt`
  - 主要变化：状态驱动的 `MessagesScreen`（列表/空/错误/加载/刷新）与 `ChatScreen`（头部、面试上下文卡、消息气泡、输入区、发送中指示），无死按钮；发送/接收气泡按 `senderType` 区分，时间格式化复用 `java.time.OffsetDateTime`。
- `android/app/src/test/java/com/adproject/candidate/CandidateConversationRepositoryTest.kt`
  - 主要变化：7 个测试——debug 默认地址、列表 envelope 解析与路径、详情+消息 `before` 游标、发送双幂等标识（头+体）、`IDEMPOTENCY_KEY_REUSED`/`CONVERSATION_CLOSED` 映射、`read-state` PUT 仅含 `lastReadMessageId`、会话 404 专用文案。
- `android/app/src/test/java/com/adproject/candidate/MessagesViewModelTest.kt`
  - 主要变化：5 个测试——列表 内容/空/错误/重试、详情+消息+标记已读、详情 404、发送成功追加并清空草稿且双 UUID、发送失败保留草稿且重试复用同一幂等键。

### Android（修改）

- `android/app/build.gradle.kts`
  - 主要变化：debug `API_BASE_URL` 默认值 `8080` → `8081`（`AD_API_BASE_URL` 覆盖与 release 地址不变）。
- `android/app/src/main/java/com/adproject/candidate/data/contract/ApiContract.kt`
  - 主要变化：新增 `ConversationParticipant`、`ConversationSummary`、`ConversationDetail`、`InterviewContext`。
- `android/app/src/main/java/com/adproject/candidate/data/api/HttpApis.kt`
  - 主要变化：新增 `CandidateConversationHttpApi` 五个端点。
- `android/app/src/main/java/com/adproject/candidate/data/api/RealRepositories.kt`
  - 主要变化：新增 `ConversationListResult`/`MessageListResult`、`CandidateConversationRepository` 接口与 `RealCandidateConversationRepository`（含 `conversationFailure` 404 专用文案与发送错误码映射）。
- `android/app/src/main/java/com/adproject/candidate/data/api/CandidateApi.kt`
  - 主要变化：从 `CandidateRepository` 接口与 `FakeCandidateRepository` 移除 `getConversations`/`getChatThread`/`sendMessage` 及对应 import；保留 `getLearning` 等其它尚未替换的 fake 方法。
- `android/app/src/main/java/com/adproject/candidate/core/network/CandidateAppContainer.kt`
  - 主要变化：注入 `candidateConversationRepository = RealCandidateConversationRepository(...)`。
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/MainScreens.kt`
  - 主要变化：删除 mock `MessagesScreen` 及 `Conversation`/`TextOverflow`/`HorizontalDivider` 等已不再使用的 import。
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`
  - 主要变化：`Route.Messages`/`Route.ChatDetail` 改由 `MessagesViewModel`/`ChatViewModel` + `MessagesScreen`/`ChatScreen` 驱动；删除 `fakeCandidateFeatures` 的消息路径调用（`getLearning` 仍走 fake）。
- `android/app/src/test/java/com/adproject/candidate/CandidateApiTest.kt`
  - 主要变化：删除 `sentChatMessageIsReturnedAsOutgoing`（`sendMessage` 已从 fake 接口移除）。

### Android（删除）

- `android/app/src/main/java/com/adproject/candidate/feature/jobs/ChatDetailScreen.kt`（旧 mock 驱动聊天页，整体删除）。

## API / 数据库变化

- API：无变化。本任务只消费已冻结的候选人会话接口，不新增/修改任何后端端点、契约或错误码。
- 数据库：无变化。不新增/修改迁移、表、索引或约束。
- 契约一致性：`CandidateConversationHttpApi` 与 `RealCandidateConversationRepository` 的字段与 `docs/openapi-v1.yaml`（`Message`、`ConversationSummary`、`ConversationDetail`、`SendMessageRequest`、`ReadStateRequest`）逐一对应；`deliveryStatus`/`senderType` 由后端返回、客户端按枚举原样解析，`context` 当前恒为 `null`。

## 测试与验证

运行环境：`android/` 目录，Gradle 8.11.1 + AGP 8.x，JVM 目标 17。

- 环境说明：系统 `JAVA_HOME` 为 JDK 1.8（不兼容 Gradle 8.11.1），PATH 上的 `java` 为 JDK 25（Kotlin 2.0.20 无法解析 Java 版本）；IntelliJ JBR 21 缺少 `jlink`。构建统一使用 `JAVA_HOME=C:\Users\14188\.jdks\ms-21.0.8`（Microsoft OpenJDK 21.0.8，含 `jlink`）。
- `./gradlew :app:testDebugUnitTest`：通过，`47` 个用例全部通过（新增 7 + 5 = 12 个会话仓库/ViewModel 用例，其余为既有 auth/job/application/profile/resume 用例）。
- `./gradlew :app:lintDebug`：通过（0 错误；12 条既有 warning 均为依赖「有更新版本」提示与 1 条既有 `R.raw.icon_save` 未使用，无一条来自本次改动）。
- `./gradlew :app:assembleDebug`：通过（APK 产物生成成功）。

## 已知限制

- **未完成真实设备验收**：本环境无法驱动 Android 模拟器并连接运行中的后端/Docker 来核对真实注册/登录与双账号互发。因此仅完成代码与自动化测试；`tasks/todo.md` 中「Messages Android 数据包」保持未勾选，待真实双账号验证后勾选。
- `ConversationDetail.context` 当前恒为 `null`（后端契约预留），页面在 `context` 非空时才渲染面试上下文卡；`deliveryStatus` 由后端返回、客户端原样解析，不做乐观伪造。
- 消息历史本次仅加载首页（`limit=30` 默认），未接入 `hasMore`/`before` 的上拉加载更多 UI（后端接口已支持，本轮 UI 未接入）。
- 旧 UI 模型 `data/model/CandidateModels.kt` 中的 `Conversation`/`ChatMessage`/`ChatThread` 已无任何引用（被契约 DTO 取代），但按要求未删除，留待后续清理。
- 未实现附件、编辑、撤回、删除、群聊、WebSocket 及任何新后端接口（按任务要求明确排除）。

## 下一步建议

- 在真实 MySQL + Docker + Android 模拟器（`10.0.2.2:8081`）+ Web 两个真实账号（Candidate ↔ Recruiter）下执行 `tasks/plan.md` 的 Checkpoint 实机验证，确认列表、详情、消息历史、发送幂等、标记已读与跨端互发后勾选 todo。
- 继续「Messages Android UI/验收包」（Task C）：接入同频率生命周期轮询、已读、发送与完整状态，并完成 Android ↔ Web 双账号验收。
- 如后续产品需要，可接入消息历史分页（`hasMore`/`before`）的上拉加载更多。
