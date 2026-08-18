# 稳定性任务下一阶段：核查与优化细则

日期：2026-08-18

## 核查结论

| Todo | 当前状态 | 证据/剩余工作 |
| --- | --- | --- |
| 安全同步与 Flyway | 已完成 | `main` 已基于 `origin/main@9f6f2ad`；最高迁移为 V26。 |
| Auth/Profile/首次问卷 | 已实现 | 已有状态复位、切号数据清理、最小 Onboarding 与测试。 |
| Job Form/Android Jobs | 已实现 | 已有 deadline、skills、salary、筛选、详情和刷新校验。 |
| 招聘消息/社区私信 | 已实现主要链路 | 招聘消息不依赖 Google connection；社区会话已进入 Messages。 |
| 社区操作行/Category/读重试 | 部分完成 | GET 最多重试一次已存在；详情操作未合并为一行，发帖 Category 仍是多个 Chip。 |
| Recruiter Web 体验 | 已实现 | 头像预览释放、Profile 分区、正式文案、响应式面试弹窗已覆盖。 |
| Admin UI/分页 | 已实现 | Moderation 前端入口已移除，共享分页已落地。 |
| 公司审核状态与职位门禁 | 已实现 | V26 移除 `CHANGES_REQUESTED`；create/update/publish 均校验 APPROVED。 |
| Admin 唯一编辑公司 | 已实现 | Admin PATCH、版本检查与审计已存在；Recruiter 修改被拒。 |
| change report/人工验收 | 未完成 | 仍需真实三角色、双端流程和截图核对。 |
| Candidate Messages 发送时间 | 确认存在 | Android 直接格式化响应 offset，没有先转设备时区；列表、气泡、面试卡片均受影响。 |

## 本阶段实施细则

### 1. Candidate Messages 时间

- 后端继续使用 `Instant` 和 UTC 存储，不修改数据库或 API 字段。
- Android 将 ISO-8601 offset 字符串解析成 `Instant`，再转换为 `ZoneId.systemDefault()`。
- 会话列表使用 `MMM d, HH:mm`，气泡使用 `HH:mm`，面试卡片使用 `EEEE, MMM d · h:mm a`。
- 固定 `Locale.ENGLISH`，避免英文界面随系统语言出现不可预测格式。
- 解析失败返回原值；不因单条脏数据导致页面崩溃。
- 单测使用显式 `Asia/Shanghai` 和 `America/New_York`，覆盖 UTC、非零 offset、跨日和非法值。

### 2. Community 交互

- 详情页将 Like 与 Message author 放入同一个可换行 `FlowRow`，保留 liking disabled 状态。
- 发帖 Category 改为单选下拉；`null` 明确映射为 `GENERAL`，选择后关闭菜单并更新 ViewModel。
- 发布中禁用 Category 和图片/发布操作，避免提交中的表单漂移。
- 增加 Compose UI 测试：默认 General、展开并选择另一类别、操作项同时可见且可点击。

## 验收

- Android 定向测试覆盖时间转换与 Community 控件。
- 运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`。
- 不改 OpenAPI、后端或数据库，因为时间事实值正确，问题只在客户端展示层。
- 自动化完成后，仍保留真实设备时区切换和三角色端到端截图作为人工验收项。
