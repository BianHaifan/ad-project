# AI Agent MVP 设计

## 1. MVP 闭环范围

Candidate 可以通过自然语言查询和维护本人默认简历中的年龄、Summary、Skills 和 Experience。查询立即返回；写操作生成字段级变更预览。

Agent 页面采用持久化多轮会话。普通问候和与求职、简历相关的无副作用交流返回
`CHAT`；查询和写入继续生成白名单工具计划。每个用户可以开启新会话，同一会话内的
用户指令、Agent 回复和 Run 状态由 Spring Boot 持久化，重新进入 Android 页面时恢复
最近会话。Planner 最多接收最近 20 条文本消息作为上下文，不接收 JWT、完整简历或
其他用户数据。

MVP 固定用例实现：

- 创建、查询、确认和取消本人 Agent Run；
- Python Agent 将自然语言转换为结构化计划；
- `get_my_resume` 只读工具；
- `read_resume_section` 查询 Summary、Skills 或 Experience；
- `preview_resume_patch` 为年龄、Summary、Skills 或 Experience 生成只读预览；
- `apply_resume_patch` 确认后写入工具；
- 确认有效期、一次性确认标识、幂等键和资源版本校验；
- Run/Step 持久化、结果和最小审计；
- Android 指令输入、计划、预览、确认、取消和结果页面。

只有 Run 所有者在预览有效期内明确确认后，才允许修改本人简历的 `age`、`summary`、`skills` 或 `experiences` 字段。未确认、过期、重复确认、版本冲突或取消后的 Run 不得修改 `resumes` 或其他业务表。

### 1.1 Recruiter Agent（HR 端）

Web Recruiter 复用同一组 `/api/v1/agent/*` 接口，控制器按角色分派到
`HrAgentRunService`。HR 端只做两件事：

- **筛选简历**：根据招聘者自己的一个岗位，对所有候选人简历纯 LLM 排序，
  输出带事实性理由的排名列表（无数值评分）；
- **安排面试**：复用现有 `InterviewService`，只安排 ONLINE 面试（自动开通
  Google Meet），支持调度、改期、取消，全部先预览后确认。

Recruiter Run 的状态机与 Candidate 相同（`PROCESSING` →
`AWAITING_CONFIRMATION` / `NEEDS_CLARIFICATION` / `NO_ACTION_REQUIRED` /
`FAILED` → `EXECUTING` → `COMPLETED`），确认同样有 15 分钟有效期、一次性
确认标识、Run 版本与幂等键校验。

## 2. 运行边界

```text
Android Candidate
  -> Spring Boot /api/v1/agent/runs
  -> 认证、角色、资源所有权、Run/Step 持久化
  -> Python /internal/v1/agent/plan
  -> 返回结构化计划，不携带用户 JWT，不访问数据库
  -> Spring Boot 执行白名单只读工具并生成预览
```

Spring Boot 是唯一对客户端公开的业务 API，也是唯一可以读取业务数据和执行工具的组件。Python Agent 使用 LangGraph 编排，只负责意图识别、缺失信息判断和结构化工具计划，不保存用户认证信息，不直接调用业务 Repository。

Recruiter 的筛选执行不走 Python：Spring Boot 汇集候选人简历后由后端 Java
DeepSeek 客户端直接调用 LLM 排序。Planner 阶段仍然先由 agent-service 的
Recruiter 系统提示把指令转换成白名单计划（`screen_applicants` 或面试工具），
Spring Boot 验证白名单后执行。

Planner 通过 DeepSeek Chat Completions API 使用
`deepseek-v4-pro` 生成严格 JSON 计划；密钥只从进程环境读取。模型输出必须再次通过
Pydantic 契约和工具参数白名单校验，非法计划不会下发执行；未配置密钥或供应商调用
失败时接口返回 503，Spring Boot 将 Run 保存为 `FAILED` 并返回安全错误。健康接口只
暴露模式、模型、最近一次计划来源和最近错误码，不暴露密钥、用户指令或供应商响应正文。

## 3. Run 状态

| 状态 | 含义 |
| --- | --- |
| `PROCESSING` | Spring Boot 正在请求计划或执行只读工具。 |
| `AWAITING_CONFIRMATION` | 已生成可确认的字段级预览；可以确认或取消。 |
| `NEEDS_CLARIFICATION` | 指令缺少年龄等必要信息，或不属于当前白名单能力。 |
| `NO_ACTION_REQUIRED` | 当前值已经等于目标值；不生成预览、不要求确认，也不执行写操作。 |
| `FAILED` | Planner、工具或持久化前置校验失败。 |
| `CANCELLED` | Run 所有者取消了尚未执行的操作。 |
| `EXECUTING` | 已通过一次性确认、过期和版本校验，正在调用写工具。 |
| `COMPLETED` | 写工具成功，结果与审计步骤已经保存。 |

确认状态使用 `NOT_REQUIRED`、`PENDING`、`CONFIRMED`、`CANCELLED`、`EXPIRED`。

## 4. Python Planner 契约

内部接口：`POST /internal/v1/agent/plan`。

请求只包含自然语言指令和当前会话最近 20 条用户/Agent 文本消息，
不包含 JWT、完整简历或其他用户数据。响应只能引用注册过的工具；普通聊天返回
`status=CHAT`、`intent=CHAT`、空操作列表：

```json
{
  "status": "READY",
  "intent": "UPDATE_RESUME",
  "target": "DEFAULT_RESUME",
  "operations": [
    {"tool": "get_my_resume", "arguments": {}},
    {"tool": "preview_resume_patch", "arguments": {"field": "age", "value": 28}}
  ],
  "message": "I can prepare an age change preview."
}
```

Planner 不确定目标字段、操作、Experience 选择器或新值时必须返回 `NEEDS_CLARIFICATION`，不得猜测。允许的操作为：查询 Summary/Skills/Experience；设置 Summary；增删改 Skills；增删改 Experience；设置 16 到 100 的年龄。

稳定请求示例：

- `查看我的简历 summary`
- `把 summary 修改为：Experienced backend engineer`
- `查看我的技能`
- `添加技能：Python、Java`
- `删除技能：Java`
- `把技能 Java 改为 Kotlin`
- `查看我的工作经历`
- `添加工作经历：职位=Engineer；公司=Acme；描述=Built APIs；开始时间=2024-01；结束时间=2025-06`
- `修改工作经历：目标=Engineer；描述=Built reliable APIs`
- `删除工作经历：目标=Engineer`

Experience 的 `目标` 可以是查询结果中的 `experienceId`、唯一职位、唯一公司或从 1 开始的列表序号。职位或公司匹配到多条记录时必须要求用户改用 ID 或序号，不得猜测。

## 5. Recruiter Agent（HR）工具与语义

Recruiter 白名单工具只有四个：

| 工具 | 语义 |
| --- | --- |
| `screen_applicants` | 对招聘者自己的岗位做纯 LLM 简历排序，只读，立即 `COMPLETED` |
| `schedule_interview` | 为筛选历史中的候选人创建 ONLINE 面试预览 |
| `reschedule_interview` | 修改本人公司已有面试的预览 |
| `cancel_interview` | 取消本人公司已有面试的预览 |

**岗位识别**：`jobId` 上下文优先；新会话首条 Run 可携带 `jobId`。否则 Planner
生成 `jobSelector`，由 Spring Boot 在招聘者自己公司的岗位中做大小写不敏感的
标题包含匹配；多个匹配返回 `NEEDS_CLARIFICATION`，无匹配或岗位不可见返回安全
错误。

**筛选**：Spring Boot 汇集全部候选人简历（池上限 30 条，不含 `age`，未投递的
候选人 `applicationStatus=null`）交给 Java DeepSeek 客户端排序。输出按
`applicationId` 索引：

```json
{
  "jobId": "job-uuid",
  "jobTitle": "Backend Engineer",
  "ranked": [
    {
      "candidateId": "candidate-uuid",
      "applicationId": "application-uuid",
      "fullName": "Ada Lovelace",
      "applicationStatus": "IN_REVIEW",
      "rank": 1,
      "strongMatches": ["Python"],
      "gaps": ["No Docker experience"],
      "recommendation": "One-sentence reason for recommending Ada."
    }
  ],
  "message": "…shortlist…"
}
```

不暴露数值评分，只给 `strongMatches[]`/`gaps[]` 事实性理由，前 3 名候选人另附
`recommendation` 一句话推荐理由（LLM 生成，仅基于简历与岗位要求）；`message` 必须包含
带 `applicationId` 的 shortlist，后续面试指令只能引用筛选历史中出现的
`applicationId`，绝不允许 LLM 凭空构造。

**面试**：复用 `InterviewService`，仅 ONLINE 模式（Google Meet 自动开通）。
预览字段为 `scheduledAt`、`timezone`、`durationMinutes`、`mode`、`status`；
时区从 `CreateRunRequest.timezone` 一路传递到计划参数和预览 JSON，服务端注入
`serverDate` 供 Planner 对齐日期。确认时重查面试版本，冲突返回 409；面试服务
的两次调用在 `REQUIRES_NEW` 事务中执行，业务异常不会污染 Run 的事务。

**失败语义**：DeepSeek 无降级；供应商调用失败将 Run 置为 `FAILED`。错误码只
使用 `no_api_key`、`timeout`、`http_XXX`、`network_error`、`invalid_response`、
`incomplete_response`、`missing_content` 等安全值；供应商响应正文与简历内容
绝不写入 Run 的 `message` 或 Step。

## 6. 对外 API

- `POST /api/v1/agent/runs`：Candidate 或 Recruiter 创建 Run，按角色分派；可携带本人已有 `conversationId` 继续会话，省略时创建新会话。Recruiter 首次 Run 可携带 `jobId` 上下文，并可携带客户端 `timezone`。
- `GET /api/v1/agent/conversations`：列出本人最近 30 段会话（按角色隔离），供客户端在新建会话后切回历史对话。
- `GET /api/v1/agent/runs/{runId}`：Run 所有者查询计划、步骤、预览、筛选结果或执行结果。
- `GET /api/v1/agent/conversations/recent`：恢复本人最近会话及其最近 50 个 Run。
- `GET /api/v1/agent/conversations/{conversationId}`：读取本人指定会话及其最近 50 个 Run。
- `DELETE /api/v1/agent/conversations/{conversationId}`：删除本人指定会话及其全部 Run（跨用户返回 404）。
- `POST /api/v1/agent/runs/{runId}/confirm`：Run 所有者携带一次性确认标识、Run 版本和 `Idempotency-Key` 执行预览（简历 patch 或面试变更）。确认被拒绝时响应可能是刷新后的 Run 而不是错误封装。
- `POST /api/v1/agent/runs/{runId}/cancel`：Run 所有者取消待确认或待澄清 Run。

Run 与会话都按用户隔离：他人 Run 返回 403，不存在的 Run 返回 404；他人会话返回 404。Admin 无 Agent 能力，调用返回 403。

## 7. 预览与审计

预览保存一次性确认标识、目标类型、目标 ID、`expectedVersion`、过期时间和字段差异。示例：

```json
{
  "confirmationId": "confirmation-uuid",
  "targetType": "RESUME",
  "targetId": "resume-uuid",
  "expectedVersion": 3,
  "expiresAt": "2026-08-15T10:15:00Z",
  "changes": [{"field": "age", "oldValue": 27, "newValue": 28}]
}
```

确认请求示例：

```http
POST /api/v1/agent/runs/{runId}/confirm
Idempotency-Key: client-generated-uuid
```

```json
{
  "confirmationId": "confirmation-uuid",
  "expectedRunVersion": 2
}
```

同一 Run 使用相同幂等键重试必须返回第一次完成结果，不得重复增加简历版本。不同幂等键重复确认、确认标识不匹配、Run 版本不匹配、预览过期和简历版本冲突返回 409。成功只修改预览中的 `age` 字段，并将简历版本增加一。

`agent_steps` 只保存工具名、资源 ID、版本、状态、错误码和耗时等摘要，不保存完整 JWT、密码、密钥或完整简历原文。

## 8. MVP 验收

- 合法的年龄、Summary、Skills 和 Experience 修改指令生成 `AWAITING_CONFIRMATION` 和正确字段差异；
- 问候等普通消息生成 `COMPLETED` 的聊天回复，不访问简历、不产生确认；
- 澄清后的下一条消息复用同一 `conversationId` 和历史上下文完成意图；
- 重新进入 Android Agent 页面恢复最近会话的多轮历史；
- Summary、Skills 和 Experience 查询返回 `COMPLETED`、`NOT_REQUIRED`，且不增加简历版本；
- 简历内容和版本在创建、查询、取消和未确认 Run 后保持不变；
- 确认后只修改 `age`，简历版本恰好增加一，Run 进入 `COMPLETED`；
- 相同幂等键重试返回原结果且不重复写入；
- 确认过期、标识错误、Run 版本错误、简历版本冲突和取消后确认均不得写入；
- 缺少年龄或不支持的指令返回 `NEEDS_CLARIFICATION`；
- 当前年龄已经等于目标年龄时返回 `NO_ACTION_REQUIRED`，明确提示无需修改，且不生成预览、不允许确认、不增加简历版本；
- 无简历、Planner 不可用等情况保存为 `FAILED` 并返回安全错误；
- Candidate 不能读取或取消其他 Candidate 的 Run；
- Step 审计不包含完整简历正文或认证令牌。

Recruiter 验收：

- 筛选指令只针对招聘者自己的岗位，返回按 `applicationId` 索引的排名列表与事实性理由，无数值评分，不写任何业务表；
- 未投递候选人 `applicationStatus=null` 且不出现在可安排面试的列表里；Web 端可“查看投递”并一键预填“安排第 N 名候选人面试”；
- `jobId` 缺失时按 `jobSelector` 匹配；多个匹配返回 `NEEDS_CLARIFICATION`；
- 面试调度、改期、取消均生成字段级预览，确认后只修改预览中的字段，面试版本恰好加一；
- 预览过期、确认标识错误、Run 版本错误、面试版本冲突、取消后确认均不得写入；
- DeepSeek 失败将 Run 置为 `FAILED`，只暴露安全错误码；Run message 与 Step 不含供应商响应正文或简历内容；
- Recruiter 不能读取或操作其他用户的 Run（403）；Admin 调用 Agent 接口返回 403；
- Web `/recruiter/agent` 支持 `?jobId=` 深链、会话侧栏、新建会话、筛选卡片、预览卡片与确认/取消。
