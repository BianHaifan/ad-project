# Package 2 — 求职者单一推荐职位流：类型筛选、搜索与服务端懒加载

**Date:** 2026-08-16
**Scope:** `GET /api/v1/candidate/recommendations/jobs` 扩展为分页推荐流（关键词搜索、雇佣类型筛选、服务端懒加载），Android 求职者端改造为单一推荐信息流（类型筛选 + 手动搜索 + 触底自动加载下一页）。

---

## 1. ML 服务兼容性结论（Step 1 兼容性门禁）

**结论：无需修改 `ml-service/**`。** 现有 Spring Boot → ML 服务的调用已经能够接受“被关键词 + 雇佣类型预先缩小过的候选集”，继续沿用现有 `limit` 语义返回排序结果，不改变模型输入/输出结构，也不改变训练/推理契约。

**代码证据：**

- `ml-service/src/ad_recommender/schemas.py` — `RecommendJobsRequest` 只接受 `candidate`、`jobs`（`jobs` 最小 1、最大 500）、`limit`（1–100），**没有任何基于标题或雇佣类型的过滤字段**。ML 服务对传入的任意 `jobs` 列表做排序，不关心列表本身是否已被外部预过滤。
- `ml-service/src/ad_recommender/api.py` / `model.py` — `recommend_jobs(candidate, jobs, limit)` 对输入的 `jobs` 列表打分排序并截取 `limit` 条，不包含标题/类型筛选逻辑。

因此 Spring Boot 侧在调用 ML **之前**先按 `q`（标题包含）与 `employmentType` 过滤 `jobs`，再交给 `recommendJobs`，属于对既有契约的**合法预过滤**，ML 侧完全无感知。**未修改 `ml-service/**` 任何文件。**

---

## 2. 实际修改文件清单

### Backend（4 个文件）

| 文件 | 变更 |
|---|---|
| `backend/src/main/java/com/adproject/recommendation/application/CandidateRecommendationService.java` | 新增 `MAX_RANKED = 100`；重写 `recommendJobs(principal, q, employmentType, page, pageSize)`：先过滤（标题 contains + 类型），再按 `offset/pageSize` 切片排序结果；新增 `matchesQuery` 辅助方法；`RecommendationMeta` 输出 `page/pageSize/total/hasNext`，`hasNext = offset + pageSize < ranked.size()` |
| `backend/src/main/java/com/adproject/recommendation/api/CandidateRecommendationController.java` | 端点新增 `@RequestParam`：`q`（可选）、`employmentType`（可选 `EmploymentType`）、`page`（默认 1，`@Min(1)`）、`pageSize`（默认 10，`@Min(1) @Max(20)`） |
| `backend/src/main/java/com/adproject/recommendation/api/RecommendationDtos.java` | `RecommendationMeta` record 增加 `page/pageSize/total/hasNext`，移除 `limit` |
| `backend/src/test/java/com/adproject/recommendation/CandidateRecommendationIntegrationTest.java` | 既有用例 `limit=5` → `pageSize=5` + 断言 `meta.page=1`、`meta.pageSize=5`；新增 5 个测试：分页/尾页越界/类型筛选/关键词/未认证 401 |

### Android（9 个文件）

| 文件 | 变更 |
|---|---|
| `android/.../data/contract/ApiContract.kt` | `RecommendationMeta` 增加 `page/pageSize/total/hasNext`，移除 `limit` |
| `android/.../data/api/HttpApis.kt` | `recommendations(q, employmentType, page=1, pageSize=10)` |
| `android/.../data/api/RealRepositories.kt` | 接口与实现 `recommendations(q, employmentType, page, pageSize)`，`q` trim 后空串→null |
| `android/.../feature/jobs/JobViewModels.kt` | `JobFeedUiState` 移除 `recommended`，新增 `query/employmentType/page/hasNext/total/loadingMore/loadMoreError`；`generation` 计数取消过期请求；append 按 `jobId` 去重；`loadMore`/`retryLoadMore` 守卫 |
| `android/.../feature/jobs/MainScreens.kt` | 移除 Recommended/Browse 切换；标题固定 “Recommended for you”；类型筛选 `All/Full time/Internship/Part time`（All 默认）；`snapshotFlow` 触底触发 `onLoadMore`；加载更多/加载失败重试/末页 “You're all caught up” 三种底部态 |
| `android/.../AdCandidateApp.kt` | 接线 `onLoadMore`/`onRetryLoadMore`，移除 `onRecommended` |
| `android/.../test/ViewModelTest.kt` | 更新测试仓库实现；新增 5 个分页/筛选/去重/失败重试/尾页测试 |
| `android/.../test/ApplicationViewModelTest.kt` | `FixedJobRepository` 补 `recommendations` override（接口已无默认实现） |
| `android/.../test/RepositoryIntegrationTest.kt` | 新增分页参数与 `RecommendationMeta` 映射断言 |

### Docs（1 个文件）

| 文件 | 变更 |
|---|---|
| `docs/openapi-v1.yaml` | `/candidate/recommendations/jobs` 增加 `q`、`employmentType`、`page`、`pageSize` 参数；`RecommendationMeta` schema 增加 `total`、`hasNext`，移除 `limit` |

---

## 3. API 契约变更

**`GET /api/v1/candidate/recommendations/jobs`**

新增查询参数：

| 参数 | 类型 | 必填 | 默认 | 约束 |
|---|---|---|---|---|
| `q` | string | 否 | — | 标题 contains（服务端 `trim`+`toLowerCase`） |
| `employmentType` | enum | 否 | — | `FULL_TIME` / `INTERNSHIP` / `PART_TIME` |
| `page` | integer | 否 | 1 | `>= 1` |
| `pageSize` | integer | 否 | 10 | `1..20` |

`RecommendationMeta`（响应 `meta`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `source` | string | `MODEL` / `FALLBACK` / `NONE` |
| `modelVersion` | string | 模型版本 |
| `featureVersion` | string | 特征版本 |
| `modelStatus` | string | `ACTIVE` / `DEGRADED` / `NOT_APPLICABLE` |
| `inferenceMs` | integer | 推理耗时 |
| `generatedAt` | string(ISO-8601) | 生成时间 |
| `page` | integer | 当前页 |
| `pageSize` | integer | 每页大小 |
| `total` | integer | 过滤后候选总数（`min(filtered.size(), 100)`） |
| `hasNext` | boolean | 是否还有下一页 |

保留 `data[]` 中每条的推荐来源、模型版本、匹配分数与理由（`source/modelVersion/score/reasons` 未改动）。

**服务端过滤顺序（需求 6）：** ACTIVE+PUBLIC → 关键词 `q` → `employmentType` → 既有推荐排序 → 分页切片。从不伪造 `/jobs` 作为推荐结果。

---

## 4. 测试命令与真实结果

### Backend（JDK 21）

```
$env:JAVA_HOME = "C:\Users\14188\.jdks\ms-21.0.8"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test
```

**结果：236 tests run, 0 failures, 0 errors, 6 skipped。**
- 6 skipped 全部来自既有的 `MySqlFlywayIntegrationTest`（Testcontainers/MySQL Docker 不可用时的既有跳过，非本次改动引入）。
- 新增用例覆盖：分页返回 `page/pageSize` 并切片、尾页越界返回空、`employmentType` 筛选收窄、`q` 标题关键词筛选、未认证 401。既有 403（recruiter）/422（RESUME_REQUIRED）/fallback 降级路径保持不变。

### Android

```
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug lintDebug
```

**结果：**
- `testDebugUnitTest`：**89 tests, 0 failures, 0 errors, 0 skipped**（BUILD SUCCESSFUL）。
- `assembleDebug` + `lintDebug`：**BUILD SUCCESSFUL**。
- 新增用例覆盖：首屏 `page=1&pageSize=10`、分页 append + `jobId` 去重、类型筛选重置到第 1 页、加载更多失败保留已加载职位 + 底部重试、尾页不再发请求；仓库层覆盖请求参数与 `meta.page/pageSize/total/hasNext` 映射。

---

## 5. 未验证项（明确不写成“已通过”）

1. **Compose UI 行为未手工/真机验证。** 本项目无 `androidTest` 源码集、未运行 Robolectric/Compose UI 测试，因此“触底自动加载”“类型筛选交互”“末页提示”仅通过**编译 + ViewModel/仓库单元测试**验证，未做端到端点击验证。
2. **“在推荐候选内搜索”的 UX 未手工演练。** 搜索语义（手动提交、无每击键请求、限定在推荐集内）由 ViewModel/服务端单测验证，未在真机确认。
3. **ML 真实推理路径未在本次本地端到端跑通。** ML 客户端在未启用/不可达时走 fallback；`MODEL` 真实返回路径的契约兼容性基于代码证据 + 既有单测，未额外启动 ML 服务做联调。

---

## 6. 明确未触碰的边界

- **未修改** `ml-service/**`、`agent/**`、DB 实体 / Flyway / 鉴权逻辑、Google Meet / OAuth / Admin / Web 端。
- **未修改** 普通 `/api/v1/jobs` 语义。
- **未使用** 任何 mock 数据或客户端伪造的推荐结果（推荐结果始终来自服务端 `CandidateRecommendationService` 真实逻辑）。
- **未修改** 全局 `TagChip`；保留 Package 1 的 job-card 标签换行/截断修复（`JobSkillChip` 未动）。
- **未修改** `docs/API_COVERAGE.csv`（该文件未登记 recommendations 端点，grep 确认无匹配，故按“仅当端点已登记时才改”的约束不修改）。
- **未执行** commit / push / pull / merge / reset。
- **未输出** 任何密钥（DB_PASSWORD / JWT_SECRET / OAuth 密钥）。
