# Dashboard 真实数据待办

- [x] 更新 OpenAPI、API 覆盖表及中文目录中的 Dashboard 契约状态与字段。
- [x] 实现招聘者 Dashboard 聚合 API 和权限/所有权保护。
- [x] 为 API 增加成功、未登录、错误角色、跨公司隔离、空数据测试。
- [x] 将 Web Dashboard 从 mock repository 切换到真实 HTTP API。
- [x] 将推荐面板改为最近申请，去掉虚构 ML/匹配分数文案，并修复真实详情跳转。
- [x] 验证状态卡和岗位/申请链接筛选一致。
- [x] 运行相关后端与 Web 测试、构建，并写入 `change_report/` 修改报告。

---

# 下一版本：真实站内会话（短轮询）

- [x] Messages 修复包：令发送接口的 Idempotency-Key 与 OpenAPI 都为必填；补 MySQL V6 迁移表/索引测试；重建并重启本项目后端，真实 API 不再返回 500；写 `change_report/conversations-backend-remediation.md`。
- [x] 修复 Candidate Jobs 列表接口（`GET /api/v1/jobs`）在异常数据（空/非法 `requirements_json`/`skills_json`）下整表 500 的问题：`CandidateJobQueryService.readList` 改为空值/非法 JSON 容错（返回空列表并告警），补回归测试；真实 Docker MySQL 复测返回 200；写 `change_report/candidate-jobs-500-fix.md`。
- [ ] Messages Web 包（代码与测试已完成，待实机验证后勾选）：用真实 API 替换 mock，接入前台详情页 1 秒/列表页 3 秒轮询、已读、真实未读、空错误状态和发送；移除附件死按钮及 ML 演示文案；已写 `change_report/conversations-web.md`（lint/typecheck/test/build 全通过，97 测试；因环境无真实浏览器，未完成双账号验收，故保留未勾选）。
- [ ] Messages Android 数据包（代码与测试已完成，待实机验证后勾选）：修正 debug 默认地址为 `10.0.2.2:8081`；用真实 API 替换 FakeCandidateRepository 消息路径（会话列表/详情/消息历史/发送/已读），补 Repository/ViewModel 测试；已写 `change_report/conversations-android-data.md`（test/lint/assembleDebug 全通过，47 测试；因环境无真实设备，未完成双账号验收，故保留未勾选）。
- [ ] Messages Android UI/验收包（代码与测试已完成，待实机验证后勾选）：接入同频率生命周期轮询、已读、发送与完整状态；已写 `change_report/conversations-android.md`（test/lint/assembleDebug 全通过，53 测试；因无既有投递关系、唯一 ACTIVE 岗位属未知凭据招聘者且新注册招聘者公司为 PENDING 无法建岗，未完成双账号验收，故保留未勾选）。
- [ ] 修正 Android debug 默认 API 地址为 `10.0.2.2:8081/api/v1/`，与本项目 Docker 后端一致；保留 `AD_API_BASE_URL` 覆盖，并在 Android Studio 默认 Run 验证注册、登录。
- [x] 清理 Android 求职者登录/注册页：移除身份选择，保持固定 Candidate 注册并补 UI 回归测试；暂时保留 Forgot password 文案。
- [ ] 不实现伪“忘记密码”流程；待认证负责人确认邮件与重置 Token 方案后再单独立项。
- [ ] 为 Web 招聘者注册页增加确认密码字段与前端一致性校验；不改变注册 API 或密码存储，并补回归测试。
- [x] 修复 Candidate 登录页 401 文案：登录失败显示“邮箱或密码不正确”；仅刷新 Token 失败显示“会话已过期”，并补 Android 回归测试。
- [x] Dashboard 真实数据任务完成后，统一创建岗位入口：仅保留 Jobs 页面及其空态入口，按钮改为 `Create job`；移除 Dashboard、Applications 的重复入口；保留表单内草稿语义和回归测试。
- [x] 小改动包 1（Android 认证）：移除登录/注册身份选择；保留无行为的 Forgot password；修正登录 401 文案；加入“注册失败 → 修改输入 → 可重试”回归测试。
- [x] 小改动包 2（Web 注册）：新增确认密码与一致性校验；编辑字段立即清除服务器页级错误；加入“注册失败 → 修改输入 → 成功重试”回归测试。
- [x] 小改动包 3（Web 招聘入口）：仅保留 Jobs 页/空态的 `Create job`；移除 Dashboard 与 Applications 重复入口；保留草稿表单语义。
- [x] Claude 后端执行包：完成会话规范、OpenAPI、V6 迁移、自动建会话与双端会话 API，写入 `change_report/conversations-backend.md`。
- [x] 冻结会话、消息、已读和短轮询的 OpenAPI 契约；附件不进入本版本。
- [x] 添加会话、消息、用户已读状态和消息幂等约束的 Flyway 迁移。
- [x] 在成功投递事务中自动创建唯一会话，并增加回归测试。
- [x] 实现候选人端真实会话、消息历史、发送和已读 API 与权限测试。
- [x] 实现招聘者端真实会话、消息历史、发送和已读 API 与公司隔离测试。
- [ ] 用真实 API 替换 Web Messages mock；增加前台详情页 1 秒、列表页 3 秒轮询及 UI 回归测试。（代码与测试已完成，待实机验证）
- [ ] 用真实 API 替换 Android Messages fake repository；增加相同频率的生命周期轮询和 ViewModel 测试。
- [ ] 在两个真实账户之间完成 Android ↔ Web 的互发、未读和轮询验收。
- [x] 更新 `change_report/`，记录 API、迁移、测试、限制和下一步。
