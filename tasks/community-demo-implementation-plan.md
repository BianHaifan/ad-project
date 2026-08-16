# 社区系统实施计划（演示版）

## 0. 定位与边界

社区在当前产品需求中属于 P2，不得阻塞招聘主链路、Admin、Google Meet、ML 或 Agent。此计划只在已有双端主流程稳定后实施，目标是完成一个可演示的真实社区闭环，而不是生产级社交平台。

### 本版必须具备

- Candidate Android 与 Recruiter Web 均可浏览按时间倒序的社区动态；
- 已登录的 Candidate、Recruiter 都可发布纯文本动态；
- 两端均可查看动态详情、点赞/取消点赞、阅读评论和发表纯文本评论；
- 点赞和评论数量真实来自后端，刷新后保持一致；
- 作者名称、头像、角色为公开投影；不显示邮箱、简历、公司审核状态或其他私有字段。

### 明确不做

- 图片、视频、文件、链接预览、话题、转发、收藏、关注、私信入口；
- 推荐流、热度排序、搜索、通知、未读数、置顶、草稿、编辑或删除；
- 举报、审核队列、反垃圾、屏蔽、管理员处置；
- 游客访问、公开 API、跨端数据库直连；
- 任何 ML、Agent、Google Meet/OAuth、Admin 模块改动。

## 1. 固定业务规则

1. 所有社区接口均要求登录；仅 `CANDIDATE` 与 `RECRUITER` 可使用。
2. 动态正文去除首尾空白后长度为 1–2,000 字符；评论为 1–500 字符；仅纯文本。
3. Feed 默认按 `createdAt DESC, id DESC` 分页，默认每页 20，最大 50；不引入算法排序。
4. 每个用户对同一动态最多一个 Like；重复点赞是幂等成功，取消不存在的 Like 也是幂等成功。
5. 评论不支持回复层级；正文、动态和评论一经发布不可编辑/删除。这是演示版的刻意限制，避免审核与审计语义缺失。
6. 作者展示字段固定为 `userId`、`fullName`、`avatarUrl`、`role`，Recruiter 额外可展示公司名称（若存在）。绝不复用含邮箱的 Auth/User DTO。
7. 时间 UTC 存储、`DATETIME(6)` 精度；客户端按本地时区显示。

## 2. API 契约

在 OpenAPI 评审通过后新增：

| 操作 | 路径 | 权限 | 说明 |
|---|---|---|---|
| 浏览 Feed | `GET /api/v1/community/posts?page&pageSize` | Candidate/Recruiter | 时间倒序分页 |
| 发布动态 | `POST /api/v1/community/posts` | Candidate/Recruiter | `{ body }` |
| 查看详情 | `GET /api/v1/community/posts/{postId}` | Candidate/Recruiter | 带当前用户点赞状态 |
| 点赞 | `PUT /api/v1/community/posts/{postId}/like` | Candidate/Recruiter | 幂等 |
| 取消点赞 | `DELETE /api/v1/community/posts/{postId}/like` | Candidate/Recruiter | 幂等 |
| 查看评论 | `GET /api/v1/community/posts/{postId}/comments?page&pageSize` | Candidate/Recruiter | 时间正序分页 |
| 发表评论 | `POST /api/v1/community/posts/{postId}/comments` | Candidate/Recruiter | `{ body }` |

所有请求和响应采用现有 envelope、错误码和分页格式。详情/列表 DTO 应包含 `likeCount`、`commentCount` 与 `likedByCurrentUser`，不得由客户端猜测。

## 3. 数据模型与迁移

新增模块：`backend/src/main/java/com/adproject/community/{api,application,domain,infrastructure}`。

建议数据表：

```text
community_posts
  id, author_id, body, created_at, updated_at

community_post_likes
  post_id, user_id, created_at
  unique(post_id, user_id)

community_comments
  id, post_id, author_id, body, created_at, updated_at
```

索引：

- `community_posts(created_at, id)`；
- `community_comments(post_id, created_at, id)`；
- Like 表主键或唯一索引 `(post_id, user_id)`。

禁止修改旧迁移。当前仓库已提交最高版本为 V10；实施前必须重新确认并与 Recruiter Profile/Admin 等并行工作协调，使用合并时的下一条可用 Flyway 编号。

## 4. 实施任务

### Task 0：范围、Figma、契约与迁移协调

**目标：** 先锁定页面设计来源与 API，避免 P2 功能破坏主线或发生迁移冲突。

**工作：**

- 阅读 `AGENTS.md`、产品/架构/测试文档与本计划；
- 检查 `git status --short` 和 `docs/openapi-v1.yaml` 是否有未提交改动；
- 取得负责人对“复用现有 Android/Web 视觉规范”的书面许可，或提供具体 Figma Frame；
- 更新 OpenAPI；若文档变更与他人重叠，停止并在报告中说明，不覆盖对方内容；
- 确认 Flyway 编号与并行模块边界。

**验收：** 契约列出 7 个端点、DTO、分页、权限、401/403/404/422；没有迁移编号冲突。

**依赖：** 无。

### Task 1：后端社区核心（Feed + 发帖）

**目标：** 建立真实持久化 Feed 与发帖闭环。

**工作：**

- 创建迁移、Entity、Repository、DTO、Service、Controller；
- 在 Service 中进行登录、角色、正文和分页边界校验；
- 以独立 Author DTO 组装展示数据，禁止暴露 Entity 或私有字段。

**验收：** Candidate 和 Recruiter 都能发布并在新 Feed 首位读到；未登录返回 401，错误角色 403，空白/超长内容 422。

**依赖：** Task 0。

### Task 2：后端详情、点赞与评论

**目标：** 完成单条动态互动闭环。

**工作：**

- 实现详情、Like/Unlike、评论列表和发表评论；
- 用数据库唯一约束及幂等处理防止重复 Like；
- 事务内维护或正确聚合 `likeCount`/`commentCount`，并确保不存在的 Post 返回 404。

**验收：** 两个账号交叉点赞/评论后计数正确；反复 Like/Unlike 不重复；评论分页稳定；无关账号不能绕过认证。

**依赖：** Task 1。

### Checkpoint A：后端与契约

- 新接口成功、未登录、错误角色、无效输入、错误资源和重复 Like 测试通过；
- Flyway 在空数据库成功迁移；
- 相关 Maven test/static check 通过；
- OpenAPI 与实现逐项核对。

### Task 3：Recruiter Web 社区页面

**目标：** Recruiter 在 React 端完成 Feed、发帖、详情、点赞和评论。

**工作：**

- 新增集中 HTTP Client、React Query hooks、模型与 `/recruiter/community`、`/recruiter/community/{postId}` 路由；
- 在 AppShell 增加 Community 导航项；
- Feed 实现分页、发布框、loading/empty/error/submitting；详情实现 Like 与评论列表/发表；
- 不使用 mock 数据定义 API。

**验收：** Web 可与 Android 账号看到同一数据；失败时保留未发送输入；按钮防重复提交。

**依赖：** Checkpoint A。

### Task 4：Candidate Android 社区 Feed 与发帖

**目标：** Candidate 在 Android 端完成真实 Feed 浏览与发布。

**工作：**

- 新增 `feature/community`、Retrofit API、Repository、ViewModel、UI state；
- 在现有底部导航或明确的入口加入 Community；
- 实现 Feed 分页/刷新和发布表单；页面处理 loading、empty、error、content、submitting。

**验收：** Android 发布后 Web Feed 刷新可见；离线/接口错误有可理解错误与重试。

**依赖：** Checkpoint A。

### Task 5：Candidate Android 动态详情、点赞与评论

**目标：** Android 完成互动并与 Web 完全互通。

**工作：**

- 实现 Post Detail、点赞、评论分页和发表评论；
- 使用后端 `likedByCurrentUser` 和计数，不在本地伪造最终状态；
- 失败时回滚乐观 UI 或刷新真实状态。

**验收：** Android 与 Web 交替点赞、评论、刷新后的状态一致；重复点击不会重复写入。

**依赖：** Task 4。

### Checkpoint B：双端演示验收

1. Candidate Android 登录并发布一条文本动态；
2. Recruiter Web 刷新 Feed，点赞和评论；
3. Candidate Android 打开详情，看到真实计数和评论，再取消点赞；
4. Web 刷新后状态一致；
5. 分别验证空 Feed、网络失败、超长文本、未登录和跨角色访问。

## 5. 修改边界

### 允许修改

- `backend/src/main/java/com/adproject/community/**`
- 新的 Flyway 迁移文件
- OpenAPI 及必要 API 覆盖文档（仅无冲突时）
- `web/src/api/**`、`web/src/pages/**`、`web/src/router/index.tsx`、`web/src/components/AppShell.tsx` 中与 Community 直接相关的最小改动
- `android/app/src/main/java/com/adproject/candidate/feature/community/**` 及导航/API/Repository 的最小接线
- 社区相关测试与 `change_report/community-*.md`

### 禁止修改

- Auth/JWT 的认证机制、数据库连接、现有 Job/Application/Interview 状态机；
- Admin、ML、Agent、Google Meet/OAuth、聊天附件的业务逻辑；
- `.env`、密钥、Token、真实账号资料；
- 既有迁移文件、他人未提交的 OpenAPI 改动、无关页面或测试。

## 6. 测试清单

| 层级 | 最低验证 |
|---|---|
| Backend | 发帖、Feed 排序/分页、详情、Like 幂等、评论、401、403、404、422、跨账号数据一致性 |
| Web | 发布、分页、Like、评论、loading/empty/error、提交失败保留输入的 Vitest |
| Android | Repository/ViewModel 与关键 Compose UI 测试；错误和刷新状态 |
| Build | Backend test/static check；Web lint/typecheck/test；Android unit test/lint/assembleDebug |
| 手测 | Checkpoint B 的 Android ↔ Web 双账号闭环 |

## 7. 风险与缓解

| 风险 | 缓解方式 |
|---|---|
| P2 功能占用主线时间 | 必须拆包；Checkpoint A 后才能进入双端 UI；主线回归失败立即停止。 |
| Flyway 冲突 | 实施前后都检查最新迁移编号，不预先占用 V11。 |
| 公开资料泄漏 | 专用 Author DTO，后端测试断言响应没有 email 等私有字段。 |
| 点赞并发重复 | DB 唯一约束 + 幂等 Service。 |
| 设计缺失 | 先取得复用现有视觉规范的明确许可，不自行扩展社交产品设计。 |

## 8. 交付要求

每个任务包完成后写独立 `change_report/community-package-N.md`，说明完成内容、修改文件、API/迁移、测试、限制与下一包。不得自行提交、推送或创建 PR，除非项目负责人明确要求。
