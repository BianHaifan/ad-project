# 修改报告：Android 认证小改动包 1

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-13
- 对应计划/任务：`tasks/plan.md`「小改动包 1（Android 认证体验包）」、`Task 7：修正 Android 登录 401 的错误提示`、`Task 9：清理 Android 求职者认证页的误导入口`，以及 `tasks/todo.md` 第 21、24、26 行对应条目
- 修改范围：仅 `android/`（认证 UI、登录数据层与相关单元测试）、`change_report/android-auth-polish.md`、`tasks/todo.md`
- 明确禁止且未改动：`backend/`、`web/`、`ml-service/`、Agent、Admin、OpenAPI、数据库迁移、认证 API、密码规则、角色模型；未实现忘记密码流程、邮件、重置 Token 或任何新页面

## 完成内容

按任务目标逐项实现：

1. **移除登录/注册页的 Candidate/Recruiter 身份选择**（Task 9）
   - 删除 `SignInScreen` 中的「SIGN IN AS」标签与 `RoleSelector()`。
   - 删除 `CreateAccountScreen` 中的「REGISTER AS」标签与 `RoleSelector()`。
   - 删除不再使用的私有 `RoleSelector`/`RoleChip` 组合函数及 `androidx.compose.ui.draw.clip` 导入。
   - 注册请求体保持固定 `role: CANDIDATE`（`CandidateRegisterRequest` 默认值 `UserRole.CANDIDATE`，`RealAuthRepository.register` 未传 role），既有测试 `loginAndRegistrationSaveRealCandidateTokens` 仍断言 `"role":"CANDIDATE"`，未改动。

2. **保留 `Forgot password?` 文案**（Task 9）
   - `SignInScreen` 中「Forgot password?」仍为纯 `Text`，无点击行为、无页面、无网络请求、无伪功能；未改动。

3. **修正登录 401 文案**（Task 7）
   - `RealAuthRepository.login` 对返回 `401` 的登录失败将 `message` 覆写为 `"Incorrect email or password."`；其余状态码仍走 `ApiErrorParser`。
   - `ApiErrorParser.failure` 的 `401 → "Your session has expired. Please sign in again."` 保持不变，因此受保护请求（如岗位/申请等，刷新 Token 失败后仍返回 401）依旧显示「会话已过期」，未污染全局 401 语义。

4. **注册失败 → 修改输入 → 可重试回归测试**（`tasks/todo.md` 第 26 行）
   - 新增 `ViewModelTest.registerFailureClearsOnEditAndAllowsRetry`：服务端拒绝后按钮恢复可用、显示错误；修改邮箱后错误与提示清除；再次提交成功发出第二次请求。

## 修改文件

### Android（修改）

- `android/app/src/main/java/com/adproject/candidate/feature/auth/AuthScreens.kt`
  - 主要变化：移除登录/注册页的「SIGN IN AS」「REGISTER AS」标签与 `RoleSelector()` 调用；删除 `RoleSelector`/`RoleChip` 私有组合函数及 `clip` 导入；`Forgot password?` 文案与字段校验、错误显示行为不变。

- `android/app/src/main/java/com/adproject/candidate/data/api/RealRepositories.kt`
  - 主要变化：`RealAuthRepository.login` 从 `= callAuth { ... }` 改为先取得结果，若为 `ApiResult.Failure` 且 `statusCode == 401` 则 `copy(message = "Incorrect email or password.")`；`ApiErrorParser` 未改动。

### Android（测试）

- `android/app/src/test/java/com/adproject/candidate/RepositoryIntegrationTest.kt`
  - 主要变化：`authFieldErrorsAndSafeLoginFailureAreMapped` 中登录 401 的断言由旧的「Your session has expired…」改为 `"Incorrect email or password."`；新增 `protectedEndpoint401StillMapsToSessionExpired`，断言受保护端点（jobs）401 仍映射为「会话已过期」。

- `android/app/src/test/java/com/adproject/candidate/ViewModelTest.kt`
  - 主要变化：新增 `registerFailureClearsOnEditAndAllowsRetry` 测试与 `QueuedAuthRepository` 假仓库（按队列依次返回结果，支持「先失败后成功」两次提交）。

## API / 数据库变化

- API：无变化。本任务不新增/修改任何后端端点、契约或错误码；`/auth/login` 的 401 语义、`/auth/register` 的 `role: CANDIDATE` 均未改动。
- 数据库：无变化。不新增/修改迁移、表、索引或约束。
- 契约一致性：与 `docs/openapi-v1.yaml` 一致，未触碰契约。

## 测试与验证

运行环境：`android/` 目录，Gradle 8.11.1 + AGP 8.x；构建统一使用 `JAVA_HOME=C:\Users\14188\.jdks\ms-21.0.8`（Microsoft OpenJDK 21.0.8，含 `jlink`）。

- `./gradlew :app:testDebugUnitTest`：通过，`55` 个用例全部通过（新增 2 个：`protectedEndpoint401StillMapsToSessionExpired`、`registerFailureClearsOnEditAndAllowsRetry`；其余 auth/job/application/profile/resume/messages 用例全通过）。
- `./gradlew :app:lintDebug`：通过（0 错误；仅存既有 warning：依赖「有更新版本」、`OldTargetApi`、`R.raw.icon_save` 未使用，无一条来自本次改动）。
- `./gradlew :app:assembleDebug`：通过（APK 产物生成成功）。

### 覆盖点核对

- 错误密码登录 401 →「Incorrect email or password.」：`RepositoryIntegrationTest.authFieldErrorsAndSafeLoginFailureAreMapped`。
- 受保护请求 401（刷新 Token 失效后的路径）→「会话已过期」：`RepositoryIntegrationTest.protectedEndpoint401StillMapsToSessionExpired`。
- 注册失败 → 修改输入 → 可重试：`ViewModelTest.registerFailureClearsOnEditAndAllowsRetry`。

## 已知限制

- 本项目无 Compose UI/仪器化测试源集（`android/app/src/androidTest/` 为空，未引入 compose-ui-test 依赖），因此 Task 9 验收中的「Compose/UI 测试覆盖登录/注册页不含误导元素」无法以渲染级 UI 测试交付；改为以「`RoleSelector`/`RoleChip` 已删除、grep 全仓无残留引用、编译与单测通过」作为等价验证。若后续引入 Compose UI 测试，可补两条「登录页/注册页不显示 Candidate/Recruiter 选择」的渲染断言。
- 本任务按「小改动包 1」范围仅做 Android 认证体验改动；功能验收（代码 + 单元测试 + `test`/`lint`/`assembleDebug`）已完成并勾选对应 todo。Task 7 的「模拟器手动验证两种提示」与 Task 9 的「模拟器核对截图」属可视化人工核对，本环境未执行截图级验收，仅作后续可选的实机复核。
- 登录成功路径的 `submitting` 状态在 ViewModel 中不重置为 false（成功即由上层导航离开），属既有行为，本次未改动；回归测试仅断言成功重试后 `message` 与 `fieldErrors` 被清空、请求确实第二次发出。

## 下一步建议

- 可由具备模拟器/设备条件的人员在 Android Studio 默认 Run 下做一次可视化复核：登录页与注册页不再出现身份选择、错误密码提示为「Incorrect email or password.」、受保护请求过期时仍提示「会话已过期」；该核对为可选，不阻塞本包的完成状态。
- 若引入 Compose UI 测试，可补两条渲染级断言，把 Task 9 的 UI 验收从「静态删除 + 单测」升级为「渲染级」。
