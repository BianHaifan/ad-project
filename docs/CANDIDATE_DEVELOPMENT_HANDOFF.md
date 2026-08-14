# Candidate 开发对接文档

更新时间：2026-08-11

本文档用于在新的 Codex 窗口中继续 Candidate 端开发。它记录当前仓库的真实实现状态、Mock 边界、Candidate 首条垂直切片建议、接口依赖和本地联调约束。

## 1. 当前结论

- `docs/openapi-v1.yaml` 共定义 45 个 HTTP 操作。
- 当前后端真实实现 17 个：Auth 4 个、Recruiter Job 6 个、Candidate Job 2 个、
  Candidate Profile 2 个、Candidate Resume 2 个、Candidate Application 提交 1 个。
- Recruiter 已经可以在 MySQL 中创建、编辑、发布、暂停、恢复和关闭职位。
- Candidate 职位浏览的真实数据前置条件已经具备：数据库中可以存在 `ACTIVE` 职位。
- Android Candidate 的 Auth、Job、Profile、单份默认 Resume、投递确认和 Application 提交使用真实 HTTP API；
  Application 列表/详情/撤回、消息和 Learning 仍为明确 Mock 或未接入。
- Recruiter Web 的 Job 页面使用真实 API；Dashboard、Applications、Messages 仍使用 Mock。

状态标记：

- ✅：后端真实实现并有自动化测试。
- ⬜：OpenAPI 已定义，但后端尚未实现。
- Mock：客户端存在演示页面或假数据，不代表 API 已完成。

## 2. API 完成情况

所有路径在运行时统一带 `/api/v1` 前缀。例如 OpenAPI 的 `/jobs` 对应 `GET /api/v1/jobs`。

### 2.1 Auth

| 方法 | 路径 | 状态 | 客户端现状 |
|---|---|---:|---|
| POST | `/auth/register` | ✅ | Recruiter Web 已接入；Android Candidate 未接入 |
| POST | `/auth/login` | ✅ | Recruiter Web 已接入；Android Candidate 未接入 |
| POST | `/auth/refresh` | ✅ | Recruiter Web 已实现最多刷新并重试一次；Android Candidate 未接入 |
| POST | `/auth/logout` | ✅ | Recruiter Web 已接入；Android Candidate 未接入 |

### 2.2 Candidate Jobs、Profile、Resume

| 方法 | 路径 | 状态 | 当前客户端边界 |
|---|---|---:|---|
| GET | `/jobs` | ✅ | Android Job Feed 使用真实 API |
| GET | `/jobs/{jobId}` | ✅ | Android Job Detail 使用真实 API 和 Application 状态 |
| GET | `/candidate/profile` | ✅ | Android Profile 使用真实 API |
| PATCH | `/candidate/profile` | ✅ | Android Profile 真实写入 |
| GET | `/candidate/resume` | ✅ | Android 单份默认 Resume 使用真实 API |
| PUT | `/candidate/resume` | ✅ | Android Resume 真实写入 |
| GET | `/features/learning` | ⬜ | Android Learning 使用 Mock 的 Coming Soon 内容 |

### 2.3 Candidate Applications

| 方法 | 路径 | 状态 | 当前客户端边界 |
|---|---|---:|---|
| POST | `/jobs/{jobId}/applications` | ✅ | Android 真实投递；事务内创建不可变 Resume Snapshot |
| GET | `/candidate/applications` | ⬜ | Android申请列表使用 Mock |
| GET | `/candidate/applications/{applicationId}` | ⬜ | 尚无真实详情 |
| POST | `/candidate/applications/{applicationId}/withdraw` | ⬜ | 尚无真实撤回 |

### 2.4 Candidate Conversations 和 Messages

| 方法 | 路径 | 状态 | 当前客户端边界 |
|---|---|---:|---|
| GET | `/candidate/conversations` | ⬜ | Android Messages 使用 Mock |
| GET | `/candidate/conversations/{conversationId}` | ⬜ | Android Chat Detail 使用 Mock |
| GET | `/candidate/conversations/{conversationId}/messages` | ⬜ | 消息历史使用 Mock |
| POST | `/candidate/conversations/{conversationId}/messages` | ⬜ | 发送只产生本地 Mock Message |
| PUT | `/candidate/conversations/{conversationId}/read-state` | ⬜ | 尚未接入 |

### 2.5 Recruiter 基础资料和 Dashboard

| 方法 | 路径 | 状态 | 当前 Web 边界 |
|---|---|---:|---|
| GET | `/recruiter/me` | ⬜ | 未接入 |
| GET | `/recruiter/company` | ⬜ | 未接入 |
| PATCH | `/recruiter/company` | ⬜ | 未接入 |
| GET | `/recruiter/dashboard` | ⬜ | Dashboard 继续使用 Mock |

### 2.6 Recruiter Jobs

| 方法 | 路径 | 状态 | 说明 |
|---|---|---:|---|
| GET | `/recruiter/jobs` | ✅ | 真实分页、过滤和 MySQL 数据 |
| POST | `/recruiter/jobs` | ✅ | 创建 `DRAFT`；公司必须 `APPROVED` |
| GET | `/recruiter/jobs/{jobId}` | ✅ | 当前公司详情，跨公司返回 404 |
| PATCH | `/recruiter/jobs/{jobId}` | ✅ | 仅 `DRAFT` 可编辑，使用 `expectedVersion` |
| POST | `/recruiter/jobs/{jobId}/publish` | ✅ | `DRAFT → ACTIVE` |
| POST | `/recruiter/jobs/{jobId}/status` | ✅ | `ACTIVE ↔ PAUSED`、`ACTIVE/PAUSED → CLOSED` |

### 2.7 Recruiter Applications 和 Interviews

| 方法 | 路径 | 状态 | 当前 Web 边界 |
|---|---|---:|---|
| GET | `/recruiter/applications` | ⬜ | Applications 使用 Mock |
| GET | `/recruiter/applications/{applicationId}` | ⬜ | 详情使用 Mock |
| POST | `/recruiter/applications/{applicationId}/transitions` | ⬜ | Mock 操作不得视为真实写入 |
| PUT | `/recruiter/applications/{applicationId}/owner` | ⬜ | 未实现 |
| GET | `/recruiter/applications/{applicationId}/notes` | ⬜ | 未实现 |
| POST | `/recruiter/applications/{applicationId}/notes` | ⬜ | 未实现 |
| GET | `/recruiter/applications/{applicationId}/resume-snapshot` | ⬜ | 未实现 |
| GET | `/recruiter/applications/{applicationId}/resume-snapshot/pdf` | ⬜ | 未实现 |
| POST | `/recruiter/applications/{applicationId}/interviews` | ⬜ | 未实现 |
| PATCH | `/recruiter/interviews/{interviewId}` | ⬜ | 未实现 |

### 2.8 Recruiter Conversations 和 Messages

| 方法 | 路径 | 状态 | 当前 Web 边界 |
|---|---|---:|---|
| GET | `/recruiter/conversations` | ⬜ | Messages 使用 Mock |
| GET | `/recruiter/conversations/{conversationId}` | ⬜ | 使用 Mock |
| GET | `/recruiter/conversations/{conversationId}/messages` | ⬜ | 使用 Mock |
| POST | `/recruiter/conversations/{conversationId}/messages` | ⬜ | 不会真实发送 |
| PUT | `/recruiter/conversations/{conversationId}/read-state` | ⬜ | 未实现 |

合计：45 个操作，17 个已实现，28 个未实现；自动化与客户端 Mock 不计入后端完成数。

## 3. Candidate 首条推荐垂直切片

建议新窗口只完成：

```text
Candidate 注册或登录
→ 从后端加载 ACTIVE 职位列表
→ 打开职位详情
→ 返回列表
→ 刷新或重启 Android 页面后重新从后端加载
```

本切片接入：

- 复用现有 `POST /api/v1/auth/register`
- 复用现有 `POST /api/v1/auth/login`
- 复用现有 `POST /api/v1/auth/refresh`
- 复用现有 `POST /api/v1/auth/logout`
- 新实现 `GET /api/v1/jobs`
- 新实现 `GET /api/v1/jobs/{jobId}`

暂不实现：

- Candidate Profile 和 Resume
- 投递、申请列表、申请详情、撤回
- 收藏职位
- 推荐模型和真实 `matchScore`
- Conversation 和 Message
- Recruiter Applications
- Learning、Agent、Admin

这样可以先验证完整的跨客户端链路：Recruiter Web 发布的真实职位能够被 Candidate Android 读取。

## 4. Candidate Job 后端规则

必须遵守 `docs/openapi-v1.yaml`：

- 只有已认证 Candidate 可以访问两个接口。
- 未登录返回 401，Recruiter 访问返回 403。
- 列表和详情只能返回 `status=ACTIVE` 且对 Candidate 可见的职位。
- `DRAFT`、`PAUSED`、`CLOSED`、不存在和不可见职位详情统一返回 404，避免泄露。
- 列表实现 OpenAPI 的 `q`、`employmentType`、`category`、`page` 和 `pageSize`。
- 响应严格使用 `data` 和 `meta` 包装。
- 默认稳定排序建议继续使用 `publishedAt DESC, id DESC`；若最终裁决使用其他排序，先写入 OpenAPI。
- 不能暴露 JPA Entity。
- 时间使用 UTC，响应必须以 `Z` 结尾。
- `matchScore` 在推荐系统未实现时返回 `null`，不得伪造分数。
- `matchAnalysis` 在推荐系统未实现时返回 `null`。
- `applicationState` 在 Application 模块未实现时只能返回真实可证明的状态。首切片可返回 `NOT_APPLIED`，但开始实现 Application 后必须改为数据库查询。
- `isSaved` 对应的收藏 API 当前不存在。不得展示为已经持久化；首切片建议返回 `false` 并把收藏按钮明确标记为未接入，或先在契约中裁决收藏能力。

建议在现有 `job` 模块中增加 Candidate API 投影，而不是创建第二套职位表：

```text
job/api/CandidateJobController
job/application/JobService 的 Candidate 查询用例
job/api Candidate DTO/response mapper
job/infrastructure 复用 JobRepository 的只读查询
```

Recruiter Job 的权限和状态机不得因 Candidate 查询而改变。

## 5. 开始编码前必须裁决的契约缺口

`GET /jobs` 定义了 `category=RECOMMENDED|AI_LLM|BACKEND|DATA`，但当前：

- OpenAPI 的 `Job`/CreateJobRequest/UpdateJobRequest 没有 category 字段。
- V2 `jobs` 表没有 category 列。
- 规范没有定义从 title、description 或 skills 推导 category 的算法。
- `RECOMMENDED` 又依赖尚未实现的推荐模型。

因此不能在代码里自行按关键词创造分类规则。进入新窗口后应先选择并评审一种方案：

1. 推荐方案：首个 Candidate Job 切片从 OpenAPI 暂时移除 `category` 参数，等职位分类和推荐模型契约确定后再恢复；说明这是消除无法实现的契约字段，而不是改变已上线行为。
2. 或者正式为 Job 增加可持久化 category，并同步 Create/Update DTO、Recruiter UI、Flyway migration 和测试。

在该决定落地前，不应声称 `GET /jobs` 已严格实现全部过滤参数。

## 6. Android 当前状态和改造边界

关键文件：

- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`
- `android/app/src/main/java/com/adproject/candidate/data/api/CandidateApi.kt`
- `android/app/src/main/java/com/adproject/candidate/data/contract/ApiContract.kt`
- `android/app/src/main/java/com/adproject/candidate/data/model/CandidateModels.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/auth/AuthScreens.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/MainScreens.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/JobDetailScreen.kt`

现状：

- `AdCandidateApp` 默认注入 `FakeCandidateRepository`。
- Repository 方法是同步方法，Compose 页面直接读取返回值。
- 登录和注册按钮没有发起网络请求，只导航到 Jobs。
- Gradle 当前没有 Retrofit、OkHttp、JSON serializer、DataStore 或网络测试依赖。
- 模拟器 API 地址常量已写为 `http://10.0.2.2:8080/api/v1/`，但没有真实 Client 使用它。
- Fake Repository 中包含演示邮箱和密码。它们只能作为 Mock 数据，不得复制到真实凭据、日志或后端配置。

首切片建议：

- 新增独立 Auth HTTP Client 和 Candidate Job HTTP Client。
- 页面不直接调用 HTTP Client。
- Repository 对外改为 `suspend`，由 ViewModel/状态持有层调用。
- 页面处理 loading、empty、error、content 和 submitting/disabled。
- Token 由集中式 Session/Token Store 管理；页面不得拼接 Authorization。
- 401 最多 refresh 并重试原请求一次。
- Refresh 成功必须保存旋转后的新 refresh token。
- Refresh 失败清理本地会话并返回登录页。
- Token 不写日志，不存入普通明文 SharedPreferences。
- 模拟器使用 `10.0.2.2:8080`；真机需要使用开发机局域网地址并单独安全配置。
- Applications、Messages、Profile、Resume 可以暂时保留 Mock，但必须从依赖注入和命名上明确，不得与真实 Job Repository 混成一个“全功能已接入”的对象。
- 不为了首切片重构所有 Compose 页面。

## 7. 推荐测试

后端至少覆盖：

- Candidate 加载 ACTIVE/PUBLIC 职位列表。
- 不返回 DRAFT、PAUSED、CLOSED 或不可见职位。
- 空列表。
- 分页和稳定排序。
- `q`、`employmentType` 及最终裁决后的 category 行为。
- Candidate 查看 ACTIVE/PUBLIC 详情。
- 不存在或不可见详情返回 404。
- 未登录返回 401。
- Recruiter 访问返回 403。
- ErrorResponse 包含 requestId。
- 当前 Recruiter Job 和 Auth 测试继续通过。
- Testcontainers 使用独立临时 MySQL，不连接本地开发库。

Android 至少覆盖：

- Candidate 登录成功并保存会话。
- 登录 loading、disabled、防重复提交和字段错误。
- 登录 401 显示安全提示。
- 职位列表 loading、empty、error、content。
- 列表只展示 API 返回数据。
- 详情加载成功和 404 状态。
- 401 后 refresh 并重试成功。
- Refresh 失败清理会话并导航登录。
- 页面不直接拼接 Authorization。
- 不输出 Token。
- Applications 和 Messages 仍明确使用 Mock。

完成后运行：

```bash
cd backend
mvn test
mvn package

cd ../android
./gradlew test
./gradlew lint
./gradlew assembleDebug

cd ..
git diff --check
git status --short
```

如果有可用模拟器，再运行相关 instrumentation 测试；没有运行时必须在交付说明中写明。

## 8. 本地联合验收数据准备

复用当前 Docker MySQL、数据库账号、密码、volume、用户、公司、职位和 Flyway 历史：

1. 不执行 `docker compose down -v`、`DROP`、`TRUNCATE` 或批量删除。
2. 不读取、修改或提交根目录 `.env`。
3. 数据库密码继续通过现有环境变量或启动参数注入。
4. 使用现有 APPROVED Recruiter 登录 Web。
5. 创建并发布一个职位，确认其状态为 ACTIVE。
6. 启动后端，保持 MySQL 不重建。
7. 在 Android 模拟器中注册或登录 Candidate。
8. Candidate Job Feed 应显示刚刚发布的真实职位。
9. 打开详情，核对 title、company、salary、skills、description 和 publishedAt。
10. 刷新页面以及重启后端后再次读取，确认数据仍来自 MySQL。

## 9. 新窗口开始提示词

可把下面内容作为新任务的开头，再附上详细验收要求：

> 完整阅读 AGENTS.md、docs/CANDIDATE_DEVELOPMENT_HANDOFF.md 和其中引用的规范。先运行 git status --short 并保留当前所有 staged/unstaged 改动；不读取或修改根目录 .env，不清空或重建当前 MySQL。目标是完成 Candidate 第一条真实垂直切片：Candidate 注册/登录 → 加载 ACTIVE 可见职位列表 → 查看职位详情 → 刷新后继续从 MySQL 读取。复用现有 Auth，不修改 Recruiter Job 状态机；只实现 OpenAPI 的 GET /api/v1/jobs 和 GET /api/v1/jobs/{jobId}，并把 Android Auth、Job Feed、Job Detail 接入真实 HTTP API。Applications、Messages、Profile、Resume、Learning 保持明确 Mock 或未接入。在编码前先处理文档中 category 过滤参数没有数据来源的契约冲突，不得自行创造分类算法。

## 10. 后续最小顺序

Candidate Jobs 联调通过后，推荐依次实现：

1. Candidate Profile。
2. 单份默认 Resume。
3. Candidate 提交 Application，同时生成不可变 Resume Snapshot。
4. Candidate Application 列表、详情和撤回。
5. Recruiter Application 列表、详情和状态流转。
6. 明确 Conversation 创建规则后实现简单 Message。

Message 不应先于 Application 落地，因为 OpenAPI 的 Conversation 必须绑定 `applicationId` 和 `jobId`，并且当前契约没有单独创建 Conversation 的接口。
