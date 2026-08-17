# 功能包 — Android 求职者 Resume Hub 完整改造

> 范围：仅 Android 求职者端 `feature/profile/**`、`AdCandidateApp.kt`（仅 Profile/Resume 路由与 ViewModel 接线）、
> Android 单元测试，以及本报告。
> 未做任何 git 写操作（无 commit/push/pull/merge/reset）。

## 1. 目标与结果

把「我的」页从“Profile 卡片 + 简历预览卡片 + 跳转独立在线简历页”的旧结构，改造为单一 **Resume Hub**
（BOSS直聘式信息层级）：账号信息与简历内容在同一页内展示与编辑，去掉重复的「在线简历 / 编辑资料」入口。

- 需求 1（唯一 Hub，无重复入口）——「我的」页成为唯一 Resume Hub；移除「Online resume」「Edit profile」两条跳转行，
  简历编辑内联进 Hub。
- 需求 2（页面结构）——头像、姓名、职业头衔、地点、邮箱（身份卡，来自 Profile API）；简历完成度/状态提示；
  个人简介、技能标签、经历时间线（简历卡，来自 Resume API）；求职偏好入口、投递概览入口（快捷操作卡）。
- 需求 3（分源、分别保存）——账号信息走 Profile API（PATCH），简历内容/技能/经历走 Resume API；两个独立的
  「Save / Save resume」按钮、两条独立网络调用，**没有伪造一次性原子保存**。
- 需求 4（空简历引导）——`notCreated` 时显示创建引导文案与「Create resume」，切到内联表单（空字段）。
- 需求 5（完整编辑 UX）——基础信息、简介、技能、经历分段；经历支持添加/编辑/删除；保留服务端版本冲突
  （`expectedVersion` 已随现有 `save` 提交）与字段级错误回显（`fieldErrors` 的 `experiences[i].title` 等键原样展示）。
- 需求 6（旧简历页）——删除 `Route.ResumeEdit` 路由与 `RealResumeScreen`；投递流程「Create resume」改为跳转
  Resume Hub（`Route.Profile`），不再保留旧重复 UI。
- 需求 7（头像保留 + 修复）——(a) 文件读取移出主线程；(b) 编辑基础资料时头像不可选，消除“可选头像但无上传/取消入口”；
  (c) 相对路径解析、缓存刷新（revision + 禁用缓存）、首字母占位全部保留。
- 需求 8（设计系统）——复用既有 `AdCard/AdTopBar/PrimaryButton/SecondaryButton/TagChip/AdMuted/AdTeal/...` 设计系统；
  本包无 Figma 稿件，为需求驱动、基于既有设计系统。
- 需求 9（状态与职责）——loading / empty / error / editing / saving / success / 版本冲突 全覆盖；Composable 不直接发网络请求
  （网络全部经 ViewModel，唯一本地 I/O 是头像选择器的文件读取）。

## 2. 复用/未修改的后端契约

- Profile：既有 `GET/PATCH /api/v1/profile`（`fullName/headline/location` + `expectedVersion` 版本控制）。
- Resume：既有 Resume API（`fullName/age/location/headline/summary/experiences[]/skills` + `expectedVersion`）。
- 头像：既有 `POST/DELETE /api/v1/profile/avatar` 与 `GET /api/v1/avatars/{userId}`（见 `candidate-android-avatar-package-2a.md`）。
- 未改任何后端接口、请求体/响应 DTO、鉴权、所有权规则；未改数据库 / Flyway / OpenAPI / Docker。

## 3. 具体修改文件

**ViewModel — `feature/profile/ProfileViewModels.kt`**
- `ProfileUiState` 移除 `resume: ResumePreviewState`；删除 `ResumePreviewState` 数据类。
- `CandidateProfileViewModel` 移除 `resumeRepository` 依赖与 `loadResume()`（简历数据改由 `CandidateResumeViewModel` 单一来源）；
  `factory` 变为 `(repository, avatarRepository)`。
- `ResumeUiState` 新增 `editing: Boolean = false`；`CandidateResumeViewModel` 新增 `edit()` / `cancelEdit()`；
  保存成功路径重置 `editing=false`，失败路径保留 `editing=true` 与 `fieldErrors`（版本冲突/字段错误不丢）。

**UI — `feature/profile/RealProfileScreens.kt`（整体重写为 Resume Hub）**
- `RealProfileScreen` 新增 `resumeState` 与 `onResumeRetry/onResumeEdit/onResumeCancelEdit/onResumeSave`，移除 `onResume`。
- 新增：`ResumeHubCard`（状态提示 + 查看/空态/编辑/加载/错误分支）、`ResumeEditForm`（基础信息/简介/技能/经历内联表单）、
  `ResumeView`（简介 + `FlowRow` 技能 `TagChip` + 经历时间线）、`ExperienceTimelineItem`、`ExperienceEditCard`
  （添加/编辑/删除，字段错误按 `experiences[i].*` 键回显）、`Section`/`SectionLabel`、`resumeStatus`（完成度/状态提示）、
  `QuickActionsCard`（「My applications」「Job preferences」两个入口）。
- 删除：`RealResumeScreen`、`ResumePreviewCard`、`ResumeSummary`、`JobSeekerActions`（含重复入口）。
- 头像修复：
  - `rememberAvatarPicker` 用 `rememberCoroutineScope().launch(Dispatchers.IO)` 读取 `contentResolver` 字节，
    读取不再阻塞主线程；持有 `context.applicationContext` 避免 Activity 泄漏。
  - `IdentityCard` 中待上传预览用 `produceState` + `Dispatchers.Default` 解码 `BitmapFactory.decodeByteArray`，解码也不在主线程。
  - 编辑资料（`state.editing`）时 `CandidateAvatar` 的 `onClick` 传 `null`（不可点击），避免“可选头像但无上传/取消入口”；
    `CandidateAvatar` 签名改为 `onClick: (() -> Unit)?`。
  - `resolveAvatarUrl` / `avatar.revision` 递增 / `AsyncImage` 禁用内存+磁盘缓存 / 首字母占位：全部保留不变。

**路由与接线 — `AdCandidateApp.kt`**
- 移除 `Route.ResumeEdit` 常量、`RealResumeScreen` 导入、以及 `composable(Route.ResumeEdit)` 代码块。
- `Route.Profile` 内同时创建 `CandidateProfileViewModel`（`(profileRepository, avatarRepository)`）与
  `CandidateResumeViewModel`，各自 `collectAsStateWithLifecycle()`，接线新回调集。
- 投递流程 `onCreateResume` 由 `Route.ResumeEdit` 改为 `Route.Profile`（进入 Resume Hub）。

**测试 — `ProfileResumeViewModelTest.kt`**
- 移除 4 个旧“简历预览”用例（`profileCoordinatesResumePreview` / `missingResumeMarksPreviewNotCreated` /
  `profileFailureKeepsResumePreviewLoadedIndependently` / `resumeFailureKeepsProfileVisibleAndRetries`）。
- 新增 3 个简历编辑状态用例：`resumeEditAndCancelEditManageEditingState`、`resumeSaveSuccessClearsEditingAndKeepsData`、
  `resumeSaveFailureKeepsEditingState`。
- 14 处 `CandidateProfileViewModel(...)` 构造点改为 `(repository, avatarRepository)` 两参。

## 4. API / 数据库 / Flyway

- API：无变化（复用既有 Profile PATCH 与 Resume API，字段、版本控制、保存逻辑均未改）。
- 数据库 / Flyway / OpenAPI / Docker：无变化。
- 未把图片字节/URI 写入 DataStore、日志、持久化 session 或 DTO。

## 5. 实际执行的命令与结果

**Android**（JDK 21.0.8：`C:\Users\14188\.jdks\ms-21.0.8`；Bash 默认 Java 8，故显式 `JAVA_HOME` 指向 JDK 21）
- `./gradlew testDebugUnitTest --tests "*ProfileResumeViewModelTest" --tests "*AvatarMediaUrlTest"`：**BUILD SUCCESSFUL**。
- `./gradlew testDebugUnitTest`（全量单元测试）：**BUILD SUCCESSFUL**。
- `./gradlew assembleDebug`：**BUILD SUCCESSFUL**。
- 编译期仅一条与本次改动无关的既有弃用警告：`AdCandidateApp.kt:93` `LocalLifecycleOwner` deprecated（来自其它既有改动）。

## 6. 未执行的测试及原因

- 未运行后端测试——后端未改动。
- 未运行 Android instrumented / Compose UI 测试（项目未配置 UI 测试基础设施；照片选择器、Compose 渲染需真机/模拟器）。
- 未做真机 390×844 端到端手测（需模拟器/设备 + 后端联调），以下第 7 节给出人工验收步骤。

## 7. 手动验收步骤（建议 390×844 竖屏）

1. 登录求职者账号，进入「我的」页：顶部为「Resume hub」标题，仅此一页承载账号 + 简历（无「在线简历/编辑资料」重复入口）。
2. 身份卡显示头像（无头像时首字母占位）、姓名、头衔、地点、邮箱；头像下方有 Add/Change/Remove photo。
3. 「Career snapshot」显示投递/面试/聊天/收藏计数；下方「Resume」卡显示完成度状态提示（No resume yet / Add summary, skills … / Complete · ready to apply）。
4. 无简历时显示创建引导 + 「Create resume」；点击进入内联表单（基础信息/简介/技能/经历为空）。
5. 填写并保存简历：校验失败显示字段级错误（含经历 `experiences[0].title` 等）；成功回到查看态，简介/技能标签/经历时间线即时展示。
6. 编辑既有简历：经历可添加（+ Add experience）、编辑、删除（Remove）；保存走 Resume API（与 Profile 保存相互独立）。
7. 编辑资料（Edit profile）保存走 Profile API；此时头像圆形不可点击、不显示头像操作（无“可选头像但无上传/取消入口”的中间态）。
8. 头像上传/替换/删除：选择大图不卡主线程（文件读取在后台），上传成功后即时替换、无旧图残留；删除回退首字母占位。
9. 断网或后端返回版本冲突：显示安全错误文案，`editing` 保持、字段错误保留，不崩溃；重试/返回再进入行为正常。
10. 投递流程「Create resume」跳转到 Resume Hub（非旧简历页），返回可回到投递确认页。

## 8. 禁止范围确认（均未触碰）

未改：`backend/**`、Flyway、OpenAPI、数据库、Docker；Web、Admin、ML、Agent；Android 登录、注册、消息、职位、投递、
面试、Google Meet/OAuth；既有 Profile PATCH 与 Resume API 的字段、版本控制或保存逻辑；Gradle 依赖、版本或构建配置；
`.env`、密钥、Token、真实测试账号。遗留的 `ResumeEditScreen.kt`（旧 fake `CandidateApi` 时代的未接线死代码）按范围约定未触碰。
