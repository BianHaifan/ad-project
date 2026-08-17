# 招聘者 AI 排序已投递求职者计划

## 目标

为招聘者提供“针对自己某个职位，对仍可处理的已投递求职者进行 AI 排序”的功能。使用当前 Python 模型的 `/internal/v1/recommend/candidates`，不训练、不修改模型、不创建全站 Talent Pool，也不向招聘者暴露未投递该职位的 Candidate。

## 产品与数据边界

- 候选集仅为当前招聘者公司、当前职位下状态为 `APPLIED`、`IN_REVIEW`、`INTERVIEW` 的申请；`WITHDRAWN` 与 `REJECTED` 不参与排序。
- 模型输入只来自该申请创建时已存在、招聘者本已可查看的 resume snapshot 与职位内容；不得读取未投递 Candidate 的资料、联系方式或实时私密数据。
- 返回卡片只包含申请跳转所需的安全摘要：申请 ID、Candidate ID、姓名、头像、headline、location、申请状态/时间、AI 分数、排名与匹配解释。邮箱和完整简历不在列表重复输出，详情页继续沿用既有权限。
- 不记录新的推荐快照、不新增表或 Flyway；每次只读查询即时计算。

## API 契约

新增只读接口：

`GET /api/v1/recruiter/jobs/{jobId}/applicant-recommendations?page=1&pageSize=20`

响应使用既有 `{ data, meta }` 风格：

- `data`: `RecommendedApplicant[]`，包含 `applicationId`、安全 Candidate 摘要、`status`、`appliedAt`、`matchScore`、`rank`、`matchAnalysis`。
- `meta`: `source`（`MODEL`/`FALLBACK`）、`modelStatus`（`ACTIVE`/`DEGRADED`）、`modelVersion`、`featureVersion`、`generatedAt`、`inferenceMs`、`page`、`pageSize`、`total`、`hasNext`。
- 候选集为空时返回 `200` 和空 `data`，不调用 ML。
- 未登录返回 401；Candidate 返回 403；不属于当前公司职位按现有招聘者资源策略返回 404；`page`/`pageSize` 保持现有 1–100 校验。
- 单次模型输入上限为 500 名候选人。超限时返回明确的 `422 RECOMMENDATION_INPUT_LIMIT`，不悄悄截断或伪造全量排序。

## 实施设计

### 1. 后端：输入装配与模型调用

- 在 `application` 模块新增只读 applicant-ranking service：验证 Recruiter 与职位公司归属，加载有效申请、职位和对应 resume snapshot，并输出一个不含 Repository 的输入 DTO 给 recommendation 模块。
- 扩展 `MlRecommendationClient`，增加 `recommendCandidates(job, candidates, limit)`，复用现有内部令牌、超时、响应 DTO 与 0–100 分数边界。
- 在 recommendation 模块新增可复用的 recruiter-ranking engine：调用当前模型的反向端点；模型不可用时采用确定性规则降级（职位技能/标题/地点/工作方式/岗位类型/薪资与 resume snapshot 比较），并返回 `FALLBACK/DEGRADED` 元数据。
- 保持模块边界：recommendation 不直接访问 application 的 Repository；application 不直接处理 Python HTTP 契约。

### 2. 后端：公开 API 与契约

- 在 application API 中新增 Controller 路由和专用 DTO，不修改既有 application list/detail 响应。
- 同步 `docs/openapi-v1.yaml`，标明 Recruiter own-company + own-job 权限、只读性质、分页、500 上限、模型降级语义和标准错误响应。

### 3. Web：招聘者申请页

- 在现有 Applications 页面中，仅在已选择某个职位时提供 `AI rank applicants` 操作；不在 Dashboard 或全站人才库增加入口。
- 新增 Web contract、HTTP client、repository/query hook，并做响应运行时校验。
- 结果区显示模型状态、模型版本/规则降级、排名、匹配分、简短强匹配与缺口；点击卡片或 `View application` 进入既有申请详情页。
- 完整处理 loading、empty、error、模型降级和分页/加载更多；不得用 mock 数据兜底伪装真实结果。

## 验收标准

- Recruiter 只能为自己公司、自己可管理的职位获得排序；Candidate、跨公司 Recruiter 与未登录请求均不能读取结果。
- 返回的所有 candidate 都确实向该职位投递且状态可处理；不得出现其他 Candidate、withdrawn 或 rejected application。
- 模型健康时返回 `MODEL/ACTIVE` 与非空 rank；ML 不可用时正常返回 `FALLBACK/DEGRADED`，原申请页仍可用。
- 结果按 `matchScore` 降序、稳定二级排序；分页不会重复或跳过条目。
- Web 页面展示真实接口数据，可进入既有 application detail。
- 后端新增成功、401、403、跨公司 404、空结果、500 上限、模型降级测试；Web 新增 client 与页面状态测试。

## 不在本包范围

- 全站人才库、Candidate discoverability/opt-in、主动联系未申请用户。
- Candidate Android、数据库迁移、ML 训练/特征/模型文件、Agent、Admin、Google Meet。
