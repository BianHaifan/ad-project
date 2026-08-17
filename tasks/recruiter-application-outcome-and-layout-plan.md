# Implementation Plan: Recruiter Application Outcome、AI 分数与详情布局

## 目标

完善招聘者申请流程的可读性和决策闭环：AI 排序结果显式展示分数；面试后可记录正向结果 `OFFERED` 或拒绝；申请详情把孤立在右侧栏的 Actions 重构为贴近进度流程的“Next step / Decision”区域。

这是一次受控的全端状态契约扩展，不是 Offer 管理系统：不包含薪资条款、合同、Offer 接受/拒绝、签约或入职流程。

## 已确认现状

- 真实 AI 排序接口与页面已存在，表格仅以普通文本显示 `79%`，不够醒目；Applications 主列表另有明确标为 Demo 的本地排序开关。
- 详情页已画出 `Outcome` 节点，但 `ApplicationStatus` 只有 `APPLIED`、`IN_REVIEW`、`INTERVIEW`、`REJECTED`、`WITHDRAWN`；招聘者无法记录 Offer。
- `ActionPanel` 独立放在窄右栏，而流程、面试和简历在主栏，造成页面操作与上下文分离。
- OpenAPI 明确写明 OFFERED/HIRED 延后，因此实施前必须先更新该规范并同步所有受影响客户端。

## 业务决策与状态机

```text
APPLIED -> IN_REVIEW -> INTERVIEW -> OFFERED
       \-> REJECTED     \-> REJECTED
Candidate: APPLIED / IN_REVIEW / INTERVIEW -> WITHDRAWN

OFFERED, REJECTED, WITHDRAWN 均为终态。
```

- Recruiter 仅可从 `INTERVIEW` 转到 `OFFERED`，并必须填写不超过 500 字的决策说明；仍可按已有规则拒绝。
- `OFFERED` 仅代表“招聘者已发出录用结果”，不暗示 Candidate 已接受。
- `OFFERED` 不进入 AI 候选人排序；Candidate 的申请列表必须仍能看到它，并在归档/结果分组中清楚标识。
- 现有记录、状态字段和 API 保持兼容；MySQL 状态以字符串保存，无需 Flyway 迁移。

## 实施包

### 包 1：状态契约与后端完整闭环

- 先更新 `docs/product-requirements.md`、`docs/openapi-v1.yaml` 的状态机和完成定义，删除“Offer 延后”的旧约束。
- 在 Spring Boot `ApplicationStatus`、Transition DTO、状态转换校验、审核事件、统计/筛选和 Candidate 投影中增加 `OFFERED`。
- 保留所有权、版本并发、原因必填和审计日志；补充成功、非法来源、401、403、404、409、Candidate 不可自行 Offer 的测试。
- 确保候选人 AI 排序只包含 `APPLIED`、`IN_REVIEW`、`INTERVIEW`。

### 包 2：Recruiter 列表、AI 排序和结果 UI

- 在真实 `AI ranked applicants` 表中将分数改为语义清楚的 `AI fit score` 徽标（如 `82 / 100`），同时显示 `#Rank`；模型与 fallback 来源继续可见，不能把降级结果伪装为 AI。
- Applications 汇总增加 `Offers`/`Offered` 的真实计数、筛选选项和状态标签；原有 `AI ranked · Demo` 必须保留 Demo 文案或收敛为普通“Sort by stored match score”，避免与真实 AI 排序混淆。
- `ProgressRail` 的 Outcome 节点区分 `Offer made`（成功色）、`Rejected`、`Withdrawn`，使用真实审计事件的时间和原因。

### 包 3：详情页决策布局与跨端兼容

- 将 `ActionPanel` 移入详情主栏，紧跟 Application progress；改名为 `Next step`/`Decision`，按当前阶段展示最少、最明确的操作。
- `INTERVIEW` 时提供 `Make offer` 和 `Reject`；所有终态展示只读的结果摘要，不保留可误触发操作。
- 维持消息入口、Interview Card、Candidate fit 侧栏和现有 Google Meet 操作；详情页在窄屏下单列。
- 更新 Candidate Android 状态枚举、标签、归档分组和相关测试，确保收到 `OFFERED` 不会解析失败且用户可看到“Offer received”。

## 验收与测试

- Recruiter 可以从 Interview 记录 Offer，刷新后招聘者/求职者均看到 `OFFERED`，时间线和 Outcome 节点正确；任何其他来源返回 `409 INVALID_APPLICATION_TRANSITION`。
- 申请列表可筛选 Offered，并显示真实统计；AI 排序每行有排名、`AI fit score` 和模型/降级来源。
- 详情页操作区位于进度之后，非终态可执行且终态只读；键盘可操作、loading/error/submitting 状态完整。
- 运行相关 Spring Boot、Android、Web 单测、typecheck/lint/build；更新 `change_report/` 说明测试和未验证项。

## 边界与风险

| 风险 | 处理 |
| --- | --- |
| `OFFERED` 是跨端公共枚举 | 同一包同步后端、OpenAPI、Web、Android；不得只改招聘者网页。 |
| 与同事的 Application/ML 改动冲突 | 不改 Python ML；对后端仅扩展状态过滤，实施前检查工作区并小范围合并。 |
| “Offer”被理解为法律合同 | UI 使用 “Offer made / Offer received”，文案明确是招聘流程结果，不承诺合同或候选人接受。 |
| UI 改造破坏面试流程 | 保留既有 InterviewCard 和 Google Meet 调用/权限，仅移动决策区。 |
