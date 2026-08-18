# 稳定性、职位、消息、Admin 与公司资料：可执行实施计划

> 实施状态（2026-08-18）：阶段 A、B、C、D、E 已完成代码实现和自动化验证；三角色人工端到端验收与截图核对尚待执行，因此暂不标记为完整交付完成。最终自动化结果见本次任务交付说明。

## 1. 当前基线与范围纠正

本计划以 2026-08-18 仓库实际状态为基线：

- 当前分支为 `main`，提交为 `df11a7c`，与 `origin/main` 一致，工作区干净；没有 `upstream` remote，也没有待保护的 README 修改。因此实施前只需重新执行只读基线检查，不执行 pull、reset、checkout 或 stash。
- Admin 已存在：后端位于 `backend/src/main/java/com/adproject/admin`，Web 位于 `web/src/pages/Admin*`，数据库基线为 `V22__create_admin_management.sql`。
- 当前最高 Flyway 版本为 V25。社区私信已经由 `V25__create_community_direct_conversations.sql`、`CommunityDirectMessageService` 和 Android/Web 独立私信页实现。本轮不得再创建第二套社区私信表。
- Candidate 公开公司详情接口、Android DTO/ViewModel/页面已经存在。本轮只补入口、资料完整性和 Admin 唯一编辑权，不重复创建公开详情能力。
- 原问题编号缺少 22；原计划包 D 中的“22”实际对应问题 21（Schedule Interview 弹窗）。
- 不修改 ML/Agent、Google OAuth/Meet 流程、真实密钥和社区审核后端；只删除 Web Admin Moderation 入口与页面。

## 2. 通用实施规则

每个阶段均按以下顺序完成，禁止先改前端 mock 再反推正式接口：

1. 先写失败测试或最小复现记录，确定真实错误码、状态和请求响应。
2. 若接口变化，先修改 `docs/openapi-v1.yaml`，再修改 Spring DTO/Service，最后修改 Web/Android 客户端。
3. 若数据库变化，只新增 V26 及后续迁移，不修改已经提交的 V1–V25。
4. 后端写操作必须保留角色、公司归属、资源所有权、版本和审计检查。
5. 每个页面覆盖 Loading、Empty、Error、Content、Submitting/Disabled；写请求不自动重试。
6. 每个阶段独立提交更容易回滚的变更；不要把 28 个问题压成一个提交。

每次开始工作前记录：

```bash
git status --short --branch
git rev-parse HEAD
find backend/src/main/resources/db/migration -maxdepth 1 -type f | sort -V | tail
```

基线必须满足：工作区状态已知、最高迁移版本已确认、没有其他任务正在修改同一模块。若不满足，先停止并解决文件归属，不覆盖已有改动。

## 3. 推荐交付拆分

| 阶段 | 覆盖问题 | 主要结果 | 是否改契约/数据库 |
| --- | --- | --- | --- |
| 0 | 全部 | 建立复现矩阵和固定测试账号 | 否 |
| A1 | 2、3、17 | 账号切换与提交状态稳定 | 否 |
| A2 | 23 | Candidate 最小问卷 | OpenAPI；通常无需迁移 |
| B1 | 1、11、12 | 职位创建日期与技能校验 | OpenAPI 语义、后端校验 |
| B2 | 7、9、14、15、24、25 | Android 职位页与入口 | 视联系人响应而定 |
| B3 | 16 | Android 筛选统一 | 可能调整查询参数 |
| C1 | 4 | Recruiter 发信失败修复 | 以复现结果为准 |
| C2 | 8、26、27 | 社区私信复用 Messages UI | 增加私信会话列表接口；不建新表 |
| C3 | 28 | 只读请求刷新策略 | 否 |
| D | 6、13、21 | Recruiter Web 布局与正式文案 | 否 |
| E1 | 5、10 | Admin UI 精简与统一分页 | 否 |
| E2 | 18、19、20 | 公司审核状态与 Recruiter 门禁 | OpenAPI + V26 |
| E3 | 29 | Admin 唯一公司编辑与演示数据 | OpenAPI；现有列足够时无需迁移 |

## 4. 阶段 0：固定复现环境

### 4.1 准备测试数据

在本地数据库准备并记录 ID，不在计划或日志中写密码：

- Admin A。
- Candidate A、Candidate B，分别有不同姓名、头像、Profile、Resume 和 Preference。
- Recruiter A，所属公司 APPROVED，至少一个公开职位和一个有效 Application。
- Recruiter B，所属公司 PENDING。
- Recruiter C，所属公司 REJECTED。
- 一个 location 非 Singapore、salaryMin 小于 3000、skills 超过 3 个、Description/Requirements 为多行长文本的职位。
- 一个社区帖子，作者与测试消息发送者不同。

优先扩展 `scripts/seed-local-demo.sql` 的演示数据；只使用明确的演示 ID，不更新未知公司记录。

### 4.2 建立问题矩阵

对每个问题记录：客户端、账号、入口、请求 URL、HTTP 状态、后端错误码、request ID、截图。尤其先固定以下四个复现：

- Candidate A 登出后进入注册页，表单和 Me Profile 是否仍为 A。
- Profile 保存成功后 `submitting` 是否保持 true。
- Recruiter A 在无 Google connection 时向其 Application Candidate 发消息的真实响应。
- Admin 将公司设为 PENDING/REJECTED 后，Recruiter Dashboard 与新建职位路由的真实响应。

停止条件：没有真实响应证据时，不假定消息失败由 Google OAuth 引起。

## 5. 阶段 A1：账号切换、表单复位和 Profile 保存

### 5.1 修复 Profile 永久 Saving（问题 3）

代码落点：

- `android/app/src/main/java/com/adproject/candidate/feature/profile/ProfileViewModels.kt`
- `android/app/src/test/java/com/adproject/candidate/ProfileResumeViewModelTest.kt`

操作：

1. 在 `CandidateProfileViewModel.save` 成功分支显式设置 `submitting = false`。
2. 保持服务端返回的新 Profile/version；成功后退出编辑态，失败后保留输入并恢复按钮。
3. 增加成功、422、409、协程取消/异常映射后的状态测试，断言按钮最终可再次操作。
4. 同查 `CandidateResumeViewModel`、`JobPreferenceViewModel` 的成功和失败分支，统一保证提交锁释放，但不扩大到无关页面重构。

### 5.2 清理 Auth 状态和上一账号资料（问题 2、17）

代码落点：

- `android/app/src/main/java/com/adproject/candidate/feature/auth/AuthViewModel.kt`
- `android/app/src/main/java/com/adproject/candidate/core/auth/SessionManager.kt`
- `android/app/src/main/java/com/adproject/candidate/core/network/CandidateAppContainer.kt`
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`
- Auth/Profile/Repository 对应单测

操作：

1. 给 `AuthViewModel` 增加明确的 `resetSignIn`、`resetRegister`、`resetOnboarding`；登录/注册成功不能只执行 `Unit`，至少释放 submitting，并在会话切换后清空敏感字段。
2. 切换 Sign in/Register 页面时清空另一页密码、错误和提交锁；是否保留邮箱必须统一，建议登出和换账号时全部清空。
3. 登出无论服务端成功、失败或网络异常，都先/最终清除本地 token；现有 `RealAuthRepository.logout` 行为保留并补测试。
4. 监听 `SessionManager` 的 userId 变化。userId 从 A→null、null→B 或 A→B 时，清空 Profile/Resume/Preference/Applications/Messages 的内存状态，并用清栈导航进入 Auth 或 Onboarding，不能只 pop 一层。
5. 若 Repository 后续引入缓存，缓存 key 必须包含当前 userId；匿名状态不得返回上次成功值。
6. 增加 A→logout→B、A→token 失效→B、注册失败→重试、注册成功→Onboarding 四组回归测试。

验收：B 登录后任何时刻都看不到 A 的姓名、Profile、Resume、Preference、消息或申请；Create 按钮不会停在 `Creating account…`。

## 6. 阶段 A2：Candidate 首次问卷（问题 23）

代码落点：

- `docs/openapi-v1.yaml` 的 `CandidateOnboardingRequest`
- `backend/src/main/java/com/adproject/onboarding/application/CandidateOnboardingService.java`
- `backend/src/test/java/com/adproject/onboarding/CandidateOnboardingIntegrationTest.java`
- `android/.../feature/auth/AuthViewModel.kt`、Onboarding Compose 页面和 UI 测试

先确认产品字段：当前注册已经提供 fullName，问卷无需重复要求姓名。最低必填建议保持 headline、location、age、至少一项 skills、desiredTitle、preferredLocation、workplaceType、employmentType；`resumeSummary` 改为可空/可省略。

操作：

1. OpenAPI 将 `resumeSummary` 从 required 移除，并声明缺失时创建空 Summary 的最小默认 Resume。
2. Spring DTO 和 Service 接受 null/空 Summary，仍在一个事务内创建 Profile、默认 Resume、Preference；不可因可选字段缺失返回 500。
3. Android 移除 Summary 必填校验，标记 Optional；年龄、工作方式、职位类型使用现有结构化选择器，技能继续使用多选/可新增输入。
4. 请求失败保留用户已填值并恢复提交按钮；未完成 Onboarding 再登录仍进入该流程。
5. 后端测试完整请求、最小请求、缺必填字段、重复完成、错误角色；Android 测试最小表单可提交及可选项为空。

## 7. 阶段 B1：职位表单日期和技能（问题 1、11、12）

代码落点：

- `web/src/pages/JobFormPage.tsx`
- `web/src/api/jobHttpClient.ts` 及测试
- `backend/src/main/java/com/adproject/job/application/JobService.java`
- `backend/src/test/java/com/adproject/job/RecruiterJobIntegrationTest.java`
- `docs/openapi-v1.yaml`

操作：

1. 将 `blank.skills` 从 Java/Spring Boot/MySQL 改为空数组；编辑职位仅回填服务端真实 skills。
2. 不依赖浏览器原生 `input[type=date]` 的本地化占位。改为受控的 `YYYY-MM-DD` 输入/日期弹层，页面显示 `Valid through 23:59:59 Asia/Singapore`。
3. 新建一个纯函数把所选日期的 Asia/Singapore 当日结束转换为 UTC ISO；禁止继续散落硬编码 `T15:59:59Z`。为夏令时无关但跨年/月底/当天增加测试。
4. UI 的最小可选日为新加坡当前日期；输入格式错误、过去日期显示字段级错误。
5. 后端使用注入的 `Clock` 校验 create、update 和 publish。deadline 为空可保存；非空必须晚于当前时刻。更新其他字段时也不能让已过期 draft 绕过 publish 校验。
6. skills 在前后端统一 trim、忽略大小写判重、拒绝空值并限制数量/单项长度；限制值先写入 OpenAPI。
7. 集成测试覆盖：今天、昨天、明天、null、无 skills、重复 skills、编辑已有职位、发布时已过期。

停止条件：UI 禁选不能代替后端校验；后端校验完成前不得宣称问题 11 已解决。

## 8. 阶段 B2：Android 职位可读性和导航（问题 7、9、14、15、24、25）

代码落点：

- `android/.../feature/jobs/MainScreens.kt`
- `android/.../feature/jobs/JobDetailScreen.kt`
- `android/.../feature/applications/RealApplicationTrackingScreens.kt`
- `android/.../AdCandidateApp.kt`
- `backend/.../job/application/CandidateJobQueryService.java`
- `backend/src/test/java/com/adproject/job/CandidateJobIntegrationTest.java`

操作：

1. 删除 mock/生产 UI 中的 `.monthly`、`NC.Recommended#` 等内部文案；薪资周期用正常文案 `per month`，推荐标签只显示用户可理解的名称。
2. `JobDetailScreen` 移除 `skills.take(3)`，使用可换行 FlowRow 展示全部技能；极多 skills 时可折叠，但默认至少显示全部并提供明确展开按钮。
3. Description 与 Requirements 使用两个独立区块；保留服务端段落换行。AI gap/location/workplace 改为逐项行或可换行文本，所有长字符串使用正常 wrap，不用固定单行高度。
4. My Applications 顶栏把文字 Refresh 换为已有 `R.raw.hirex_refresh` 图标，加载中禁用，提供 `contentDescription = "Refresh applications"`。
5. HR 卡片：头像/姓名区域进入 Recruiter Profile；主按钮改为 Message；公司 Logo/名称进入现有 `Route.companyProfile(companyId)`。
6. 对 `CandidateJobQueryService.toRecruiterContact` 写集成测试，分别覆盖 ownerId、createdBy、缺 RecruiterProfile、错误角色/脏数据。有效 Recruiter 即使没有 profile title 也应返回姓名和头像；只有用户不存在或角色错误才为 null。
7. Android 对真正的 null 显示 `Recruiter details unavailable` 和 Retry/返回，不在每张有效职位卡固定显示 unavailable。
8. UI 测试加入 4+ skills、多行 description/requirements、超长公司名、缺头像和完整联系人。

说明：公开公司详情 API、DTO、ViewModel 和页面已存在，本阶段只连通职位详情入口和核对公开字段，不新建重复页面。

## 9. 阶段 B3：Android 职位筛选（问题 16）

先核对 Candidate Job API 当前 location/salary 参数是精确匹配、模糊匹配还是最低工资比较；以 OpenAPI 为准统一语义。

操作：

1. Jobs filter、Profile、Preference 中的 location 都改为可输入文本；Preference 可继续保存多个地点，但不能限制为 Singapore/Remote。
2. Employment type、Workplace type、经验/排序等有限枚举用可清空下拉，第一项为 Any。
3. Salary 下拉包含 Any、低于 3000 的档位以及当前档位；发送明确的 `salaryMin`，不把显示文本发给 API。
4. Apply 才提交筛选；Cancel 不污染已生效筛选；Clear 恢复 Any/空文本。
5. 后端测试 location 大小写/部分匹配、salary 边界、组合筛选和非法参数；Android ViewModel 测试 query mapping，UI 测试非新加坡地点。

## 10. 阶段 C1：Recruiter → Candidate 发信（问题 4）

代码落点：

- `backend/.../conversation/application/ConversationService.java`
- `backend/.../conversation/application/ConversationProvisioningService.java`
- `backend/src/test/java/com/adproject/conversation/ConversationIntegrationTest.java`
- `web/src/api/conversationHttpClient.ts`、`web/src/pages/MessagesPage.tsx` 及测试

操作：

1. 用阶段 0 的 APPROVED Recruiter + 有效 Application 复现，记录 response status/error code/request ID；确认失败发生在获取会话还是发送消息。
2. 增加一个先失败的集成测试，明确 Recruiter 未绑定 Google 时仍可打开该 Application conversation 并发送。
3. 检查 recruiter userId、company membership、job company、application ownership 和 conversation provisioning；只修复错误的关联或请求格式，不删除所有权检查。
4. 保留 rejected/withdrawn 会话的既定只读规则和消息幂等策略；若当前接口没有幂等键，本轮只在契约明确后增加，不能前端静默重发写请求。
5. Web 将安全错误码映射为可操作提示；失败后恢复输入和 Send 按钮，成功后只更新目标会话和列表摘要。
6. 测试 401、错误角色、非本公司 Recruiter、无 Application、有效 Application、无 Google connection、终止状态和重复请求。

## 11. 阶段 C2：社区私信复用 Messages（问题 8、26、27）

当前 V25 已有独立社区会话数据模型，最小安全方案是复用同一 Messages UI 与导航，而不是迁移/伪装成 Application conversation。

代码落点：

- `backend/.../community/application/CommunityDirectMessageService.java`
- `docs/openapi-v1.yaml`
- `android/.../feature/community/CommunityDirectMessage.kt`
- `android/.../feature/messages/MessagesScreens.kt`
- `android/.../AdCandidateApp.kt`
- Community 与 Messages 的现有测试

操作：

1. 在 OpenAPI/后端增加 `GET /api/v1/community/direct-conversations`，仅返回当前用户参与的会话；为 repository 增加 participant A/B 的分页查询，避免全表读取。V25 现有索引可先复用，只有执行计划证明不足才新增 V27 索引。
2. 把 `ChatScreen` 的消息列表、本人/对方气泡、输入栏、错误和返回处理抽成共享 UI；Application 和 Community ViewModel 分别提供数据适配，不混用权限模型。
3. 删除 `CommunityDirectMessageScreen` 的整页重复布局；`Message author` 创建/取得会话后导航到 Messages 下的 community thread route。
4. Messages 列表同时呈现 Application 和 Community Direct 会话，条目标明来源；打开后仍调用各自后端 API。
5. 本人消息气泡只占右侧合理最大宽度（建议屏宽 75%），对方消息靠左；长文本换行。
6. 使用 `navController.popBackStack()` 返回实际来源，不硬编码 `navigateBack(Route.Community)`；从帖子进入后一次返回帖子，从 Messages 进入后一次返回会话列表。
7. 帖子详情 Like 与 Message author 放同一 FlowRow/操作行；窄屏允许换行但保持视觉分组。
8. 发帖 Category 使用单选下拉，默认值、错误恢复和编辑状态一致；后端枚举不变。
9. 测试私信列表只含本人、他人访问 404/403、不能给自己发信、消息气泡方向、一次返回、进程重建后的 route 恢复。

## 12. 阶段 C3：刷新与短暂 unavailable（问题 28）

先从复现矩阵统计发生在 DNS、连接、401 refresh、5xx 还是空数据映射。不要用重试掩盖契约解析错误。

操作：

1. 只对 GET/HEAD 和明确幂等的读取增加最多一次短退避重试（建议 300–500 ms + jitter），仅覆盖连接错误、超时和 502/503/504。
2. 401 只走现有 token refresh 一次；refresh 失败立即登出，禁止循环。
3. 4xx、JSON/schema 解析错误不重试；POST/PUT/PATCH 消息、Like、发帖、申请等不自动重试。
4. Android 恢复前台时只刷新超过 freshness window 的数据；手动刷新始终可用；成功写入后精确失效相关列表。
5. UI 在重试期间保持已有内容并显示 refreshing；最终失败才显示 unavailable + Retry。
6. 用 fake clock/fake API 测试成功、一次失败后成功、连续失败、401、写请求不重试。

## 13. 阶段 D：Recruiter Web 体验（问题 6、13、21）

代码落点：

- `web/src/pages/ProfilePage.tsx`、对应 CSS 和测试
- `web/src/pages/ApplicationsPage.tsx`、`AiRankApplicants.tsx` 及测试
- Schedule Interview 所在页面/组件及 `ApplicationPages.test.tsx`

操作：

1. Avatar 选择后用 `URL.createObjectURL(file)` 作为 `<img alt="Avatar preview">` 的 src；更换文件和卸载时 `URL.revokeObjectURL`。上传成功切到服务器 URL，失败保留本地预览与重试。
2. Profile 分成 Avatar、Personal profile、Company、Google integration、Actions 区块。公司资料先显示只读，为 E3 的 Admin 唯一编辑权做准备。
3. 头像按钮容器使用 flex-wrap/移动端单列；320、768、1440 px 无水平滚动，不允许三个操作按钮被强塞在同一行。
4. 删除用户可见 `Demo`/`Demo notice`；如果 AI 为降级结果，显示真实的 unavailable/fallback 状态，不伪装成模型成功。
5. Schedule Interview dialog 设置可控 max-width、`max-height` 和滚动 body；表单 grid 在窄屏降为一列，input/textarea/select 设置 `min-width: 0`、`max-width: 100%`，错误文本可换行。
6. Web 测试 object URL 生命周期、上传成功/失败、Demo 文案不存在、dialog 320 px 不溢出和提交禁用。

## 14. 阶段 E1：Admin UI 精简与分页（问题 5、10）

### 14.1 删除 Moderation UI

删除/修改：

- 删除 `web/src/pages/AdminModerationPage.tsx`。
- 从 `web/src/router/index.tsx`、Admin 导航和 `RouteTitle` 移除 `/admin/moderation`。
- 删除仅被该页面使用的 query/client/type 和前端测试；若其他页面仍用则保留共享代码。
- 后端 `admin` moderation controller/service/table/audit 暂时保留，不做数据库删除迁移。

测试旧 URL 返回 Not Found/重定向，导航中不存在 Moderation，Admin 其他页面仍可打开。

### 14.2 统一分页

提取共享 `AdminPagination`，由 Accounts、Company reviews、Audit log 使用。计算规则：

- `totalPages = total === 0 ? 0 : ceil(total / pageSize)`
- `start = total === 0 ? 0 : (page - 1) * pageSize + 1`
- `end = min(page * pageSize, total)`
- 显示 `Showing X–Y of Z`、`Page P of N`、`20 per page`。

筛选/搜索变化回到第 1 页；空列表显示 `Showing 0–0 of 0`；删除/操作导致当前页越界时退到最后有效页；Previous/Next 在边界禁用。为 total=0、1、20、21、最后一页和过滤重置写组件测试。

## 15. 阶段 E2：审核状态、Dashboard 和职位门禁（问题 18、19、20）

代码落点：

- `backend/.../company/domain/CompanyVerificationStatus.java`
- `backend/.../admin/application/AdminService.java`
- `backend/.../dashboard`、`backend/.../job/application/JobService.java`
- `web/src/pages/AdminCompaniesPage.tsx`、`DashboardPage.tsx`、`JobFormPage.tsx`
- `docs/openapi-v1.yaml`
- 新迁移 `V26__remove_changes_requested_company_status.sql`

操作：

1. 迁移先统计并把现有 `CHANGES_REQUESTED` 更新为 `REJECTED`；若数据库存在枚举/check constraint，同一迁移同步约束。迁移必须可在包含旧状态和不包含旧状态的测试库运行。
2. 从 Java enum、OpenAPI enum、Web model/filter/action 移除 CHANGES_REQUESTED；Admin 审核只接受 APPROVE/REJECT。
3. 删除 `CompanyEntity.updateProfile` 中 CHANGES_REQUESTED→PENDING 的隐式状态回退。
4. Dashboard 查询对 PENDING/REJECTED 返回正常 200 和 company status；指标无权限时返回可定义的空/只读结构，不抛通用 500。
5. Recruiter 路由守卫在渲染 JobForm 前读取 Dashboard/company status。非 APPROVED 显示明确 blocked state 和返回 Dashboard/Profile 的入口，不加载/提交职位表单。
6. 后端 create、update、publish 均执行 APPROVED 检查。当前代码只明确在 publish 检查，必须补 create/update，避免绕过 UI 保存 draft。
7. REJECTED 为本轮最终状态，不提供重新提交按钮；页面解释需要新公司审核时联系 Admin，而不是表现为网络错误。
8. 测试 PENDING/APPROVED/REJECTED 三态的 Dashboard、直接访问 `/recruiter/jobs/new`、直接调用 create/update/publish、错误角色和旧状态迁移。

## 16. 阶段 E3：Admin 唯一编辑公司资料与演示数据（问题 29）

现有 `companies` 表已经有 logo、stage、employee_range、website、description、location、version，无需为相同字段新增列；公开公司详情 API 和 Android 页面也已存在。

操作：

1. OpenAPI 新增 Admin company update request：可编辑 name/logoUrl/website/stage/employeeRange/location/description，必填 `expectedVersion` 和 `reason`。
2. 在 `AdminController`/`AdminService` 增加更新接口，使用 `findByIdForUpdate`、版本冲突 409、字段校验和 `admin_audit_events` before/after/reason/requestId。
3. Admin Company detail 增加编辑表单；保存期间禁用，409 要求刷新，不覆盖他人修改。
4. Recruiter Profile 中公司字段改为只读；移除 Web 更新入口。后端 `RecruiterCompanyController` 的 update 接口应删除或固定拒绝，并同步 OpenAPI，确保不能绕过 UI。
5. 核对 Candidate 公共 DTO 只包含公开字段；不得返回 verification reason、审计、创建人、成员邮箱或 Admin 信息。
6. 在职位详情显示公司卡片并进入现有 Android CompanyPublicProfileScreen；补 loading/empty/error/content。
7. 只通过 `scripts/seed-local-demo.sql` 或 Admin API 补全明确演示公司。seed 使用确定 ID/邮箱定位并具有幂等性，禁止 `UPDATE companies` 无 WHERE 或按未知生产数据批量覆盖。
8. 测试 Admin 成功、非 Admin 403、版本冲突、非法 URL、空理由、审计记录、Recruiter 更新被拒、Candidate 公共字段白名单。

## 17. 每阶段测试命令

按改动范围运行，不能只运行单个测试后宣称整个阶段通过：

```bash
# Backend：先定向，再全量
cd backend
mvn -Dtest=RecruiterJobIntegrationTest,CandidateJobIntegrationTest test
mvn -Dtest=ConversationIntegrationTest,CommunityIntegrationTest,AdminIntegrationTest,DashboardIntegrationTest test
mvn test
mvn package

# Web
cd web
npm run typecheck
npm run lint
npm test
npm run build

# Android
cd android
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

涉及 Flyway 时额外运行 `MySqlFlywayIntegrationTest`/社区迁移测试，并在临时 MySQL 库验证 V1→最新版本；不得对本地正式 `adproject` 库做破坏性验证。涉及 OpenAPI 时运行仓库现有契约测试；若当前没有独立 validator，至少运行 Web `contract.test.ts`、Android `CandidateApiTest` 和相关后端 HTTP 集成测试，并在交付报告中说明缺少独立 validator。

## 18. 人工端到端验收顺序

1. Candidate A 登录→编辑 Profile→保存成功→登出。
2. 直接注册/登录 Candidate B→确认所有字段与 A 隔离→用最小问卷完成 Onboarding。
3. APPROVED Recruiter 新建职位：skills 初始为空；昨天不可保存；今天/未来可保存；多行 Description/Requirements 正常。
4. Android 用 Candidate B 搜索非 Singapore、salary<3000 职位→打开详情→查看全部 skills、Recruiter、公司资料→发送消息→投递。
5. 无 Google connection 的 Recruiter 打开该 Application→给 Candidate 发消息；双方刷新后都能看到，且不重复。
6. 从社区帖子 Message author→进入统一 Messages Chat UI→本人气泡靠右→一次返回帖子；从 Messages 列表再进入后一次返回列表。
7. Admin 查看 Accounts/Companies/Audit 三页分页→Reject PENDING 公司→Recruiter Dashboard 仍可打开但职位表单在显示前被拦截。
8. Admin 编辑演示公司并填写理由→Android 公司详情刷新后显示新资料→Recruiter 无编辑入口且 API 也拒绝。
9. 在 320、768、1440 px 截图 Profile、Schedule Interview、Admin 分页；Android 截图 Jobs、Job Detail、Applications、Community Chat、Company Detail。

## 19. 交付报告模板

每个阶段在 `change_report/` 新建独立报告，至少写：

- 覆盖的问题编号和复现证据。
- 修改的 backend/android/web/docs/scripts 文件。
- OpenAPI 是否变化；Flyway 版本、旧数据转换数量和回滚/备份说明。
- 定向测试、全量测试、build/lint 的准确命令与结果。
- 人工验收账号类型、路径和截图位置（不写密码/token）。
- 未运行项目及原因。
- 已知限制和下一步最小安全工作。

只有自动化测试、真实三角色流程和截图核对均完成，才能把对应阶段标记为完成。
