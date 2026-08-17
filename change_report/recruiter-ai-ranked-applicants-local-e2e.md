# 招聘者 AI 排序已投递求职者 — 本机真实联调验收

**Date:** 2026-08-17
**性质:** 只读验收（read-only acceptance）。未修改任何业务代码、OpenAPI、数据库结构、ML 模型或测试数据；未创建/删除/修改任何业务数据；未执行 git 写操作；未读取或输出任何密钥。

---

## 1. 结论（TL;DR）

**本次无法完成 `MODEL/ACTIVE` 真实结果验收，按任务要求第 5 条记录为「因无既有可处理申请而跳过」。** 同时发现一个**部署层面的阻塞问题**：当前本机正在运行的 Spring Boot 是**旧构建**（JAR 打于功能代码编译之前），根本没有注册新端点，因此新接口在本机当前进程里实际不可用。

三个独立阻塞因素叠加：

1. **运行中的后端是旧构建**（关键阻塞）：`RecruiterApplicantRecommendationController` 未加载，新端点返回 `500 NoResourceFoundException`。
2. **数据库无任何申请**：三名招聘者（rec1/rec2/rec3）的申请数均为 0，无 `APPLIED/IN_REVIEW/INTERVIEW` 候选可排序。
3. **ML 服务未运行**：健康接口连接被拒，Spring Boot 持续走 `FALLBACK` 降级。

---

## 2. 实际运行服务与健康状态

| 服务 | 状态 | 证据 |
|---|---|---|
| Spring Boot | **运行中，但为旧构建** | PID 35624，`java -jar target/backend-0.0.1-SNAPSHOT.jar`，端口 8081，连 MySQL 8.4.11 `localhost:13306/adproject`。JAR 时间戳 **2026-08-16 23:56:47**；进程启动于 **2026-08-17 10:27:42**。 |
| ML（FastAPI） | **未运行** | 无 `python`/`uvicorn` 进程；`GET http://127.0.0.1:8000/internal/v1/health` → 连接拒绝（HTTP 000）。 |
| Web（Vite） | **未运行** | 无 `node` 进程；`web-dev.log` 为历史残留（`vite --host 127.0.0.1` 已退出）。 |

**ML 健康接口检查（任务第 3 条）：** 结果为 **not ready** —— 端口 8000 无监听，健康请求连接被拒。Spring Boot 侧确认**无法连接**：后端日志反复输出
`Recommendation model unavailable; using deterministic fallback (ResourceAccessException)`。

---

## 3. 数据库真实数据状态

使用 `docs/seed-test-accounts.md` 中的测试账号登录（`seed.rec*` 系列）逐一核实：

| 招聘者 | 公司 | 岗位数 | 申请数（`GET /api/v1/recruiter/applications`） |
|---|---|---|---|
| seed.rec1（Derek Ong） | TechNova Solutions | 50 | **0** |
| seed.rec2（Mei Ling Tan） | DataSphere Analytics | 50 | **0** |
| seed.rec3（Ryan Koh） | PixelWorks Studio | 50 | **0** |

- 种子数据共 150 个岗位、3 名候选（简历/偏好齐全），但**没有任何 candidate 投递（applications）记录**。现有种子只覆盖「求职者 → 岗位」推荐方向，未覆盖「招聘者 → 已投递求职者」方向所需的投递数据。
- `scripts/seed-local-demo.sql`（内含 3 条 `APPLIED/IN_REVIEW` 申请）**未应用到当前库**：其账号 `recruiter@demo.local` 登录返回 401，说明该脚本未执行。

因此满足任务第 5 条「当前数据库没有符合条件的申请」的情形。

---

## 4. 端点真实调用结果

对 `GET /api/v1/recruiter/jobs/{jobId}/applicant-recommendations` 的真实请求：

| 请求 | 结果 | 说明 |
|---|---|---|
| 未携带 token | **401 UNAUTHORIZED** | Spring Security 在路由前拦截，正常。 |
| 携带 candidate token | **500 INTERNAL_ERROR** | 预期应为 403。 |
| 携带 rec1 token（本人职位） | **500 INTERNAL_ERROR** | 预期应为 200 空 `{data:[], meta:{source:NONE, modelStatus:NOT_APPLICABLE}}`。 |
| rec2 token（跨公司调用 rec1 职位） | **500 INTERNAL_ERROR** | 预期应为 404。 |
| 不存在的 jobId | **500 INTERNAL_ERROR** | 预期应为 404。 |

**根因（后端日志）：**

```
NoResourceFoundException: No static resource
api/v1/recruiter/jobs/.../applicant-recommendations.
```

运行中的 JAR（`target/backend-0.0.1-SNAPSHOT.jar`，2026-08-16 23:56 打包）**早于**新端点的编译产物（`RecruiterApplicantRecommendationController.class` 于 2026-08-17 12:18 由 `mvn test` 编译到 `target/classes/`）。即：**新代码已编译但未重新打包、未重启**，运行中的 JVM 里没有该路由。

> 修复方式（本次未执行，属下一步工作）：`mvn package` 重新打包并重启后端 JAR。无需改代码。

---

## 5. 真实结果验证：跳过原因（对应任务第 4/5 条）

**`MODEL/ACTIVE` 真实返回未验证，跳过。** 原因三重：

1. 数据库无符合条件的申请（`APPLIED/IN_REVIEW/INTERVIEW` 均为 0）——即便后端与 ML 就绪，端点也会在空候选集分支直接返回 `NONE/NOT_APPLICABLE`，根本不会调用模型；
2. ML 服务未运行（健康 not ready，后端 `FALLBACK` 降级）；
3. 运行中后端为旧构建，未注册该端点（500）。

按任务第 5 条，**未补造任何申请数据**。

**补充说明：** 排序结果「仅含该职位申请者、无 email」的契约由 `ApplicantCandidateSummary`（`candidateId/fullName/headline/avatarUrl/location`，无 email 字段）+ 后端集成测试保证；但因旧构建无法在线复核。

---

## 6. Web 手动验证结果

**未执行（跳过）。** 原因：

1. Web 开发服务器未运行（无 node 进程）；
2. 数据库无申请，即便启动 Web，`AI rank applicants` 面板也只会渲染空态，无法验证列表/分数/模型状态/跳转详情。

（Web 侧交互已由 `AiRankApplicants.test.tsx` + `applicationHttpClient.test.ts` 的组件/客户端测试覆盖，见主变更报告。）

---

## 7. 未修改的内容

- **未修改** 任何业务代码、OpenAPI、数据库结构、ML 模型、测试数据。
- **未创建/删除/修改** 任何业务数据（仅登录 + 只读 GET）。
- **未启动/重启** 任何服务（后端、ML、Web 均保持原状）。
- **未读取/输出** `.env`、密码、JWT、内部令牌等密钥（登录复用 `docs/seed-test-accounts.md` 已公开的测试账号；响应的 access token 仅写临时文件供同进程复用，未回显）。
- **未执行** commit / push / pull / merge / reset。
- **未重复运行** 完整单元测试或构建（按任务第 6 条）。

---

## 8. 发现的阻塞问题

| # | 阻塞 | 影响 | 是否代码缺陷 |
|---|---|---|---|
| B1 | 运行中后端为旧构建，未打包/未重启新端点 | 新端点 500 不可用 | 否（部署问题，`mvn package` + 重启即恢复） |
| B2 | 数据库无任何 application（0 条） | 无法触发 `MODEL/ACTIVE` 真实排序 | 否（种子数据仅覆盖求职者方向） |
| B3 | ML 服务未运行 | 健康 not ready，后端降级 FALLBACK | 否 |
| B4 | Web 开发服务器未运行 | 无法手动 UI 验收 | 否 |

**结论：** 功能代码与测试已完备（主变更报告：后端 262 测试、Web 208 测试全绿），但**尚未在本机完成「重新打包 + 重启后端 → 启动 ML → 准备投递数据」的部署闭环**，因此本机真实 `MODEL/ACTIVE` 联调无法闭环验证。

**下一步安全且最小的工作（需用户明确授权后再做）：**
1. `mvn package` 重新打包并重启后端（使新端点上线，可先验证 200 空态 / 403 / 404 / 422 分支）；
2. 启动 ML（`conda run -n ad-project-ml ad-recommender serve --port 8000`）并确认 `/internal/v1/health` 为 `ready`；
3. 若要验证 `MODEL/ACTIVE`，需在测试库中准备若干条 `APPLIED/IN_REVIEW` 投递（属**新增测试数据**，须另行授权，不属本只读验收范围）。
