# 个人页与申请详情 UI 最终验收准备报告

> 日期：2026-08-16
> 范围：仅「验收准备」——重跑 Android/Web 构建与测试、启动本地环境、准备人工验收清单。未修改任何业务代码、配置、数据库、Flyway、OAuth/Google Meet、Admin、ML、Agent；未 commit / push / pull / merge；未删除 Docker 数据卷；未重置数据库；未输出任何密钥。

## 一、命令结果

### Android（本机 JDK 21：`~/.jdks/ms-21.0.8`）

| 命令 | 结果 |
|---|---|
| `./gradlew :app:lintDebug`（`--rerun-tasks` 强制重跑） | ✅ BUILD SUCCESSFUL（54s，28 任务执行） |
| `./gradlew :app:assembleDebug`（`--rerun-tasks`） | ✅ BUILD SUCCESSFUL（35s，37 任务执行） |
| `./gradlew :app:testDebugUnitTest`（`--rerun-tasks`） | ✅ BUILD SUCCESSFUL（43s，25 任务执行） |

单测汇总：**83 用例 / 0 失败 / 0 错误 / 0 跳过**，分布如下：

| 测试类 | 用例数 |
|---|---|
| ApplicationTrackingViewModelTest | 6 |
| ApplicationViewModelTest | 5 |
| CandidateApiTest | 4 |
| CandidateConversationRepositoryTest | 9 |
| CandidatePublicProfileRepositoryTest | 4 |
| InterviewMeetingDisplayTest | 9 |
| MessagesPollingTest | 6 |
| MessagesViewModelTest | 9 |
| ProfileResumeViewModelTest | 10 |
| PublicProfileViewModelTest | 2 |
| RepositoryIntegrationTest | 13 |
| ViewModelTest | 6 |

> 说明：`--rerun-tasks` 为强制重新执行（首次 `lintDebug` 命中缓存为 UP-TO-DATE，故加 `--rerun-tasks` 确保真实重跑）。过程中仅有 1 条**预存在**编译警告（`AdCandidateApp.kt:95` 的 `LocalLifecycleOwner` 弃用提示），非本任务修改文件，且不影响结果。

### Web（`web/` 下）

| 命令 | 结果 |
|---|---|
| `npm run typecheck` | ✅ 通过（exit 0，无错误输出） |
| 定向测试：`npx vitest run src/pages/ApplicationPages.test.tsx src/api/conversationHttpClient.test.ts src/api/applicationHttpClient.test.ts src/api/repository.test.ts` | ✅ 4 文件 / **52 用例全通过**（exit 0） |
| `npm run build` | ✅ 通过（exit 0，Vite 构建 127 模块） |

## 二、服务启动状态与访问地址

| 服务 | 状态 | 访问地址 |
|---|---|---|
| MySQL | ✅ 运行（`adproject-local-mysql`，healthy） | host `localhost:13306`（容器内 3306） |
| 后端 | ✅ 运行（`adproject-local-backend`，容器内 `mvn spring-boot:run`，Tomcat 8080） | host `http://localhost:8081`（容器 8081→8080） |
| Web 开发端 | ✅ 运行（`npm run dev`，Vite） | `http://localhost:4173/` |
| Android 调试端 | ✅ 运行（模拟器 `Pixel_10_Pro`，APK 已安装并启动） | 模拟器 `emulator-5554`，App `com.adproject.candidate` |

启动链路校验（真实探测，非臆测）：

- 后端 `http://localhost:8081/` → **HTTP 401**（服务在线，未授权即 Spring Security 拦截）。
- `http://localhost:8080/` → **HTTP 000**（8080 无服务，确认后端在 8081）。
- Web 根 `http://localhost:4173/` → **HTTP 200**。
- Web 代理 `http://localhost:4173/api/v1/health` → **HTTP 401**（代理已连通后端）。
- 后端启动日志：`Tomcat started on port 8080` + `Started BackendApplication in 20.9s`；Flyway 校验 13 个迁移、库从 **v12 自动迁移至 v13**（既有迁移脚本，非本任务改动，属正常启动行为）。

关键接线（已核实一致，未改配置）：

- Web 代理目标：`web/.env.local` 的 `VITE_API_PROXY_TARGET=http://localhost:8081`（前端配置，非密钥）→ 正确指向后端容器。
- Android 调试 API：`BuildConfig.API_BASE_URL = http://10.0.2.2:8081/api/v1/`（`build.gradle.kts` 的 `.orElse` 默认值）→ 模拟器 `10.0.2.2` 映射宿主机 8081，正确指向后端。

## 三、需要人工点击验证的清单（不使用/记录真实账号密码）

### 招聘者（Web，`http://localhost:4173`）

1. **Application Detail 状态流程**：`APPLIED` → 点 `Start review`（需填写理由）→ `IN_REVIEW` → `Schedule interview` → `INTERVIEW` → 面试卡（重排/完成/取消）→ `REJECTED` / `WITHDRAWN` 只读终止态。
2. **面试卡**：模式、日期/时间（显示时区）、时长、会议/链接/地点、Google Meet 同步状态。
3. **跳转求职者聊天**：`Message candidate` 按 `applicationId` 精确查会话并跳 `/recruiter/messages/{conversationId}`；无会话时显示中性禁用态；加载中禁用标签；失败可重试。

### 求职者 Android（模拟器 `Pixel_10_Pro`，390×844）

1. **Profile 身份卡**：头像/姓名首字母、姓名、headline、location、邮箱、`Edit profile` 入口。
2. **Career snapshot 统计**：Applications / Interviews / Chats / Saved 真实计数（含 0 态）。
3. **编辑资料**：仅 `fullName`/`headline`/`location` 可改（邮箱/头像只读），校验、提交中禁用、保存成功。
4. **简历摘要**：有简历（headline + 截断 summary + ≤2 条经历 + `View / edit resume`）/ 无简历（创建引导）/ 加载失败（可重试且 Profile 仍可见）。
5. **退出登录**：`Sign out` 为独立危险操作。

## 四、未验证项与原因

| 未验证项 | 原因 |
|---|---|
| 招聘者 Web 全流程手工点击 | 需真实招聘者账号登录，本人不提供/不使用真实账号密码 |
| 求职者 Android 各页面手工点击 | 需真实求职者账号登录；当前 App 已启动并停在登录页 |
| 390×844 个人页视觉走查/截图 | 需登录并导航至 Profile 页，无账号无法进入；本轮未做模拟器截图 |
| Android 端到后端的数据链路（登录后） | 已确认 `API_BASE_URL` 与后端可达，但未以真实账号触发业务请求 |

> 以上「未手测」项均未标记为「已通过」。

## 五、边界确认

- 未修改任何业务代码、配置、数据库、Flyway、OAuth/Google Meet、Admin、ML、Agent。
- 未 commit / push / pull / merge。
- 未删除 Docker 数据卷、未重置数据库（MySQL 数据卷 `mysql-data` 原样保留）。
- 未输出任何密钥：仅展示容器 env 的**键名**、前端 `VITE_API_PROXY_TARGET` 与模拟器回环地址 `10.0.2.2` 等非敏感配置；`DB_PASSWORD`/`JWT_SECRET`/OAuth 密钥等值全程未打印。
- 本报告涉及的环境仅做**只读探测**（curl 探测 HTTP 状态码、docker ps/inspect 状态、adb 状态），无任何写入性操作。
