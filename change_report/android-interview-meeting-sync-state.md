# 修改报告：Android 求职端 Google Meet 面试同步状态兼容与安全展示

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 修改范围：`android/app/src/main/`、`android/app/src/test/`、`android/DATA_API.md`（数据映射说明）、`tasks/google-meet-integration-*.md`（仅更新状态）、`change_report/`
- 明确禁止且未改动：`backend/`、Web、Admin、ML、Agent、数据库迁移、认证、OAuth、真实 Google 凭据、依赖版本、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 安全复核）；未引入真实 Google 凭据或任何 Google 授权流程。

## 目标

让求职端在面试详情卡片中安全、兼容地展示 Google Meet 同步状态，同时保持对旧后端的向后兼容与对敏感信息的零泄露：

1. 契约层新增候选安全字段并保证旧后端缺字段时的回退。
2. 既有 Interview Card 仅新增安全的展示提示，不新增页面、Google 登录或招聘方操作。
3. 用纯函数承载展示决策，便于单元测试覆盖 PENDING/FAILED/CANCELLED/COMPLETED 等终态。
4. 更新数据映射文档与任务清单，并通过 Android 单测/lint/构建验证。

## 设计边界

- 复用现有申请详情 Interview Card（Figma Frame 2044:150），不新增页面。
- 求职端永不处理 Google 凭据、OAuth token、Google event id、内部同步错误码或原始 Provider 错误——这些字段在 `Interview` 模型中根本不存在。
- 求职端不提供任何招聘方专属操作（“Reconnect Google”/“Retry sync”/“Create Meet”）。
- 会话 `context` 字段后端当前始终返回 `null`，故本次未在 `InterviewContext` 上新增会议同步字段。

## 契约与兼容

`data/contract/ApiContract.kt`：

- 新增枚举 `MeetingProvider { MANUAL, GOOGLE_MEET }`、`MeetingSyncStatus { NOT_APPLICABLE, PENDING, READY, FAILED }`。
- `Interview` 末尾新增两个带默认值的只读字段：`meetingProvider: MeetingProvider = MANUAL`、`meetingSyncStatus: MeetingSyncStatus = NOT_APPLICABLE`。

Moshi 使用 `KotlinJsonAdapterFactory`（反射），对缺失 JSON 字段套用 Kotlin 默认值，因此旧后端不发送这两个字段时自动回退为 `MANUAL + NOT_APPLICABLE`，页面无任何 Google 文案。字段名与后端 `InterviewDtos.Interview` 的 `meetingProvider`/`meetingSyncStatus` 精确一致，无需额外注解。

## 展示决策（纯函数）

新增 `feature/applications/InterviewMeetingDisplay.kt`，以 `meetingDisplay(interview): MeetingDisplay` 作为唯一展示决策来源（provider 标签、状态提示、链接可点性），返回值 `MeetingDisplay(providerLabel, statusHint, linkOpenable)`：

- `MANUAL + NOT_APPLICABLE`：无 Google 文案。
- `GOOGLE_MEET + READY`：短标签 “Google Meet”；仅当 `status == SCHEDULED` 且链接非空、`mode == ONLINE`、http(s) 时链接可打开。
- `PENDING`：中性提示 “Interview update in progress. Your current invitation remains available.”，保留旧链接（仍可打开），无链接时也不伪造 “Join”。
- `FAILED`：不暴露内部错误码；有旧链接时提示 “Meeting update could not be completed. Your current invitation is unchanged.” 并保留链接；无链接时提示 “Meeting invitation is not available yet. Please check back later.”。
- `CANCELLED`：保持终态、无链接（`status != SCHEDULED` 即不可打开）。
- `COMPLETED`：保持终态、无链接、无新的 Google 操作。

`InterviewCard` 在 `RealApplicationTrackingScreens.kt` 中改为：

- 状态标签旁新增 provider 标签（`TagChip("Google Meet")`，仅在 GOOGLE_MEET 时出现）。
- 在状态提示区新增 `statusHint`（`Text`，灰色 11sp）。
- 链接可点性由 `meeting.linkOpenable` 取代原先内联的 `isLink` 判断，其余时间/时区/时长/方式/状态/链接逻辑保持不变。

## 测试

### 解析测试（`RepositoryIntegrationTest`）

- `interviewDetailParsesFullInterview` 增加断言：旧后端缺字段 → `MeetingProvider.MANUAL`、`MeetingSyncStatus.NOT_APPLICABLE`（向后兼容）。
- 新增 `interviewDetailParsesGoogleMeetSyncState`：解析 `GOOGLE_MEET + READY` 的 `meetingProvider`/`meetingSyncStatus`/链接/状态。

### 展示决策测试（`InterviewMeetingDisplayTest`，新增）

覆盖：

- GOOGLE_MEET+READY 显示标签且链接可打开。
- 缺字段回退 MANUAL 无 Google 文案。
- PENDING 中性提示、保留旧链接、不泄露内部错误码（断言提示不含 “SYNC”/“GOOGLE_MEET_PROVISIONING”）。
- PENDING 无链接时提示仍在但链接不可打开。
- FAILED 有链接/无链接两种提示、无内部错误码泄露。
- CANCELLED GOOGLE_MEET 即便有 location 也不可打开。
- COMPLETED GOOGLE_MEET 无 join 操作、无状态提示。
- MANUAL 遗留链接行为不回归（SCHEDULED 可打开、CANCELLED 不可打开）。

## 修改/新增文件

- 修改 `android/app/src/main/java/com/adproject/candidate/data/contract/ApiContract.kt`（新增枚举与字段）。
- 新增 `android/app/src/main/java/com/adproject/candidate/feature/applications/InterviewMeetingDisplay.kt`（纯展示决策函数）。
- 修改 `android/app/src/main/java/com/adproject/candidate/feature/applications/RealApplicationTrackingScreens.kt`（`InterviewCard` 接入 `meetingDisplay`）。
- 修改 `android/app/src/test/java/com/adproject/candidate/RepositoryIntegrationTest.kt`（解析断言 + 新增测试 + helper）。
- 新增 `android/app/src/test/java/com/adproject/candidate/InterviewMeetingDisplayTest.kt`。
- 修改 `android/DATA_API.md`（新增“面试会议同步字段映射”章节）。
- 修改 `tasks/google-meet-integration-todo.md` / `tasks/google-meet-integration-plan.md`（标注 Android 候选侧终态展示完成）。

## 测试与验证

### 命令

```
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

（在 `android/` 目录下执行）

### 结果

- `BUILD SUCCESSFUL in 1m 36s`，`53 actionable tasks: 17 executed, 36 up-to-date`。
- `testDebugUnitTest`、`lintDebug`、`assembleDebug` 全部通过。
- 仅有一条与本包无关的既有弃用告警：`AdCandidateApp.kt:84` 的 `LocalLifecycleOwner` 已弃用（建议迁移至 `lifecycle-runtime-compose` 的 `androidx.lifecycle.compose` 包），本次未改动。

## 未做事项

- 未接入真实 Google 凭据，未执行真实 OAuth/Calendar/Meet 调用。
- 未新增页面、Google 登录、招聘方专属操作、后台补偿任务或数据库迁移。
- 未在 `InterviewContext` 上新增会议同步字段（后端 `context` 恒为 `null`）。
- 招聘方 Web 端 Google 连接/自动创建 UI 与真实双账号演示仍属后续任务。
