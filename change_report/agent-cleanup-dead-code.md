# Agent 死代码清理（dead code cleanup）

日期：2026-08-19

## 完成了什么

清理 AI Agent 功能中的死代码和退化配置，三类改动（A/B/C）：

- **A — 删除 ClientContext 死链**：该字段被 Android 发送、后端校验、落库（`client_context_json`）、转发给 Planner，但整条链上没有任何人读取。全链路删除：
  - Android：`ApiContract.CreateAgentRunRequest.clientContext` 与 `AgentClientContext` 数据类、`AgentViewModel` 调用点
  - 后端：`AgentDtos.ClientContext` 记录、`AgentPlannerClient.PlanRequest`、`AgentRunService` 落库/转发调用、`AgentRunEntity.clientContextJson` 字段
  - Python：`models.PlanRequest.clientContext` 与 `ClientContext` 模型
  - 文档：`docs/openapi-v1.yaml`（CreateAgentRunRequest 与 AgentClientContext schema）、`docs/api-design.md` 示例、`docs/agent-design.md` §4
- **B — 删除 `AGENT_LLM_ENABLED`**：规则引擎删除后该开关的语义退化为"关掉整个服务"。删除 `deepseek.py`、`docker-compose.yml`、`.env.example`、`scripts/restart-agent-deepseek.ps1` 四处引用。
- **C — Android DTO 瘦身**：删除 UI 从未读取的 4 个 Kotlin 字段（`AgentStep.inputSummary/outputSummary/durationMs`、`AgentTarget.type`）。后端、数据库、OpenAPI 契约不动（`durationMs` 等是 OpenAPI required 字段和 agent-design.md §6 规定的审计内容）。

## 修改了哪些模块

- `backend/src/main/java/com/adproject/agent/`（api/application/infrastructure）
- `backend/src/test/java/com/adproject/agent/AgentRunIntegrationTest.java`（测试助手移除 clientContext）
- `agent-service/src/agent_service/models.py`、`deepseek.py`
- `android/.../data/contract/ApiContract.kt`、`feature/agent/AgentViewModel.kt` 及对应测试 fixture
- `docs/openapi-v1.yaml`、`docs/api-design.md`、`docs/agent-design.md`
- `infra/docker/docker-compose.yml`、`.env.example`、`scripts/restart-agent-deepseek.ps1`

## API 或数据库是否变化

- **数据库**：新增 Flyway 迁移 `V30__remove_agent_client_context.sql`，删除 `agent_runs.client_context_json` 列（该列数据从未被读取）。已在本地库执行成功（schema v29 → v30）。
- **API**：`POST /api/v1/agent/runs` 请求体移除 `clientContext` 字段（OpenAPI 已同步）。旧客户端仍发送该字段时后端忽略（Jackson 默认行为），向后兼容。

## 运行了哪些测试及结果

| 测试 | 结果 |
|---|---|
| agent-service `pytest -q` | 15 passed |
| 后端 `AgentRunIntegrationTest`（Docker Maven，H2） | 13/13 passed, 0 failures |
| Android Agent 单测（AgentViewModelTest + AgentScreenUiTest，Robolectric） | BUILD SUCCESSFUL |
| 在线冒烟（查询技能 / 年龄预览+取消 / 聊天） | 全部正常 |

说明：本地 Maven 运行的 jacoco:report 步骤因宿主机 `backend/target/jacoco.exec` 残留旧产物而报错（测试已全部通过），CI 干净环境不受影响。

## 仍存在什么限制

- 无已知限制。清理后的镜像已部署到本地 dev 容器（agent/backend），模拟器 App 为旧 APK 但完全兼容，下次构建即可带上 C 的 DTO 瘦身。

## 下一步安全且最小的工作

- （可选）在 CI 通过后提交本次清理（当前 agent 相关代码仍未提交，见各 change_report）。
- 与本清理无关：组员正在并行扩展 recruiter Agent（screen_applicants / schedule_interview 等），本报告未覆盖其内容。
