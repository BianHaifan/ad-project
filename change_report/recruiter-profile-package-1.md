# Recruiter Profile Package 1 交付报告

> 状态：实现完成，等待 Codex/主协调人复核；未 commit / push。
> 说明：当前 AI 环境无法直接执行 shell；已由用户在本机运行 `git status --short`、Web typecheck/lint/Vitest 并取得结果。后端 Maven 测试尚未成功执行，仍需在正确 shell 中运行。

## 完成内容

- 后端新增 Recruiter 个人资料持久化字段 `title`、`bio`，复用 `users.full_name`、`users.avatar_url`。
- 新增真实 API：
  - `GET /api/v1/recruiter/profile`
  - `PATCH /api/v1/recruiter/profile`
- 返回 DTO 包含：`userId`、`fullName`、`avatarUrl`、`title`、`bio`、`company`、`email`、`createdAt`、`updatedAt`。
- PATCH 仅允许修改 `fullName`、`title`、`bio`、`avatarUrl`；`email`、`company`、`role`、`createdAt` 等只读字段作为未知字段被拒绝（400 `INVALID_REQUEST`）。
- 后端强制 RECRUITER 角色和本人资源所有权（路径固定为当前登录用户，不提供按 ID 修改）。
- 没有把 JPA Entity 暴露为 DTO。
- Web 新增 `/recruiter/profile` 页面，并从右上角账户/头像区域进入。
- Profile 页面展示头像、姓名、任职公司、职位、简介、邮箱、注册时间；公司、邮箱、注册时间只读。
- 编辑表单具备 loading、字段校验、submitting/disabled、错误、保存成功、刷新后数据一致性。

## 未完成内容（按任务边界）

- 未做 Candidate 公开 Recruiter/Company Profile 页（下一包）。
- 未做面试自动通知、聊天附件。
- 未做头像二进制上传；仅支持编辑现有 `avatarUrl` 字段。
- 未修改 Android、Google Meet/OAuth、ML、Agent、Admin。
- 未创建 mock API 或 demo 数据替代真实 API。

## 实际修改文件

### 后端

- `backend/src/main/resources/db/migration/V11__create_recruiter_profiles.sql`（新增）
- `backend/src/main/java/com/adproject/profile/infrastructure/RecruiterProfileEntity.java`（新增）
- `backend/src/main/java/com/adproject/profile/infrastructure/RecruiterProfileRepository.java`（新增）
- `backend/src/main/java/com/adproject/profile/api/RecruiterProfileDtos.java`（新增）
- `backend/src/main/java/com/adproject/profile/api/RecruiterProfileController.java`（新增）
- `backend/src/main/java/com/adproject/profile/application/RecruiterProfileService.java`（新增）
- `backend/src/main/java/com/adproject/user/infrastructure/UserEntity.java`（增加 `updateAvatarUrl`）
- `backend/src/test/java/com/adproject/profile/RecruiterProfileIntegrationTest.java`（新增）

### Web

- `web/src/models/recruiter.ts`（新增 `RecruiterCompanySummary`、`RecruiterProfileDetail`、`UpdateRecruiterProfileInput`；旧 `RecruiterProfile` 增加可选扩展字段）
- `web/src/api/contract.ts`（新增 `recruiterProfile` 路径）
- `web/src/api/recruiterProfileHttpClient.ts`（新增）
- `web/src/api/recruiterProfileHttpClient.test.ts`（新增）
- `web/src/api/recruiterRepository.ts`（扩展 Repository 接口）
- `web/src/api/repository.ts`（接入真实 HTTP client）
- `web/src/api/repository.test.ts`（补充 Profile 真实数据源边界断言）
- `web/src/api/queries.ts`（新增 `useRecruiterProfile`、`useUpdateRecruiterProfile`）
- `web/src/mocks/mockRecruiterRepository.ts`（Omit 新增真实方法）
- `web/src/pages/ProfilePage.tsx`（新增）
- `web/src/pages/ProfilePage.test.tsx`（新增）
- `web/src/components/AppShell.tsx`（账户区增加 Profile 链接）
- `web/src/components/AppShell.test.tsx`（增加入口导航测试）
- `web/src/router/index.tsx`（新增 `/recruiter/profile` 路由）
- `web/src/theme/global.css`（增加 Profile 页面样式）

### 文档

- `docs/openapi-v1.yaml`（新增 `/recruiter/profile` GET/PATCH、`RecruiterCompanySummary`、`RecruiterProfileDetail`、`UpdateRecruiterProfileRequest`；保留 Admin 未提交草案）
- `docs/API_COVERAGE.csv`（新增两行 Recruiter Profile API）

## API / 数据库 / Flyway 变化

- API：
  - `GET /api/v1/recruiter/profile`
  - `PATCH /api/v1/recruiter/profile`
- 数据库：
  - 新增 `recruiter_profiles` 表：
    - `user_id CHAR(36) PK FK -> users.id`
    - `title VARCHAR(100) NOT NULL`
    - `bio VARCHAR(1000) NULL`
    - `created_at DATETIME(6) NOT NULL`
    - `updated_at DATETIME(6) NOT NULL`
- Flyway：
  - 新增 `V11__create_recruiter_profiles.sql`
  - 已确认仓库中无已存在或未提交的 `V11` 冲突；当前最高提交版本为 `V10`。

## OpenAPI / Flyway 冲突检查

- Flyway：未发现冲突，`V11` 可安全使用。
- OpenAPI：
  - 仓库中存在此前 Admin 包 0 的未提交 OpenAPI 修改（Admin tag、Admin paths、Admin schemas、API_COVERAGE 的 Admin 行）。
  - 本次 Recruiter Profile 修改位于 `/recruiter/profile`、Profile schemas 和 Profile API coverage 区域，与 Admin 内容不重叠；未删除或改写 Admin 相关段落。
  - 若主协调人要求严格隔离所有未提交 OpenAPI 改动，则需先复核/合并 Admin 草案；本报告不强行合并。

## 测试命令与结果

由于当前会话 shell 工具不可用，未能执行以下命令：

```powershell
cd backend
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -o -Dtest=RecruiterProfileIntegrationTest test

cd web
npm run typecheck
npm run lint
npm test -- --run src/pages/ProfilePage.test.tsx src/api/recruiterProfileHttpClient.test.ts src/api/repository.test.ts src/components/AppShell.test.tsx
```

- 已编写后端集成测试：`RecruiterProfileIntegrationTest`，覆盖成功读取/更新、未登录、Candidate 错误角色、字段校验、禁止修改只读字段、部分更新保留未提交字段。
- 已编写 Web 测试：`ProfilePage.test.tsx`、`recruiterProfileHttpClient.test.ts`、`AppShell.test.tsx`、`repository.test.ts`，覆盖入口导航、加载、保存成功、字段错误、请求失败后保留输入、真实数据源边界。
- Web 已由用户在本机执行并通过：
  - `npm run typecheck`：通过
  - `npm run lint`：通过
  - `npm test -- --run src/pages/ProfilePage.test.tsx src/api/recruiterProfileHttpClient.test.ts src/api/repository.test.ts src/components/AppShell.test.tsx`：4 个测试文件、11 个测试全部通过
- 后端 Maven 测试尚未执行；用户尝试运行但使用了 CMD 下的 PowerShell 语法，命令未生效。

## 本地手测

未执行。原因是无法启动本地服务。建议复核后由有 shell 的环境执行：
1. Recruiter 登录
2. 右上角进入 Profile
3. 修改姓名/职位/简介
4. 保存
5. 刷新页面确认数据一致

## 下一包建议

- 下一包为 Candidate 公开 Recruiter/Company Profile：
  - 新增 `GET /api/v1/candidate/recruiters/{recruiterId}`
  - 新增 `GET /api/v1/candidate/companies/{companyId}`
  - 使用独立 Public DTO，绝不返回 Recruiter 邮箱、注册时间、账号状态等私有字段。
  - 按职位/会话关联校验 Candidate 可见性。
  - Android 职位详情和聊天详情接入入口。
