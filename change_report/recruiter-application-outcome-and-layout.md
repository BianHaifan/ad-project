# 招聘者申请 Outcome、AI 分数与详情布局（Recruiter Application Outcome & Layout）

**Date:** 2026-08-17
**Scope:** 为招聘者申请流程补齐真实 `OFFERED` 终态（含跨端状态契约、状态机、审计、计数、筛选、AI 排序排除），在真实 AI 排序表中显式展示 `AI fit score` 与 `#Rank`，并把详情页孤立的右侧 `Actions` 面板重构为紧贴进度流程的 `Next step / Decision` 区。**未修改 `ml-service/**`、Agent、Admin、Google Meet / OAuth 业务逻辑、认证方式或数据库结构。**

---

## 1. 状态机与业务裁决

```text
APPLIED -> IN_REVIEW -> INTERVIEW -> OFFERED
       \-> REJECTED     \-> REJECTED
Candidate: APPLIED / IN_REVIEW / INTERVIEW -> WITHDRAWN

OFFERED, REJECTED, WITHDRAWN 均为终态。
```

- 招聘者仅可从 `INTERVIEW` 转到 `OFFERED`，且必须填写不超过 500 字决策说明；仍可在 `APPLIED` / `IN_REVIEW` / `INTERVIEW` 拒绝。
- `OFFERED` 仅代表「招聘者已发出录用结果」，**不**暗示候选人已接受，也不涉及薪资条款、合同、Offer 接受/拒绝、签约或入职。
- `OFFERED` 不进入 AI 候选人排序（`ELIGIBLE` 保持 `{APPLIED, IN_REVIEW, INTERVIEW}`，本就已排除，本次仅补测试）。
- 候选人申请列表/详情仍可见 `OFFERED`，并归入「归档 / 结果」分组，标签为「Offer received」。

**无需 Flyway 迁移**：`applications.status` 为 `VARCHAR`（`@Enumerated(EnumType.STRING)`），V5 迁移无 `CHECK` 约束，直接扩展枚举即可。

---

## 2. 实际修改文件清单

### Backend（6 修改 + 3 测试）

| 文件 | 变更 |
|---|---|
| `backend/.../application/domain/ApplicationStatus.java` | 枚举新增 `OFFERED`（`APPLIED, IN_REVIEW, INTERVIEW, OFFERED, REJECTED, WITHDRAWN`） |
| `backend/.../application/domain/ApplicationListFilter.java` | `ARCHIVED` 归组加入 `OFFERED` |
| `backend/.../application/api/RecruiterApplicationDtos.java` | `TransitionTarget` 增加 `OFFERED`；`Counts` 增加 `offered` |
| `backend/.../application/application/RecruiterApplicationService.java` | `allowed()` 增加 `INTERVIEW -> OFFERED`；`counts()` 增加 `countByCompanyIdAndStatus(..., OFFERED)` |
| `backend/.../application/application/CandidateApplicationQueryService.java` | 归档列表加入 `OFFERED`；撤回守卫改为 `OFFERED/REJECTED/WITHDRAWN` 均不可撤回 |
| `backend/.../application/application/CandidateApplicationResponseMapper.java` | `nextSteps()` 对 `OFFERED/REJECTED/WITHDRAWN` 返回空列表 |
| `backend/.../test/.../RecruiterApplicationIntegrationTest.java` | 新增 `offeredFollowsInterviewIsTerminalAuditedAndGuarded`：APPLIED→OFFERED 409、INTERVIEW→OFFERED 成功、OFFERED→REJECTED 409（终态）、版本冲突 409、候选人→OFFERED 403、审计事件（to_status=OFFERED + reason）、`meta.counts.offered==1`、候选人详情可见 OFFERED |
| `backend/.../test/.../RecruiterApplicantRecommendationIntegrationTest.java` | 新增 `offeredApplicantsAreExcludedFromRanking`：一条 OFFERED 不进入排序，`total==1` 仅剩 Active Candidate |
| `backend/.../test/.../CandidateApplicationQueryIntegrationTest.java` | 新增 `offeredIsArchivedAndCannotBeWithdrawn`：ARCHIVED 过滤返回 OFFERED；OFFERED 撤回 → 409 `INVALID_APPLICATION_TRANSITION` |

### Web（11 修改 + 4 测试）

| 文件 | 变更 |
|---|---|
| `web/src/models/recruiter.ts` | `ApplicationStatus` 增加 `'OFFERED'`；`RecruiterApplicationCounts` 增加 `offered: number`；`ApplicationTransitionRequest.toStatus` 增加 `'OFFERED'` |
| `web/src/api/recruiterRepository.ts` | `RecruiterTransitionStatus` 增加 `'OFFERED'` |
| `web/src/api/applicationHttpClient.ts` | 运行时解析器 `isStatus()` 增加 `'OFFERED'`；`isCounts()` 增加 `offered` 校验 |
| `web/src/components/StatusBadge.tsx` | 增加 `OFFERED: 'Offer made'` 标签 |
| `web/src/pages/applicationDetail/ActionPanel.tsx` | 重写为 `Next step / Decision`：按状态派生最少操作（APPLIED→Start review/Reject；IN_REVIEW→Schedule interview/Reject；INTERVIEW→Make offer/Reject；终态只读摘要），保留 loading/error/submitting 与键盘可操作的表单 |
| `web/src/pages/applicationDetail/ProgressRail.tsx` | 增加 `'success'` 阶段态；`OFFERED` 映射为第 4 节点；`outcomeLabel()` 返回 `Offer made/Rejected/Withdrawn`，Offer 用成功视觉，终态+成功均展示审计时间与原因 |
| `web/src/pages/ApplicationDetailPage.tsx` | 将 `<ActionPanel>` 从右侧栏移入主栏，紧跟 `<ProgressRail>` |
| `web/src/pages/ApplicationsPage.tsx` | 阶段卡片 4→5，加入 `Offered`（`stage-cards`）；`applicationStatus()` 解析增加 `OFFERED` |
| `web/src/pages/AiRankApplicants.tsx` | 表头 `Match`→`AI fit score`；排名徽标 `{rank}`→`#{rank}`；分数 `{matchScore}%`→`AI fit score: N / 100` 徽标；模型/降级来源保持可见 |
| `web/src/theme/recommendation-demo.css` | 排名表格列宽适配 `#Rank` + `fit-score` 徽标 |
| `web/src/theme/global.css` | 新增 `.badge.offered`、`.stage-offered.selected`、`.progress-stage.success` 样式 |
| `web/src/api/contract.test.ts` | 枚举断言增加 `OFFERED` |
| `web/src/pages/ApplicationPages.test.tsx` | 计数补 `offered: 0`；`it.each` 更新为状态感知：INTERVIEW 断言 `Make offer / Reject application`；新增 OFFERED 行（终态无操作）；新增 `makes an offer only from interview with a reason...` 用例；`outcomeLabel()` 状态感知替换原硬编码 `Outcome` |
| `web/src/pages/AiRankApplicants.test.tsx` | `'88%'`→`'88 / 100'` + 新增 `'#1'` 断言；计数补 `offered: 0` |
| `web/src/api/applicationHttpClient.test.ts` | 计数补 `offered: 0` |

### Android（3 修改 + 2 测试）

| 文件 | 变更 |
|---|---|
| `android/.../data/contract/ApiContract.kt` | `ApplicationStatus` 与 `CandidateJobApplicationState` 均增加 `OFFERED`（后者避免 `valueOf` 解析失败崩溃） |
| `android/.../feature/applications/RealApplicationTrackingScreens.kt` | `ApplicationStatus.label()` 增加 `OFFERED -> "Offer received"` |
| `android/.../feature/jobs/JobDetailScreen.kt` | `CandidateJobApplicationState.displayLabel()` 增加 `OFFERED -> "Offer received"` |
| `android/.../test/.../CandidateApiTest.kt` | 枚举列表增加 `"OFFERED"` |
| `android/.../test/.../ApplicationTrackingViewModelTest.kt` | 新增 `assertFalse(canWithdraw(OFFERED))` 断言 |

### Docs（1 修改，PRD 无需改）

| 文件 | 变更 |
|---|---|
| `docs/openapi-v1.yaml` | `ApplicationStatus` 枚举增加 `OFFERED` + 状态机描述更新；`ApplicationTransitionRequest.toStatus` 增加 `OFFERED`；`RecruiterApplicationCounts` 增加必填 `offered` |
| `docs/product-requirements.md` | **无需改**：第 43 行已写明「录用」，无「Offer 延后」旧约束文本 |

---

## 3. API / 数据库变化

- **无 DB 迁移**：状态字段为 `VARCHAR`，无 `CHECK` 约束，扩展枚举即可。审计事件沿用既有 `application_status_events` 表（`to_status='OFFERED'` + `reason`）。
- **`RecruiterApplicationCounts`** 新增必填 `offered`（候选端 `Counts` 无此字段，但 `CandidateApplicationListMeta` 的 `archived` 计数自然涵盖 OFFERED）。
- **`ApplicationTransitionRequest.toStatus`** 新增 `OFFERED`，但后端 `allowed()` 只接受 `INTERVIEW -> OFFERED`，其余来源一律 409 `INVALID_APPLICATION_TRANSITION`；候选人角色调用一律 403。
- **AI 排序候选集** 保持 `{APPLIED, IN_REVIEW, INTERVIEW}`，`OFFERED`/`REJECTED`/`WITHDRAWN` 均被排除。

---

## 4. 测试命令与真实结果

### Backend（JDK 21，Maven 离线）

```
JAVA_HOME=/c/Users/14188/.jdks/ms-21.0.8
/c/Users/14188/.m2/wrapper/dists/apache-maven-3.9.16/.../bin/mvn -o test
```

**结果：272 tests run, 0 failures, 0 errors, 6 skipped（BUILD SUCCESS）。**
- 6 skipped 全部来自既有 `MySqlFlywayIntegrationTest`（Testcontainers/MySQL Docker 不可用时的既有跳过，非本次引入）。
- 新增用例覆盖：成功（INTERVIEW→OFFERED + 审计 + 计数 + 候选人可见）、非法来源 409、终态不可再转 409、版本冲突 409、候选人 403、AI 排序排除 OFFERED、候选人归档分组与撤回守卫 409。

### Android（JDK 21）

```
JAVA_HOME=/c/Users/14188/.jdks/ms-21.0.8 ./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain
```

**结果：BUILD SUCCESSFUL in 1m 46s**（`testDebugUnitTest`、`lintDebug`、`assembleDebug` 全绿）。
- 唯一告警为既有的 `LocalLifecycleOwner` deprecation（`AdCandidateApp.kt:100`），非本次改动引入。

### Web

```
npm test            # vitest run → 24 files / 210 tests passed
npm run typecheck   # tsc -b → 通过（0 错误）
npm run build       # tsc -b && vite build → 通过，产出 dist/（130 modules，445.06 kB JS）
```

**结果：** `typecheck`、`test`、`build` 均通过。

> 过程说明：首轮 `typecheck` 报 `ActionPanel.tsx(20,9): TS6133 'terminal' is declared but never read`（终态分支已由显式 `status === ...` 处理，`terminal` 变量冗余），已删除该未用变量后重跑，三关全绿。

---

## 5. 未验证项（明确不写成「已通过」）

1. **Web 端 UI 未手工/浏览器端到端演练。** 「Next step / Decision」区的交互（各阶段按钮、Make offer 弹窗、终态只读摘要、窄屏单列）仅通过 **typecheck + 单元/组件测试** 验证，未在浏览器手工点按。
2. **Android 端未装新 APK 到模拟器手工点验。** `OFFERED` 的解析与「Offer received」标签仅通过 `testDebugUnitTest` + `assembleDebug` 验证，未在 `emulator-5554` 手工确认（遵循「最小化手工模拟器验证」约定）。
3. **真实 AI 推理路径未本地端到端联调。** 测试环境 ML 为降级（`source=FALLBACK / modelStatus=DEGRADED`），`AI fit score` 徽标改动是纯展示层，真实 `MODEL/ACTIVE` 分数来源的契约兼容性基于既有 `RecommendationMeta` 复用，未额外启动 ML 服务联调。

---

## 6. 明确未触碰的边界

- **未修改** `ml-service/**`（Python 训练/模型/特征/接口）、`agent/**`、`admin/**`、Google Meet / OAuth 的业务逻辑、认证方式与数据库结构。
- **未新增** DB 迁移（复用既有 `applications` / `application_status_events` 表，无 schema 变更）。
- **未展开** Offer 接受/拒绝、薪资条款、合同、签约或入职流程（`OFFERED` 仅「已发出录用结果」）。
- **未改动** 既有 `InterviewCard`、Message 候选人入口、Candidate fit 侧栏、Google Meet 调用与权限（仅移动决策区、保留原有操作）。
- **未输出** 任何密钥（DB_PASSWORD / JWT_SECRET / internal token / Google OAuth secret 等）。
- **未执行** commit / push / pull / merge / reset（保留工作区其他同事的未提交改动）。
