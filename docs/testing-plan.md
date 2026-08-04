# 测试计划

## 1. 测试目标

验证第一条垂直切片在真实数据库、真实 HTTP 接口、Android 和 React 页面之间完整运行，并证明权限、状态机和 AI/ML 降级行为符合规范。

## 2. 测试层级

### 后端单元测试

- 申请状态机的允许与禁止跳转
- 职位发布字段和公司审核规则
- 重复投递判断
- 资源所有权判断
- 推荐结果过滤、排序响应转换与降级逻辑
- Agent 写操作确认逻辑

### Spring Boot 集成测试

- 使用测试数据库或 Testcontainers 验证 Repository 和迁移
- 使用 MockMvc/WebTestClient 验证 HTTP 状态码、错误码和 JSON
- 创建申请与状态历史事务一致性
- JWT、角色和所有权校验
- ML 服务超时、错误和无结果场景

### Android 测试

- ViewModel 状态与 Repository 单元测试
- Compose 关键页面 UI 测试
- 登录过期、空职位、网络错误、重复投递提示
- 使用测试后端验证职位列表 → 详情 → 投递 → 申请记录

### Web 测试

- 表单校验和 API 错误映射单元测试
- 招聘者发布职位和状态操作组件测试
- Playwright 验证发布职位 → 查看申请 → 更新状态
- Admin 审核的权限和确认行为

### ML 测试

- 从固定数据版本可重复完成训练并生成模型产物
- 训练集、验证集和测试集无用户或时间泄露
- 与热门职位或内容相似度基线进行比较
- 独立测试集输出 Precision@K、Recall@K、NDCG@K 等选定指标
- 固定输入下 Top-N 响应 schema、rank 和 modelVersion 稳定
- 分数范围、排序方向、候选职位过滤正确
- 新用户冷启动有结果，空字段和长文本不会使服务崩溃
- 在线推理失败时 Spring Boot 返回规则降级结果

### Agent 测试

- 只读分析不会修改简历
- “把默认简历年龄改成 28”生成正确字段 patch 和预览
- 未确认、确认过期或重复确认的写操作不能执行
- 确认后只修改目标字段并产生新简历版本和审计记录
- Agent 不能读取其他用户资源
- Candidate Agent 不能调用招聘者修改申请状态或管理员工具
- 工具失败时返回可理解错误并记录步骤
- Prompt injection 样本不能导致越权工具调用或秘密泄露

## 3. 第一条垂直切片验收场景

### Happy path

1. 已审核招聘者登录。
2. 创建并发布一个职位。
3. Candidate 在 Android 端看到该职位。
4. Candidate 打开详情并使用默认简历投递。
5. Recruiter 在网页端看到新申请。
6. Recruiter 将状态改为 `SCREENING`，再改为 `INTERVIEW`。
7. Candidate 刷新后看到状态和完整时间线。

### 必须覆盖的失败场景

- 未登录投递 → 401
- Recruiter 调用 Candidate 投递接口 → 403
- 没有默认简历 → 业务错误并引导创建
- 同一职位重复投递 → 409 `APPLICATION_ALREADY_EXISTS`
- 已关闭职位投递 → 409 `JOB_NOT_ACCEPTING_APPLICATIONS`
- 非本公司 Recruiter 查看申请 → 404/403（按统一策略）
- 非法状态跳转 → 409 `INVALID_APPLICATION_TRANSITION`
- ML 服务不可用 → 职位仍可查看和投递

## 4. UI 状态验收

所有数据页面至少验证：

- Loading：有明确加载反馈，不重复提交
- Empty：说明当前无数据并提供下一步操作
- Error：说明失败并可重试，不展示原始异常
- Content：正常数据可读、操作按钮与权限一致
- Disabled/Submitting：写操作期间避免重复点击

## 5. 提交前检查

每个功能任务完成后至少运行与改动相关的：

```text
backend: test + 静态检查/格式检查
android: unit test + lint + assembleDebug
web: lint + typecheck + test
contracts: OpenAPI validation
```

命令在工程脚手架建立后写入根目录开发说明和 CI；不得在规范中假定尚未创建的具体脚本名。

## 6. 完成定义（Definition of Done）

功能只有同时满足以下条件才算完成：

- 实现符合 PRD、权限与 API 契约
- 主要成功路径和失败路径有自动化测试
- UI 处理 loading、empty、error 和重复提交
- OpenAPI 与实现同步
- 数据库变更通过迁移脚本完成
- 无明文密钥和敏感日志
- 相关 Figma Frame 或交互说明已引用
- 在真实接口环境完成一次人工端到端验证
- 更新必要文档，不遗留未说明的行为差异
