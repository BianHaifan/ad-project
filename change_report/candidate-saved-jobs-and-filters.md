# Candidate 收藏职位 + 职位筛选

## 目标

为 Candidate 端补齐两个真实能力：**收藏职位**（保存/取消保存/收藏列表，列表与详情展示真实收藏态）与**职位筛选**（推荐接口按职位类型、工作方式、地点、最低薪资过滤）。范围严格限定在 Spring Boot 的 Candidate/Job/Recommendation 适配层、OpenAPI、Flyway、Android Candidate Job 页面及相关测试；不改 ml-service、Agent、Admin、招聘者网页端、认证方式、既有职位/申请状态机；不提交密钥或环境配置。

## 完成内容

### 1. 后端：收藏职位持久化（Flyway V17 + 实体/仓储 + 服务/控制器）

- 新增迁移 `V17__create_candidate_saved_jobs.sql`：`candidate_saved_jobs` 表，`(candidate_id, job_id)` 唯一约束，`created_at DATETIME(6)` 存 UTC（沿用既有 UTC 精度约定）。
- 新增 `CandidateSavedJobEntity` / `CandidateSavedJobRepository`。
- `CandidateJobQueryService` 新增 `save`（幂等，已存在则直接返回）、`unsave`（幂等）、`savedJobs`（分页、只返回本人且当前 ACTIVE+PUBLIC 可浏览的职位，按收藏时间倒序）。
- 新增 `CandidateSavedJobController`：
  - `PUT /api/v1/candidate/saved-jobs/{jobId}` → 204（幂等）
  - `DELETE /api/v1/candidate/saved-jobs/{jobId}` → 204（幂等）
  - `GET /api/v1/candidate/saved-jobs?page&pageSize` → 分页返回本人收藏
  - 统一校验：未认证 401、非 Candidate 403、职位不存在或不可浏览 404。

### 2. 后端：真实 `isSaved` 与推荐过滤

- 职位详情、职位列表、推荐列表的 `toSummary`/`toDto` 现在带上真实 `isSaved`（不再硬编码 `false`），并在批量查询时一次性 `findByCandidateIdAndJobIdIn` 组装 `savedIds`，避免 N+1。
- `CandidateRecommendationService` 在 Spring Boot 侧应用可选过滤：`keyword`（q）、`employmentType`、`workplaceType`、`location`、`minimumSalary`；过滤后再走既有排序，**Python ML 服务不改**。分页 `page/pageSize/total/hasNext` 保持真实。

### 3. OpenAPI 同步

- `docs/openapi-v1.yaml` 新增 `/api/v1/candidate/saved-jobs` 三个操作、`isSaved` 字段（旧字段保持向后兼容）、推荐接口的 `workplaceType/location/minimumSalary` 过滤参数。

### 4. Android 数据层

- `ApiContract.kt`：`CandidateJob` 与 `RecommendedJob` 末尾新增 `isSaved`（默认 `null`，兼容旧响应）。
- `HttpApis.kt`：`recommendations` 增 3 个过滤参数；新增 `savedJobs` / `saveJob`（PUT）/ `unsaveJob`（DELETE）。
- `RealRepositories.kt`：接口与实现补齐 `saveJob`/`unsaveJob`/`savedJobs`，`toCandidateJob` 透传 `isSaved`。
- `CandidateModels.kt`：UI `Job` 末尾新增 `isSaved: Boolean = false`。

### 5. Android ViewModel

- `JobFeedViewModel`：新增 `selectWorkplaceType/selectLocation/selectMinimumSalary/clearFilters`（任一过滤变更重置回第 1 页并重载）；`toggleSave` 乐观更新 + 失败回滚 + `saveError`。
- `JobDetailViewModel`：加载时写入 `isSaved`；`toggleSave` 乐观切换 + 失败回滚 + `saveError`。
- 新增 `SavedJobsViewModel`：分页加载/加载更多/重试、`unsave` 乐观移除 + 失败按原位置回滚。

### 6. Android UI

- `MainScreens.kt`：Job 页新增 **Filter** 入口打开 `ModalBottomSheet`（Job type / Workplace / Location / Minimum salary 四组 `FilterChip`）；已选条件以摘要 chip + “Clear all” 展示；`JobCard` 尾部新增 Save/Saved 按钮。
- `JobDetailScreen.kt`：顶栏 Save/Saved 开关替换原“Save unavailable”，展示保存失败提示。
- 新增 `SavedJobsScreen.kt`：收藏列表（loading/error/empty/分页失败重试态齐全），复用 `JobCard`。
- `RealProfileScreens.kt`：Me 页新增 “Saved jobs” 入口；`AdCandidateApp.kt` 接线新增路由与回调。
- 默认 Tab 仍为 “Recommended for you”，不新增底部导航项；沿用 `COMMON_LOCATIONS` / `SALARY_OPTIONS` 目录。

### 7. 测试

- 后端：新增 `CandidateSavedJobIntegrationTest`（成功/幂等/401/403/404/用户隔离/分页+不可浏览隐藏，5 用例）与 `CandidateRecommendationFilterIntegrationTest`（过滤应用 + 组合过滤 + 推荐 isSaved，2 用例）。
- Android：`RepositoryIntegrationTest` 更新推荐签名并新增收藏三接口用例；`ViewModelTest` 新增过滤重置回第 1 页、清空过滤、保存失败回滚、取消收藏成功移除/失败恢复 5 用例；`ApplicationViewModelTest` 同步 fake 签名。

## 改动文件

**后端**

- `backend/src/main/resources/db/migration/V17__create_candidate_saved_jobs.sql`（新增）
- `backend/src/main/java/com/adproject/job/infrastructure/CandidateSavedJobEntity.java`（新增）
- `backend/src/main/java/com/adproject/job/infrastructure/CandidateSavedJobRepository.java`（新增）
- `backend/src/main/java/com/adproject/job/api/CandidateSavedJobController.java`（新增）
- `backend/src/main/java/com/adproject/job/application/CandidateJobQueryService.java`
- `backend/src/main/java/com/adproject/recommendation/api/RecommendationDtos.java`
- `backend/src/main/java/com/adproject/recommendation/application/CandidateRecommendationService.java`
- `backend/src/main/java/com/adproject/recommendation/api/CandidateRecommendationController.java`
- `docs/openapi-v1.yaml`
- `backend/src/test/java/com/adproject/job/CandidateSavedJobIntegrationTest.java`（新增）
- `backend/src/test/java/com/adproject/recommendation/CandidateRecommendationFilterIntegrationTest.java`（新增）

**Android**

- `android/app/src/main/java/com/adproject/candidate/data/contract/ApiContract.kt`
- `android/app/src/main/java/com/adproject/candidate/data/api/HttpApis.kt`
- `android/app/src/main/java/com/adproject/candidate/data/api/RealRepositories.kt`
- `android/app/src/main/java/com/adproject/candidate/data/model/CandidateModels.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/JobViewModels.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/MainScreens.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/JobDetailScreen.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/jobs/SavedJobsScreen.kt`（新增）
- `android/app/src/main/java/com/adproject/candidate/feature/profile/RealProfileScreens.kt`
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`
- `android/app/src/test/java/com/adproject/candidate/ApplicationViewModelTest.kt`
- `android/app/src/test/java/com/adproject/candidate/ViewModelTest.kt`
- `android/app/src/test/java/com/adproject/candidate/RepositoryIntegrationTest.kt`

## API / 数据库

- 新增 3 个 Candidate 专属真实接口（见上），全部走既有 `AuthenticatedUser` 鉴权与角色校验；不把 JPA Entity 直接暴露为 DTO。
- 新增迁移 `V17`（Flyway 唯一入口，无手工 DB 变更）；时间统一 UTC `DATETIME(6)`。
- 推荐接口新增可选过滤参数，旧客户端不传即行为不变。

## 测试结果

后端（JDK 21 / Maven）：

```
mvn test
[INFO] Tests run: 269, Failures: 0, Errors: 0, Skipped: 6
[INFO] BUILD SUCCESS
```

其中 6 个 Skipped 为 `MySqlFlywayIntegrationTest`（需 Docker 环境，本机不可用，属既有跳过，与本包无关）。新增收藏 + 过滤 7 用例全绿。

Android（JDK 21）：

```
./gradlew testDebugUnitTest   # BUILD SUCCESSFUL
./gradlew assembleDebug       # BUILD SUCCESSFUL
./gradlew lintDebug           # BUILD SUCCESSFUL
```

单元测试（含新增 5 条过滤/收藏用例与收藏接口用例）全绿；`assembleDebug` 产出 APK 成功；`lintDebug` 无错误。唯一告警为 `AdCandidateApp.kt` 既有的 `LocalLifecycleOwner` 弃用提示（非本包引入）。

## 已知限制

- 推荐过滤在 Spring Boot 侧做内存/查询级过滤后再排序，未改动 ML 服务；ML 不可用时沿用既有确定性 fallback 路径（测试日志中的 fallback WARN 属预期）。
- 收藏列表只展示当前 ACTIVE+PUBLIC 的可浏览职位；被下架/关闭的职位会自动从收藏列表与 `total` 中剔除（后端行为），对应收藏记录保留但不可见。
- 本包未做模拟器截图/视觉回归，仅完成编译 + 单元/集成测试验证（遵循“尽量少做手工模拟器验证”约定）。

## 建议手动验证步骤

1. 安装 debug APK：Job 页点 **Filter** 选 Job type / Workplace / Location / Minimum salary，确认列表随过滤变化、摘要 chip 出现、**Clear all** 恢复默认 “Recommended for you”。
2. Job 卡片与详情页点 **Save** → 变 **Saved**；进 Me → **Saved jobs** 看到该职位；再点取消收藏 → 从列表移除；断网取消收藏确认失败提示与回滚。
3. 收藏列表分页到底显示 “You're all caught up”；清空后显示空态文案。
