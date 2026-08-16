# 修改报告：面试核心流程第一包

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 对应计划/任务：`tasks/interview-plan.md`、`tasks/interview-todo.md`「面试核心流程第一包」
- 修改范围：`backend/`（application/interview 代码、V7 迁移、测试）、`web/`（招聘者申请详情页、API 客户端/模型、测试）、`android/`（候选人申请详情、测试）、`docs/openapi-v1.yaml`、`docs/API_COVERAGE.csv`、`change_report/`、`tasks/`
- 明确禁止且未改动：`ml-service/`、Agent、Admin、认证/JWT/刷新令牌、既有 Messages API/表/轮询、`google-meet-integration-plan.md` / `google-meet-integration-todo.md`（下一包）、他人改动与构建产物

## 完成内容

- 把只有 `INTERVIEW` 状态的申请流程，扩展成招聘者可以实际安排、改期、完成、取消面试的闭环；候选人 Android 只读查看面试详情。
- 后端新增 Flyway `V7__create_interviews.sql`，一张 `interviews` 表（`application_id` 唯一，每申请最多一场面试），字段：`applicationId`、`scheduledAt`（UTC）、`timezone`、`durationMinutes`、`mode`（ONLINE/ONSITE/PHONE）、`locationOrMeetingUrl`、`note`、`status`（SCHEDULED/COMPLETED/CANCELLED）、`version`、`createdAt`、`updatedAt`，含 `duration_minutes`（1–1440）与 `version >= 1` 两个 CHECK、`uk_interviews_application` 唯一约束与外键。
- 新增 interview 领域/基础设施/应用/接口层（`com.adproject.application` 下），实现 OpenAPI 两个端点：
  - `POST /api/v1/recruiter/applications/{applicationId}/interviews`（201）
  - `PATCH /api/v1/recruiter/interviews/{interviewId}`（200）
- 创建面试是单事务：解析招聘者所属公司 → `PESSIMISTIC_WRITE` 锁定申请 → 校验 `expectedApplicationVersion`（不匹配 409 `VERSION_CONFLICT`）→ `existsByApplicationId`（重复 409 `INTERVIEW_ALREADY_EXISTS`）→ 状态必须 `IN_REVIEW`（否则 409 `INVALID_APPLICATION_TRANSITION`）→ 保存 `SCHEDULED` 面试 + 申请 `transitionTo(INTERVIEW)` + 写一条 `Interview scheduled` 审计事件。
- 更新面试：SCHEDULED 可 reschedule（非空字段覆盖，空字段回退旧值）/ complete / cancel；`COMPLETED`、`CANCELLED` 为终态（否则 409 `INVALID_INTERVIEW_TRANSITION`）；乐观锁 `expectedVersion` 不匹配 409。
- 权限与所有权：仅 `RECRUITER` 可访问（非招聘者 403）；招聘者只能操作本公司的申请/面试（跨公司 404）；候选人及跨公司招聘者无法发现。
- 移除旧「Move to interview」状态下拉：`TransitionTarget` 由 `{ IN_REVIEW, REJECTED, INTERVIEW }` 收敛为 `{ IN_REVIEW, REJECTED }`；`allowed()` 移除 `IN_REVIEW -> INTERVIEW` 分支，保留 `INTERVIEW -> REJECTED`（面试后拒绝仍可用）。
- 招聘者 Web 申请详情：
  - 仅 `IN_REVIEW` 显示「Schedule interview」弹窗（date/time、timezone、duration、mode、location/meeting link 必填，note 可选）；提交成功后 refetch 详情并显示面试卡片。
  - 面试卡片展示时间/时区/时长/模式/地点链接/note/状态；`SCHEDULED` 可 reschedule / complete / cancel；终态仅提示不可再改。
  - 处理 loading / 校验 / submitting / 错误 / 409 冲突 / 终态；会议链接保持手动输入（不自动生成）。
- 候选人 Android 申请详情新增只读面试卡片：展示时间、时区、时长、模式、地点链接（点击经 `LocalUriHandler` 打开系统浏览器）、状态；处理无面试、已取消、加载、错误状态；不提供候选人侧 accept/reject/reschedule/Google 授权。
- 候选人映射侧：`note` 置空（招聘者专属），`scheduledAt` 仅在面试 `SCHEDULED` 时返回，避免泄露招聘者私有备注与已取消面试时间。

## 修改文件

### 后端（新增）

- `backend/src/main/resources/db/migration/V7__create_interviews.sql`（新增）
  - 主要变化：`interviews` 表 + 唯一约束/CHECK/外键（见上）。
- `backend/src/main/java/com/adproject/application/domain/InterviewStatus.java`、`InterviewMode.java`（新增）
  - 主要变化：`SCHEDULED/COMPLETED/CANCELLED` 与 `ONLINE/ONSITE/PHONE` 枚举。
- `backend/src/main/java/com/adproject/application/infrastructure/InterviewEntity.java`（新增）
  - 主要变化：构造时 `version=1`；`reschedule(...)` / `complete(now)` / `cancel(now)` / 私有 `touch(now)`（`updatedAt=now`、`version+=1`）。
- `backend/src/main/java/com/adproject/application/infrastructure/InterviewRepository.java`（新增）
  - 主要变化：`findByApplicationId`、`existsByApplicationId`、`findByIdForUpdate`（`@Lock(PESSIMISTIC_WRITE)`）。
- `backend/src/main/java/com/adproject/application/api/InterviewDtos.java`（新增）
  - 主要变化：`Interview`、`CreateInterviewRequest`（校验 `scheduledAt`/`timezone`/`durationMinutes`/`mode`/`locationOrMeetingUrl`/`expectedApplicationVersion`）、`UpdateInterviewRequest`（可选字段 + 必填 `expectedVersion`）、`InterviewResponse`。
- `backend/src/main/java/com/adproject/application/application/InterviewService.java`（新增）
  - 主要变化：`create()` / `update()` 单事务 + 权限/所有权/乐观锁/状态机 + `toDto()`。
- `backend/src/main/java/com/adproject/application/api/RecruiterInterviewController.java`（新增）
  - 主要变化：两个端点，透传 `RequestIdFilter.current(...)`。

### 后端（既有文件修改）

- `backend/src/main/java/com/adproject/application/api/RecruiterApplicationDtos.java`
  - 主要变化：`TransitionTarget` 收敛为 `{ IN_REVIEW, REJECTED }`；`Detail.interview` 字段类型改为 `InterviewDtos.Interview`。
- `backend/src/main/java/com/adproject/application/application/RecruiterApplicationService.java`
  - 主要变化：注入 `InterviewRepository`；`detail()` 填充 `interview`；新增 `interviewDto()`；`allowed()` 移除 `IN_REVIEW -> INTERVIEW` 分支。
- `backend/src/main/java/com/adproject/application/api/CandidateApplicationResponseMapper.java`
  - 主要变化：`summary()`/`detail()` 接收 `InterviewEntity`；新增 `scheduledAt()`（仅 SCHEDULED 返回）、`interviewDto()`（`note=null`）。
- `backend/src/main/java/com/adproject/application/application/CandidateApplicationQueryService.java`
  - 主要变化：注入 `InterviewRepository`，`summary()`/`detail()` 查询并传递面试。

### 测试

- `backend/src/test/java/com/adproject/application/RecruiterInterviewIntegrationTest.java`（新增）
  - 主要变化：7 个集成测试——创建成功并转换申请+写审计、招聘者认证、跨公司/错误源状态/重复面试/过期版本、字段校验、改期/完成/取消状态机、所有权与角色与版本、面试后仍可拒绝。
- `backend/src/test/java/com/adproject/application/RecruiterApplicationIntegrationTest.java`（修改）
  - 主要变化：状态机测试由「IN_REVIEW→INTERVIEW」改为「IN_REVIEW→REJECTED→IN_REVIEW(409)→REJECTED(409 版本)」，断言候选侧版本 3、时间线 3 条、审计 2 条、WITHDRAWN 原始转换 expectedVersion=3。

### 招聘者 Web

- `web/src/models/recruiter.ts`：新增 `UpdateInterviewRequest`；`ApplicationTransitionRequest.toStatus` 收敛为 `'IN_REVIEW' | 'REJECTED'`。
- `web/src/api/recruiterRepository.ts`：`RecruiterTransitionStatus` 收敛；接口新增 `createInterview` / `updateInterview`。
- `web/src/api/applicationHttpClient.ts`：新增 `createInterview` / `updateInterview` 与 `parseInterviewEnvelope` / `parseInterview` / `isInterviewMode` / `isInterviewStatus`。
- `web/src/api/repository.ts`：把 `createInterview` / `updateInterview` 接真实 HTTP 客户端。
- `web/src/api/queries.ts`：新增 `useCreateInterview` / `useUpdateInterview`（成功后 invalidate `['applications']` 与 `keys.application(...)`）。
- `web/src/pages/ApplicationDetailPage.tsx`：移除「Move to interview」下拉；新增 schedule/reschedule 弹窗与面试卡片（含 reschedule/complete/cancel 与终态提示）。
- `web/src/mocks/mockRecruiterRepository.ts`：`Omit` 列表补充 `createInterview` / `updateInterview`。
- `web/src/theme/global.css`：新增 `.badge.scheduled` / `.badge.completed` / `.badge.cancelled`。
- `web/src/pages/ApplicationPages.test.tsx`：新增 3 个测试（IN_REVIEW 安排面试、SCHEDULED 卡片动作、已有面试不再显示安排入口）。

### 候选人 Android

- `android/.../feature/applications/RealApplicationTrackingScreens.kt`：详情新增只读 `InterviewCard`（时间/时区/时长/模式/地点链接/状态，链接打开系统浏览器）；新增 `InterviewStatus.label()` / `InterviewMode.label()`。
- `android/.../test/.../RepositoryIntegrationTest.kt`：新增 `interviewDetailParsesFullInterview` 解析测试。

### 契约与文档

- `docs/openapi-v1.yaml`：`createRecruiterInterview` / `updateRecruiterInterview` 的 `x-status` 与描述由 `DRAFT` → `IMPLEMENTED`。
- `docs/API_COVERAGE.csv`：`Interviews` 两行状态由 `DRAFT` → `IMPLEMENTED`。

## API 变化

- 是否变化：是
- 状态：两个面试端点由 DRAFT 冻结为 IMPLEMENTED；申请详情/列表契约新增 `interview` / `scheduledAt` 字段（非破坏性，均 nullable）。
- `POST /api/v1/recruiter/applications/{applicationId}/interviews`：`CreateInterviewRequest`（`scheduledAt`/`timezone`/`durationMinutes`/`mode`/`locationOrMeetingUrl`/`expectedApplicationVersion` 必填，`note` 可选）→ 201 + `Interview`；409 场景：`VERSION_CONFLICT` / `INTERVIEW_ALREADY_EXISTS` / `INVALID_APPLICATION_TRANSITION`。
- `PATCH /api/v1/recruiter/interviews/{interviewId}`：`UpdateInterviewRequest`（仅 `expectedVersion` 必填）→ 200 + `Interview`；`status` 传 `COMPLETED`/`CANCELLED` 触发终态，否则按非空字段 reschedule；409：`VERSION_CONFLICT` / `INVALID_INTERVIEW_TRANSITION`。
- 权限：非招聘者 403；跨公司/不存在资源 404；未登录 401。
- 与 OpenAPI 是否一致：是（已同步 `x-status`、描述与覆盖表）。

## 数据库变化

- 是否变化：是
- 新增迁移：`V7__create_interviews.sql`
- 新增表：`interviews`
- 新增约束/索引：`uk_interviews_application`（`application_id` 唯一）、`fk_interviews_application`、`chk_interviews_duration`（1–1440）、`chk_interviews_version`（>=1）。
- 未改动既有表结构、既有迁移文件、数据库类型。

## 权限与安全

- 涉及角色：Candidate（只读）、Recruiter（写）。
- 认证检查：路由统一 JWT 过滤器，未登录 401。
- 角色检查：面试端点仅 `RECRUITER`，非招聘者 403。
- 资源所有权检查：通过 `company_members` 解析招聘者公司，申请/面试所属公司必须匹配，否则 404（跨公司不可见）；候选人仅能查看自己的申请。
- 敏感信息风险：低。候选人视角不返回 `note`（招聘者私有备注）；`scheduledAt` 仅在 `SCHEDULED` 时返回，已取消面试不泄露时间。
- 并发：创建/更新均 `PESSIMISTIC_WRITE` + 乐观锁 `expectedVersion`/`expectedApplicationVersion`，不匹配 409。

## 测试与验证

### 后端（IntelliJ 内置 Maven + JBR 21；环境 `JAVA_HOME` 原为 JDK 1.8、`mvn` 不在 PATH，已显式指定）

- 命令：`mvn -o test -Dtest='RecruiterInterviewIntegrationTest,RecruiterApplicationIntegrationTest'`
  - 结果：`Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`（面试 7 + 申请 4）。
- 命令：`mvn -o test`（全量）
  - 结果：`Tests run: 83, Failures: 2, Errors: 0, Skipped: 3`
  - 面试/申请相关测试全部通过；2 个既有失败为 `RecruiterJobIntegrationTest` 的 H2 纳秒（9 位）vs 微秒（6 位）精度断言（`publishedAt` 第 231、332 行），与本任务无关、非本次引入；3 个跳过为 `MySqlFlywayIntegrationTest`（Docker 不可用）。

### Web

- `npm run typecheck`：通过。
- `npm run lint`：通过。
- `npm test`：`14 files, 108 tests` 全部通过。
- `npm run build`：`tsc -b && vite build` 成功。

### Android（`JAVA_HOME=/c/Users/14188/.jdks/ms-21.0.8`，JBR 无 `jlink` 已改用 Microsoft JDK 21）

- `./gradlew :app:testDebugUnitTest`：通过（含新增 `interviewDetailParsesFullInterview`）。
- `./gradlew :app:lintDebug`：通过。
- `./gradlew :app:assembleDebug`：通过。

## 已知限制

- 时区为展示用途：`scheduledAt` 以 UTC 存储；招聘者 Web 表单将 `datetime-local` 输入值按 UTC 直接提交（未做浏览器本地时区 → UTC 的自动换算），`timezone` 字段仅用于展示。计划中「浏览器时区换算」的意图本轮未实现，建议下一包（Google Calendar/Meet 集成）统一补齐。
- 未进行真实 MySQL 端到端验证（Docker 不可用），迁移与行为由 H2（MySQL 模式）集成测试覆盖。
- 未进行真实双账号 Web→Android 手动演示（本环境无浏览器/真机交互）；功能由三端构建与测试验证。
- 无自动生成 Google Meet 链接 / 日历 / 授权 / 候选人 accept/decline/reschedule（按任务禁止，下一包做）。
- 面试取消不改变申请阶段（保持 `INTERVIEW`），招聘者需通过既有「拒绝」动作决定后续状态。

## 风险与注意事项

- `RecruiterApplicationService` 与 `CandidateApplicationQueryService` 直接注入 `InterviewRepository`，与既有跨模块只读查询约定一致，未引入循环依赖（`InterviewService → ApplicationRepository` 为叶子）。
- V7 为新增表，未改动已发布迁移；请确认目标环境 Flyway 兼容（沿用既有迁移命名与风格）。
- 请 Codex 复核：`CreateInterviewRequest` 中 `locationOrMeetingUrl` 强制必填、候选人 `scheduledAt` 仅在 SCHEDULED 返回、取消不改变申请阶段这三处语义是否符合预期。
- 请 Codex 复核 Web 弹窗的 UTC 直存语义是否需在本包内改为浏览器时区换算（见「已知限制」）。

## 下一步建议

- 在真实 MySQL + 双账号（招聘者/候选人）下做一次端到端验收：`IN_REVIEW` 安排 → 候选人在 Android 看到面试 → 改期/完成/取消 → 候选人刷新看到对应状态。
- 修复既有 `RecruiterJobIntegrationTest` 的 H2 纳秒精度断言（与本任务无关，可单独处理）。
- 下一包接 Google OAuth / Google Calendar / Google Meet 自动创建，并补齐时区换算。
