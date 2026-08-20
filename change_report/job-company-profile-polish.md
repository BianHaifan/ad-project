# 修改报告：职位信息、公司详情与 Recruiter Profile 优化

## 完成内容

- Web Recruiter Jobs：移除冗余的 `Owner` 列，仅调整前端表格与响应式列宽，保留 API `owner` 字段。
- Web Recruiter Profile：Personal profile 卡片取消固定最大宽度，填满右侧主栏。
- Android Job Detail：将 `About this role` 改为独立的 `Job description`；Requirements 保持 API 的列表结构并按项目符号逐条渲染，长文本可换行。
- Candidate Company Profile：在不新增公司表字段或 Flyway 迁移的前提下展示公司阶段、团队规模、官网、公开 ACTIVE 职位总数及最近三项公开职位。
- Company Public Profile API：扩展 DTO/OpenAPI；所有新增岗位数据均只来自公开 ACTIVE 职位，不暴露公司审计字段、私有职位或内部状态。

## 修改模块

- `web/src/pages/JobsPage.tsx`
- `web/src/pages/JobPages.test.tsx`
- `web/src/theme/global.css`
- `android/app/src/main/java/com/adproject/candidate/data/model/CandidateModels.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/JobDetailScreen.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/JobViewModels.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/profile/PublicProfileScreens.kt`
- Android 相关 DTO、演示 API 与 UI 测试
- `backend/.../profile/CandidatePublicProfileService`
- `backend/.../profile/CandidatePublicProfileDtos`
- `backend/.../job/JobRepository`
- `docs/openapi-v1.yaml`
- 后端 Company Public Profile 集成测试

## API / 数据库

- API：`GET /api/v1/candidate/companies/{companyId}` 新增 `stage`、`employeeRange`、`website`、`activeJobCount`、`openJobs`。
- 数据库：无变化；不新增 Flyway 迁移，不修改 `companies` 表。
- 权限：保持 Candidate 角色与“已关联或公司有公开职位”可见性规则；岗位摘要仅过滤 `ACTIVE + PUBLIC`。

## 验证

- Web：`npm test -- --run src/pages/JobPages.test.tsx src/pages/ProfilePage.test.tsx`，50 项通过。
- Web：`npm run typecheck`、`npm run lint`，通过。
- Android：使用本机 JDK 21 运行 `testDebugUnitTest`（JobsScreensUiTest、ProfileScreensUiTest），通过。
- `git diff --check`：通过。
- 后端 Maven 集成测试：未运行；当前环境没有 Maven 命令。新增后端集成测试已写入，需在团队 Maven/CI 环境执行。

## 未完成与边界

- Recruiter AI score / Candidate fit 修复以及 Agent Talent Pool 主动外联仍未实施；它们分别涉及 ML 和 Agent/Conversation 核心契约，需对应负责人确认后执行。
- 未写入或覆盖任何现有数据库中的公司资料；字段为空时客户端保持空态。演示数据补全应由 seed 数据负责人单独处理。
