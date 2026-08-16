# Admin MVP 实施计划

## 目标

实现精简 Admin 审核端，供演示使用：审核待验证公司、下架违规职位、禁用违规用户，以及查看基础统计和操作审计。

每个高风险操作都必须填写原因、二次确认并留下审计记录。不要实现复杂运营后台、批量操作、社区审核、文件审核、权限委派或企业级组织管理。

## 当前代码事实与边界

已有能力：

- 公司状态：`PENDING`、`APPROVED`、`REJECTED`；
- 用户状态：`ACTIVE`、`DISABLED`；
- 职位状态：`DRAFT`、`ACTIVE`、`PAUSED`、`CLOSED`；
- 招聘者注册后公司默认 `PENDING`，未批准公司不能发布职位；
- JWT 请求会读取当前用户状态，因此禁用账户后已有 JWT 也应失效；
- 已有职位审计表，但没有统一的 Admin 审计表。

缺失能力：

- 后端 `UserRole` 尚无 `ADMIN`；
- 没有 Admin API、审计表、页面、路由或首个 Admin 初始化机制；
- 尚未确认可用的 Admin Figma Frame。

## 开工前置条件（必须由项目负责人确认）

在任何代码修改前，先获得书面确认：

1. **首个 Admin 初始化方式。**

   推荐：仅在本机/部署环境通过 `ADMIN_BOOTSTRAP_EMAIL` 将一个已存在账号提升为 `ADMIN`。不得开放 Admin 注册接口，不得把真实邮箱或密码写入迁移脚本。

2. **Admin 页面设计来源。**

   推荐：确认可复用现有 React `AppShell`，并指定 Admin Figma Frame；没有 Figma 时必须获得最小页面结构的书面许可。

3. **审核范围。**

   本 MVP 固定为：批准/拒绝 `PENDING` 公司；将非 `CLOSED` 职位下架为 `CLOSED`；将 `ACTIVE` 用户禁用为 `DISABLED`。不提供恢复、重新启用、删除数据或批量处理。

未获得以上确认时，只可阅读代码、提出 OpenAPI 草案和列出风险，不得实现或合并代码。

## 修改边界

允许：

- `docs/openapi-v1.yaml`、`docs/API_COVERAGE.csv`；
- 新建 `backend/src/main/java/com/adproject/admin/**` 和对应 `backend/src/test/java/com/adproject/admin/**`；
- 新增 Admin 审计迁移。先确认最新 Flyway 版本和无人占用后再确定编号（预期为 `V11`）；
- Admin 必须的最小共享调整：`UserRole`、`UserEntity`、`CompanyEntity`、对应 Repository 的只读/行锁方法，以及经过确认的 Admin 初始化配置；
- `web/src/admin/**`、Admin 路由、集中 API 客户端及相关测试；
- `change_report/admin-mvp-*.md`。

禁止：

- Android、Google Meet/OAuth、ML、Agent；
- Candidate/Recruiter 既有 API 字段或业务语义；
- 公开 Admin 注册、真实密钥/邮箱/Token、依赖升级；
- `web/dist`、`web/node_modules`；
- 破坏性 Git 操作或覆盖其他人的工作。

## API 契约（先更新 OpenAPI）

沿用现有 `{ data: ... }` 成功响应与 `{ error: ... }` 错误响应。

### 概览

`GET /api/v1/admin/overview`

返回 `pendingCompanies`、`activeJobs`、`activeUsers`、`disabledUsers` 和最多 10 条安全的 `recentActions`。未登录 401，Candidate/Recruiter 403，Admin 200。

### 审核列表与详情

- `GET /api/v1/admin/reviews?type=COMPANY|JOB|USER&page=1&pageSize=20&q=&status=`
- `GET /api/v1/admin/companies/{companyId}`
- `GET /api/v1/admin/jobs/{jobId}`
- `GET /api/v1/admin/users/{userId}`

`type` 必填，`pageSize` 默认 20、最大 50。默认状态：公司为 `PENDING`、职位为 `ACTIVE`、用户为 `ACTIVE`。列表和详情只返回审核所需最小字段；绝不返回密码哈希、Refresh Token、Google OAuth 连接、简历正文或其他无关敏感数据。

### 高风险操作

- `POST /api/v1/admin/companies/{companyId}/approve`
- `POST /api/v1/admin/companies/{companyId}/reject`
- `POST /api/v1/admin/jobs/{jobId}/remove`
- `POST /api/v1/admin/users/{userId}/disable`

统一请求体：

```json
{
  "reason": "不超过 500 字的必填原因",
  "expectedVersion": 3
}
```

规则：原因去首尾空格后必填、长度 1–500；找不到目标 404；版本冲突 409 `VERSION_CONFLICT`；状态不允许操作 409 `INVALID_ADMIN_TRANSITION`；Candidate/Recruiter 403；Admin 禁用自己 409 `ADMIN_SELF_DISABLE_FORBIDDEN`。所有成功操作都必须写入 Admin 审计。

下架职位只改为 `CLOSED`，禁用用户只改为 `DISABLED`，均不删除任何业务数据。

## 数据与审计

新增独立 `admin_audit_events` 表，字段至少包括：

- `id`、`actor_id`、`target_type`、`target_id`、`action`；
- `from_status`、`to_status`、`reason`、`request_id`、`occurred_at`（UTC `DATETIME(6)`）。

添加 `(target_type, target_id, occurred_at, id)` 与 `(actor_id, occurred_at, id)` 索引。

只为必要实体添加领域方法：

- `CompanyEntity.review(CompanyVerificationStatus target, Instant now)`；
- `UserEntity.disable(Instant now)`。

职位继续复用 `changeStatus(JobStatus.CLOSED, now)`，但另外写入 Admin 审计；不得伪装为 Recruiter 操作。

## 分包顺序

### 包 0：契约与初始化决策

- 确认首个 Admin 初始化方式、Figma/页面许可、审核范围；
- 更新 OpenAPI 草案与 API 覆盖表；
- 确认 Flyway 编号和共享文件的修改时间窗口；
- 不改业务代码。

验收：负责人明确批准契约和边界。

### 包 1：后端角色、审计与公司审核

- 后端 `UserRole` 增加 `ADMIN`，公开注册仍只允许 Candidate/Recruiter；
- 实现批准的 Admin 初始化方式；
- 新增 Admin 审计迁移、实体、Repository、Service、Controller、DTO；
- 实现概览、公司列表/详情、批准、拒绝。

测试：Admin 成功批准/拒绝；401、403、404、原因 422、非法状态 409、版本冲突 409、审计正确；Recruiter 不能注册为 Admin；既有认证/职位/申请测试回归。

### 包 2：职位下架与用户禁用

- 实现职位与用户列表/详情；
- 实现下架和禁用；
- 禁用用户后，登录、刷新 Token 和携带旧 JWT 请求都必须被拒绝；
- 禁止 Admin 禁用自己；
- 所有动作写入审计。

测试：下架后职位对 Candidate 不可见且不可投递；禁用 Candidate/Recruiter 后各认证路径均为 401；已关闭职位重复下架、自禁用、跨角色、版本冲突、原因校验和审计均被覆盖。

### 包 3：React Admin 页面

页面：

- `/admin/sign-in`：复用登录组件，只有 Admin 成功进入 `/admin/reviews`；
- `/admin/reviews`：公司、职位、用户 Tab，分页、搜索、loading、empty、error；
- `/admin/reviews/:type/:id`：详情和审计摘要；
- `AdminActionDialog`：显示目标、后果、必填原因、二次确认及提交中禁用状态。

UI 规则：前端路由保护只是体验层，后端仍必须鉴权；不渲染敏感字段；严格使用确认的 Figma；测试覆盖路由保护、四种状态、原因必填、确认、提交、刷新及 403/409/422 展示。

### 包 4：集成验收与交接

后端：

```powershell
cd backend
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -o test
```

Web：

```powershell
cd web
npm run typecheck
npm run lint
npm test
```

若 Web build 会覆盖已有未提交 `web/dist`，不得运行，并在报告中说明。

手工验收：Admin 批准公司；该公司 Recruiter 发布职位；Admin 下架职位且 Candidate 不可见；Admin 禁用测试用户且旧 JWT 被拒绝；查看三条审计记录；Recruiter/Candidate 访问 Admin API 均被拒绝。

交付 `change_report/admin-mvp.md`，说明模块、共享文件、API/数据库、迁移版本、真实测试结果、限制与下一步；不提交、不推送，等待主协调人复核。

## 明确不做

- Admin 自助注册、批量审核、恢复账户/职位、删除数据；
- 社区举报、内容审核、文件审核、上传；
- Android Admin、复杂统计、导出、通知、权限委派；
- 通过前端隐藏按钮代替后端权限校验。
