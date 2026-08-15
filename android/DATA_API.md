# Candidate Android 页面与动态数据清单

项目当前共有 12 个 Navigation Compose 页面。UI 文案、颜色和间距仍由 Compose 页面负责；用户、职位、申请、聊天、统计、时间等可变业务数据统一由 `CandidateApi` 提供。

| 页面 | Route | 动态数据 | CandidateApi |
|---|---|---|---|
| 登录 | `sign-in` | 默认邮箱、默认密码 | `getSignInDefaults()` |
| 注册 | `create-account` | 姓名、邮箱、默认密码、协议默认状态 | `getRegistrationDefaults()` |
| Job Feed | `jobs` | 搜索建议、职位列表、公司、薪资、技能、匹配度、招聘人 | `getJobFeed()` |
| Learning | `learning` | 功能状态、标题、说明 | `getLearning()` |
| Messages | `messages` | 会话 ID、联系人、摘要、时间、未读数 | `getConversations()` |
| Chat Detail | `chat-detail/{conversationId}` | 联系人状态、关联职位、面试时间、消息记录、会话状态 | `getChatThread(id)`、`sendMessage(id, body)` |
| Me / Profile | `profile` | 姓名、职业标题、统计数字、工具入口 | `getProfile()` |
| My Application | `applications` | 分类数量、申请列表、进度、匹配度 | `getApplications()` |
| Job Detail | `job-detail/{jobId}` | 职位、公司、薪资、地点、技能、匹配分析、岗位介绍、招聘人 | `getJobDetail(jobId)` |
| Apply Confirm | `apply/{jobId}` | 职位摘要、提交简历、联系邮箱、可见信息 | `getApplyConfirmation(jobId)` |
| Application Submitted | `submitted/{jobId}` | 提交时间、申请 ID、简历快照、后续步骤 | `submitApplication(jobId)` |
| Resume Edit | `resume-edit` | 个人信息、职业摘要、经历；保存操作 | `getResume()`、`saveResume(resume)` |

## 数据层结构

- `data/model/CandidateModels.kt`：纯 Kotlin 数据模型，不存放假数据。
- `data/api/CandidateApi.kt`：API 契约和当前 `FakeCandidateApi` 实现；所有前端假数据只存放在该实现中。
- `AdCandidateApp.kt`：负责调用 API、按 Route 参数取数，再将数据传给无数据源依赖的 Screen。
- `feature/**`：只展示传入的数据和处理局部表单状态，不再引用全局 `MockData`。

接入真实后端时，实现新的 `CandidateApi`（例如 `RetrofitCandidateApi`），然后向 `AdCandidateApp(api = ...)` 注入即可。页面参数和导航流程无需再次改造。

## 面试会议同步字段映射（求职端安全展示）

面试详情中的 `Interview` DTO 仅消费后端返回的两个候选安全字段，用于“Google Meet 同步状态”的展示，不包含任何凭据或内部错误：

| 字段 | 类型（Kotlin enum） | 后端缺省值 | 含义 |
|---|---|---|---|
| `meetingProvider` | `MeetingProvider { MANUAL, GOOGLE_MEET }` | `MANUAL` | 会议方式；旧后端不返回时按手动面试处理 |
| `meetingSyncStatus` | `MeetingSyncStatus { NOT_APPLICABLE, PENDING, READY, FAILED }` | `NOT_APPLICABLE` | Google Meet 同步的终态/中间态，仅用于提示文案与链接可点性 |

Moshi 使用 `KotlinJsonAdapterFactory`（反射），对缺失字段套用 Kotlin 默认值，因此旧后端不发送这两个字段时自动回退为 `MANUAL + NOT_APPLICABLE`，页面无 Google 文案。

展示决策集中在 `feature/applications/InterviewMeetingDisplay.kt` 的纯函数 `meetingDisplay(interview)`：

- `MANUAL + NOT_APPLICABLE`：不显示任何 Google 文案。
- `GOOGLE_MEET + READY`：显示短标签 “Google Meet”；仅当 `status == SCHEDULED`、链接非空且为 `ONLINE` 的 http(s) 链接时可打开。
- `PENDING`：中性提示“面试更新进行中，你当前的邀请仍然有效”，保留旧链接，无链接时也不伪造 “Join” 按钮。
- `FAILED`：不暴露内部错误码；有旧链接时提示“会议更新未完成，你的当前邀请未变”，无链接时提示“会议邀请暂不可用，请稍后再查看”。
- `CANCELLED` / `COMPLETED`：保持终态展示，不提供链接与任何新的 Google 操作；不提供招聘方专属操作（重新连接 Google / 重试同步 / 创建 Meet）。

面试上下文 `InterviewContext` 来自会话 `context` 字段，后端当前始终返回 `null`，故本次未在该模型上新增会议同步字段。
