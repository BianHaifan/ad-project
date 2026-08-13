# 修改报告：招聘者 Dashboard 真实数据改造

## 基本信息

- 执行者：Claude
- 时间：2026-08-13 16:04
- 对应计划/任务：`tasks/plan.md`（招聘者 Dashboard 真实数据改造计划）、`tasks/todo.md`
- 修改范围：`backend/`（新增 dashboard 聚合模块 + 复用查询方法）、`web/`（Dashboard 真实 HTTP 接入与页面改造）、`docs/`（OpenAPI 契约与 API 目录同步）、`tasks/todo.md`、`change_report/`

## 完成内容

- 新增只读、按当前招聘者所属公司隔离的 `GET /api/v1/recruiter/dashboard`，聚合返回：
  - `metrics.activeJobs`（本公司 ACTIVE 岗位数）
  - `metrics.appliedApplications` / `inReviewApplications` / `interviewApplications`（本公司对应申请状态数）
  - `metrics.companyVerificationStatus`（本公司认证状态）
  - `recentApplications`（本公司最近更新的最多 3 条申请摘要，复用既有 `RecruiterApplicationSummary` 结构）
  - `recentJobs`（本公司最近创建的最多 3 个岗位摘要，复用既有 `RecruiterJobSummary` 结构）
- Web 端 Dashboard 从 mock 切换到真实 HTTP 客户端，页面不再出现 “Demo data”“Dashboard mock”“Recommended by ML algorithm” 或虚构匹配百分比。
- “Talent Pool Recommendations” 改为 “Recent applications / 最近申请”，点击使用后端返回的真实申请 ID 进入 `/recruiter/applications/:applicationId`，修复了此前点击模拟申请 ID（如 `app_001`）导致的 “Something went wrong”。
- 最近岗位使用真实岗位 ID，点击进入岗位详情。
- 状态卡链接：Open Roles → `/recruiter/jobs?status=ACTIVE`（并让 JobsPage 读取并应用该筛选）；New Applications/In Review/Interviews → `/recruiter/applications?stage=APPLIED|IN_REVIEW|INTERVIEW`；Verification 无跳转。
- 补齐了 Dashboard 的 loading / error / empty / content 状态。

## 修改文件

### 后端

- `backend/src/main/java/com/adproject/dashboard/api/DashboardController.java`（新增）
  - 修改原因：新增 dashboard 端点入口。
  - 主要变化：`GET /api/v1/recruiter/dashboard`，委托 `DashboardService`。
- `backend/src/main/java/com/adproject/dashboard/api/DashboardResponses.java`（新增）
  - 修改原因：定义明确的响应 DTO，不暴露 JPA Entity。
  - 主要变化：`Metrics` / `DashboardData` / `DashboardResponse` 记录，字段与 OpenAPI 一致。
- `backend/src/main/java/com/adproject/dashboard/application/DashboardService.java`（新增）
  - 修改原因：聚合逻辑与公司所有权校验。
  - 主要变化：复用 `RecruiterApplicationService` 与 `JobService` 的计数/摘要方法；仅 `RECRUITER` 角色且属于某公司可读；跨公司数据天然隔离。
- `backend/src/main/java/com/adproject/application/application/RecruiterApplicationService.java`
  - 修改原因：抽出可复用的计数与最近申请摘要方法，供 dashboard 复用，避免重复映射。
  - 主要变化：新增 `counts(companyId)` 与 `recentSummaries(companyId, limit)`；`list()` 改为调用 `counts(companyId)`（行为不变）。
- `backend/src/main/java/com/adproject/job/application/JobService.java`
  - 修改原因：抽出可复用的岗位计数与最近岗位摘要方法。
  - 主要变化：新增 `activeJobCount(companyId)` 与 `recentJobs(companyId, limit)`。
- `backend/src/main/java/com/adproject/job/infrastructure/JobRepository.java`
  - 修改原因：为 dashboard 提供按公司统计与分页查询。
  - 主要变化：新增 `countByCompanyIdAndStatus` 与 `findByCompanyId(companyId, pageable)`。
- `backend/src/test/java/com/adproject/dashboard/DashboardIntegrationTest.java`（新增）
  - 主要变化：成功聚合、未登录、求职者角色拒绝、跨公司隔离、空公司 4 个集成测试。

### Web

- `web/src/models/recruiter.ts`
  - 主要变化：`Dashboard` 类型改为严格字段 `DashboardMetrics`（`activeJobs` 等）+ `recentApplications`（替换 `recommendedApplications`）+ `recentJobs`；新增 `CompanyVerificationStatus`。
- `web/src/api/dashboardHttpClient.ts`（新增）
  - 主要变化：真实 `getDashboard()`，严格解析 `metrics`/`recentApplications`/`recentJobs`。
- `web/src/api/jobHttpClient.ts`、`web/src/api/applicationHttpClient.ts`
  - 主要变化：导出 `parseJob` / `parseSummary` 供 dashboard 复用，避免重复解析逻辑。
- `web/src/api/repository.ts`
  - 主要变化：`getDashboard` 从 mock 切到 `dashboardHttpClient.getDashboard()`。
- `web/src/mocks/mockRecruiterRepository.ts`
  - 主要变化：删除 mock `getDashboard`，从 `MockOnlyRepository` 的保留范围中移除。
- `web/src/pages/DashboardPage.tsx`
  - 主要变化：重写为真实指标卡 + 最近申请/最近岗位，去掉 mock/ML 文案与匹配百分比，新增空态，修复真实 ID 跳转。
- `web/src/pages/JobsPage.tsx`
  - 主要变化：岗位状态筛选改为读取 URL `?status=`，使 Open Roles 的 `?status=ACTIVE` 链接生效。
- `web/src/api/repository.test.ts`、`web/src/pages/JobPages.test.tsx`（更新）与 `web/src/pages/DashboardPage.test.tsx`（新增）
  - 主要变化：断言 dashboard 已切到真实客户端；新增 URL 状态筛选测试；新增 dashboard 显示/跳转/空态/无 mock 文案测试。

### 契约与文档

- `docs/openapi-v1.yaml`：dashboard 端点移除 `from`/`to` 参数，`x-status` 改为 `IMPLEMENTED`；`RecruiterDashboard` 改为明确 DTO，新增 `RecruiterDashboardMetrics`。
- `docs/API_COVERAGE.csv`、`docs/API_CATALOG.zh-CN.md`、`docs/API_CATALOG.en.md`：dashboard 行状态改为 `IMPLEMENTED`，请求参数由 `params: from/to` 改为 `—`。

## API 变化

- 是否变化：是
- 新增接口：`GET /api/v1/recruiter/dashboard`（`getRecruiterDashboard`）
  - 响应：`{ data: { metrics: { activeJobs, appliedApplications, inReviewApplications, interviewApplications, companyVerificationStatus }, recentApplications: [], recentJobs: [] } }`
- 移除：原 DRAFT 契约中的 `from` / `to` 查询参数。
- 字段变化：`RecruiterDashboard.metrics` 由宽泛 `object` 改为 `RecruiterDashboardMetrics`；`recommendedApplications` 更名为 `recentApplications`。
- 与 OpenAPI 是否一致：是（已同步更新 `docs/openapi-v1.yaml`）。

## 数据库变化

- 是否变化：否
- 无新增迁移、无表结构或索引变更。复用既有 `jobs`、`applications`、`companies`、`company_members` 表及既有索引（`idx_jobs_company_status`、`idx_jobs_company_created_id` 等）。

## 权限与安全

- 涉及角色：Recruiter（仅限）。
- 认证检查：路由走统一 JWT 过滤器（`anyRequest().authenticated()`），未登录返回 401。
- 角色检查：`DashboardService.requireCompany` 校验 `principal.role() == RECRUITER`，否则 403。
- 资源所有权检查：通过 `company_members` 解析当前招聘者所属公司，所有计数与列表均以该公司 ID 过滤；跨公司资源不可见。
- 敏感信息风险：无。仅返回本公司的聚合数字与申请/岗位摘要，不暴露 JPA Entity、简历原文或其他公司数据。

## 测试与验证

### 后端（IntelliJ 内置 Maven + JBR 21；环境 `JAVA_HOME` 原为 JDK 1.8、`mvn` 不在 PATH，已显式指定）

- 命令：`mvn -q -Dtest=DashboardIntegrationTest test`
  - 结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`（成功、未登录、错误角色、跨公司隔离、空数据均覆盖）
- 命令：`mvn -q test`（全量）
  - 结果：`Tests run: 65, Failures: 1, Errors: 0, Skipped: 2`
  - `RecruiterApplicationIntegrationTest` 4/4 通过（验证 `counts` 重构无回归）
  - 1 个既有失败：`RecruiterJobIntegrationTest.approvedRecruiterPublishesDraftAndPersistsAuditAndVersion`，`$.data.publishedAt` 期望 9 位纳秒精度、H2 返回 6 位微秒精度——与本任务无关的既有 H2 精度问题（重跑仍失败，非本次改动引入）。
  - 2 个跳过：`MySqlFlywayIntegrationTest`（Docker 不可用，`disabledWithoutDocker=true`）。

### Web

- 命令：`node node_modules/typescript/bin/tsc -b --pretty false`（typecheck）→ 通过（exit 0）
- 命令：`npm run lint`（eslint）→ 通过（exit 0）
- 命令：`node node_modules/vitest/vitest.mjs run`（test）→ `11 files / 75 tests passed`（含新增 `DashboardPage.test.tsx` 4 项、`JobPages.test.tsx` 30 项）
- 命令：`npm run build`（`tsc -b && vite build`）→ 通过（exit 0）

### 手工验证

- 未执行真实浏览器端到端验证（本环境未启动后端服务与 MySQL，也未配置登录态）。后端聚合行为由集成测试在 H2 上覆盖。

## 已知限制

- 本环境后端全量测试存在 1 个与本任务无关的既有失败（`RecruiterJobIntegrationTest` 的 H2 纳秒精度断言）；未修改该测试。
- Web 端 `node_modules` 原为 macOS 安装（缺少 Windows 原生 rollup/esbuild 二进制），为运行测试/构建临时补装了 `@rollup/rollup-win32-x64-msvc` 与 `@esbuild/win32-x64`；已在 `git status` 中产生少量 `web/node_modules`、`web/dist` 的未跟踪构建产物（非源码改动，未提交）。
- 未进行真实浏览器端到端 / 真机验证；未启动 MySQL 容器（Docker 不可用）。
- Messages 仍为 mock（与本任务无关，未改动）；Dashboard 已真实接入，不再属于 mock。

## 风险与注意事项

- `RecruiterApplicationService.list()` 的重构（抽出 `counts`）改动极小、行为等价，且 `RecruiterApplicationIntegrationTest` 已通过；请 Codex 复核确认无遗漏。
- `JobsPage` 状态筛选由组件内部 `useState` 改为读取 URL `?status=`，请确认不影响既有岗位列表交互（已有 30 项 JobPages 测试通过）。
- 复用既有 `RecruiterApplicationDtos.Summary` 与 `JobResponses.RecruiterJobDetail` 作为 dashboard 摘要，与 OpenAPI 的 `RecruiterApplicationSummary`/`RecruiterJobSummary` 字段一致，但后端 DTO 类型命名与 OpenAPI schema 命名略有差异（沿用既有代码风格），请审查时留意。

## 下一步建议

- 在真实 MySQL + 登录态下做一次人工端到端验收（创建岗位 → 投递 → Dashboard 数字与列表一致 → 点击进入详情）。
- 修复既有 `RecruiterJobIntegrationTest` 的 H2 纳秒精度断言（与本任务无关，可单独处理）。
- 继续按 `tasks/todo.md` 中「下一版本：真实站内会话（短轮询）」推进，其中 Web Messages mock 替换可参考本次 Dashboard 的切换模式。
