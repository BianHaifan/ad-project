# 修改报告：Agent 前五阶段基础能力

## 基本信息

- 时间：2026-08-15
- 目标：完成 Agent 开发的前五个阶段——架构与契约冻结、Agent Run 基础能力、Python 编排服务、`get_my_resume`、`preview_resume_patch`。
- 修改范围：`agent-service/`、`backend/` Agent 模块与 V27 迁移、Agent 契约文档、Docker/CI/CD 配置。
- 明确未实现：确认执行、简历写入、通用业务工具、Android/Web Agent UI。

## 完成内容

- 冻结 Agent 安全边界：Spring Boot 是唯一公开业务 API；Python 服务仅负责意图解析和计划生成，不接收 JWT、不访问数据库、不执行写操作。
- 新增 Agent Run API：创建、查询、取消；Run 与 Step 均持久化，并记录状态、工具摘要、预览与错误。
- 新增内部 Python LangGraph 编排服务，当前仅解析中英文“修改本人简历年龄”指令；缺少年龄时返回澄清，其他意图返回不支持。
- 实现白名单工具语义：
  - `get_my_resume`：由 Spring Boot 按当前 Candidate 身份和所有权读取本人简历。
  - `preview_resume_patch`：只生成 `age` 字段的旧值、新值、预期版本和过期时间，不修改简历。
- Spring Boot 对 Python 计划做二次校验：意图、目标、字段和值域必须匹配；非白名单工具直接拒绝。
- 有副作用的 Agent 操作尚未开放；取消 Run 是幂等的，不会触发业务写入。
- 新增 Docker 镜像、Compose 内网编排、CI Python 测试与 Agent 镜像构建/发布配置。

## API 变化

- 新增 `POST /api/v1/agent/runs`：Candidate 创建 Agent Run。
- 新增 `GET /api/v1/agent/runs/{runId}`：Run 所有者查询状态、步骤和预览。
- 新增 `POST /api/v1/agent/runs/{runId}/cancel`：Run 所有者取消 Run。
- 错误角色返回 403；跨用户 Run 返回 404；未登录由既有安全链返回 401。
- OpenAPI、API Coverage、中文/英文 API Catalog 已同步。

## 数据库变化

- 新增 Flyway V27：`agent_runs`、`agent_steps`。
- `agent_runs` 保存用户、指令、状态、确认状态、预览、过期时间、版本和错误。
- `agent_steps` 保存步骤顺序、类型、工具名、安全摘要、状态、耗时和错误。
- 未修改既有表和既有迁移。

## 测试与验证

- `mvn -q -Dtest=AgentRunIntegrationTest test`：4 个测试通过，覆盖预览生成、敏感信息不进入步骤摘要、角色/所有权、幂等取消、澄清、缺失简历、恶意非白名单工具拒绝及简历不变。
- `mvn -q test`：全量后端测试通过；共 81 个测试，77 个执行通过、4 个 MySQL/Testcontainers 测试因当前环境 Docker 不可用而跳过。
- `pytest -q -s`：Python Planner 6 个测试通过，覆盖中英文指令、澄清、不支持意图、工具白名单和严格请求模型。
- `python -m compileall`：通过。
- `openapi-spec-validator docs/openapi-v1.yaml`：通过。
- `git diff --check`：通过。

## 已知限制

- 当前 Planner 是受限的确定性 LangGraph 流程，尚未接入外部 LLM；这是首个安全垂直用例，不是通用聊天 Agent。
- 只支持预览 `age` 修改，不支持姓名、联系方式、经历等其他字段。
- 没有确认/拒绝 API，也不会执行简历更新；Run 不会进入写操作后的 `COMPLETED` 状态。
- 初始开发时未在真实 MySQL 环境执行 Agent 迁移集成测试；合并新版主线后将从空库验证 V1–V28。
- 尚未实现客户端 Agent 入口和交互页面。

## 下一步安全且最小的工作

- 实现第六阶段“明确确认后执行”：新增确认/拒绝契约，校验预览过期时间与 `expectedVersion`，并由 Spring Boot 复用正常简历服务完成一次受审计的写入；随后补齐冲突、过期、重复确认和取消后的回归测试。
