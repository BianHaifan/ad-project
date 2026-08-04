# Figma MVP 设计审查

审查日期：2026-08-04  
Figma：[AD project Copy](https://www.figma.com/design/ellcZx2GjomKwCQNxuryri/AD_project--Copy-?node-id=0-1)  
依据：`product-requirements.md`、`user-flows.md`、`api-design.md`、`database-design.md`

## 1. 审查结论

当前 Figma 已建立清晰的视觉方向，并覆盖职位浏览、职位详情、求职者工具、招聘者 Dashboard、简历审查和信息架构。但它仍不能完整演示 MVP 的“发布职位 → 投递 → 处理申请 → 查看进度”垂直流程。

下一阶段只补齐 MVP 断点，不继续扩展社区、课程、会员等非核心功能。关键页面补齐并通过流程检查后，应立即进入工程骨架和第一条垂直切片开发。

## 2. 现有页面覆盖

| Figma Frame | MVP 用途 | 覆盖情况 |
|---|---|---|
| `01 Candidate / Job Feed` | 职位列表/推荐入口 | 部分覆盖 |
| `01B Candidate / Job Detail` (`2004:2`) | 职位详情、推荐解释、投递入口 | 已覆盖主状态 |
| `02 Candidate / Profile & Tools` | 个人入口、简历和 Agent 入口 | 部分覆盖 |
| `03 Candidate / Resume Agent` | AI 辅助 | 目前偏分析型，不是操作型 Agent |
| `04 Candidate / Learning & Company` | P1/P2 功能 | 暂不优先 |
| `05 Candidate / Community & Messages` | P1/P2 功能 | 暂不优先 |
| `06 Recruiter / Dashboard` | 招聘者概览 | 部分覆盖 |
| `07 Recruiter / Resume Review` | 申请/简历详情 | 可复用，但缺少明确状态操作 |
| `08 Resume Rating / Detail` | 评分详情 | 可作为 P1 或解释能力 |
| `09 Product IA / Flow Overview` | 信息架构 | 需要同步新的推荐模型和操作型 Agent 定义 |

## 3. MVP 缺失页面与优先级

### P0-A：开始工程前必须补齐

1. `10 Recruiter / Create Job`
   - 职位标题、Employment type、Work mode、地点
   - 薪资下限、上限、币种
   - 技能标签、职位描述、任职要求
   - Save draft、Preview、Publish
2. `11 Candidate / Apply Confirmation`
   - 目标职位摘要、默认简历/选择简历、简历版本与更新时间
   - 无简历、重复投递、职位关闭提示
3. `12 Recruiter / Applications List`
   - 所属职位、申请人、申请时间、状态
   - 推荐分数/理由、状态筛选、排序、搜索
4. `13 Recruiter / Application Detail`
   - 投递时简历快照、状态时间线、推荐理由、招聘者备注
   - SCREENING、INTERVIEW、OFFERED、REJECTED 操作
5. `14 Candidate / My Applications`
   - 投递记录、当前状态、状态时间线、允许时撤回申请

### P0-B：可以与工程骨架并行补齐

6. Candidate 登录/注册
7. Recruiter 登录/注册与公司待审核
8. 简历创建、编辑和上传
9. 精简管理员审核
10. Agent 操作计划、变更预览、确认和结果

## 4. 当前设计与新工程规范的差异

### 推荐模型

现有设计主要展示单个职位的 `AI Match 96%`。工程规范已经改为团队自行训练的 Top-N 职位推荐模型，因此需要：

- 在职位首页明确“Recommended for you”排序来自推荐模型；
- 保存单个职位的推荐理由，但不把它误写成完整推荐系统；
- 支持冷启动/推荐不可用的普通职位列表；
- 后期在演示说明中展示 `modelVersion`，不必在普通用户界面突出技术字段。

### 操作型 AI Agent

现有 Agent 更接近简历分析/改写。目标 Agent 应表现为软件的自然语言操作层：

```text
用户输入指令
→ Agent 理解目标并调用读取工具
→ 展示操作计划或字段变更
→ 用户确认
→ 调用受控写工具
→ 返回执行结果
```

第一条设计用例固定为：“把我默认简历里的年龄改成 28”。需要展示目标简历、原值、新值、确认、取消、执行结果和审计入口。

## 5. 视觉与交互约束

- Android Frame：390 × 844，与现有 Candidate 页面保持一致。
- Recruiter/Admin Frame：1440 × 1024，与现有桌面端保持一致。
- 继续使用现有 Inter 字体、青绿色品牌色、浅灰页面背景、白色圆角卡片。
- 桌面表单采用左侧导航 + 主内容 + 可选预览/帮助区。
- 主要写操作使用清晰的 Primary button；危险/终态操作不能与普通操作视觉等价。
- 每个数据页面必须说明 Loading、Empty、Error、Content、Submitting/Disabled。
- 高影响操作使用确认弹窗或确认步骤，不允许点击后无反馈直接生效。

## 6. 第一张待绘页面：Recruiter Create Job

页面目标：让已审核招聘者创建职位草稿、完成字段校验并发布职位，对应：

- `POST /api/v1/recruiter/jobs`
- `PUT /api/v1/recruiter/jobs/{id}`
- `POST /api/v1/recruiter/jobs/{id}/publish`

默认示例数据：

```text
Title: AI Backend Engineer
Employment type: Full-time
Work mode: Hybrid
Location: Shanghai
Salary: 42K–68K CNY / month
Skills: Python, FastAPI, LLM / RAG, Kubernetes
```

主操作：`Save draft`、`Preview`、`Publish job`。发布前必须校验公司已审核和必填字段完整。

## 7. 停止继续画图的条件

当 P0-A 五组页面满足以下条件后，停止扩展 Figma 并建立工程：

- 每个垂直流程操作都有明确入口和结果；
- 页面字段与数据库/API 对齐；
- Candidate 和 Recruiter 可以从发布职位走到申请状态展示；
- 已标注主要异常和禁用状态；
- 推荐模型和 Agent 的产品表达与工程规范一致。
