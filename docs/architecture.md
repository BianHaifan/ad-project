# 系统架构

## 1. 架构原则

- Spring Boot 是唯一对客户端公开的业务后端。
- Android 和 React 不得直接访问数据库、ML 服务或大模型供应商。
- 先采用模块化单体，避免 MVP 阶段拆分大量微服务。
- Python 仅承载自训练推荐模型的数据处理、训练、评估与推理，以及不接触业务数据的 Agent 结构化计划生成；两者都只通过内部 API 被 Spring Boot 调用，不承载认证或招聘业务写入。
- 核心业务在 ML/LLM 不可用时仍能运行。
- OpenAPI 是前后端接口契约的唯一来源。

## 2. 逻辑架构

```text
┌────────────────────┐     ┌────────────────────┐
│ Android Candidate  │     │ React Web          │
│ Kotlin + Compose   │     │ Recruiter + Admin  │
└─────────┬──────────┘     └─────────┬──────────┘
          └──────────────┬───────────┘
                         │ HTTPS / JSON
                ┌────────▼─────────┐
                │ Spring Boot API  │
                │ Auth + Business  │
                └───┬─────────┬────┘
                    │         │ internal HTTP
             ┌──────▼───┐ ┌───▼────────────┐
             │  MySQL   │ │ Python ML API  │
             └──────────┘ └────────────────┘
                    │ internal HTTP
          ┌─────────▼──────────────┐
          │ Python Agent Planner   │
          │ LangGraph + LLM adapter│
          └─────────┬──────────────┘
                    │
          Approved LLM Provider
```

## 3. 仓库结构建议

```text
adproject/
├── AGENTS.md
├── docs/
├── backend/                 # Java + Spring Boot
├── android/                 # Kotlin + Jetpack Compose
├── web/                     # React + TypeScript
├── ml-service/              # Python 推荐模型训练、评估与推理
├── agent-service/           # Python Agent 计划生成；不执行业务写操作
├── contracts/               # OpenAPI、共享 schema 和生成物
├── infra/                   # Docker Compose、部署配置
└── scripts/                 # 本地开发与验证脚本
```

初期可以只创建正在开发的目录，但目录职责不得混用。

## 4. Spring Boot 模块边界

推荐按业务能力组织，而不是按 controller/service/repository 全局分层：

```text
com.adproject
├── auth
├── user
├── company
├── resume
├── job
├── application
├── recommendation
├── agent
├── admin
└── common
```

每个模块内部可包含 `api`、`application`、`domain`、`infrastructure`。模块间通过公开服务或明确 DTO 交互，不得跨模块直接操作对方 Repository。

## 5. Android 架构

- 单 Activity + Jetpack Compose Navigation
- UI → ViewModel → Use case/Repository → Retrofit API
- ViewModel 暴露不可变 UI state
- 网络 DTO 与 UI model 分离
- 认证令牌统一由安全存储和拦截器管理
- 每个页面必须建模 Loading、Content、Empty、Error 状态
- 不在 Composable 内直接发网络请求或写业务规则

推荐按 feature 组织：

```text
android/app/src/main/java/.../
├── core/network
├── core/auth
├── core/designsystem
├── feature/auth
├── feature/jobs
├── feature/resume
├── feature/applications
└── feature/agent
```

## 6. React 架构

- TypeScript 严格模式
- 页面按 recruiter/admin feature 分区
- API 客户端从 OpenAPI 生成或集中封装
- 权限既在路由层处理，也必须由后端再次校验
- 表单使用共享 schema 校验，服务端错误映射到字段或页面提示
- 不在组件中拼接 SQL、后端 URL 或角色判断字符串

## 7. 数据和一致性

- MySQL 是用户、职位、简历、申请等业务事实的唯一数据源。
- 创建申请和写入首次状态历史必须在同一数据库事务内完成。
- 发布职位、修改申请状态等写操作需要服务端校验当前状态。
- 对外 ID 使用不可预测的 UUID 或等价标识；数据库内部可采用合适主键策略。
- 时间统一以 UTC 存储，持久化精度统一为微秒（`DATETIME(6)`），客户端按用户时区展示。

## 8. 推荐模型与 ML 服务边界

`ml-service` 包含离线训练与在线推理两部分。训练任务从经过脱敏和版本化的数据集中生成模型产物与评估报告；在线服务加载已批准模型，为指定求职者对候选职位排序。

内部推理接口只接收完成推荐所需的最小特征，不接收客户端 JWT，不直接访问业务数据库。

```text
POST /internal/v1/recommend
{
  "candidateFeatures": {...},
  "candidateJobs": [{...}],
  "limit": 20
}
```

返回 `jobId`、score、rank、reasons、modelVersion 和推理耗时。Spring Boot 负责权限、候选集生成、特征组装、缓存、持久化、行为采集和降级。

训练代码、特征定义、随机种子、数据版本、模型产物摘要和测试集指标必须可追踪。模型上线由配置指定版本，不能在请求期间临时训练。

## 9. Agent 安全边界

Agent 采用“Python 规划、Spring Boot 授权与执行”的内部服务模式，详细契约见 `docs/agent-design.md`。Python Agent Planner 只把自然语言转换为结构化计划，不对客户端公开、不接收客户端 JWT、不访问业务数据库，也不直接执行任何有副作用的业务操作。

- Agent 所有工具调用必须携带当前用户上下文并由服务端鉴权。
- 读取工具和写入工具分开注册。
- Agent 工具复用现有业务服务，例如 `get_my_resume`、`preview_resume_patch`、`apply_resume_patch`、`save_job`、`create_application`。
- “把简历年龄改成 28”应先生成字段级 patch 和预览，再由用户确认后调用写工具。
- 写入简历、收藏、投递、撤回、发送消息等操作需要显式用户确认和幂等键。
- 不允许模型自行构造数据库查询或绕过业务服务。
- 保存 Agent Run、步骤、工具名称、输入摘要、结果和错误。
- 日志不得保存密码、完整 JWT、API Key 或不必要的敏感简历原文。

Recruiter Agent 在同一组接口上按角色分派到 `HrAgentRunService`：

- 规划阶段由 agent-service 的 Recruiter 系统提示生成白名单计划，仅携带指令与历史，不接收岗位、简历等业务数据。
- 筛选执行由 Spring Boot 直接调用后端 Java DeepSeek 客户端：后端先汇集自己岗位的候选人简历池（上限 30 条、不含 `age`），再让模型输出按 `applicationId` 索引的排名与事实性理由。模型失败只保存安全错误码，供应商响应正文与简历内容绝不进入 Run message 或 Step。
- 面试工具复用 `InterviewService`，只允许 ONLINE 模式；调度/改期/取消全部先预览后确认，确认时重查面试版本并校验 15 分钟有效期与幂等键。
- Run 所有权按用户隔离：他人的 Run 返回 403，不存在的 Run 返回 404。

## 10. 配置与环境

- `local`、`test`、`production` 使用独立配置。
- 密钥只通过环境变量或密钥服务注入，不提交 Git。
- 提供 `.env.example`，只包含变量名和安全示例。
- 数据库迁移使用 Flyway 或 Liquibase，并在团队内统一一种。
- 本地集成环境使用 Docker Compose 启动 MySQL 和 ML 服务。
