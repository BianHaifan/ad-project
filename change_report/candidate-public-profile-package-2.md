# Candidate 公开 Recruiter / Company Profile（Package 2）交付报告

> 状态：实现完成，等待 Codex/主协调人复核；未 commit / push。
> 本包承接 Package 1（Recruiter Profile），新增求职者查看招聘者/公司公开信息的后端 API 与 Android 页面。
> 评审修复：可见性已由「仅 application/conversation 关联」扩展为「application / conversation / 公开职位」，满足求职者首次浏览职位、尚未投递或建立会话时即可查看关联招聘者与公司。

## 完成内容

### 后端

- 新增 Candidate 专用公开信息 API（`/api/v1/candidate` 前缀）：
  - `GET /api/v1/candidate/recruiters/{recruiterId}`
  - `GET /api/v1/candidate/companies/{companyId}`
- 新增独立公开 DTO（不暴露 JPA Entity）：
  - `RecruiterPublicProfile`：`recruiterId`、`fullName`、`avatarUrl`、`title`、`bio`、`company`（`CompanySummary`）。
  - `CompanyPublicProfile`：`companyId`、`name`、`logoUrl`、`description`、`location`、`verificationStatus`。
  - 严格不含招聘者邮箱、注册时间、账号状态、角色等私有字段；公司不含内部审计数据。
- 角色控制：两个端点均强制 `CANDIDATE` 角色，Recruiter/Admin/未登录统一拒绝（403/401）。
- 可见性控制：候选人可通过三种途径查看——与目标的 application、与目标的 conversation，或目标发布的公开职位（`ACTIVE` + `PUBLIC`），因此求职者**首次浏览职位、尚未投递或建立会话时也能查看**该职位的招聘者/公司；不可达资源与不存在资源返回一致的 404（`NOT_FOUND`，不泄露资源是否存在）。
- 招聘者公司归属通过 `company_members` 解析；招聘者缺失 `recruiter_profiles` 时 `title` 回退为空串、`bio` 回退为 null。
- 补充 `CandidateJobQueryService.toRecruiterContact(JobEntity)`：职位详情中的招聘者预览在 `ownerId` 为空时回退到 `createdBy`，且仅当该用户确为 RECRUITER 时返回。

### Android

- 数据层：
  - `ApiContract` 新增 `RECRUITERS` / `COMPANIES` 路径与 `RecruiterPublicProfile` / `CompanyPublicProfile` / `PublicCompanySummary` DTO。
  - `HttpApis` 新增 `CandidatePublicProfileHttpApi`（两个 GET 端点，返回 `Response<DataEnvelope<...>>`）。
  - `RealRepositories` 新增 `CandidatePublicProfileRepository` 接口与 `RealCandidatePublicProfileRepository`，404 映射为「This recruiter/company is no longer available.」。
  - `CandidateAppContainer` 接入真实 repository。
  - `Job` 模型新增 `companyId` 字段；`toUiJob` 透传 `job.company.companyId`；demo `FakeCandidateRepository` 三条职位补齐 `companyId`。
- 页面/状态：
  - 新增 `RecruiterPublicProfileViewModel` / `CompanyPublicProfileViewModel`（loading / content / error / notFound / retry）。
  - 新增 `RecruiterPublicProfileScreen` / `CompanyPublicProfileScreen`（含 loading、空态、错误重试、返回导航、verification status 展示）。
  - `JobDetailScreen`：公司信息行与招聘者卡片可点击，跳转对应公开页。
  - `MessagesScreens` 的 `ChatHeader`：招聘者头像/姓名可点击，跳转招聘者公开页。
  - `AdCandidateApp`：新增 `recruiter/{recruiterId}`、`company/{companyId}` 路由与目标，接入上述入口。

### 文档

- `docs/openapi-v1.yaml`：新增 `/candidate/recruiters/{recruiterId}`、`/candidate/companies/{companyId}` 两条路径及 `CompanySummary`、`RecruiterPublicProfile`、`CompanyPublicProfile` 三个 schema（置于 `/recruiter/me` 之前 / `RecruiterContact` 之后，未触碰 Admin 未提交草案段落）。
- `docs/API_COVERAGE.csv`：新增两行 Profile API；同时修复文件末尾多余空行，`git diff --check` 通过。

## 实际修改文件

### 后端

- `backend/src/main/java/com/adproject/profile/api/CandidatePublicProfileDtos.java`（新增）
- `backend/src/main/java/com/adproject/profile/application/CandidatePublicProfileService.java`（新增）
- `backend/src/main/java/com/adproject/profile/api/CandidatePublicProfileController.java`（新增）
- `backend/src/main/java/com/adproject/job/infrastructure/JobRepository.java`（修改：新增公开职位可见性查询）
- `backend/src/main/java/com/adproject/job/application/CandidateJobQueryService.java`（新增 `toRecruiterContact` 助手并接入职位详情）
- `backend/src/test/java/com/adproject/profile/CandidatePublicProfileIntegrationTest.java`（新增）

### Android

- `android/app/src/main/java/com/adproject/candidate/data/contract/ApiContract.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/data/api/HttpApis.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/data/api/RealRepositories.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/core/network/CandidateAppContainer.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/data/model/CandidateModels.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/data/api/CandidateApi.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/JobViewModels.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/JobDetailScreen.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesScreens.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`（修改）
- `android/app/src/main/java/com/adproject/candidate/feature/profile/PublicProfileViewModels.kt`（新增）
- `android/app/src/main/java/com/adproject/candidate/feature/profile/PublicProfileScreens.kt`（新增）
- `android/app/src/test/java/com/adproject/candidate/CandidatePublicProfileRepositoryTest.kt`（新增）
- `android/app/src/test/java/com/adproject/candidate/PublicProfileViewModelTest.kt`（新增）

### 文档

- `docs/openapi-v1.yaml`（修改）
- `docs/API_COVERAGE.csv`（修改）

## API / 数据库 / Flyway 变化

- API：
  - `GET /api/v1/candidate/recruiters/{recruiterId}`
  - `GET /api/v1/candidate/companies/{companyId}`
- 数据库 / Flyway：无新增表、无新增迁移（复用 `users`、`companies`、`company_members`、`recruiter_profiles`、`applications`、`conversations`）。

## 可见性规则

- `canSeeRecruiter`：`applications.existsByCandidateIdAndRecruiterId(...)` OR `conversations.existsByCandidateIdAndRecruiterId(...)` OR `jobs.existsByRecruiterIdAndStatusAndVisibility(recruiterId, ACTIVE, PUBLIC)`。
- `canSeeCompany`：`applications.existsByCandidateIdAndCompanyId(...)` OR `conversations.existsByCandidateIdAndCompanyId(...)` OR `jobs.existsByCompanyIdAndStatusAndVisibility(companyId, ACTIVE, PUBLIC)`。
- 公开职位判定：`status = ACTIVE` 且 `visibility = PUBLIC`，与职位列表/详情对候选人的可见集合一致；招聘者归属按 `ownerId`（为空回退 `createdBy`）判定。
- 不满足可见性或资源不存在均返回 404 `NOT_FOUND`（不区分，避免泄露存在性）。

## OpenAPI / 冲突检查

- Flyway：无冲突（本包未新增迁移）。
- OpenAPI / API_COVERAGE：仓库中仍存在 Admin 包 0 的未提交改动；本包新增内容集中在 `/candidate/*` 路径与 Profile schemas 区域，与 Admin 内容不重叠，未改写 Admin 段落。`git diff --check` 干净（仅 LF→CRLF 提示，非空白错误）。

## 测试命令与结果

后端（在线 Maven，JDK 21）：

```bash
export JAVA_HOME="C:/Users/14188/.jdks/ms-21.0.8"; export PATH="$JAVA_HOME/bin:$PATH"
cd backend
"/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.3/plugins/maven/lib/maven3/bin/mvn" test -Dtest=CandidatePublicProfileIntegrationTest
```

结果：`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`。

Android 单元测试（JDK 21）：

```bash
export JAVA_HOME="C:/Users/14188/.jdks/ms-21.0.8"; export PATH="$JAVA_HOME/bin:$PATH"
cd android
./gradlew testDebugUnitTest --tests "com.adproject.candidate.CandidatePublicProfileRepositoryTest" --tests "com.adproject.candidate.PublicProfileViewModelTest" --console=plain
```

结果：`BUILD SUCCESSFUL`。

覆盖范围：

- 后端 `CandidatePublicProfileIntegrationTest`（6 用例）：成功读取招聘者公开信息且不含私有字段、成功读取公司公开信息且不含内部字段、首次浏览职位（未投递/未建立会话）即可查看招聘者与公司公开信息、职位详情包含招聘者预览、端点要求登录 + CANDIDATE 角色（401/403）、缺失或不可达资源返回 404。
- Android `CandidatePublicProfileRepositoryTest`（4 用例）：envelope 解析 + 公开字段、404 使用资源专属文案（招聘者/公司）。
- Android `PublicProfileViewModelTest`（2 用例）：招聘者加载内容→错误→404 状态流转；公司加载内容→404 状态流转。

## 未完成内容（按任务边界）

- 未做头像二进制上传（沿用 `avatarUrl` 字段）。
- 未修改 Web、Google Meet/OAuth、ML、Agent、Admin。
- 未做本地手测（本包未启动本地服务）。

## 下一包建议

- 面试自动通知、聊天附件等，与 Package 1 边界一致，可另行排期。
