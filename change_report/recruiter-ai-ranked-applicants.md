# 招聘者按职位智能排序已投递求职者（Recruiter AI-ranked applicants）

**Date:** 2026-08-17
**Scope:** 新增只读端点 `GET /api/v1/recruiter/jobs/{jobId}/applicant-recommendations`，对某职位下已投递求职者做 AI 排序（含确定性规则降级），并同步 OpenAPI 与招聘者 Web 端「AI rank applicants」入口。**未修改 `ml-service/**`**（复用既有 `/internal/v1/recommend/candidates` 反向调用）。

---

## 1. ML 服务兼容性结论（Step 1 兼容性门禁）

**结论：无需修改 `ml-service/**`。** 既有 `POST /internal/v1/recommend/candidates` 已支持「职位 + 候选人列表 → 排序结果」的反向契约，Spring Boot 侧只需新增一个复用同一 token/超时的反向客户端方法。

**代码证据：**

- `ml-service/src/ad_recommender/schemas.py` — `RecommendCandidatesRequest{job, candidates(1..500), limit(1..100)}`，已有完整定义。
- `ml-service/src/ad_recommender/model.py` — `recommend_candidates(job, candidates, limit)` 对全部候选人打分排序后 `[:limit]`。

**关键边界（避免静默截断）：** ML 返回的是 top-`limit`（`limit ≤ 100`），而输入允许最多 500 人。为保证「排满全集、不静默截断」，本实现先对全集排序；若模型返回条数少于输入（部分返回 / 异常），则**回退到确定性规则覆盖全集**（`source=FALLBACK`、`modelStatus=DEGRADED`），绝不只返回前 100 条冒充全集。

---

## 2. 实际修改文件清单

### Backend（8 个文件，3 新增 + 3 修改 + 2 测试）

| 文件 | 变更 |
|---|---|
| `backend/.../recommendation/application/MlRecommendationClient.java` | 新增 `recommendCandidates(MlJob, List<MlCandidate>, int limit)`（复用 token/超时，POST `/internal/v1/recommend/candidates`）与 `RecommendCandidatesRequest` record |
| `backend/.../recommendation/application/RecruiterApplicantRankingService.java`（新） | 排序引擎：`MAX_CANDIDATES=500`、`MAX_MODEL_RESULTS=100`；`rankCandidates` 先走模型，异常/部分返回 → 确定性规则 `fallback`（技能覆盖 45 + 标题 25 + 地点 10 + 工作方式 10 + 雇佣 5 + 薪资 5） |
| `backend/.../application/infrastructure/ApplicationRepository.java` | 新增 `findByJobIdAndStatusInOrderByAppliedAtAscIdAsc`（按 jobId + 状态集合查询，稳定排序） |
| `backend/.../application/api/RecruiterApplicantRecommendationDtos.java`（新） | `ApplicantCandidateSummary`（**无 email**）、`RecommendedApplicant`、`RecommendedApplicantResponse`；复用 `RecommendationDtos.MatchAnalysis` 与 `RecommendationMeta` |
| `backend/.../application/application/RecruiterApplicantRecommendationService.java`（新） | 组装层：`requireCompany`(403) → 职位归属校验(404) → `ELIGIBLE = {APPLIED, IN_REVIEW, INTERVIEW}` → 空集返回 NONE/NOT_APPLICABLE → `>500` 抛 422 `RECOMMENDATION_INPUT_LIMIT` → 组装 `MlJob`+`MlCandidate`(仅简历快照+职位内容) → 调排序引擎 → 分页切片（rank=全局序号+1） |
| `backend/.../application/api/RecruiterApplicantRecommendationController.java`（新） | `GET` 端点，`page`（默认 1，`@Min(1)`）、`pageSize`（默认 20，`@Min(1) @Max(100)`） |
| `backend/.../test/.../application/RecruiterApplicantRecommendationIntegrationTest.java`（新） | 6 用例：成功(FALLBACK/DEGRADED 确定性排序)、空集 200、501 人 422、跨公司/不存在 404、候选人 403、未认证 401 |
| `backend/.../test/.../recommendation/MlRecommendationClientTest.java` | 新增 `sendsReverseCandidatesRequestAndParsesRankedApplicants`（反向请求序列化 + 解析断言） |

### Web（12 个文件，2 新增 + 10 修改）

| 文件 | 变更 |
|---|---|
| `web/src/models/recruiter.ts` | 新增 `ApplicantCandidateSummary`、`ApplicantMatchAnalysis`、`RecommendedApplicant`、`RecommendationSource`/`RecommendationModelStatus`、`RecommendationMeta`、`RecommendedApplicantListResult` |
| `web/src/api/contract.ts` | 新增 `applicantRecommendations(jobId)` 路径 |
| `web/src/api/applicationHttpClient.ts` | 新增 `listApplicantRecommendations` + 解析器（候选摘要/匹配分析/meta 严格校验） |
| `web/src/api/recruiterRepository.ts` | 接口新增 `listApplicantRecommendations` |
| `web/src/api/repository.ts` | 接线到真实 `applicationHttpClient`（**无 mock 降级**） |
| `web/src/api/queries.ts` | 新增 `keys.applicantRecommendations` 与 `useApplicantRecommendations`（`enabled: !!jobId`） |
| `web/src/mocks/mockRecruiterRepository.ts` | `MockOnlyRepository` 的 `Omit` 增加该 key（保持 mock 仅覆盖登录/getMe） |
| `web/src/pages/ApplicationsPage.tsx` | 工具栏新增「AI rank for job」职位选择器（`useJobs`）；选中职位后渲染 `AiRankApplicants` 面板 |
| `web/src/pages/AiRankApplicants.tsx`（新） | 排名面板：入口按钮（点开才请求）、加载/空/错误/降级态、排名列表（rank + 分数 + 强匹配/gaps + 阶段 + View 跳转详情）、分页 |
| `web/src/theme/recommendation-demo.css` | 新增排名表格/徽标/匹配行样式 |
| `web/src/api/applicationHttpClient.test.ts` | 新增客户端测试（URL 断言 + 畸形 payload 拒绝） |
| `web/src/pages/AiRankApplicants.test.tsx`（新） | 面板测试（点开才请求、内容/降级/空/错误、跳转详情）+ ApplicationsPage 入口可见性测试 |

### Docs（1 个文件）

| 文件 | 变更 |
|---|---|
| `docs/openapi-v1.yaml` | 新增 `/recruiter/jobs/{jobId}/applicant-recommendations` 路径（200/401/403/404/422，`page`/`pageSize` 参数）与 `RecruiterApplicantRecommendationResponse`、`RecommendedApplicant`、`RecruiterApplicantCandidateSummary` 三个 schema |

---

## 3. API 契约变更

**`GET /api/v1/recruiter/jobs/{jobId}/applicant-recommendations`**（只读）

| 参数 | 类型 | 必填 | 默认 | 约束 |
|---|---|---|---|---|
| `page` | integer | 否 | 1 | `>= 1` |
| `pageSize` | integer | 否 | 20 | `1..100` |

**鉴权与归属：** 未认证 401；非招聘者 403；招聘者无公司 403；职位不存在或非本公司 404。

**候选集范围：** 仅该职位下 `APPLIED`/`IN_REVIEW`/`INTERVIEW`（排除 `WITHDRAWN`/`REJECTED`）；只使用**简历快照 + 职位内容 + 安全候选摘要**，绝不查询/返回非投递者 Candidate。

**响应 `{data, meta}`：**

- `data[]`：`applicationId`、`candidate`（`candidateId/fullName/headline/avatarUrl/location`，**无 email**）、`status`、`appliedAt`、`matchScore`(0–100)、`rank`、`matchAnalysis`（`strongMatches/gaps/evidence`）。
- `meta`：`source`(`MODEL`/`FALLBACK`/`NONE`)、`modelVersion`、`featureVersion`、`modelStatus`(`ACTIVE`/`DEGRADED`/`NOT_APPLICABLE`)、`inferenceMs`、`generatedAt`、`page/pageSize/total/hasNext`。

**边界行为：** 空候选集 → 200 空 `data`（`NONE`/`NOT_APPLICABLE`，不发模型请求）；候选数 > 500 → 422 `RECOMMENDATION_INPUT_LIMIT`（不静默截断）。

---

## 4. 测试命令与真实结果

### Backend（JDK 21）

```
JAVA_HOME=/c/Users/14188/.jdks/ms-21.0.8
"/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.3/plugins/maven/lib/maven3/bin/mvn" test
```

**结果：262 tests run, 0 failures, 0 errors, 6 skipped（BUILD SUCCESS）。**
- 6 skipped 全部来自既有 `MySqlFlywayIntegrationTest`（Testcontainers/MySQL Docker 不可用时的既有跳过，非本次改动引入）。
- 新增/更新用例：`RecruiterApplicantRecommendationIntegrationTest`（6）覆盖成功(FALLBACK 确定性排序 + 分页切片)、空集、>500 人 422、跨公司/不存在 404、候选人 403、未认证 401；`MlRecommendationClientTest`（2）含反向 `/internal/v1/recommend/candidates` 请求序列化与响应解析。

### Web

```
npm run typecheck   # tsc -b，通过
npm test            # vitest run，208 passed（24 files）
npm run build       # tsc -b && vite build，通过
```

**结果：**
- `typecheck`：通过（无 TS 错误）。
- `test`：**24 files / 208 tests 全部通过**，含新增 `AiRankApplicants.test.tsx`（点开才请求、内容/降级/空/错误、跳转详情、入口可见性）与 `applicationHttpClient.test.ts`（URL + 畸形 payload 拒绝）。
- `build`：通过（`tsc -b && vite build` 成功产出 `dist/`）。

---

## 5. 未验证项（明确不写成“已通过”）

1. **Web 端 UI 未手工/浏览器端到端验证。** 「AI rank applicants」面板的交互（职位选择、点开加载、分页翻页、跳转详情）仅通过 **typecheck + 单元/组件测试** 验证，未在浏览器手工演练。
2. **ML 真实推理路径未本地端到端联调。** 测试环境 `app.recommendation.enabled=false`，成功路径断言的是 `FALLBACK`/`DEGRADED` 确定性排序；`MODEL`/`ACTIVE` 真实返回路径的契约兼容性基于代码证据（复用既有 `recommendJobs` 的 token/超时/序列化）+ `MlRecommendationClientTest` 反向请求单测，未额外启动 ML 服务联调。
3. **未运行 Android 端任何测试**（本功能不涉及 Android，明确未触碰）。

---

## 6. 明确未触碰的边界

- **未修改** `ml-service/**`（训练/模型/特征/Python 接口）、`agent/**`、`admin/**`、`android/**`、Google Meet / OAuth。
- **未新增** DB 迁移（复用既有 `applications`/`resume_snapshots`/`candidate_profiles` 表，无 schema 变更）。
- **未新增** 站点级 Talent Pool；候选集严格限定在该职位已投递者。
- **未改动** 既有 application 列表/详情接口与推荐职位接口（`recommendJobs`）行为。
- **未使用** 任何客户端 mock 降级（Web 端只调用真实 `applicationHttpClient`）。
- **未输出** 任何密钥（DB_PASSWORD / JWT_SECRET / internal token 等）。
- **未执行** commit / push / pull / merge / reset。
