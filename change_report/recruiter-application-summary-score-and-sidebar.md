# 招聘者申请：真实匹配分数 + 详情右侧候选人信息（Recruiter Application Score & Sidebar）

**Date:** 2026-08-17
**Scope:** 让招聘者 Applications 总览展示真实、可解释的 AI 匹配分数（复用已持久化且版本仍有效的 Candidate–Job recommendation 快照，绝不 mock），并把 Application Detail 右侧从「可能整片空白」补齐为固定的 `Candidate at a glance` 卡片（含头像/姓名/联系/摘要/快捷操作），`Candidate fit` 在无分析时显示「AI analysis unavailable」空态而非消失。**未修改 `ml-service/**`、Agent、Admin、Google Meet / OAuth 业务逻辑、认证方式或数据库结构，也未新增 DB 迁移。**

---

## 1. 真实分数的来源与有效性规则

- **来源**：`candidate_job_recommendations` 表的既有快照 `CandidateJobRecommendationEntity`（字段 `score` 0–100、`source`、`modelVersion`、`featureVersion`、`strongMatchesJson`/`gapsJson`/`evidenceJson`、`resumeVersion`/`preferenceVersion`/`jobVersion`、`generatedAt`）。
- **有效性判定**（复刻 `CandidateJobQueryService.isCurrent` 的语义，不新增接口）：仅当快照满足
  `resumeVersion == 当前简历 version && preferenceVersion == 当前偏好 version && jobVersion == 岗位 version` 时才返回分数；三者任一漂移即视为过期 → 返回 `null`。
  - `resumeVersion`：`resumes.findByCandidateId(candidateId).map(ResumeEntity::getVersion).orElse(-1)`
  - `preferenceVersion`：`preferences.findById(candidateId).map(CandidateJobPreferenceEntity::getVersion).orElse(0)`（偏好实体 `@Id == candidateId`）
  - `jobVersion`：`job.getVersion()`
- **绝不** 按行调用 ML 服务、绝不伪造分数：无有效快照时 `matchScore = null`，Web 渲染为 `—`（而非假 `0`）。
- **权限边界**：`matchScore` / `matchAnalysis` 是「建议性展示数据」，**不**授权访问、**不**决定状态迁移；跨公司/无权限仍由既有 `requireJob`/`requireCompany` 守卫返回 404/403。

---

## 2. 实际修改文件清单

### Backend（2 修改 + 1 测试）

| 文件 | 变更 |
|---|---|
| `backend/.../application/api/RecruiterApplicationDtos.java` | 新增 `MatchAnalysis(int score, List<String> evidence, List<String> strongMatches, List<String> gaps, String modelVersion, Instant generatedAt)`；`Detail.matchAnalysis` 由 `Object` 改为 `MatchAnalysis` |
| `backend/.../application/application/RecruiterApplicationService.java` | 注入 `CandidateJobRecommendationRepository` / `CandidateJobPreferenceRepository` / `ResumeRepository` / `ObjectMapper`；新增 `storedMatch(candidateId, job)` 与 `readList(...)`；`summary()` 填充 `matchScore`，`detail()` 填充 `matchScore` + `matchAnalysis` |
| `backend/.../test/.../RecruiterApplicationIntegrationTest.java` | 新增 3 用例：`validRecommendationScoreIsReturnedInListAndDetail`（有效快照 → 列表/详情返回 87 + 完整 evidence/strongMatches/gaps/modelVersion/generatedAt）、`staleRecommendationScoreIsOmitted`（resumeVersion 漂移 → 列表/详情均为 null）、`recommendationDoesNotLeakAcrossCompanies`（他司 recruiter 详情 404、列表 0 条） |

### OpenAPI（1 修改）

| 文件 | 变更 |
|---|---|
| `docs/openapi-v1.yaml` | `RecruiterApplicationSummary.matchScore` 增加「复用仍有效快照，null 渲染为 `—` 而非 0」说明；`MatchAnalysis` schema 描述改为「复用持久化快照、仅版本仍有效时填充、不授权/不决定迁移」；`RecruiterApplicationDetail.matchAnalysis` 增加「null 时展示 AI analysis unavailable 空态」说明 |

### Web（6 修改 + 1 新增 + 2 测试）

| 文件 | 变更 |
|---|---|
| `web/src/api/applicationHttpClient.ts` | 新增 `isMatchAnalysis` 校验器（score 数值 + evidence/strongMatches/gaps 字符串数组 + modelVersion/generatedAt 字符串）；`parseDetail` 用其替换 `isRecord(matchAnalysis)` 松校验 |
| `web/src/pages/ApplicationsPage.tsx` | 移除 `showMatch` 条件；总览**始终**显示 `AI fit score` 列：有分 → `fit-score` 徽标 `{score} / 100`，无分 → `—`；表格固定 `with-match` 七列布局 |
| `web/src/pages/ApplicationDetailPage.tsx` | 删除主栏 `candidate-summary` 面板；右侧新增 `<CandidateAtAGlance>`（位于 `Candidate fit` 之前）；`Candidate fit` 改为恒渲染，无分析时显示 `AI analysis unavailable` 空态；移除 resume-snapshot 标题区的重复 `Open full resume` 按钮（快捷入口收敛到 glance 卡片，避免重复） |
| `web/src/pages/applicationDetail/CandidateAtAGlance.tsx` | **新增**：固定侧栏卡片——真实头像（`avatarUrl` 缺失回退 initials）、姓名、headline、email、location、applied job / applied time / 当前状态（`StatusBadge`）、resume summary 预览 + 最近 1–2 段经历、显式 `Message candidate` 与 `Open full resume`；**不含** phone / birthday / gender |
| `web/src/theme/global.css` | 新增 `.glance-card` / `.glance-head` / `.glance-preview` / `.glance-actions` / `.glance-empty` 样式（复用既有 `.metadata-list` / `.fit-score` / `.match-badge` / `.avatar`） |
| `web/src/api/applicationHttpClient.test.ts` | 新增 `parses a stored match analysis and rejects a malformed one`（有效 matchAnalysis 通过、`score` 为字符串的畸形载荷被拒） |
| `web/src/pages/ApplicationPages.test.tsx` | 总览断言改为 `AI fit score` 列 + 无分 `—`；新增「有效分 → `87 / 100` 徽标」用例；新增「glance 卡片 + AI analysis unavailable 空态（且无 phone/gender/birthday）」与「有分析时展示 explainable 明细」用例；`on-site location` 用例因 glance 卡片也含 `Location` 标签改为 `getAllByText` |

---

## 3. API / 数据变化

- **无 DB 迁移**：复用 `candidate_job_recommendations` / `resumes` / `candidate_job_preferences` / `jobs` 既有表，无 schema 变更。
- **`RecruiterApplicationSummary.matchScore`**：由「恒 null」改为「有效快照分数或 null」。
- **`RecruiterApplicationDetail.matchAnalysis`**：由 `Object`（恒 null）改为 `MatchAnalysis`（有效快照时填充 score + 解析后的 evidence/strongMatches/gaps + modelVersion + generatedAt，否则 null）。
- **候选端 `CandidateJobSummary.matchScore` / `CandidateJobDetail.matchAnalysis` 不受影响**：仍是原有 `RecommendationDtos.MatchAnalysis(strongMatches, gaps, evidence)`，本次未触碰。

---

## 4. 测试命令与真实结果

### Backend（JDK 21，Maven 离线）

```
JAVA_HOME=/c/Users/14188/.jdks/ms-21.0.8
/c/Users/14188/.m2/wrapper/dists/apache-maven-3.9.16/0daed3.../bin/mvn -o test
```

**结果：275 tests run, 0 failures, 0 errors, 6 skipped（BUILD SUCCESS）。**
- 6 skipped 为既有 `MySqlFlywayIntegrationTest`（Testcontainers/MySQL Docker 不可用时的既有跳过，非本次引入）。
- 本次新增 3 用例（有效分、版本漂移 null、跨公司隔离），`RecruiterApplicationIntegrationTest` 全类 8 用例通过。

### Web

```
npm test            # vitest run → 24 files / 214 tests passed
npm run typecheck   # tsc -b → 通过（0 错误）
npm run build       # tsc -b && vite build → 通过，产出 dist/（131 modules，446.17 kB JS）
```

**结果：** `test`、`typecheck`、`build` 均通过（214 用例较前次 210 新增 4 条）。

> 过程说明：首轮 `npm test` 因 glance 卡片新增 `Location` 元数据标签与 `InterviewCard` 的 on-site `Location` 字段标签同名，导致 `ApplicationPages.test.tsx` 的 `getByText('Location')` 命中两个元素；改为 `getAllByText('Location').length > 0` 后重跑全绿。

---

## 5. 未验证项（明确不写成「已通过」）

1. **Web 端 UI 未手工/浏览器端到端演练。** `Candidate at a glance` 卡片与 `AI fit score` 列仅通过 **typecheck + 单元/组件测试** 验证，未在浏览器手工点按；窄屏单列、头像图片加载等未目视确认。
2. **真实 AI 推理路径未本地端到端联调。** 测试环境 ML 为降级（`source=FALLBACK / modelStatus=DEGRADED`）。本次改动复用**已持久化**的推荐快照分数，不调用 ML；真实 `MODEL/ACTIVE` 快照的契约兼容性基于 `CandidateJobQueryService` 既有 `isCurrent` 语义复用，未额外启动 ML 服务联调。
3. **未对 `avatarUrl` 真实头像渲染做目视确认**（测试数据 `avatarUrl` 均为 null，走 initials 回退路径）。

---

## 6. 明确未触碰的边界

- **未修改** `ml-service/**`（Python）、`agent/**`、`admin/**`、Google Meet / OAuth 业务逻辑、认证方式与数据库结构。
- **未新增** DB 迁移（复用既有表，无 schema 变更）。
- **未改动** 业务状态机、`ActionPanel` / `ProgressRail` / `InterviewCard` 的既有逻辑（仅移动/删除候选信息展示与重复按钮，决策流程原样）。
- **未新增** 候选人资料接口、未展示 phone / birthday / gender（沿用已授权的 application + resumeSnapshot 数据）。
- **未输出** 任何密钥（DB_PASSWORD / JWT_SECRET / internal token / Google OAuth secret 等）。
- **未执行** commit / push / pull / merge / reset（保留工作区其他同事的未提交改动）。

---

## 7. 后续建议（Next steps）

- 若需「AI ranked applicants」面板与普通列表分数完全一致的可解释来源，可在 `CandidateJobQueryService` 的 `analysis()` 与 `RecruiterApplicationService.storedMatch()` 之间抽取共享的「快照 → MatchAnalysis」映射器，消除两处 JSON 解析重复（当前刻意各自实现以不触碰候选端契约）。
- 可在浏览器中补一次目视走查（总览 `AI fit score` 列、详情 glance 卡片与 `AI analysis unavailable` 空态、窄屏单列）。
