# Package 2W — Recruiter Web 头像上传与 Google Integration 归位

> 范围：仅 `web/src/**` 与报告；复用既有头像后端接口，未改动其实现、Flyway 或 OpenAPI。
> 本次会话未做任何 git 写操作（无 commit/push/pull/merge/reset）。

## 1. 目标 A — Recruiter 真实头像上传

- 删除 Profile 表单的 `AVATAR URL` 字段、前端 `ProfileForm` 中的可写 `avatarUrl`、以及 URL 长度校验与提交字段。
- 前端模型 `UpdateRecruiterProfileInput` 移除 `avatarUrl`；读取侧 `RecruiterProfileDetail.avatarUrl` / 会话 `avatarUrl` 保留为只读。
- 新增 `avatarHttpClient`（`POST /api/v1/profile/avatar` 走 multipart `file`、`DELETE /api/v1/profile/avatar`），`requestWithAuthForm` 复用既有附件上传通道，未新增依赖。
- Profile 页新增头像区块：PNG/JPEG 文件选择、本地选择预览（`URL.createObjectURL`，替换/卸载/上传/删除时 `revokeObjectURL`）、上传/删除进行中禁用按钮、格式/大小/失败的安全提示、删除后回退姓名首字母。
- 头像展示使用后端返回的 `/api/v1/avatars/{userId}` 路径，未重新开放外部 URL。
- 上传/删除成功后 `invalidateQueries(['recruiterProfile'])` 刷新查询；顶栏头像通过会话 `AuthSessionStore.updateAvatarUrl()` 同步，`AppShell` 在有 `avatarUrl` 时渲染图片、否则回退首字母。

## 2. 目标 B — Integration 归入 Profile

- 移除顶栏 `Integrations` 导航。
- Google Calendar 连接状态与 Connect / Reconnect / Disconnect 移入 Profile 的独立区块（`GoogleConnectionSection`）。
- 未改 Google OAuth 后端、配置或 redirect URI（授权/状态/断连仍走 `/recruiter/google-oauth*`）。
- `/recruiter/google-oauth` 保留为纯 callback 中转：只接受 `connected / denied / failed`，立即 `replace` 跳转 `/recruiter/profile`（安全结果经 `?googleOAuth=` 传递）；未知 query、OAuth code、state、原始错误一律丢弃不转发。
- Profile 显示一次性 OAuth 结果提示（`connected/denied/failed` 三种安全文案）后清除 query；直接访问旧 Integration 路径安全跳回 Profile。

## 3. 修改文件

**新增**
- `web/src/api/avatarHttpClient.ts`、`web/src/api/avatarHttpClient.test.ts`
- `web/src/api/avatarQueries.test.tsx`
- `web/src/components/GoogleConnectionSection.tsx`

**修改**
- `web/src/models/recruiter.ts`（新增 `AvatarMetadata`；`UpdateRecruiterProfileInput` 移除 `avatarUrl`）
- `web/src/api/contract.ts`（新增 `avatar: '/profile/avatar'`）
- `web/src/api/authSession.ts`（新增 `updateAvatarUrl`）
- `web/src/api/recruiterRepository.ts`、`web/src/api/repository.ts`、`web/src/mocks/mockRecruiterRepository.ts`（接入 `uploadAvatar`/`deleteAvatar`）
- `web/src/api/queries.ts`（`useUploadAvatar`/`useDeleteAvatar`，成功后乐观更新并刷新 profile 缓存）
- `web/src/components/AppShell.tsx`（移除 Integrations 导航；顶栏头像图片/首字母）
- `web/src/pages/GoogleOAuthPage.tsx`（改为 callback relay）
- `web/src/pages/ProfilePage.tsx`（头像区块 + Google 区块 + OAuth 结果横幅）
- `web/src/theme/global.css`（`img.avatar` object-fit、`.profile-main`、`.avatar-editor`、`.sr-only`）
- 测试：`ProfilePage.test.tsx`、`GoogleOAuthPage.test.tsx`、`AppShell.test.tsx`、`recruiterProfileHttpClient.test.ts`

## 4. 测试真实结果

- `npm run typecheck`：通过（0 错误）。
- `npm test`（Vitest）：**23 files，196 tests，0 失败**。
  - 覆盖：上传成功、删除成功、上传失败、URL 字段不再出现、客户端格式/大小拒绝、Profile 内 Google connected/disconnected/reconnect/revoked、callback 跳转与未知参数安全处理、顶栏头像图片/首字母。
- `npm run build`：成功（`tsc -b && vite build`）。

## 5. 未手测项与边界

- 未做真实浏览器端到端手测：未对接线上后端与真实 Google OAuth 回调（自动测试已用 mock 覆盖跳转与安全分支）。
- 未新增依赖；`web/package-lock.json` 未被本次改动触碰（工作区中该文件的既有改动来自其它 agent）。
- 运行 `npm run build` 会重新生成 `web/dist/**` 产物（新哈希文件名 + 更新 `index.html`），属构建副作用，非手写内容。
- 未改动：`backend/**`、`android/**`、`ml-service/**`、`agent/**`、Google Cloud/OAuth redirect URI/`.env`/密钥、招聘者姓名/职位/简介/公司/注册既有编辑行为；未新增 mock 数据（仅测试内 mock）。
