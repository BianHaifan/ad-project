# 修改报告：Web 招聘入口小改动包 3

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-13
- 对应计划/任务：`tasks/plan.md`「小改动包 3（Web 招聘入口）」、`tasks/todo.md` 第 25 行「统一创建岗位入口」与第 28 行「小改动包 3（Web 招聘入口）」
- 修改范围：仅 `web/`（招聘者 Jobs/Dashboard/Applications 页面及其测试）、`change_report/web-job-entry-polish.md`、`tasks/todo.md`
- 明确禁止且未改动：`backend/`、`android/`、`ml-service/`、Agent、Admin、OpenAPI、数据库迁移、岗位 API、岗位状态机；未改动 Dashboard 的真实数据逻辑与 Messages 逻辑；未提交、未推送、未清理或覆盖现有改动

## 完成内容

按任务目标逐项实现：

1. **统一「创建岗位」入口职责，仅保留 Jobs 页面页头与空态**
   - `JobsPage` 页头与空态两处按钮文案统一为 `Create job`，且均跳转 `/recruiter/jobs/new`。
   - 页面结构、列表/筛选/分页、空态展示逻辑均未改动。

2. **移除重复入口**
   - `DashboardPage` 页头不再出现 `Create Job Posting` 按钮（`PageHeader` 的 `actions` 移除）；「Recent job postings」空态中的 `Create job draft` 按钮（`EmptyState` 的 `action`）同步移除，仅保留标题与描述文案。
   - `ApplicationsPage` 页头不再出现 `Create job posting` 按钮。
   - Dashboard 的指标卡、最近申请、最近岗位的跳转逻辑，以及 Applications 的阶段筛选、详情跳转逻辑均未改动。

3. **保留草稿语义**
   - `JobFormPage` 新岗位页仍显示 `Create job draft` 标题与 `Save draft` 提交按钮；创建仍走 `create.mutateAsync`（草稿，版本 1 DRAFT），未改为直接发布。
   - 无任何 API、数据库迁移或岗位状态机变更。

4. **测试**
   - `JobPages.test.tsx` 新增 2 个用例：页头 `Create job` 跳转 `/recruiter/jobs/new`；空态 `Create job` 跳转 `/recruiter/jobs/new`（并断言空态下页头 + 空态共 2 个 `Create job` 入口）。
   - `DashboardPage.test.tsx`：在「真实指标与最近项」用例中增加断言——Dashboard 不出现任何 `Create job` 按钮；新增 1 个用例——最近岗位为空时也不出现 `Create job` 入口。
   - `ApplicationPages.test.tsx`：在「真实列表」用例中增加断言——Applications 页不出现任何 `Create job` 按钮。
   - 既有 Dashboard/Jobs/Applications 行为测试全部保留并通过。

## 修改文件

### Web（修改）

- `web/src/pages/DashboardPage.tsx`
  - 主要变化：移除页头 `Create Job Posting` 按钮与「Recent job postings」空态 `Create job draft` 按钮；指标卡、最近申请、最近岗位渲染与跳转不变。

- `web/src/pages/ApplicationsPage.tsx`
  - 主要变化：移除页头 `Create job posting` 按钮；阶段筛选与详情跳转不变。

- `web/src/pages/JobsPage.tsx`
  - 主要变化：页头与空态按钮文案由 `Create job draft` 改为 `Create job`（跳转 `/recruiter/jobs/new` 不变）。

### Web（测试）

- `web/src/pages/JobPages.test.tsx`
  - 新增：`navigates to the new-job form from the header Create job button`、`navigates to the new-job form from the empty-state Create job button`。

- `web/src/pages/DashboardPage.test.tsx`
  - 新增：`does not offer a create-job entry when recent jobs are empty`；并在 `renders real metrics and recent items without mock or ML copy` 中增加「不出现 Create job 按钮」断言。

- `web/src/pages/ApplicationPages.test.tsx`
  - 在 `renders the real list without inventing match or owner data` 中增加「不出现 Create job 按钮」断言。

## API / 数据库变化

- API：无变化。不新增/修改任何端点、契约或错误码。
- 数据库：无变化。不新增/修改迁移、表、索引或约束。
- 岗位状态机：无变化。创建仍为 DRAFT 草稿，发布/暂停/恢复/关闭等生命周期动作均未触碰。
- 契约一致性：与 `docs/openapi-v1.yaml` 一致，未触碰契约。

## 测试与验证

运行环境：`web/` 目录。

- `npm run lint`（eslint）：通过（0 错误）。
- `npm run typecheck`（`tsc -b`）：通过。
- `npm test`（vitest run）：通过，`105` 个用例全部通过（本次新增 3 个：JobPages 页头跳转、JobPages 空态跳转、Dashboard 空态无入口；Applications 在既有用例中新增断言）。
- `npm run build`（`tsc -b && vite build`）：通过（114 模块，产物生成成功）。

### 覆盖点核对

- Jobs 页头 `Create job` 跳转 `/recruiter/jobs/new`：`navigates to the new-job form from the header Create job button`。
- Jobs 空态 `Create job` 跳转 `/recruiter/jobs/new`：`navigates to the new-job form from the empty-state Create job button`。
- Dashboard 不出现创建岗位入口（含空态）：`does not offer a create-job entry when recent jobs are empty` + `renders real metrics…` 中的断言。
- Applications 不出现创建岗位入口：`renders the real list without inventing match or owner data` 中的断言。
- JobForm 保留草稿语义：既有 `creates once while pending and navigates to the persisted detail`（`Save draft` 按钮）与 `JobFormPage` 的 `Create job draft` 标题未改动，测试继续通过。

## 已知限制

- 本任务为纯前端入口职责收敛，未涉及后端与数据；「创建岗位」业务动作最终仍落在 Jobs 页与 JobForm 草稿表单上，语义不变。
- 未做真实浏览器端到端/截图级验收（本环境无浏览器交互）；完成定义以 lint/typecheck/test/build 全通过 + 组件级测试覆盖为准。

## 下一步建议

- 后续若需在侧边导航或其他入口再次暴露「创建岗位」，应统一指向 `/recruiter/jobs/new` 并复用同一文案 `Create job`，避免再次出现重复入口。
- 可由具备浏览器环境的人员做一次可视化复核：Dashboard 与 Applications 页头不再出现创建岗位按钮，Jobs 页头与空态仍为 `Create job` 且正确跳转。
