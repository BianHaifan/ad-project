# 修改报告：招聘者 Web 的 Google 账号连接页面与 OAuth 回跳结果提示

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-15
- 修改范围：`web/`、`tasks/google-meet-integration-*.md`、`change_report/`
- 明确禁止且未改动：`backend/`、Android、Admin、ML、Agent、数据库迁移、认证机制、依赖版本、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 安全复核）；未启动真实授权，未填入任何真实 OAuth 密钥 / URL / 加密密钥

## 背景

上一包已把后端 OAuth callback 改为 303 安全回跳（`change_report/google-oauth-web-handoff.md`），浏览器被送回固定 Web 返回 URI 并只携带一个非敏感结果参数 `googleOAuth=connected|denied|failed`。本包补齐招聘者 Web 端：固定连接状态页、发起/断开连接，以及回跳后安全展示结果提示；同时修正前端契约里漏掉的 `REVOKED` 状态。

## 完成内容

### 1. 修正前端 Google OAuth 契约（REVOKED）

- `models/recruiter.ts`：`GoogleConnectionStatus` 由 `'CONNECTED' | 'DISCONNECTED'` 扩为 `'CONNECTED' | 'DISCONNECTED' | 'REVOKED'`。
- `api/googleOAuthHttpClient.ts`：`isConnectionStatus` 运行时解析新增 `'REVOKED'`，不再把合法的 `REVOKED` 当成 `UNEXPECTED_RESPONSE`。

### 2. 安全校验纯函数（`lib/googleOAuth.ts`）

- `parseOAuthCallbackResult(value)`：只接受 `connected` / `denied` / `failed`，其余（含未知、大小写变体、空、缺失）一律返回 `null`，绝不渲染/记录/回传。
- `isGoogleAuthorizationUrl(value)`：以 `URL` 严格校验授权地址 —— `https`、host 精确为 `accounts.google.com`、path 精确为 `/o/oauth2/v2/auth`；非法（http、伪造 host、子域欺骗、错误 path、畸形字符串、`javascript:`）一律拒绝。

### 3. 受保护页面路由与入口

- `router/index.tsx`：新增 `/recruiter/google-oauth`，挂在 `AppShell` 的 `children` 下，复用既有 Recruiter 鉴权（未登录会跳 `/recruiter/sign-in`）。
- `components/AppShell.tsx`：顶部导航新增最小入口 `Integrations`，指向该页面。

### 4. 连接状态页（`pages/GoogleOAuthPage.tsx`）

- `api/queries.ts` 新增 `useGoogleConnection`（`refetchOnMount: 'always'`）、`useBeginGoogleConnection`、`useDisconnectGoogle`（断开成功即失效并刷新连接状态）。
- 页面状态：
  - `loading`：加载中提示。
  - `error`：安全通用错误 + 重试，不泄露后端细节。
  - `DISCONNECTED`：显示 `Connect Google Calendar` 按钮。
  - `CONNECTED`：显示已连接徽标、连接时间（沿用项目 `toLocaleString()` 习惯）与 `Disconnect` 按钮；断开后立即刷新为 `DISCONNECTED`。
  - `REVOKED`：明确提示「授权已失效」并只提供 `Reconnect Google`，绝不伪装成已连接。
  - `submitting`：连接/断开进行中禁用按钮（文案切换为 `Connecting…` / `Disconnecting…`），阻止重复点击。
- 连接流程：`beginGoogleConnection()` 成功后，仅当 `isGoogleAuthorizationUrl` 通过才跳转；非法则显示安全通用错误且不跳转。
- 错误文案：网络/服务错误统一安全可操作文案；`GOOGLE_OAUTH_NOT_CONFIGURED` 单独提示「当前演示环境尚未配置 Google 集成」，不暴露后端细节。

### 5. OAuth 回跳结果提示

- 读取本页 query 参数 `googleOAuth`，只接受三个安全值并显示简短提示：
  - `connected` → Successfully connected to Google.
  - `denied` → You cancelled the Google authorization.
  - `failed` → The connection wasn't completed. You can try again.
- 未知值忽略；读取后以 `setSearchParams(..., {replace: true})` 清空整个 query，避免刷新/历史记录重复显示，且不展示/记录/回传任何其它 query 参数。
- 回跳后重新读取连接状态：即使结果为 `connected` 但状态读取失败，仍正确显示 error 状态。

### 6. 视觉

- `theme/global.css` 追加最小样式：`.badge.connected/.disconnected/.revoked` 与 `.oauth-banner`（success/info/warn 三态），复用现有配色变量与面板/徽标语言，未引入任何新设计系统或依赖。

## 修改文件

- `web/src/models/recruiter.ts`（修改）：`GoogleConnectionStatus` 增加 `REVOKED`。
- `web/src/api/googleOAuthHttpClient.ts`（修改）：运行时解析接受 `REVOKED`。
- `web/src/lib/googleOAuth.ts`（新增）：回跳结果解析 + 授权 URL 校验。
- `web/src/api/queries.ts`（修改）：新增 `keys.googleConnection` 与三个 hook。
- `web/src/router/index.tsx`（修改）：新增 `/recruiter/google-oauth` 路由。
- `web/src/components/AppShell.tsx`（修改）：新增 `Integrations` 导航入口。
- `web/src/pages/GoogleOAuthPage.tsx`（新增）：连接状态页 + 回跳提示。
- `web/src/theme/global.css`（修改）：追加连接徽标与回跳横幅样式。
- `web/src/lib/googleOAuth.test.ts`（新增）：5 个单元测试。
- `web/src/api/googleOAuthHttpClient.test.ts`（新增）：7 个客户端测试。
- `web/src/pages/GoogleOAuthPage.test.tsx`（新增）：12 个页面测试。
- `tasks/google-meet-integration-plan.md` / `tasks/google-meet-integration-todo.md`（修改）：仅标记本包完成的 Web 连接项。
- `change_report/web-google-oauth-connection-ui.md`（本报告）。

## API 与数据库变化

- 无后端 API / 数据库变更。本包仅修正前端对既有 `/recruiter/google-oauth/status` 契约的解析（补上 `REVOKED`），并消费既有 authorize/status/disconnect 端点。OpenAPI 无改动。

## 测试命令与结果

在 `web/` 执行：

```powershell
npm run typecheck   # 通过
npm run lint        # 通过
npm test            # 18 个测试文件、148 个用例全部通过
npm run build       # 通过（tsc -b && vite build）
```

`npm test` 中与本包相关的新增用例：

- `lib/googleOAuth.test.ts`：6 通过（三种安全回跳结果、未知/空/缺失忽略；授权 URL 通过、http/伪造 host/子域欺骗/错误 path/畸形/`javascript:` 拒绝、非默认端口拒绝）。
- `api/googleOAuthHttpClient.test.ts`：7 通过（授权 URL 解析、三种合法状态含 `REVOKED`、未知状态拒绝、畸形信封拒绝、DELETE 断开、`GOOGLE_OAUTH_NOT_CONFIGURED` 透传）。
- `pages/GoogleOAuthPage.test.tsx`：13 通过（loading、error、disconnected 连接并跳转固定 URL、connected 显示时间并断开刷新、revoked 仅提供 reconnect、三种回跳提示并清空 URL、回跳后状态读取失败仍显示提示与安全 error、未知值忽略、配置缺失提示、非法授权 URL 不跳转、submitting 禁用防重复）。

> 注：`npm run build` 会重新生成 `web/dist/`（构建产物）。本包未手工编辑 `web/dist` / `web/node_modules`；构建输出为任务要求运行 build 的必然产物。

## 未做事项

- 未实现 Google Meet 建会/改期/取消表单（Task 4 的排程 UI：auto-create Meet 选择与 sync-state 渲染仍待后续包）。
- 未接入真实 Google 凭据；真实授权仍需人工把 `GOOGLE_OAUTH_WEB_RETURN_URI` 配置为本页面实际地址（例如 `https://<host>/recruiter/google-oauth`），并注册到 Google Cloud OAuth 客户端。**不得**在代码或本报告中写入任何真实密钥。

## 下一步

- 待 Task 4 实现招聘者排程 UI（在面试弹窗中选择 `GOOGLE_MEET` 并渲染同步状态）。
- 人工配置 `GOOGLE_OAUTH_WEB_RETURN_URI` 后，做一次真实两账号演示（recruiter 连接 Google → 排期 → 候选人 Android 看到 Meet 链接）。
