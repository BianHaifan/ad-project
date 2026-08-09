# AD Project Candidate API v1

状态：Draft v1  
范围：Candidate Android 客户端  
Base URL：`https://api.example.com/api/v1`（开发环境地址待后端确定）

本文件是前后端第一版接口契约。对应的机器可读规范见 [`openapi-v1.yaml`](./openapi-v1.yaml)。招聘方、推荐算法管理、文件上传和推送服务不在本版范围内。

## 1. 通用约定

### 1.1 认证

除注册、登录和刷新 Token 外，接口都需要：

```http
Authorization: Bearer <accessToken>
```

- Access Token：JWT，建议有效期 2 小时。
- Refresh Token：建议有效期 30 天，只用于刷新 Token。
- Android 客户端应把 Token 保存到加密存储，不写入日志。

### 1.2 数据格式

- 请求和响应：`application/json; charset=utf-8`
- 字段命名：`camelCase`
- ID：后端生成的不透明字符串，客户端不得解析其格式。
- 时间：ISO 8601 UTC，例如 `2026-08-09T01:42:00Z`；“Today”“Yesterday”由客户端本地化。
- 金额：整数，单位由 `period` 指定；禁止使用 `"$42–68K"` 作为存储格式。
- 可空字段必须明确返回 `null`，不建议用空字符串代替。

### 1.3 成功响应

单对象：

```json
{
  "data": {}
}
```

分页列表：

```json
{
  "data": [],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 57,
    "hasNext": true
  }
}
```

### 1.4 错误响应

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "fieldErrors": {
      "email": "Invalid email address"
    },
    "requestId": "req_01J5..."
  }
}
```

通用状态码：

| HTTP | 含义 |
|---|---|
| `200` | 查询或更新成功 |
| `201` | 创建成功 |
| `204` | 成功且无响应体 |
| `400` | 请求格式或业务参数错误 |
| `401` | 未登录、Token 无效或过期 |
| `403` | 已登录但无权限 |
| `404` | 资源不存在 |
| `409` | 重复申请等资源冲突 |
| `422` | 字段校验失败 |
| `429` | 请求过于频繁 |
| `500` | 服务端异常 |

### 1.5 分页

- 职位和申请列表使用 `page`、`pageSize`，`page` 从 1 开始，默认 `pageSize=20`，最大 100。
- 消息历史使用游标分页：`before` 和 `limit`，避免新消息插入导致页码漂移。

### 1.6 幂等

提交申请必须携带唯一请求头：

```http
Idempotency-Key: <uuid>
```

相同用户、相同职位重复提交时，后端返回 `409 ALREADY_APPLIED`，不能生成第二条申请。

## 2. 接口总览

| 模块 | 方法 | Path | 说明 |
|---|---|---|---|
| Auth | POST | `/auth/register` | 注册 Candidate |
| Auth | POST | `/auth/login` | 邮箱密码登录 |
| Auth | POST | `/auth/refresh` | 刷新 Token |
| Auth | POST | `/auth/logout` | 退出当前会话 |
| Jobs | GET | `/jobs` | 搜索/筛选职位 |
| Jobs | GET | `/jobs/{jobId}` | 职位详情与匹配分析 |
| Applications | GET | `/candidate/applications` | 申请列表和分类计数 |
| Applications | POST | `/jobs/{jobId}/applications` | 提交职位申请 |
| Applications | GET | `/candidate/applications/{applicationId}` | 申请详情 |
| Messages | GET | `/candidate/conversations` | 会话列表 |
| Messages | GET | `/candidate/conversations/{conversationId}` | 会话上下文 |
| Messages | GET | `/candidate/conversations/{conversationId}/messages` | 消息历史 |
| Messages | POST | `/candidate/conversations/{conversationId}/messages` | 发送消息 |
| Messages | PUT | `/candidate/conversations/{conversationId}/read-state` | 更新已读位置 |
| Profile | GET | `/candidate/profile` | Candidate 资料和统计 |
| Profile | PATCH | `/candidate/profile` | 更新基础资料 |
| Resume | GET | `/candidate/resume` | 获取在线简历 |
| Resume | PUT | `/candidate/resume` | 保存完整在线简历 |
| Features | GET | `/features/learning` | Learning 功能状态 |

## 3. Auth

### 3.1 注册

`POST /auth/register`

请求：

```json
{
  "role": "CANDIDATE",
  "fullName": "Yan Bohao",
  "email": "bohao.yan@example.com",
  "password": "password123",
  "acceptedTermsVersion": "2026-08-01"
}
```

响应 `201`：

```json
{
  "data": {
    "user": {
      "id": "usr_001",
      "role": "CANDIDATE",
      "fullName": "Yan Bohao",
      "email": "bohao.yan@example.com"
    },
    "accessToken": "eyJ...",
    "refreshToken": "rt_...",
    "expiresIn": 7200
  }
}
```

可能错误：`EMAIL_ALREADY_REGISTERED`、`WEAK_PASSWORD`、`TERMS_NOT_ACCEPTED`。

### 3.2 登录

`POST /auth/login`

```json
{
  "email": "bohao.yan@example.com",
  "password": "password123"
}
```

响应与注册的 Token 部分一致。错误凭据统一返回 `401 INVALID_CREDENTIALS`，不要暴露邮箱是否存在。

### 3.3 刷新 Token

`POST /auth/refresh`

```json
{
  "refreshToken": "rt_..."
}
```

响应 `200` 返回新的 `accessToken` 和 `refreshToken`。旧 Refresh Token 立即失效。

### 3.4 退出登录

`POST /auth/logout`

```json
{
  "refreshToken": "rt_..."
}
```

成功返回 `204`。

## 4. Jobs

### 4.1 职位列表

`GET /jobs?q=AI&employmentType=FULL_TIME&category=AI_LLM&page=1&pageSize=20`

Query 参数均可选：

| 参数 | 类型 | 说明 |
|---|---|---|
| `q` | string | 标题、公司、技能关键词 |
| `employmentType` | enum | `FULL_TIME`、`INTERNSHIP`、`PART_TIME` |
| `category` | enum | `RECOMMENDED`、`AI_LLM`、`BACKEND`、`DATA` |
| `page` | integer | 默认 1 |
| `pageSize` | integer | 默认 20，最大 100 |

响应 `200`：

```json
{
  "data": [
    {
      "id": "job_001",
      "title": "AI Backend Engineer",
      "company": {
        "id": "company_001",
        "name": "Moonshot AI",
        "logoUrl": null,
        "stage": "SERIES_B",
        "employeeRange": "500_999"
      },
      "salary": {
        "min": 42000,
        "max": 68000,
        "currency": "CNY",
        "period": "MONTH"
      },
      "location": "Shanghai",
      "employmentType": "FULL_TIME",
      "workplaceType": "HYBRID",
      "skills": ["Python", "LLM", "K8s", "RAG"],
      "matchScore": 96,
      "recruiter": {
        "id": "usr_recruiter_001",
        "name": "Mia Chen",
        "role": "Hiring Manager",
        "avatarUrl": null
      },
      "publishedAt": "2026-08-08T03:00:00Z"
    }
  ],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 3,
    "hasNext": false
  }
}
```

### 4.2 职位详情

`GET /jobs/{jobId}`

响应 `200` 在职位列表字段基础上增加：

```json
{
  "data": {
    "id": "job_001",
    "title": "AI Backend Engineer",
    "company": {
      "id": "company_001",
      "name": "Moonshot AI",
      "logoUrl": null,
      "stage": "SERIES_B",
      "employeeRange": "500_999"
    },
    "salary": {
      "min": 42000,
      "max": 68000,
      "currency": "CNY",
      "period": "MONTH"
    },
    "location": "Shanghai",
    "employmentType": "FULL_TIME",
    "workplaceType": "HYBRID",
    "skills": ["Python", "LLM", "K8s", "RAG"],
    "matchScore": 96,
    "matchAnalysis": {
      "strongMatches": ["Python", "LLM / RAG", "FastAPI"],
      "gaps": ["Latency optimization evidence"]
    },
    "description": "Build production AI services for LLM and RAG products.",
    "requirements": ["2+ years", "Python / FastAPI", "Kubernetes"],
    "recruiter": {
      "id": "usr_recruiter_001",
      "name": "Mia Chen",
      "role": "Hiring Manager",
      "avatarUrl": null
    },
    "applicationState": "NOT_APPLIED",
    "isSaved": false
  }
}
```

`applicationState`：`NOT_APPLIED`、`APPLIED`、`IN_REVIEW`、`INTERVIEW`、`REJECTED`、`WITHDRAWN`。

## 5. Applications

### 5.1 申请列表

`GET /candidate/applications?status=ACTIVE&page=1&pageSize=20`

`status`：`ACTIVE`、`INTERVIEW`、`ARCHIVED`，不传表示全部。

```json
{
  "data": {
    "counts": {
      "active": 3,
      "interview": 1,
      "archived": 8
    },
    "items": [
      {
        "id": "app_001",
        "jobId": "job_001",
        "jobTitle": "AI Backend Engineer",
        "company": {
          "id": "company_001",
          "name": "Moonshot AI",
          "logoUrl": null
        },
        "status": "IN_REVIEW",
        "matchScore": 96,
        "appliedAt": "2026-08-09T01:42:00Z",
        "interviewAt": null,
        "timeline": [
          {"status": "APPLIED", "completed": true},
          {"status": "IN_REVIEW", "completed": true},
          {"status": "INTERVIEW", "completed": false}
        ]
      }
    ]
  },
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 3,
    "hasNext": false
  }
}
```

### 5.2 提交申请

`POST /jobs/{jobId}/applications`

请求头必须包含 `Idempotency-Key`。

```json
{
  "resumeId": "resume_001",
  "contactEmail": "bohao.yan@example.com",
  "shareProfile": true
}
```

响应 `201`：

```json
{
  "data": {
    "id": "app_001",
    "jobId": "job_001",
    "jobTitle": "AI Backend Engineer",
    "company": {
      "id": "company_001",
      "name": "Moonshot AI",
      "logoUrl": null
    },
    "status": "APPLIED",
    "matchScore": 96,
    "appliedAt": "2026-08-09T01:42:00Z",
    "interviewAt": null,
    "timeline": [
      {"status": "APPLIED", "completed": true, "occurredAt": "2026-08-09T01:42:00Z"},
      {"status": "IN_REVIEW", "completed": false, "occurredAt": null},
      {"status": "INTERVIEW", "completed": false, "occurredAt": null}
    ],
    "submittedAt": "2026-08-09T01:42:00Z",
    "resumeSnapshot": {
      "id": "resume_snapshot_001",
      "name": "Default resume snapshot"
    },
    "nextSteps": [
      {"type": "RECRUITER_REVIEW", "title": "Recruiter review", "description": "Moonshot AI reviews your resume snapshot."},
      {"type": "STATUS_UPDATE", "title": "Status update", "description": "Track every stage in My Applications."},
      {"type": "INTERVIEW_INVITATION", "title": "Interview invitation", "description": "We'll notify you if an interview is scheduled."}
    ]
  }
}
```

可能错误：`JOB_NOT_FOUND`、`JOB_CLOSED`、`RESUME_NOT_FOUND`、`ALREADY_APPLIED`。

### 5.3 申请详情

`GET /candidate/applications/{applicationId}`

返回提交申请响应中的完整申请对象，并可增加 `updatedAt`、`interview` 和完整时间线。

## 6. Messages

### 6.1 会话列表

`GET /candidate/conversations?page=1&pageSize=20`

```json
{
  "data": [
    {
      "id": "conversation_001",
      "participant": {
        "id": "usr_recruiter_001",
        "name": "Mia Chen",
        "avatarUrl": null,
        "role": "Hiring Manager",
        "companyName": "Moonshot AI",
        "online": true
      },
      "lastMessage": {
        "id": "msg_004",
        "body": "Great — I’ve sent the meeting details.",
        "sentAt": "2026-08-09T01:42:00Z",
        "senderType": "RECRUITER",
        "clientMessageId": null,
        "deliveryStatus": "DELIVERED"
      },
      "unreadCount": 2
    }
  ],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 5,
    "hasNext": false
  }
}
```

### 6.2 会话上下文

`GET /candidate/conversations/{conversationId}`

```json
{
  "data": {
    "id": "conversation_001",
    "participant": {
      "id": "usr_recruiter_001",
      "name": "Mia Chen",
      "avatarUrl": null,
      "role": "Hiring Manager",
      "companyName": "Moonshot AI",
      "online": true
    },
    "context": {
      "type": "INTERVIEW_INVITATION",
      "jobId": "job_001",
      "jobTitle": "AI Backend Engineer",
      "interviewAt": "2026-08-11T06:00:00Z",
      "interviewMode": "ONLINE"
    }
  }
}
```

### 6.3 消息历史

`GET /candidate/conversations/{conversationId}/messages?before=msg_100&limit=30`

不传 `before` 时返回最新一页，按 `sentAt` 升序排列。

```json
{
  "data": [
    {
      "id": "msg_001",
      "body": "Hi Bohao, we'd like to invite you to a 30-minute interview.",
      "senderType": "RECRUITER",
      "sentAt": "2026-08-09T01:36:00Z",
      "clientMessageId": null,
      "deliveryStatus": "DELIVERED"
    }
  ],
  "meta": {
    "nextCursor": null,
    "hasMore": false
  }
}
```

### 6.4 发送消息

`POST /candidate/conversations/{conversationId}/messages`

```json
{
  "body": "Tuesday at 2:00 PM works for me.",
  "clientMessageId": "8a17be72-8ace-4d9a-93c5-11c2718438dc"
}
```

响应 `201` 返回创建后的消息。`clientMessageId` 用于弱网重试去重，同一会话内必须唯一。

### 6.5 更新已读位置

`PUT /candidate/conversations/{conversationId}/read-state`

```json
{
  "lastReadMessageId": "msg_004"
}
```

成功返回 `204`。

## 7. Profile

### 7.1 获取资料

`GET /candidate/profile`

```json
{
  "data": {
    "id": "candidate_001",
    "fullName": "Yan Bohao",
    "email": "bohao.yan@example.com",
    "headline": "CS Student · AI Backend Engineer",
    "avatarUrl": null,
    "location": "Shanghai",
    "stats": {
      "chatCount": 14,
      "applicationCount": 28,
      "interviewCount": 6,
      "savedJobCount": 31
    },
    "updatedAt": "2026-08-09T01:00:00Z"
  }
}
```

页面里的工具入口名称和顺序属于前端 UI 配置，不需要由后端返回。

### 7.2 更新基础资料

`PATCH /candidate/profile`

只传需要更新的字段：

```json
{
  "fullName": "Yan Bohao",
  "headline": "CS Student · AI Backend Engineer",
  "location": "Shanghai"
}
```

响应 `200` 返回更新后的完整 Profile。

## 8. Resume

### 8.1 获取在线简历

`GET /candidate/resume`

```json
{
  "data": {
    "id": "resume_001",
    "fullName": "Yan Bohao",
    "age": 27,
    "location": "Shanghai",
    "headline": "CS Student · AI Backend Engineer",
    "summary": "Backend-focused CS student building RAG applications.",
    "experiences": [
      {
        "id": "experience_001",
        "title": "AI Engineering Intern",
        "company": "ByteLab",
        "description": "Implemented FastAPI services and vector-search experiments.",
        "startDate": "2025-06",
        "endDate": "2025-09"
      }
    ],
    "updatedAt": "2026-08-09T01:00:00Z"
  }
}
```

### 8.2 保存在线简历

`PUT /candidate/resume`

PUT 表示提交完整简历。请求体与 GET 的 `data` 一致，但新经历允许不传 `id`，且不传 `updatedAt`。

响应 `200` 返回保存后的完整 Resume。校验错误返回 `422 VALIDATION_ERROR`。

## 9. Learning 功能状态

`GET /features/learning`

```json
{
  "data": {
    "status": "COMING_SOON",
    "title": "Learning is not available yet",
    "description": "We're preparing personalized learning paths."
  }
}
```

`status`：`COMING_SOON`、`AVAILABLE`、`MAINTENANCE`。

## 10. 页面与接口映射

| Android 页面 | 使用接口 |
|---|---|
| 登录 | `POST /auth/login` |
| 注册 | `POST /auth/register` |
| Job Feed | `GET /jobs` |
| Learning | `GET /features/learning` |
| Messages | `GET /candidate/conversations` |
| Chat Detail | 会话上下文、消息历史、发送消息、更新已读位置 |
| Profile | `GET /candidate/profile` |
| My Application | `GET /candidate/applications` |
| Job Detail | `GET /jobs/{jobId}` |
| Apply Confirm | 职位详情 + Profile + Resume；不单独设计接口 |
| Application Submitted | 使用提交申请接口的 `201` 响应；无需再次请求 |
| Resume Edit | `GET/PUT /candidate/resume` |

登录页和注册页当前 Mock 中的默认账号只用于开发调试，不属于后端接口，也不能在正式包中预填真实密码。

## 11. v1 冻结项与待确认项

建议前后端现在冻结：

- Base path：`/api/v1`
- 上述资源路径和 HTTP 方法
- JSON 字段使用 `camelCase`
- 时间、分页、错误体和认证格式
- 核心状态枚举

后端启动开发前仍需产品/技术共同确认：

- 开发、测试、生产 Base URL
- Access/Refresh Token 实际有效期
- 是否需要邮箱验证码和忘记密码流程
- 简历是否支持多份；v1 暂按每个 Candidate 一份在线简历
- 消息实时更新使用 WebSocket、推送还是轮询
- 头像、公司 Logo、附件上传的对象存储方案
- 匹配度由推荐服务实时计算还是写入职位推荐结果
