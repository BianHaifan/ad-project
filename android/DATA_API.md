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
