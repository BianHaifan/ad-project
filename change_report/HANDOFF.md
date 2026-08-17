# 交接提示词（新会话用）

> 把下面整段粘贴给新会话即可。它自包含项目背景、本次完成的工作、环境状态、待办与约束，新会话无需再回读旧 transcript。

---

你是 `ad-project`（招聘平台）的开发助手。项目是「Claude 实现 / Codex 复核」多智能体工作流的一环，语言为中文。请先理解现状再行动，**不要未经要求就 commit/push/pull/merge/reset，不要打印任何密钥（DB_PASSWORD / JWT_SECRET / OAuth 等）**。

## 一、项目结构

| 端 | 技术栈 | 位置 |
|---|---|---|
| 后端 | Java 21 + Spring Boot 3.5.4 + MySQL 8.4 + Flyway | `backend/` |
| Web | React + TypeScript | `web/` |
| Android | Kotlin + Jetpack Compose | `android/` |
| 推荐模型 | Python（独立服务，可降级） | `ml-service/` |

当前分支 `main`。工作区有大量**跨多个 package 的未提交改动**（conversation、recommendation、web 等），属正常现象，是我方与其它 agent 的多包改动叠加，**不要随意 `git checkout` / `reset` 丢弃它们**。

## 二、本次会话已完成的工作

### Package 2A — 推荐职位流分页与窄屏复核修复（已实现 + 测试通过）

修复了两个已确认问题：

1. **Android 类型筛选窄屏溢出**（`android/app/src/main/java/com/adproject/candidate/feature/jobs/MainScreens.kt`）：`All / Full time / Internship / Part time` 由普通 `Row` 改为可横向滚动的局部 `LazyRow`，保留 All 默认、筛选行为、选中样式（选中 22.sp 加粗 + `AdText`，未选中 17.sp + `0xFF6E7781`）、`Modifier.clickable` 无障碍语义。**未触碰** JobSkillChip/TagChip。

2. **后端分页整数溢出**（`backend/src/main/java/com/adproject/recommendation/application/CandidateRecommendationService.java`）：`int offset = (page-1)*pageSize` 改为 `long`，并加守卫：`offset >= total` 时返回 **200 + 空 data + 正确 page/pageSize/total + hasNext=false**（不抛异常、不 500、无负索引）。`total = min(filtered.size(), MAX_RANKED=100)`，故 `int start = (int) offset` 安全窄化。

3. 新增集成测试 `hugePageDoesNotOverflowAndReturnsEmptyPage`（`backend/src/test/java/com/adproject/recommendation/CandidateRecommendationIntegrationTest.java`），断言 `page=2147483647&pageSize=20` → 200 + 空 data + total=1 + hasNext=false。

**测试结果（真实）**：
- 后端 JDK21：`CandidateRecommendationIntegrationTest`(8) + `MlRecommendationClientTest`(1) = **9 tests，0 失败**。
- Android：`testDebugUnitTest` **89 tests 0 失败**，`lintDebug`、`assembleDebug` 通过。

### 最终人工验收准备（已就绪）

环境已准备好供用户本人手动验收，未改任何源码/配置/DB。产出两份报告：
- `change_report/candidate-recommended-jobs-package-2a.md`
- `change_report/candidate-recommended-jobs-final-verification.md`

## 三、当前环境状态（可复用，不必重建）

| 组件 | 状态 | 说明 |
|---|---|---|
| MySQL | ✅ 运行中 | Docker 容器 `adproject-local-mysql`，端口 `13306->3306` |
| 后端 | ✅ 运行中 | 容器 `adproject-local-backend`，`mvn spring-boot:run`（bind-mount 当前源码），端口 `8081->8080`；**已重启到 Package 2/2A 最新源码** |
| Android 模拟器 | ✅ `emulator-5554` | AVD `Pixel_10_Pro` |
| debug APK | ✅ 已装最新 | 17:09 构建，`adb install -r` 保留会话，App 落在 Jobs 页 |
| ML Python | ⛔ 未运行 | 推荐走 `Rules fallback` 降级（`modelStatus=DEGRADED`），符合预期 |

后端未登录请求推荐接口 `GET /api/v1/candidate/recommendations/jobs` 返回 **HTTP 401**，即服务在线。

## 四、关键环境细节 / 已知坑（务必沿用）

- **JAVA_HOME 每次 PowerShell 调用都是新会话，必须显式设置**：`$env:JAVA_HOME = "C:\Users\14188\.jdks\ms-21.0.8"`，否则 gradle/mvn 会误用 Java 8。
- **Maven 路径**：`C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\plugins\maven\lib\maven3\bin\mvn.cmd`（或先设好 JAVA_HOME 再调 `mvn`）。
- **adb 全路径**：`C:\Users\14188\AppData\Local\Android\Sdk\platform-tools\adb.exe`（不在 PATH）。
- **Android debug API 地址**：`http://10.0.2.2:8081/api/v1/`（模拟器访问宿主机回环别名，见 `android/app/build.gradle.kts`）。
- **PowerShell 5.1 坑**：native exe 的 stderr 会被包装成 `NativeCommandError`，`$?` 变 false 但进程可能实际成功；**不要用 stderr 判成功**，用退出码 / surefire XML / 日志判断。
- **Maven `-Dtest` 通配**：`-Dtest='com.adproject.recommendation.*'` 不匹配；用显式类名如 `-Dtest='CandidateRecommendationIntegrationTest,MlRecommendationClientTest'`。
- **不要删 Docker 卷、不要重置数据库**：`mysql-data` 卷是验收数据所在。

## 五、待办（属于「用户本人」的人工验收，非我方任务）

新会话如被要求继续验收，只做**能客观验证**的事；以下交互项必须由用户本人点击/滑动确认，**不得声称已通过**：
1. 类型筛选横向滑动不溢出（窄屏/滑动验证）。
2. 点击类型后列表刷新。
3. 搜索按钮手动提交。
4. 向下滚动自动加载下一页（懒加载）；**且无法确认库中 ACTIVE+PUBLIC 职位是否 >10 条**，若 ≤10 则懒加载无从触发，如实标注「无足够真实数据验证」。

## 六、硬约束（跨会话保持一致）

- 不改：`ml-service/**`、`agent/**`、OpenAPI（`docs/openapi-v1.yaml`）、数据库/Flyway、认证、Google Meet/OAuth、Admin、Web、推荐算法、模型输入输出、普通 `/api/v1/jobs`。
- 不做 git 写操作（commit/push/pull/merge/reset）除非用户明确要求。
- 不打印密钥。
- 只做与当前任务一致的修改，不扩展产品范围；改动前先确认边界，改动后如实报告（测试结果、未验证项都照实写，不夸大）。
