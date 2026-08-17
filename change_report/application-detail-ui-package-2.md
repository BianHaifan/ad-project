# 招聘者申请详情页 UI 重构 + 直达聊天入口（Package 2）交付报告

> 状态：实现完成，等待复核；未 commit / push。
> 依赖：在 Package 1（`GET /api/v1/recruiter/conversations?applicationId={id}` 精确查询）之上完成。

## 完成内容

### 直达聊天入口

- 复用 Package 1 的 `GET /api/v1/recruiter/conversations?applicationId={id}` 精确查询，不在详情页扫描会话列表前 20 条，也不在打开详情页时创建会话。
- 网页端对话数据链路逐层透传 `applicationId`（URL-encoded）：
  - `conversationHttpClient.listConversations(applicationId?)`：有 `applicationId` 时追加 `applicationId` query（`URLSearchParams` 负责编码）。
  - `RecruiterRepository.listConversations(applicationId?)`：接口签名扩展。
  - `repository.listConversations`：接线到 HTTP client。
  - 新增 `useConversationByApplication(applicationId)`（`useQuery`，`enabled: !!applicationId`），与列表轮询钩子 `useConversations` 分离，不干扰 Messages 页的既有列表行为。
- `MessageCandidateButton`（新组件，候选人信息区内主操作）状态：
  - 查询中 → 禁用按钮「Message candidate…」。
  - 命中唯一会话 → 跳转 `/recruiter/messages/{conversationId}`。
  - 成功但无会话 → 禁用按钮 + 中性说明「No conversation with this candidate yet.」。
  - 失败 → 可重试按钮 + 安全错误提示（不暴露后端原始错误信息）。
- `useCreateInterview` 成功后追加 `['conversationByApplication']` 失效，使排期后聊天入口能反映新会话。

### 申请详情 UI 重构

`ApplicationDetailPage.tsx` 拆分为两个清晰区域并抽出 5 个小组件（`web/src/pages/applicationDetail/`）：

1. **`ProgressRail.tsx` — Application progress（申请进展）**
   - 四段轨道：Submitted → Review → Interview → Outcome。
   - 完成阶段显示时间戳；当前阶段高亮；未来阶段弱化；被跳过的阶段显示「Not reached」（不伪造过程节点）；Rejected/Withdrawn 以终止态（红色）结束轨道，并带来自审计事件的实际时间/原因。
2. **`InterviewCard.tsx` — Interview（面试生命周期）**
   - 结构化展示：时间（按保存时区显示）、时区、时长、模式、Meeting/地点/链接、Google Meet 同步状态、候选人备注、Scheduled/Completed/Cancelled 状态徽章。
   - 排期/改期仍保留在父级弹窗中，卡片只呈现已有面试与既有接口允许的操作（改期、标记完成、取消），文案与实际效果一致；新建在线面试不暴露手动会议链接输入框。
3. **`ActivityHistory.tsx` — Activity history（完整审计）**
   - 用竖向列表呈现完整审计轨迹（含招聘者原因），不再把每个审计事件伪装成标准进度节点。
4. **`ActionPanel.tsx` — Actions（上下文动作卡）**
   - 取代原「NEXT STAGE」下拉，动作严格派生自真实状态机：
     - `APPLIED` → `Start review`（主）+ `Reject`（次，危险）。
     - `IN_REVIEW`（无面试）→ `Schedule interview`（主）+ `Reject`（次）。
     - `INTERVIEW` → `Reject application`（面试操作在 InterviewCard）。
     - `REJECTED` / `WITHDRAWN` → 只读终止说明，无任何转换动作。
   - Start review / Reject 均通过独立确认弹窗要求必填原因；保留 `expectedVersion`、乐观并发（提交中禁用）、禁用态与错误提示；`submit` 额外加 `update.isPending` 守卫确保等待期间只提交一次。
5. **`MessageCandidateButton.tsx`**（如上）。

### 文档修正（Package 1 复核）

- `docs/openapi-v1.yaml`：`/recruiter/conversations` 的 `x-permission` 与 `description` 由「own-company jobs」改为「own-company conversations」。

## 状态机 → UI 映射（真实，未发明 Offer/Hired）

| 申请状态 | 进展轨道（当前阶段） | 动作卡 | 面试生命周期 |
|---|---|---|---|
| `APPLIED` | Submitted（当前） | Start review / Reject | 无 |
| `IN_REVIEW` | Review（当前） | Schedule interview（无面试时）/ Reject | 无（排期后转入 INTERVIEW） |
| `INTERVIEW` | Interview（当前） | Reject application | InterviewCard：SCHEDULED/COMPLETED/CANCELLED |
| `REJECTED` | Outcome（终止，带原因/时间） | 只读终止说明 | 无 |
| `WITHDRAWN` | Outcome（终止） | 只读终止说明 | 无 |

- 未新增任何 Offer/Hired/Accepted 状态；面试生命周期仅 SCHEDULED/COMPLETED/CANCELLED，与申请进展分区展示。
- 未绕过 `reason`、`expectedVersion`、服务端权限与并发处理；未改动在线面试创建逻辑（仍仅 Google Meet 自动创建）。

## 实际修改文件

### 网页（web/）

- `src/api/conversationHttpClient.ts`（`listConversations` 增加 `applicationId`）
- `src/api/recruiterRepository.ts`（接口签名）
- `src/api/repository.ts`（接线）
- `src/api/queries.ts`（新增 `useConversationByApplication` 与 key；`useCreateInterview` 失效扩展）
- `src/pages/ApplicationDetailPage.tsx`（重构，拆分组件）
- `src/pages/applicationDetail/MessageCandidateButton.tsx`（新增）
- `src/pages/applicationDetail/ProgressRail.tsx`（新增）
- `src/pages/applicationDetail/ActivityHistory.tsx`（新增）
- `src/pages/applicationDetail/ActionPanel.tsx`（新增）
- `src/pages/applicationDetail/InterviewCard.tsx`（新增）
- `src/theme/global.css`（新增 progress rail / activity history / action stack / candidate-actions / message-candidate 样式）
- `src/pages/ApplicationPages.test.tsx`（更新既有用例 + 新增用例）
- `src/api/conversationHttpClient.test.ts`（新增 applicationId 传参用例）

### 文档

- `docs/openapi-v1.yaml`（own-company jobs → own-company conversations）

## 测试

网页（Vitest 4，Node 22）：

```bash
npm run typecheck   # tsc -b --pretty false —— 通过
npm run lint        # eslint . —— 通过
npm test            # vitest run —— 21 个文件 / 185 用例全部通过
npm run build       # tsc -b && vite build —— 通过（127 模块）
```

新增/更新的关键用例：

- 五种真实状态（APPLIED/IN_REVIEW/INTERVIEW/REJECTED/WITHDRAWN）的进展轨道四段标签 + 上下文动作（存在/缺失）逐一断言。
- 面试生命周期与申请进展分离：INTERVIEW 状态下 `Interview` 标题与 `Application progress` 分属两个面板。
- Start review 必填原因 + 等待期间仅提交一次（保留 `expectedVersion` 与原始 reason 透传）。
- Reject 必填原因 + 提交中禁用。
- 拒绝原因在 Outcome 阶段与 Activity history 中可见（来自审计事件）。
- 聊天入口四态：命中唯一会话跳转 `/recruiter/messages/{id}`、无会话禁用 + 中性说明、查询中禁用加载态、失败可重试 + 安全提示（不泄露原始错误）。
- `ConversationHttpClient.listConversations('app-1')` 正确追加 `applicationId=app-1`。

## 未完成内容（按任务边界 / 未手工验证）

- 未做本地手工流程验证（APPLIED → IN_REVIEW → 排期 → 发消息），因未启动本地后端与前端联调；仅以组件测试覆盖状态/动作/聊天四态。
- 未修改 Android、数据库/Flyway、Application 状态机、Google Meet/OAuth、Admin、ML、Agent。
- 未新增依赖、未改动配置/密钥。
- 未 commit、未 push。

## 未改动边界（显式确认）

- 申请状态枚举、面试状态枚举、`transition`/`interview` 接口的服务端权限与并发逻辑均未触碰。
- 排期/改期表单的业务字段（时区、时长、模式、地点/链接、备注、`expectedVersion` 等）未重新设计，仅澄清层级/标签/动作。
- 在线面试创建逻辑未改动（仍仅 Google Meet 自动创建，无手动会议链接输入）。
