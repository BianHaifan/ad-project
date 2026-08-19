# 修改报告：Agent 固定 MVP 完整闭环

## 基本信息

- 时间：2026-08-18
- 目标：完成 `docs/agent-design.md` 冻结的 Agent MVP，包括计划、最小读取、字段级预览、明确确认、幂等执行、结果、审计和 Android 交互。
- 固定用例：Candidate 使用自然语言把本人默认简历的年龄修改为指定值。

## 完成内容

- Python LangGraph Planner 将中英文年龄修改指令转换为只包含注册工具的结构化计划；缺值或不支持指令返回澄清。
- Spring Boot 是唯一公开业务 API，并实现 Run 创建、查询、取消和确认执行。
- 白名单工具为 `get_my_resume`、`preview_resume_patch`、`apply_resume_patch`；Python 不接收 JWT、不访问 MySQL、不执行写操作。
- 写操作执行前校验 Run 所有权、Candidate 角色、一次性确认 ID、Run 版本、预览有效期、简历所有权、简历版本和值域。
- 确认接口要求 UUID `Idempotency-Key`；相同键重试返回原结果，不重复增加简历版本，不同键重复确认被拒绝。
- 成功执行只修改预览中的 `age` 字段，保留简历其他字段，简历版本恰好增加一。
- Run/Step 保存计划、工具、安全摘要、确认、结果、错误码和耗时，不保存完整 JWT 或完整简历正文。
- Android 增加“我的 → AI Agent”入口，以及指令输入、计划、字段差异、确认、取消、刷新、错误和完成结果状态。
- Android 模拟器默认 API 地址修正为契约规定的 `http://10.0.2.2:8080/api/v1/`。
- CI/CD 和 Docker Compose 增加 Agent Planner 测试、镜像构建、健康检查与后端内部依赖。

## API 变化

- `POST /api/v1/agent/runs`：创建并处理 Agent Run，返回预览、澄清或安全失败。
- `GET /api/v1/agent/runs/{runId}`：仅 Run 所有者查询步骤、预览和结果。
- `POST /api/v1/agent/runs/{runId}/cancel`：取消待确认或待澄清 Run。
- `POST /api/v1/agent/runs/{runId}/confirm`：携带确认 ID、Run 版本和 `Idempotency-Key` 执行预览。
- Agent 的 4 个操作已并入 OpenAPI、API Coverage 和中英文 API Catalog。新版主线自身新增的 Admin、推荐、收藏职位与 Google OAuth 契约仍存在 catalog/schema 不一致，见“测试与验证”。

## 数据库变化

- V27 新增 `agent_runs` 和 `agent_steps`。
- V28 为 `agent_runs` 增加 `confirmation_id`、`execution_idempotency_key`、`confirmed_at`、`completed_at`、`result_json`。
- V28 增加确认 ID 唯一约束，以及 `(user_id, execution_idempotency_key)` 唯一约束。
- Agent 迁移在合并新版主线后顺延至 V27/V28，未修改远端已有 V1–V26。

## 测试与验证

- `mvn clean test`：316 个测试，0 失败、0 错误；10 个 MySQL/Testcontainers 测试因 WSL 无法识别 Docker Desktop socket 跳过。
- `AgentRunIntegrationTest`：8/8 通过，覆盖未确认不写、确认成功、字段和版本、幂等重放、过期、版本冲突、取消、角色、所有权、非白名单计划、Planner 故障和安全审计。
- `mvn -B pmd:check`：通过。
- `pytest -q -s`：6/6 通过，覆盖中英文指令、澄清、不支持意图、Prompt injection 工具白名单和严格请求模型。
- `openapi-spec-validator docs/openapi-v1.yaml`：Agent 路径和 schema 已成功解析；全量校验仍被新版主线既有的缺失 `AdminUserResponse` 等 Admin schema 阻断。Coverage 也缺少新版主线的 24 个操作并保留 2 个旧 Admin operationId，非 Agent 合并引入。
- `gradlew assembleDebug lint test --no-daemon`：通过；Debug 140/140、Release 140/140，lint 通过，并生成 Debug APK。
- Agent Docker 镜像构建成功；临时容器 `/health` 返回 `UP` 后已移除。
- 全新 MySQL 8.4 容器从空库成功执行 21 条迁移并达到 schema version 24；`agent_runs`、`agent_steps`、确认字段和唯一约束均可用。
- 真实 HTTP 闭环：注册 Candidate、把 Profile 年龄设为 25、创建 v1 简历、创建 Run 得到 `25 → 28` 预览、确认后 Run 为 `COMPLETED`，简历变为 `28/v2`，并生成 1 条 Run 和 5 条 Step。

## 当前边界

- 本次完成的是 `docs/agent-design.md` 明确冻结的固定 MVP，当前仅支持默认简历 `age` 修改，不是通用聊天 Agent。
- Planner 是确定性的 LangGraph 安全流程，尚未接入外部 LLM；替换解析器时必须保持同一结构化契约和 Spring Boot 二次校验。
- 新版主线已经提供正式推荐、求职偏好和收藏职位业务服务，但本次仍严格保持冻结的单一 `age` 用例，没有在未冻结工具契约前扩大 Agent 权限。
- 仓库没有 Agent 页面对应的 Figma Frame；页面复用现有 Android Design System，已完成构建和状态测试，但未做 Figma 截图对比。

## 下一步安全且最小的工作

先修复新版主线 Admin/OpenAPI/Catalog 的既有契约不一致；随后为“职位推荐”或“收藏职位”冻结第二个 Agent 工具的输入、预览、确认、幂等和审计规则，再复用相同 Run 执行框架扩展。Python Planner 仍不得直接访问业务数据或业务 API。
