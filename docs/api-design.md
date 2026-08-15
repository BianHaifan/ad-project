# API 设计规范

## 1. 通用约定

- 前缀：`/api/v1`
- 协议：HTTPS + JSON
- 认证：`Authorization: Bearer <access-token>`
- 时间：ISO 8601 UTC，例如 `2026-08-04T10:00:00Z`
- 分页参数：`page` 从 0 开始，`size` 默认 20，最大 100
- 排序参数：`sort=publishedAt,desc`
- 写操作校验失败返回结构化字段错误
- OpenAPI 文件是客户端与服务端的契约来源
- 客户端不得依赖数据库字段名或 JPA Entity 结构

## 2. 响应与错误格式

成功响应直接返回资源或分页对象；不再额外包装无意义的 `success=true`。

分页响应示例：

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

错误响应：

```json
{
  "code": "APPLICATION_ALREADY_EXISTS",
  "message": "You have already applied for this job.",
  "fieldErrors": [],
  "traceId": "01J..."
}
```

不得向客户端返回堆栈、SQL、内部类名或密钥。

## 3. HTTP 状态码

- `200` 查询或更新成功
- `201` 创建成功
- `204` 无响应体成功
- `400` 请求格式或业务参数错误
- `401` 未认证或令牌失效
- `403` 已认证但无权限
- `404` 资源不存在，或出于安全考虑不暴露资源存在性
- `409` 重复投递、非法状态流转、职位已关闭等冲突
- `422` 可选：复杂字段语义校验失败；团队若不用则统一 400
- `429` 频率限制
- `500` 未处理错误
- `503` 外部 ML/LLM 服务不可用

## 4. MVP 接口清单

### Authentication

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/register` | 注册 Candidate 或 Recruiter | Public |
| POST | `/auth/login` | 登录并返回令牌 | Public |
| POST | `/auth/refresh` | 刷新访问令牌 | Public with refresh token |
| POST | `/auth/logout` | 使刷新令牌失效 | Authenticated |
| GET | `/me` | 当前用户与角色 | Authenticated |

### Candidate Profile and Resume

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/candidate/profile` | 获取自己的资料 |
| PATCH | `/candidate/profile` | 部分更新自己的资料，使用 `expectedVersion` |
| GET | `/candidate/resume` | 获取自己的单份默认简历；不存在返回 404 |
| PUT | `/candidate/resume` | 创建或全量替换单份默认简历；首次使用 `expectedVersion=0` |

MVP 只支持一份结构化在线简历。多简历、文件上传和默认简历切换延期。

### Jobs

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/jobs` | 查询已发布职位 | Public/Authenticated |
| GET | `/jobs/{id}` | 职位详情 | Public/Authenticated |
| POST | `/recruiter/jobs` | 创建职位草稿 | Recruiter |
| GET | `/recruiter/jobs` | 自己公司的职位 | Recruiter |
| GET | `/recruiter/jobs/{id}` | 管理视角详情 | Owning Recruiter |
| PUT | `/recruiter/jobs/{id}` | 编辑草稿或可编辑职位 | Owning Recruiter |
| POST | `/recruiter/jobs/{id}/publish` | 发布职位 | Owning Recruiter |
| POST | `/recruiter/jobs/{id}/close` | 关闭职位 | Owning Recruiter |

### Applications

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/jobs/{jobId}/applications` | 使用指定简历投递 | Candidate |
| GET | `/candidate/applications` | 我的申请 | Candidate |
| GET | `/candidate/applications/{id}` | 我的申请详情和状态历史 | Owning Candidate |
| POST | `/candidate/applications/{id}/withdraw` | 撤回申请 | Owning Candidate |
| GET | `/recruiter/jobs/{jobId}/applications` | 职位申请人列表 | Owning Recruiter |
| GET | `/recruiter/applications/{id}` | 申请详情 | Owning Recruiter |
| PUT | `/recruiter/applications/{id}/status` | 更新申请状态 | Owning Recruiter |

投递请求：

```json
{
  "resumeId": "uuid",
  "idempotencyKey": "client-generated-uuid"
}
```

状态更新请求：

```json
{
  "status": "INTERVIEW",
  "note": "Technical interview recommended"
}
```

### Recommendation and Agent

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET/PUT | `/candidate/job-preferences` | 读取或保存结构化求职偏好；写入使用 `expectedVersion` | Candidate |
| GET | `/candidate/recommendations/jobs` | 获取模型生成的 Top-N 职位推荐；模型不可用时返回规则降级结果 | Candidate |
| POST | `/candidate/recommendation-events` | 记录允许采集的曝光/点击/忽略反馈 | Candidate |
| GET | `/jobs/{jobId}/match` | 获取推荐解释或职位详情匹配依据 | Candidate |
| POST | `/agent/runs` | 提交自然语言操作指令并创建 Agent Run | Authenticated |
| GET | `/agent/runs/{id}` | 查询自己的 Agent Run | Owning Candidate |
| POST | `/agent/runs/{id}/confirm` | 确认 Agent 提出的具体写操作 | Run Owner |
| POST | `/agent/runs/{id}/cancel` | 取消尚未执行的操作 | Run Owner |

推荐响应必须包含 `modelVersion`、`generatedAt` 和每个职位的 `matchScore/rank/matchAnalysis`。推荐不可用时 Spring Boot 返回明确的 fallback 标识及规则排序结果。

Agent 创建请求示例：

```json
{
  "instruction": "把我默认简历里的年龄改成28",
  "clientContext": {
    "screen": "RESUME_DETAIL",
    "resumeId": "uuid"
  }
}
```

Agent 不能使用通用“任意 API”工具。每项能力对应一个带 DTO、鉴权、校验和审计的白名单业务工具。

### Admin

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/reviews` | 待审核对象列表 |
| POST | `/admin/companies/{id}/approve` | 审核通过公司 |
| POST | `/admin/companies/{id}/reject` | 拒绝公司并记录原因 |
| POST | `/admin/jobs/{id}/remove` | 下架职位 |
| POST | `/admin/users/{id}/disable` | 禁用账户 |

## 5. API 变更规则

- 已被客户端使用的字段不得直接改名或改变语义。
- 增加可选字段通常向后兼容；删除字段、改类型、改枚举属于破坏性变更。
- 破坏性变更必须升级 API 版本或安排迁移周期。
- 每个新接口必须同时提交 OpenAPI、权限校验、成功测试和主要失败测试。
- 前端不得通过猜测补充后端尚未定义的字段。
