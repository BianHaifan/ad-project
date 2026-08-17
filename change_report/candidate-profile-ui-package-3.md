# Android 求职者个人页 UI 优化（Package 3）交付报告

> 状态：实现完成，等待复核；未 commit / push。
> 范围：仅 Android 求职者端个人页相关代码、测试与报告；未改动后端、Web、数据库/Flyway、Google Meet/OAuth、Admin、ML、Agent。

## 页面层级与真实数据来源

将原先「My profile 只列姓名/邮箱 + 四个平铺按钮」的页面，重构为「身份卡 → 求职概览 → 求职操作 → 简历摘要 → 退出登录」的信息层级。全部数据来自既有 API，不新增任何字段或 mock：

| 区块 | 内容 | 数据来源 |
|---|---|---|
| **身份卡（顶部）** | 头像（无头像用姓名首字母）、姓名、headline、location、邮箱，明确 `Edit profile` 入口 | `CandidateProfileDto`（`fullName/headline/location/email/avatarUrl`） |
| **Career snapshot** | Applications / Interviews / Chats / Saved 四项计数，0 时仍显示 `0`（不隐藏、不伪造） | `CandidateProfileDto.stats`（`applicationCount/interviewCount/chatCount/savedJobCount`） |
| **求职操作卡** | `Online resume`、`My applications`、`Edit profile`、`Job preferences` | 既有路由（ResumeEdit / Applications / 本页编辑 / JobPreferences） |
| **简历摘要** | headline、截断 summary、最多两条经历、`View / edit resume`；无简历（既有 404）显示创建引导；读取失败显示可重试错误 | `Resume`（`headline/summary/experiences`，`CandidateResumeRepository.get()`） |
| **退出登录** | 视觉独立的次级/危险操作（红色、独立卡片，与求职动作分组分开） | 既有 `AuthViewModel.logout` |

### 状态处理（第 6 条要求）

- 整体 **loading**：满屏居中加载指示。
- **profile error**：`data == null` 时全屏安全错误 + `Try again`，保留底部导航。
- **resume error**：Profile 正常可见，仅简历卡片内显示「Couldn't load your resume.」+ `Retry`（独立重试，不影响身份/统计内容）。
- **editing / submitting / 保存成功**：身份卡内联编辑 fullName/headline/location，`Save` 提交中禁用，成功退出编辑并显示 `Profile saved`，`Cancel` 可取消。
- **Composable 不发网络请求**：Profile 与 Resume 的加载全部由 `CandidateProfileViewModel` 在 `viewModelScope` 中协调，UI 只消费 `StateFlow`。

## 关键实现

- `ProfileUiState` 新增 `resume: ResumePreviewState` 子状态，Profile 与 Resume 独立加载、独立失败：`loadResume()` 只重置 `resume` 子状态，`save()` 成功改用 `update { copy(...) }` 保留 resume 内容（避免保存后 resume 子状态被清空）。
- `CandidateProfileViewModel` 构造函数改为 `(CandidateProfileRepository, CandidateResumeRepository)`，新增 `loadResume()`、`cancelEdit()`；`CandidateResumeViewModel` 未改动（ResumeEdit 页继续沿用）。
- 头像：有 `avatarUrl` 时用 `AsyncImage`，否则用姓名首字母（与全应用既有的首字母视觉一致；后端当前无头像上传，`avatarUrl` 实际为 null，走首字母分支）。
- 可编辑字段仍严格限制为 `fullName`/`headline`/`location`；邮箱与头像仅展示；简历编辑继续跳转既有 `ResumeEdit`。

## 修改文件

- `android/app/src/main/java/com/adproject/candidate/feature/profile/ProfileViewModels.kt`
  - `ProfileUiState` 增加 `resume` 子状态；新增 `ResumePreviewState`。
  - `CandidateProfileViewModel` 协调 Profile + Resume 加载；新增 `loadResume()` / `cancelEdit()`；`save()` 成功保留 resume 并退出编辑；factory 增加 resume repository 依赖。
- `android/app/src/main/java/com/adproject/candidate/feature/profile/RealProfileScreens.kt`
  - 重写 `RealProfileScreen`（身份卡 / Career snapshot / 求职操作卡 / 简历摘要 / 退出登录），抽出 `IdentityCard`、`CandidateAvatar`、`CareerSnapshotCard`、`StatTile`、`JobSeekerActions`、`ActionRow`、`ResumePreviewCard`、`ResumeSummary`、`SignOutCard`、`ProfileError`；`RealResumeScreen` 保持不变。
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`
  - 仅改 Profile 路由的依赖注入接线：factory 传入 `candidateResumeRepository`，并传入 `onCancelEdit` / `onResumeRetry` 回调。
- `android/app/src/test/java/com/adproject/candidate/ProfileResumeViewModelTest.kt`
  - 更新既有构造调用；新增 4 个用例（见下）。

## 测试

```bash
./gradlew :app:testDebugUnitTest   # BUILD SUCCESSFUL —— 83 用例全通过（0 失败/0 错误）
./gradlew :app:lintDebug            # BUILD SUCCESSFUL（无修改文件相关的 lint 问题）
./gradlew :app:assembleDebug        # BUILD SUCCESSFUL
```

> 说明：本机默认 `JAVA_HOME` 指向 JDK 8，会触发 AGP 的「requires JVM 11+」错误；已改用本机 `~/.jdks/ms-21.0.8`（Microsoft JDK 21）执行 Gradle。

`ProfileResumeViewModelTest` 由 6 个用例增至 10 个，覆盖：

- 正常 Profile + 有简历（`profileCoordinatesResumePreview`：两者同时可见）。
- 无简历（`missingResumeMarksPreviewNotCreated`：`resume.notCreated`）。
- Profile 加载失败（`profileFailureKeepsResumePreviewLoadedIndependently`：`data == null` 且 message 安全；resume 仍独立加载成功）。
- Resume 加载失败但 Profile 仍可见并可重试（`resumeFailureKeepsProfileVisibleAndRetries`：`data != null` + `resume.message`，`loadResume()` 后成功）。
- 编辑字段校验、提交中禁用、保存成功（`profileLoadsEditsAndPreventsDuplicateSave` 更新断言 `editing == false`；`profileValidationAndRetryErrorAreSafe`）。
- 既有的 `CandidateResumeViewModel` / `JobPreferenceViewModel` 用例未改动、继续通过。

> Compose UI 测试：本工程 `src/test` 仅具备 JVM 单测（JUnit4 + coroutines-test + mockwebserver），无 Robolectric / `androidx.compose.ui:ui-test-junit4` / `androidTest` 源集，因此按既有工程能力以 ViewModel 层测试覆盖状态与交互；Compose 渲染本身未做 instrumented 断言（见下「未手测」）。

## 未完成项 / 未手工验证

- **未做模拟器截图与手测**：未启动模拟器对 390 × 844 做视觉走查（身份卡、概览、简历摘要的层级与间距）。因无本地后端联调，仅以 ViewModel 测试覆盖各状态；Compose 渲染未做 instrumented 断言。
- 头像渲染：后端当前不返回 `avatarUrl`（无头像上传功能），页面走姓名首字母分支；`avatarUrl` 非空的 `AsyncImage` 分支未在真机联调验证。

## 未改动边界（显式确认）

- 未新增任何 API、DTO、数据库字段、Flyway 迁移或 mock 数据。
- 未新增头像上传、邮箱编辑、实名认证、关注数、薪资、求职进度百分比、会员/社区等后端不支持的内容。
- 可编辑字段仍仅 `fullName`/`headline`/`location`；邮箱、头像仅展示；简历编辑继续跳转既有 `ResumeEdit`。
- 未改动后端、Web、数据库/Flyway、Google Meet/OAuth、Admin、ML、Agent。
- 底部导航、退出登录、我的投递、在线简历入口、Job preferences 入口与权限逻辑均保留。
- 未改动共享 design system（`Components.kt`/`Theme.kt` 未触碰，新 UI 复用既有 `AdCard`/`PrimaryButton`/`SecondaryButton`/`AdBottomBar`/`AdTopBar` 与色板）。
- 未新增依赖、未改动配置/密钥。
- 未 commit、未 push。
