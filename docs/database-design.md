# 数据库设计草案

## 1. 设计约定

- 数据库：MySQL，字符集 `utf8mb4`。
- 表名和字段名使用 `snake_case`。
- 业务时间使用 UTC；至少包含 `created_at`、`updated_at`。
- 业务删除默认使用状态字段或 `deleted_at`，避免直接物理删除关键记录。
- 密码只保存安全哈希，不保存明文或可逆密文。
- 枚举值在代码与 OpenAPI 中统一定义，数据库可用字符串字段保存。
- 外键、唯一约束和索引必须由迁移脚本创建，不能只依赖应用校验。

## 2. 核心实体关系

```text
User 1 ── 0..1 CandidateProfile
User 1 ── 0..1 RecruiterProfile
User 1 ── 0..1 Resume
Company 1 ── N RecruiterProfile
Company 1 ── N Job
User(Candidate) 1 ── N Application
Resume 1 ── N Application
Job 1 ── N Application
Application 1 ── N ApplicationStatusHistory
User(Candidate) 1 ── N RecommendationEvent
User(Candidate) + Job 1 ── N RecommendationResult
MLModelVersion 1 ── N RecommendationResult
User 1 ── N AgentRun
AgentRun 1 ── N AgentStep
User(Admin) 1 ── N AuditLog
```

## 3. 表结构草案

### users

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | UUID/CHAR(36) | PK |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| display_name | VARCHAR(100) | NOT NULL |
| role | VARCHAR(32) | CANDIDATE/RECRUITER/ADMIN |
| status | VARCHAR(32) | PENDING/ACTIVE/DISABLED |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

### candidate_profiles

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| user_id | UUID/CHAR(36) | PK, FK users |
| headline | VARCHAR(200) | 可空 |
| location | VARCHAR(100) | 可空 |
| bio | TEXT | 可空 |
| phone | VARCHAR(32) | 可空，敏感字段 |
| version | INT | NOT NULL，乐观锁版本 |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

当前 OpenAPI MVP 投影只使用 `headline` 和 `location`；`bio`、`phone` 不进入本阶段 API。

### companies

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | UUID/CHAR(36) | PK |
| name | VARCHAR(200) | NOT NULL |
| description | TEXT | 可空 |
| website | VARCHAR(500) | 可空 |
| size_range | VARCHAR(50) | 可空 |
| status | VARCHAR(32) | PENDING/APPROVED/REJECTED/SUSPENDED |
| created_by | UUID/CHAR(36) | FK users |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

### recruiter_profiles

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| user_id | UUID/CHAR(36) | PK, FK users |
| company_id | UUID/CHAR(36) | FK companies, NOT NULL |
| job_title | VARCHAR(100) | 可空 |
| verified | BOOLEAN | NOT NULL DEFAULT FALSE |

### resumes

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | UUID/CHAR(36) | PK |
| candidate_id | UUID/CHAR(36) | FK users, NOT NULL |
| full_name | VARCHAR(100) | NOT NULL |
| age | INT | NOT NULL，16–100 |
| location | VARCHAR(100) | NOT NULL |
| headline | VARCHAR(200) | NOT NULL |
| summary | TEXT | NOT NULL |
| experiences_json | TEXT/JSON | OpenAPI Experience 数组，保持提交顺序 |
| version | INT | 乐观锁或业务版本 |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

MVP 每个求职者最多一份简历，由 `UNIQUE(candidate_id)` 强制保证。多简历、文件上传、教育和技能字段在契约确定前不加入正式表。

### jobs

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | UUID/CHAR(36) | PK |
| company_id | UUID/CHAR(36) | FK companies, NOT NULL |
| created_by | UUID/CHAR(36) | FK users, NOT NULL |
| title | VARCHAR(200) | NOT NULL |
| description | TEXT | NOT NULL |
| requirements | TEXT | NOT NULL |
| skills | JSON | 规范化技能数组 |
| location | VARCHAR(100) | NOT NULL |
| employment_type | VARCHAR(32) | FULL_TIME/PART_TIME/INTERNSHIP |
| work_mode | VARCHAR(32) | ONSITE/HYBRID/REMOTE |
| salary_min | DECIMAL(12,2) | 可空 |
| salary_max | DECIMAL(12,2) | 可空 |
| currency | CHAR(3) | 有薪资时必填 |
| status | VARCHAR(32) | DRAFT/PUBLISHED/CLOSED/REMOVED |
| published_at | DATETIME(6) | 可空 |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

### applications

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | UUID/CHAR(36) | PK |
| job_id | UUID/CHAR(36) | FK jobs, NOT NULL |
| candidate_id | UUID/CHAR(36) | FK users, NOT NULL |
| resume_id | UUID/CHAR(36) | FK resumes, NOT NULL |
| resume_snapshot_id | UUID/CHAR(36) | FK resume_snapshots, UNIQUE, NOT NULL |
| contact_email | VARCHAR(255) | 投递时使用的联系邮箱，NOT NULL |
| share_profile | BOOLEAN | 是否共享 Candidate Profile，NOT NULL |
| status | VARCHAR(32) | APPLIED/IN_REVIEW/INTERVIEW/REJECTED/WITHDRAWN |
| applied_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |
| version | INT | NOT NULL，初始值为 1 |

唯一约束：`UNIQUE(job_id, candidate_id)`。

### resume_snapshots

每次成功投递创建一行不可变快照，字段复制投递瞬间的完整 Resume：

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | UUID/CHAR(36) | PK，对外 `snapshotId` |
| resume_id | UUID/CHAR(36) | FK resumes, NOT NULL |
| candidate_id | UUID/CHAR(36) | FK users, NOT NULL |
| full_name | VARCHAR(100) | NOT NULL |
| age | INT | NOT NULL |
| location | VARCHAR(100) | NOT NULL |
| headline | VARCHAR(200) | NOT NULL |
| summary | TEXT | NOT NULL |
| experiences_json | TEXT/JSON | 保持 Resume Experience 提交顺序 |
| resume_version | INT | 投递瞬间的 Resume version，NOT NULL |
| resume_created_at | DATETIME(6) | 投递瞬间 Resume createdAt，NOT NULL |
| resume_updated_at | DATETIME(6) | 投递瞬间 Resume updatedAt，NOT NULL |
| captured_at | DATETIME(6) | NOT NULL |

快照不提供修改 API；后续 Resume 更新不得改变既有快照。

### application_status_events

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | BIGINT | PK |
| application_id | UUID/CHAR(36) | FK applications |
| from_status | VARCHAR(32) | 首次记录可空 |
| to_status | VARCHAR(32) | NOT NULL |
| changed_by | UUID/CHAR(36) | FK users |
| note | VARCHAR(500) | 可空 |
| changed_at | DATETIME(6) | NOT NULL |

首次记录使用 `from_status=NULL`、`to_status=APPLIED`，并与 Application、Resume Snapshot、
幂等结果和职位申请人数更新处于同一事务。

### recommendation_events

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | BIGINT | PK |
| candidate_id | UUID/CHAR(36) | FK users |
| job_id | UUID/CHAR(36) | FK jobs |
| event_type | VARCHAR(32) | IMPRESSION/VIEW/SAVE/APPLY/DISMISS |
| session_id | VARCHAR(100) | 可空，脱敏会话标识 |
| occurred_at | DATETIME(6) | NOT NULL |

事件只采集产品功能和模型训练真正需要的行为，并在隐私说明中解释用途。

### ml_model_versions

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | VARCHAR(100) | PK，模型版本 |
| algorithm | VARCHAR(100) | NOT NULL |
| dataset_version | VARCHAR(100) | NOT NULL |
| feature_version | VARCHAR(100) | NOT NULL |
| metrics | JSON | 独立测试集指标 |
| artifact_uri | VARCHAR(1000) | 模型产物位置，不保存密钥 |
| status | VARCHAR(32) | TRAINED/APPROVED/ACTIVE/ARCHIVED |
| created_at | DATETIME(6) | NOT NULL |

### recommendation_results

| 字段 | 类型建议 | 约束/说明 |
|---|---|---|
| id | UUID/CHAR(36) | PK |
| candidate_id | UUID/CHAR(36) | FK users |
| job_id | UUID/CHAR(36) | FK jobs |
| score | DECIMAL(5,2) | 0–100 |
| rank_position | INT | 推荐列表位置 |
| reasons | JSON | 推荐理由和解释特征 |
| model_version | VARCHAR(100) | FK ml_model_versions |
| request_id | VARCHAR(100) | 同批推荐请求标识 |
| inference_ms | INT | 可空 |
| generated_at | DATETIME(6) | NOT NULL |

离线训练数据应从行为事件构建快照，按时间或用户划分训练/验证/测试集，并记录数据版本。

### agent_runs / agent_steps

`agent_runs` 保存用户、目标类型/ID、状态、确认状态、最终输出摘要和时间；`agent_steps` 保存步骤序号、工具名、输入/输出摘要、状态、耗时和错误。不得记录密钥、密码或完整认证令牌。

### audit_logs

记录管理员审核、职位下架、账户禁用等高风险行为，包括操作者、动作、目标类型、目标 ID、原因、时间和必要的前后状态摘要。

## 4. 关键索引

- `users(email)` 唯一索引
- `jobs(status, published_at)`
- `jobs(company_id, status)`
- `applications(job_id, status)`
- `applications(candidate_id, submitted_at)`
- `application_status_history(application_id, changed_at)`
- `recommendation_events(candidate_id, occurred_at)`
- `recommendation_events(job_id, event_type, occurred_at)`
- `recommendation_results(candidate_id, request_id, rank_position)`
- `recommendation_results(model_version, generated_at)`
- `audit_logs(target_type, target_id, created_at)`

## 5. 事务边界

- 投递：校验职位与简历 → 创建 application → 创建首次历史，单事务。
- 修改申请状态：校验状态机 → 更新 application → 写历史，单事务。
- 设置默认简历：取消旧默认 → 设置新默认，单事务。
- 发布职位：校验招聘者、公司审核状态和字段完整性后更新状态。
