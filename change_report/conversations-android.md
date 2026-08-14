# 修改报告：Android Messages 前台轮询与跨端验收

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-13
- 对应计划/任务：`tasks/plan.md`「Task C：Android Messages 前台轮询与跨端验收」与 `tasks/todo.md`「Messages Android UI/验收包」
- 修改范围：仅 `android/`（新增轮询调度与测试、重写两个 ViewModel 加入轮询、在 Compose 层接入生命周期信号）、`change_report/conversations-android.md`、`tasks/todo.md`
- 明确禁止且未改动：`backend/`、`web/`、`ml-service/`、Agent、Admin、OpenAPI 契约、数据库迁移、认证协议与角色模型；未实现附件、群聊、编辑/撤回/删除、WebSocket、消息历史分页、忘记密码流程。

## 完成内容

- 为现有 `MessagesViewModel`（列表）与 `ChatViewModel`（详情）加入生命周期感知的前台轮询，与 Web 参考实现 `web/src/api/polling.ts` 同频率、同退避：
  - 列表页每 3 秒刷新一次、聊天详情每 1 秒刷新一次，仅在前台且页面可见时进行。
  - 离开 Messages/Chat 路由、应用进入后台或页面不可见时立即停止；页面再次可见时立即刷新一次。
  - 同一时刻至多一个请求在途（`inFlight` 标志），慢请求未返回时跳过下一 tick，避免并发堆积。
  - 连续失败按 3 秒 / 10 秒 / 30 秒退避，成功后复位到默认频率（`PollSchedule.delayAfter`）。
  - 网络请求全部在 ViewModel 层发起，Composable 只负责把生命周期信号回调到 ViewModel，页面不直接发请求。
- 轮询调度抽成纯函数 `PollSchedule`（`LIST_INTERVAL_MS=3000`、`DETAIL_INTERVAL_MS=1000`、`delayAfter(consecutiveFailures, baseIntervalMs)`），与 Web `polling.ts` 的 `nextPollDelay` 语义一致，便于单测。
- Compose 层新增 `ScreenLifecyclePolling` 桥接：用 `LocalLifecycleOwner`（NavHost 内为 `NavBackStackEntry`，其生命周期受宿主 Activity 上限约束）+ `LifecycleEventObserver` 监听 `ON_START`/`ON_STOP`，映射到 ViewModel 的 `onScreenStarted()`/`onScreenStopped()`；首次进入若已 STARTED 立即启动，`onDispose` 时停止并移除观察者。
- 保留既有行为不变：打开详情、发送成功、标记已读后仍立即刷新；发送失败仍保留草稿并可原样重试；不乐观伪造消息或未读数；保留 loading / empty / error(retry) / content / sending 各状态。
- 轮询的详情刷新用 `markRead=false`（静默），初始加载与 `retry()` 用 `markRead=true`，避免轮询高频触发已读写操作。

## 修改文件

### Android（新增）

- `android/app/src/main/java/com/adproject/candidate/feature/messages/Polling.kt`
  - 主要变化：纯对象 `PollSchedule`，集中定义列表 3 秒 / 详情 1 秒轮询频率与 3/10/30 秒连续失败退避映射。
- `android/app/src/test/java/com/adproject/candidate/MessagesPollingTest.kt`
  - 主要变化：6 个测试——`pollDelayEscalatesAndCaps`（纯退避）、列表 3 秒轮询、详情 1 秒轮询、隐藏停止且恢复立即刷新、在途请求跳过 tick、连续失败退避并在成功后复位；使用 `CountingConversationRepository` 假仓库与 `CompletableDeferred` 门控验证在途跳过。

### Android（修改）

- `android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesViewModel.kt`
  - 主要变化：两个 ViewModel 各新增 `pollingJob`/`inFlight`/`consecutiveFailures` 与 `onScreenStarted()`/`onScreenStopped()`/`pollLoop()`/静默拉取方法；列表 `fetchSilently()`、详情 `fetchDetailAndMessages(markRead)` 复用既有加载逻辑；`send()`/`updateDraft()`/`retry()`/`refresh()` 行为保持不变。
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`
  - 主要变化：新增 `ScreenLifecyclePolling` 组合函数，并接入 `Route.Messages` 与 `Route.ChatDetail` 两处 `composable`，分别绑定 `messagesViewModel::onScreenStarted/Stopped` 与 `chatViewModel::onScreenStarted/Stopped`；新增 `DisposableEffect`/`LocalLifecycleOwner`/`Lifecycle`/`LifecycleEventObserver` 导入。

## API / 数据库变化

- API：无变化。本任务只消费已冻结的候选人会话接口，不新增/修改任何后端端点、契约或错误码。
- 数据库：无变化。不新增/修改迁移、表、索引或约束。
- 契约一致性：轮询仅改变客户端刷新节奏，不改变请求/响应形状；列表与详情轮询仍分别走 `GET /candidate/conversations` 与 `GET /candidate/conversations/{id}` + `GET .../messages`，与 `docs/openapi-v1.yaml` 一致。

## 测试与验证

运行环境：`android/` 目录，Gradle 8.11.1 + AGP 8.x，JVM 目标 17。

- 环境说明：系统 `JAVA_HOME` 为 JDK 1.8、PATH 上 `java` 为 JDK 25、IntelliJ JBR 21 缺少 `jlink`；构建统一使用 `JAVA_HOME=C:\Users\14188\.jdks\ms-21.0.8`（Microsoft OpenJDK 21.0.8，含 `jlink`）。
- `./gradlew :app:testDebugUnitTest`：通过，`53` 个用例全部通过（新增 `MessagesPollingTest` 6 个轮询用例；既有 `MessagesViewModelTest` 5 个未受影响，其余 auth/job/application/profile/resume 用例全通过）。
- `./gradlew :app:lintDebug`：通过（0 错误；12 条 warning 均为既有项：10 条依赖「有更新版本」、1 条 `OldTargetApi`、1 条 `R.raw.icon_save` 未使用，无一条来自本次改动）。
- `./gradlew :app:assembleDebug`：通过（APK 产物生成成功）。

### 真实验收（Checkpoint）

- 已确认 Docker 后端与数据库运行中：`ad-project-backend`（`0.0.0.0:8081->8080`）与 `ad-project-mysql`（`0.0.0.0:13306->3306`，healthy）。
- 已确认 MySQL Flyway 在 V6：`flyway_schema_history` 中第 6 条「create conversations and messages」`success=1`（后端启动日志也显示 `now at version v6`）。
- 已确认 Android 模拟器可用：`emulator-5554`（AVD `Pixel_10_Pro`）在线，`app-debug.apk` 可安装并启动到登录页。
- 已确认真实认证与候选人会话接口可用：新注册 Candidate（`acc.candidate@example.com`）与 Recruiter（`acc.recruiter@example.com`）均返回 201；`GET /candidate/conversations` 带候选 token 返回 `200` 空列表（`{"data":[],"meta":{...}}`），与 Android 数据层预期一致。

### 未完成的真实跨端验收与原因

未完成「两个真实账户之间的 Android ↔ Web 互发、未读与轮询」实机验证，原因（均已在环境中核实，非猜测）：

1. **无既有的「已投递」关系**：`applications`、`conversations`、`messages` 三张表当前均为空，不存在可供验收的会话起点。会话只能在候选人成功投递时由后端事务自动创建，没有独立建会话接口。
2. **唯一 ACTIVE 岗位属于未知凭据的招聘者**：现有唯一 `ACTIVE`/`PUBLIC` 岗位「hyc_test」归属招聘者 `1418880681@qq.com`（yichen huang，其公司「Zhejiang University」为 APPROVED）。我没有该账户凭据，且按要求不去读取/重置既有账户密码。
3. **新注册招聘者无法创建 ACTIVE 岗位**：新注册的 Recruiter 会自动创建一家 `PENDING` 公司（实测 `verificationStatus:"PENDING"`），而 `POST /recruiter/jobs` 与 `/publish` 的权限标注为「Recruiter; verified own company / verified company」，即未验证公司不能建岗/发布，无法形成新的投递关系；公司验证未在 MVP 中通过 API 暴露。
4. **附带发现**：`GET /candidate/jobs` 当前返回 `500 INTERNAL_ERROR`（requestId 见后端日志），候选人岗位列表接口本身异常，即使有招聘者会话也会阻断「浏览岗位 → 投递」路径。该问题在 `backend/`（本次禁止修改范围），仅作记录。

因此按任务要求，**不宣称真实验收完成**，`tasks/todo.md` 中 Messages 相关条目保持未勾选。

## 已知限制

- 未完成真实双账号跨端验收（见上），故「Messages Android UI/验收包」「用真实 API 替换 Android Messages fake repository」「两个真实账户之间的 Android ↔ Web 互发、未读和轮询验收」保持未勾选。
- 轮询为前台可见时的短轮询（列表 3s / 详情 1s），非 WebSocket/推送；退避与在途跳过仅经 ViewModel 单元测试验证，未在真实网络抖动下实测。
- 本任务仅新增轮询与测试，未改动消息历史分页、未读拉取 UI 等；`ConversationDetail.context` 仍由后端契约预留、客户端按 `null` 处理。
- 验收过程中在本地开发库新增了三个测试账户/公司（`acc.candidate@example.com`、`acc.recruiter@example.com`、`Acc Test Co`），均为无副作用的最小验收数据，未删除。

## 下一步建议

- 由掌握既有招聘者账户凭据（或能通过正式流程验证公司）的人员，在真实 MySQL + Docker + Android 模拟器 + Web 下补齐投递关系：用 `yichen huang`（或新验证公司）发布 ACTIVE 岗位 → Android 候选人投递 → 双端互发，观察列表 3 秒/详情 1 秒自动刷新、失焦停止、返回立即刷新、未读与已读，确认后勾选 todo。
- 修复 `GET /candidate/jobs` 的 500（属 `backend/`，另行立项），否则候选人「浏览岗位 → 投递」链路无法走通。
- 如需更强一致性，可考虑后续将轮询退避的边界行为（`advanceTimeBy` 不执行边界任务、`runTest` 结束时 `advanceUntilIdleOr { false }` 会挂起未取消的无限循环）沉淀为一条 Android 测试约定，避免后续轮询测试踩坑。
