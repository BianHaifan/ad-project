# Implementation Plan: Candidate 收藏职位与职位筛选

## 目标

让已登录 Candidate 能收藏/取消收藏职位、在 Job 页回看已收藏职位，并在保持“Recommended for you”为默认列表的前提下，以真实后端查询筛选推荐职位。范围只覆盖 Spring Boot 的 Candidate/Job/Recommendation 适配层与 Kotlin Android；不修改 `ml-service`、Agent、Admin、招聘者网页端或既有职位状态机。

## 已确认的现状

- Android Job 页目前仅把标题关键词和单一 `employmentType` 传给 `GET /api/v1/candidate/recommendations/jobs`。
- 详情响应包含 `isSaved`，但 `CandidateJobQueryService` 固定返回 `false`；列表和推荐响应没有收藏状态。
- `candidate_saved_jobs` 表和收藏 API 尚不存在；当前最大迁移为 `V16`。

## 契约与数据决策

1. 新增 `candidate_saved_jobs`，以 `(candidate_id, job_id)` 作为唯一约束，并使用 `DATETIME(6)` UTC `created_at`；不修改已有业务表。
2. 新增仅 Candidate 可用的 API：
   - `PUT /api/v1/candidate/saved-jobs/{jobId}`：幂等收藏一个当前可浏览的职位，返回 `204`。
   - `DELETE /api/v1/candidate/saved-jobs/{jobId}`：幂等取消收藏，返回 `204`。
   - `GET /api/v1/candidate/saved-jobs?page&pageSize`：分页返回当前 Candidate 仍可浏览的收藏职位。
3. 向 `CandidateJobSummary` 和 `RecommendedJob` **追加** `isSaved` 字段，保留所有旧字段与响应结构；详情的 `isSaved` 改为真实值。
4. 扩展推荐查询（不改模型协议）为可选 `q`、`employmentType`、`workplaceType`、`location`、`minimumSalary`。这些条件在 Spring Boot 组装候选职位时生效，再调用原有模型/降级排序，保证分页总数、懒加载和筛选结果一致。
5. Android 默认仍为推荐列表。筛选以 Bottom Sheet 提供“职位类型、工作方式、地点、最低薪资”和清除操作；收藏视图仅显示当前用户的收藏，不作为新的底部主导航。

## 分包顺序

### 包 1：契约、迁移与收藏后端

- 新建 `V17__create_candidate_saved_jobs.sql`，并按现有实体/Repository 模式建立收藏实体与仓储。
- 新建 Candidate 收藏服务与控制器，校验认证、Candidate 角色、职位可浏览性及资源归属。
- 让普通职位列表、职位详情、推荐列表和已收藏列表返回真实 `isSaved`。
- 同步 `docs/openapi-v1.yaml`。

验收：未登录为 401；Recruiter 为 403；Candidate 无法收藏不存在或不可浏览职位为 404；重复收藏/取消收藏幂等；不同 Candidate 互不影响。

### 包 2：推荐筛选与 Android 数据层

- 扩展 Spring Boot 推荐适配层的筛选参数与分页元数据，禁止将筛选下沉到客户端已加载列表。
- 更新 Android Retrofit、契约模型与 Repository；新增收藏、取消收藏、已收藏分页请求。
- 更新 ViewModel，使筛选变化重置到第 1 页，并在保存/取消保存后同步卡片、详情与已收藏视图。

验收：筛选后的 `total/hasNext` 与请求条件一致；刷新或重新打开页面仍保留后端真实收藏状态；网络失败回滚乐观状态并展示可理解提示。

### 包 3：Android Job 页交互与验证

- 将现有类型标题栏收敛为筛选入口，使用可访问的筛选 Bottom Sheet；已选条件以可清除摘要展示。
- 在职位卡和详情页增加可点击、带语义标签的收藏按钮；详情页移除当前的 “Save unavailable”。
- 加入“Saved jobs”入口与 loading、empty、error、分页/重试状态。
- 补充后端集成测试、Android Repository/ViewModel 测试，并运行相关构建检查。

验收：320px 宽度下标签不挤压；保存状态不只依赖本地内存；筛选、清除、收藏、取消收藏和已收藏分页均可演示。

## 风险与边界

| 风险 | 处理方式 |
| --- | --- |
| `V17` 被其他未合并分支占用 | 实施前重新核对迁移目录；若被占用，只在确认后顺延编号，绝不改写已提交迁移。 |
| 触及推荐功能 | 只改变 Spring Boot 的职位候选集筛选与响应映射，不变更 Python 模型、训练数据、特征或内部 ML API。 |
| 收藏职位后来关闭 | 已收藏列表只显示仍可浏览职位；不在本包增加历史/下架职位的独立展示规则。 |
| 并行协作冲突 | 优先新增独立收藏文件；对 `CandidateRecommendationService`、OpenAPI、Android Job 页面采用小范围增量编辑。 |

## 完成标准

- OpenAPI、Flyway、Spring Boot 与 Android 契约同步。
- 通过相关后端成功/401/403/404/幂等测试，以及 Android Repository/ViewModel 测试。
- 运行 `backend` 相关测试、Android 单元测试/lint/assembleDebug；未运行项目必须在报告中明确说明。
- Claude 在 `change_report/` 写入一份合并报告，列明迁移编号、API、测试和未验证项。
