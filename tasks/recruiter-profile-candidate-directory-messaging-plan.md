# 实施计划：招聘者资料、求职端信息页与消息增强

## 范围

本轮交付五项关联能力：招聘者维护自己的资料；Candidate 从职位和会话查看招聘者及公司公开资料；面试创建成功后自动发送通知消息；Web 与 Android 在会话中发送和下载附件。

不修改 Admin、ML、Agent、Google OAuth/Meet 的授权流程，也不开放 Candidate 的邮箱、招聘者注册信息或任意用户的私有文件。

## 冻结的产品与安全决策

1. Recruiter 自己可编辑：头像、姓名、职位名称和个人简介。任职公司来自既有公司成员关系，只读；邮箱、注册时间和账号角色为只读注册信息。
2. Candidate 只可看到 Recruiter 的公开字段：头像、姓名、职位名称、个人简介、所属公司及公司公开资料；绝不返回 Recruiter 邮箱、注册时间、账号状态或后台资料。
3. Candidate 可从职位详情的招聘者/公司标签，或本人会话中的招聘者头像进入资料页。访问必须是 Candidate 身份，且服务端确认目标与该职位或该 Candidate 的会话存在可见关联。
4. 成功安排面试时，系统以实际操作的 Recruiter 身份在已有会话中发送一条自动通知。它包含职位、时间、时区、模式、地点或已就绪的 Meet 链接。仅在首次创建成功时发送；Google Meet 尚未就绪或失败时不发送含伪造链接的通知。重试不得产生重复消息。
5. 附件采用后端托管的私有本地文件存储。允许 PDF、DOC、DOCX、PNG、JPG/JPEG，单文件最大 10 MiB、每条最多 3 个；下载接口再次验证会话参与者，使用 `Content-Disposition: attachment`，不提供公开静态 URL。
6. 头像复用同一存储基础设施，但仅允许图片且最大 5 MiB；聊天附件与头像使用不同用途、不同授权规则。

## 依赖顺序

```text
OpenAPI / 可见性规则
  -> Flyway 迁移与后端资料、附件服务
     -> Recruiter 资料页与 Candidate 公开资料接口
        -> Web / Android 页面接入
  -> 面试成功事件
     -> 幂等自动通知消息
  -> 附件消息契约
     -> Web / Android 选择、上传、下载 UI
```

## 实施包

### 包 0：契约与迁移编号协调

**目标：** 先在 OpenAPI 明确 DTO、端点、错误码、文件限制和可见性；与 Admin 任务负责人确认下一个 Flyway 编号后才创建迁移。

**接口方向：**

- `GET/PATCH /api/v1/recruiter/profile`
- `POST /api/v1/recruiter/profile/avatar`
- `GET /api/v1/candidate/recruiters/{recruiterId}`
- `GET /api/v1/candidate/companies/{companyId}`
- 候选人与招聘者的既有 message 端点增加 multipart 发送形式与附件元数据；附件下载保持各自角色路径。

**验收：** OpenAPI 不泄漏私有字段；迁移编号不与 Admin 分支冲突；两端 DTO 由契约同步。

### 包 1：Recruiter 资料垂直切片（后端 + Web）

**后端：** 新增 Recruiter 专属资料字段（职位名称、简介），保留 `users.avatar_url`；实现本人读取、更新、头像上传及严格的 RECRUITER/本人校验。

**Web：** 在右上角头像/账户区域增加可访问的“Profile”入口和 `/recruiter/profile` 页面；表单提供 loading、字段校验、提交禁用、错误和成功反馈。

**验收：** Recruiter 可修改后刷新仍看到新资料；Candidate/未登录不可调用本人编辑接口；邮箱与注册时间不可编辑。

### 包 2：Candidate 的招聘者与公司公开资料（后端 + Android）

**后端：** 以独立公开投影返回 Recruiter 与 Company，按职位/会话关联校验 Candidate 的可见性；职位详情返回点击所需的 recruiterId/companyId 预览信息。

**Android：** 在 Job Detail 的 Recruiter/Company 标签增加导航；在 Chat Detail 的 Recruiter 头像增加导航；分别实现公开 Recruiter Profile 和 Company Profile 页面。

**验收：** 两个入口到达相同资料；无关联 ID、跨用户会话、未登录和错误角色均拒绝；页面完整处理 loading、empty/error/content。

### 检查点 A

- 后端资料与可见性集成测试通过。
- Web typecheck/test 与 Android unit test/lint/assembleDebug 通过。
- 真机或模拟器验证：职位详情 -> 招聘者/公司资料；消息 -> 招聘者资料。

### 包 3：面试自动通知

**后端：** 在 InterviewService 的首次成功排期路径调用 ConversationService 的内部通知能力；使用确定性的 UUID/idempotency key，以 interviewId 作为唯一来源，复用既有会话与未读计数逻辑。

**规则：** ONSITE/PHONE 在排期数据库事务成功后发送；ONLINE 仅在 Meet 已成功生成且链接已验证后发送。Google 重试、并发请求和接口重放都不得重复消息。

**验收：** Candidate 在轮询刷新后看到一条准确通知；通知的 sender 为实际 Recruiter；Meet 失败时不出现虚假邀请；普通消息流程不受影响。

### 包 4：私有附件基础设施与后端消息扩展

**后端：** 新增附件元数据表与迁移；实现 MIME/大小/数量/文件名校验、临时写入后提交、会话参与者下载鉴权，以及消息与附件的原子关联。消息可为“文本、附件或两者”，但不得两者皆空。

**验收：** 非会话参与者无法上传、枚举、下载；不允许可执行文件、超限文件和伪造 MIME；重复请求不重复创建消息/附件；附件下载不暴露磁盘路径。

### 包 5：双端附件 UI

**Web：** 恢复并实现消息输入框的加号：选择文件、展示待发送文件、移除、上传中禁用、发送失败可重试、消息内下载。

**Android：** 使用系统文件选择器完成相同流程，显示文件名与大小、上传状态及下载/打开动作；不要求图片预览或多媒体播放。

**验收：** Web -> Android、Android -> Web 均能发送 PDF 与图片并下载；网络失败不会清空未发送内容；附件与纯文本的未读数一致。

### 检查点 B：交付前

- 后端：成功、未登录、错误角色、错误所有权、大小/MIME/数量冲突、幂等和面试重试测试通过。
- Web：lint、typecheck、相关 Vitest 测试通过。
- Android：unit test、lint、assembleDebug 通过。
- 真实环境手测完整流程：Recruiter 更新资料 -> Candidate 查看职位资料 -> 安排线上/线下面试 -> Candidate 收到通知 -> 双向传递附件。

## 风险与控制

| 风险 | 控制措施 |
|---|---|
| Admin 与本轮同时新增 Flyway 迁移 | 开工前由负责人锁定下一个迁移编号；迁移合并前再次 rebase/验证。 |
| 文件泄漏或路径遍历 | 只存服务端生成文件名；下载永远走授权控制器；禁止原始路径和公开 URL。 |
| Google Meet 失败仍通知 Candidate | 仅以最终 READY 的 Meet 状态生成含链接通知。 |
| 自动通知在重试时重复 | 以 interviewId 派生确定性幂等键，并在数据库唯一约束下发送。 |
| 公开资料扩大隐私暴露 | 后端使用独立 Public DTO，禁止复用 Auth/User DTO。 |

## 不在本轮范围

- 头像裁剪、图片编辑、病毒扫描、云对象存储/CDN、文件预览。
- 邮件/推送通知、面试改期和取消的自动消息。
- Candidate 资料公开、联系人关注、社区或企业成员管理。
