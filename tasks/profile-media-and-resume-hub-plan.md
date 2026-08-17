# Profile、媒体附件与 Android 简历中心改造计划

## 目标

1. Candidate Android 与 Recruiter Web 都能上传、更换和删除真实头像；删除 Web 的手填 `Avatar URL`。
2. 消息中的 PNG/JPEG 在两端以安全的内联预览显示，非图片仍作为下载附件。
3. Recruiter Web 将 Google Calendar Integration 从顶栏独立页面归入 Profile；保留 OAuth 回调兼容。
4. Candidate Android 的“Me/Profile”改造成单一的简历中心，合并用户看到的 Profile 与 Resume 入口，并重做简历信息层级。

## 已确认事实

- 当前头像只是 `users.avatar_url` 字符串；Web 允许手填 URL，Android 只展示 URL，均没有文件上传。
- 消息附件已经保存 BLOB、进行会话所有权校验，并有带鉴权的下载接口；但响应固定 `Content-Disposition: attachment`，两端 UI 只下载，故图片不会显示。
- Google OAuth 的浏览器回调当前固定返回 `/recruiter/google-oauth?googleOAuth=...`；不能删除该路由或改动 Google/OAuth 配置。
- Candidate Profile（姓名、邮箱、地点、统计）和 Resume（年龄、摘要、技能、经历）是两个已有实体/API，存在姓名/职位/地点重叠字段。直接把两张表硬合并会扩大迁移与并发风险。
- 当前 Flyway 最新迁移为 `V13`。新增头像表前，实施者必须再次检查迁移目录与团队未提交迁移，确认使用的下一个编号，不能擅自猜测或重编号。

## 共同安全设计

### 头像

- 使用 MySQL 新表保存每名用户最多一张头像 BLOB（用户 ID、受控 content type、字节数、内容、更新时间）；不提交图片文件，不使用外部图床，也不再接受任意 URL。
- 只接受 PNG/JPEG，最大 5 MiB；服务端验证空文件、声明类型、可解码图片和合理像素上限，拒绝 SVG/GIF/HTML 与伪造 MIME。
- 新增当前用户上传/删除接口，以及只返回头像二进制的媒体读取接口。头像仅是公开资料的一部分，读取端不返回账户、邮箱、Token 或文件系统路径。
- 上传成功时只由服务端生成稳定的 API 路径写入现有 `avatar_url`；删除时清空该值并删除 BLOB。客户端不能提交 `avatarUrl`。
- Web 用 `<input type=file accept=image/png,image/jpeg>`；Android 使用系统照片选择器。两端均有预览、上传中禁用、格式/大小错误、上传失败重试和删除/回退首字母状态。

### 消息图片

- 不改变消息附件实体、迁移、上传大小限制或会话权限。
- 对 `image/png`、`image/jpeg`，Web/Android 以现有**鉴权下载接口**拿到 bytes/blob 后预览；不把受保护 URL 直接放进 `img src`，避免缺少 Authorization header。
- 下载响应新增显式安全的 inline/preview 语义（或客户端从现有二进制响应预览）；文件仍带 `nosniff`，非图片维持点击下载。
- 预览加载/失败显示可下载的文件卡，不暴露原始异常；生成的浏览器 Object URL 必须回收。

### Resume 中心

- UI 合并，不合并数据库实体：Profile 仍是账户/统计事实，Resume 仍是投递简历事实。
- Android `Me` 顶层改为“Resume”中心：头像、姓名、headline、地点和邮箱作为个人信息区；下方以摘要、技能、经历、求职偏好、投递概览组成结构化简历。移除重复的“Online resume / Edit profile”入口和单独 Profile 心智模型。
- 基础信息编辑调用既有 Profile API；简历内容编辑调用既有 Resume API。页面明确各自保存结果，避免无版本约束的双接口伪原子保存。
- 视觉仅借鉴 BOSS 直聘的清晰分区、简历完整性、时间线信息层级，不复制其资产、文案或像素设计。当前无匹配 Figma Frame，实施前在报告中注明采用现有 design system 的需求驱动设计。

## 分包

### Package 1：头像后端契约与存储（先做）

**允许范围**：`backend` 的 profile/user 媒体模块、测试、OpenAPI、必要的一条新 Flyway 迁移。

- 实施前重新审计 Flyway 编号；若与未提交团队迁移冲突，停止并报告，不重编号。
- 建立头像表、实体/Repository、验证服务、上传/删除/读取接口；严格认证、角色和仅本人写入校验。
- 移除 Recruiter Profile PATCH 中 `avatarUrl` 可写字段；既有展示 DTO 可继续返回服务端生成的路径。
- 后端测试：Candidate/Recruiter 成功上传、未登录 401、错误角色/他人资源 403/404、错误 MIME、伪图片、超限、删除、读取 content type 与无敏感响应。

### Package 2：双端头像上传与 Web Integration 归位

**依赖 Package 1。允许范围**：Web Profile/API/tests/router/AppShell/CSS；Android profile API/照片选择器/ViewModel/UI/tests；不改 Google OAuth 后端。

- Web 删除 Avatar URL 表单，加入头像选择、预览、上传/删除；头像在 Profile、顶栏和相关招聘者显示处正确回退首字母。
- Android 在 Resume 中心个人信息卡中加入照片选择、预览、上传/删除；相对媒体路径通过统一 resolver 正确加载。
- Profile 内嵌 Google Calendar 连接卡；顶栏移除 Integrations。旧 `/recruiter/google-oauth` 只作为回调中转，立即安全跳转 Profile 并展示一次性 connected/denied/failed 提示；不改 redirect URI 或 OAuth 配置。
- Web/Android 全部覆盖 loading/error/submitting；头像文件不进入 Redux/local storage/log。

### Package 3：消息图片预览（可与 Package 2 并行，避免改同一 Profile 文件）

**允许范围**：conversation attachment response/client/tests、Web Messages/UI/tests、Android Messages/API/UI/tests；不改头像。

- Web 图片消息缩略图可点开/查看，非图片下载；Android 图片消息内联缩略图，可点开系统/全屏预览（若工程无现成安全全屏组件，先实现受控大图对话框）。
- 复用会话 attachment 权限与 BLOB，不新增公开附件 URL。
- 添加 content type 分支、加载失败回退下载、Object URL 回收、权限回归测试。

### Package 4：Android Resume Hub 与简历 UI

**依赖 Package 2 的头像 UI；允许范围**：Android candidate profile/resume/route/ViewModel/tests 与报告。

- 将 Me/Profile 页面重构为单一 Resume Hub，复用已有真实 profile+resume 数据，删除重复入口而非删除后端数据。
- 重做 Resume 编辑：分区卡片（基本信息、个人概述、技能、经历），经历可添加/编辑/删除，保持实际 API 字段和版本校验。
- 完整处理 profile/resume 各自 loading、empty、error、editing、saving/success；避免 Composable 网络请求和重复保存。
- 在 390×844 模拟器做真实视觉验收并记录；不再保留已废弃但不可达的旧 UI 路径。

### Package 5：全链路验收

- 后端 `mvn test`，Web typecheck/相关 tests/build，Android unit test/lint/assemble。
- 人工验收：两种角色上传/替换/删除头像；双方对同一图片消息预览与非图片下载；OAuth connect/disconnect 位于 Profile 且 callback 可回到 Profile；Candidate Resume Hub 保存 profile 与简历字段、经历编辑和空简历引导。
- 每包单独写入 `change_report/`；不得声称未登录/未手测路径已通过。

## 不做

- 不接入第三方对象存储、CDN、裁剪/滤镜、AI 头像或社交头像同步。
- 不开放附件或私密消息的匿名访问。
- 不修改 Google Cloud 配置、OAuth redirect URI、ML、Agent、Admin。
- 不删除既有用户头像 URL 数据或重写历史消息附件。
