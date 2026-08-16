# Admin MVP 包 0：契约与初始化决策（草案）

> 状态：等待项目负责人书面批准三项开工前置条件。
> 本报告只完成包 0（读代码、起草 OpenAPI/API 覆盖表、列出风险与依赖）。
> 未新增或修改任何业务代码；未运行实现相关测试；未 commit / push。

## 三项前置条件确认

截至本次实施开始，仓库内未发现项目负责人对以下三项的书面批准：

1. **首个 Admin 初始化方式**
   - 推荐方案：仅本机/部署环境通过 `ADMIN_BOOTSTRAP_EMAIL` 把已存在账号提升为 `ADMIN`。
   - 禁止开放 Admin 注册接口；禁止把真实邮箱/密码写入迁移脚本。
   - 当前状态：**未获书面确认**。
2. **Admin 页面设计来源**
   - 推荐方案：复用现有 React `AppShell` 并指定 Admin Figma Frame；无 Figma 时必须获得最小页面结构书面许可。
   - 当前状态：**未获书面确认**。
3. **审核范围**
   - 推荐范围：批准/拒绝 `PENDING` 公司；非 `CLOSED` 职位下架为 `CLOSED`；`ACTIVE` 用户禁用为 `DISABLED`；不提供恢复、重新启用、删除数据或批量处理。
   - 当前状态：**未获书面确认**。

## 已阅读的关键代码事实

后端：

- 后端 Flyway 迁移已到 `V11__create_recruiter_profiles.sql`（本分支未提交、由 recruiter-candidate-improvements 占用）。**Admin 审计迁移应使用 `V12`，不得使用 `V11`。**
- `UserRole` 当前只有 `CANDIDATE`、`RECRUITER`，尚无 `ADMIN`。
- `UserStatus`：`ACTIVE`、`DISABLED`；`CompanyVerificationStatus`：`PENDING`、`APPROVED`、`REJECTED`；`JobStatus`：`DRAFT`、`ACTIVE`、`PAUSED`、`CLOSED`。
- `UserEntity`：`email`、`passwordHash`、`role`、`status`、`acceptedTermsVersion`、`avatarUrl`、`createdAt`、`updatedAt`。**无 `version` 列，也无 `disable()` 方法。** `UserRepository` 已有 `findByEmail`、`existsByEmail`、`findByIdForUpdate`（悲观写锁）。
- `CompanyEntity`：有 `verificationStatus`、`version`（int，初始 1）、`createdBy`、`createdAt`、`updatedAt`。**无 `review()` 方法。** `CompanyRepository` 目前只有裸 `JpaRepository`，无行锁/分页/计数查询。
- `JobEntity`：已有 `version`、`changeStatus(JobStatus, now)`（会递增 version）、`publish`。`JobRepository` 已有 `findByIdForUpdate`、`findOwnJobForUpdate`、`countByCompanyIdAndStatus`、`findByCompanyId`，并实现 `JpaSpecificationExecutor`。
- 认证：`JwtService.createAccessToken` 在 token 内写 `role` claim；`parse` 还原为 `AuthenticatedUser(userId, role)`。`JwtAuthenticationFilter` 每次请求重新读 `UserEntity`，校验 `status == ACTIVE` 且角色与 token 一致，授予 `ROLE_<role>` 权限；因此禁用用户后旧 JWT 与刷新 token 都会被拒绝（`AuthService.refresh` 也校验 `ACTIVE`）。
- 授权：`SecurityConfig` 未启用方法级安全（无 `@EnableMethodSecurity`/`@PreAuthorize`），当前 `anyRequest().authenticated()`；`/auth/register|login|refresh` 与 Google OAuth callback `permitAll`。Admin 需新增 `/api/v1/admin/**` 的 `hasRole("ADMIN")` 匹配器（或引入方法级安全）。
- 响应/错误约定：成功 `{ data: ... }`（列表附带 `meta`）；错误 `{ error: { code, message, fieldErrors, requestId } }`；`GlobalExceptionHandler` 把 `ApiException` 映射到对应状态码；`RequestIdFilter` 提供 `requestId`。
- 乐观锁约定：对比 `entity.version != expectedVersion` → 409 `VERSION_CONFLICT`（见 `JobService.changeStatus`）。
- 审计约定：`JobAuditEventEntity`（id/actor/action/from/to/reason/request_id/occurred_at），迁移 `V3` 为参考模板，时间用 `DATETIME(6)`，`now()` 截断到微秒。

共享认证的阻塞点（新增发现，必须纳入包 1）：

- `AuthService.findCompany(user)` 只对 `CANDIDATE` 返回 `null`；其余角色若 `memberRepository.findByUserId` 为空会抛 `IllegalStateException("Recruiter company membership is missing")`。**一旦加入 `ADMIN`，Admin 走 `/auth/login` 会 500**，须改为 ADMIN → `company = null`。
- `AuthService.register` 的 `parsePublicRole` 直接 `UserRole.valueOf(rawRole)`，且 `validateRoleSpecificFields` 无 ADMIN 分支。**一旦 enum 加入 ADMIN，注册接口会放行 ADMIN（无公司成员）**，必须显式拒绝 ADMIN 并保持报错文案“CANDIDATE or RECRUITER”。
- `UserEntity` 无 `version` 列，而 `AdminActionRequest` 统一带 `expectedVersion` 做乐观锁；**用户禁用无法直接用版本号做冲突检测**。需负责人在“为 users 增加 version 列（改表 + 迁移）”与“用户禁用改用 `findByIdForUpdate` 悲观锁、忽略 expectedVersion”之间决策。

Web：

- 路由集中在 `src/router/index.tsx`，全部挂在 `/recruiter/**` 下；现有 `AppShell` 面向 Recruiter（品牌、导航、账户区依赖 `recruiter.company.name`）。
- `authClient` / `authSession` 只接受 `role === 'RECRUITER'` 且强制 `company` 存在；`isAuthSession` 同样要求 RECRUITER + company。Admin 登录需要独立 session 类型（或泛化角色模型），不能破坏现有 Recruiter 登录。

## 已更新文档

- `docs/openapi-v1.yaml`
  - 新增 `Admin` tag。
  - 新增 9 个 Admin 操作（DRAFT）：`GET /admin/overview`、`GET /admin/reviews`、`GET /admin/companies/{companyId}`、`GET /admin/jobs/{jobId}`、`GET /admin/users/{userId}`、`POST /admin/companies/{companyId}/approve`、`POST /admin/companies/{companyId}/reject`、`POST /admin/jobs/{jobId}/remove`、`POST /admin/users/{userId}/disable`。
  - `UserRole` 枚举加入 `ADMIN`，并注明公开注册仍只允许 `CANDIDATE` / `RECRUITER`，`ADMIN` 仅通过已确认的 bootstrap 配置创建。
  - 新增 Admin DTO schema（`AdminReviewType`、`AdminAuditEvent`、`AdminOverview`、列表摘要、详情、`AdminActionRequest`、`AdminActionResponse`）。
  - 明确列表/详情不返回密码哈希、Refresh Token、Google OAuth 连接、简历正文等敏感字段。
- `docs/API_COVERAGE.csv`
  - 新增 9 行 Admin API，状态 `DRAFT`。

> 注：上述文档草案不涉及迁移版本号，无需因 Flyway 版本变化而改动；但 `AdminActionRequest.expectedVersion` 与“用户无 version 列”的冲突仍需负责人在实现前定夺（见风险 4）。

## 建议实现顺序（待批准后执行）

- 包 1：后端角色/审计/公司审核（含 `V12__create_admin_audit_events.sql`、`ADMIN_BOOTSTRAP_EMAIL` 初始化、`AuthService.findCompany`/`parsePublicRole` 的 ADMIN 处理、概览、公司列表/详情/批准/拒绝）。
- 包 2：职位下架与用户禁用（含列表/详情、审计、自禁用保护、禁用后认证失效回归；需先决策用户禁用乐观锁方案）。
- 包 3：React Admin 页面（`/admin/sign-in`、`/admin/reviews`、`/admin/reviews/:type/:id`、`AdminActionDialog`）。
- 包 4：集成验收与交接报告。

## 主要风险与依赖

1. **前置条件未确认（阻塞）**
   - 未批准前不能实现业务代码；OpenAPI 与覆盖表仅是草案。
   - 若选择非 `ADMIN_BOOTSTRAP_EMAIL` 的初始化方式，需要重新评估安全边界。
   - 若没有 Admin Figma Frame/最小页面结构书面许可，Web 页面不能按最终视觉实现。
2. **共享认证/会话调整（含两个硬阻塞）**
   - `AuthService.findCompany` 会让 ADMIN 登录抛 500，必须改为 ADMIN → `company = null`。
   - `parsePublicRole` + `validateRoleSpecificFields` 会放行 ADMIN 注册，必须显式拒绝 ADMIN。
   - Web `authSession` 只接受 RECRUITER 且要求 company；Admin 登录需新增独立 session 类型或泛化，不能破坏现有 Recruiter 登录。
3. **审计表与迁移**
   - **`V11` 已被 `recruiter_profiles` 占用（本分支未提交），Admin 审计迁移必须用 `V12`。** 若其他任务再新增迁移，须由主协调人确认编号后再创建。
   - 迁移需包含 `(target_type, target_id, occurred_at, id)` 和 `(actor_id, occurred_at, id)` 索引。
   - `admin_audit_events.reason` 必填，长度 1–500；`request_id` 从 `RequestIdFilter` 获取。
4. **并发与状态机（含用户无 version 的契约冲突）**
   - 公司/职位有 `version`，可套用 `expectedVersion` 乐观锁；`UserEntity` 无 `version`，用户禁用需改用悲观锁或为 users 增加 version 列（须负责人决策）。
   - `CompanyEntity.review(target, now)` 需新增并递增 `version`、更新 `updatedAt`；`CompanyRepository` 需新增 `findByIdForUpdate` 及按状态分页/计数查询。
   - 非法状态返回 409 `INVALID_ADMIN_TRANSITION`；版本冲突返回 409 `VERSION_CONFLICT`。
   - 职位下架复用 `JobEntity.changeStatus(CLOSED, now)`，但必须同时写 Admin 审计，不得伪装成 Recruiter 操作。
5. **禁用用户后的认证路径**
   - 登录、刷新 token、旧 JWT 请求都要被拒绝；现有 JWT 过滤器已按 `ACTIVE` 校验，但需补集成测试覆盖 Admin 禁用后三条路径。
   - 禁止 Admin 禁用自己，业务层返回 409 `ADMIN_SELF_DISABLE_FORBIDDEN`。
6. **前端敏感字段**
   - Admin 页面不得渲染密码哈希、Refresh Token、Google OAuth、简历正文等；OpenAPI 已限定最小字段，前端仍需按契约解析并忽略未知字段。
7. **环境/工具**
   - 本仓库无 Maven Wrapper；后端需用缓存中的 Maven 3.9.16 + JDK 21。
   - Web `npm run build` 可能覆盖已有未提交 `web/dist`；包 4 中除非主协调人明确同意，否则不运行 build。

## 下一步

请项目负责人书面确认：

- [ ] 首个 Admin 初始化方式 = 仅本机/部署环境通过 `ADMIN_BOOTSTRAP_EMAIL` 提升已存在账号为 `ADMIN`，不开放 Admin 注册。
- [ ] Admin 页面设计来源 = 复用现有 React `AppShell`，并指定/确认 Admin Figma Frame 或最小页面结构。
- [ ] 审核范围 = 批准/拒绝 PENDING 公司；非 CLOSED 职位下架为 CLOSED；ACTIVE 用户禁用为 DISABLED；不提供恢复、重新启用、删除数据或批量处理。

另需一并确认的实现级决策：

- [ ] 用户禁用乐观锁：为 `users` 增加 `version` 列，还是用 `findByIdForUpdate` 悲观锁并忽略 `expectedVersion`？

获得确认后，按包 1 → 包 4 继续实现并运行对应测试。
