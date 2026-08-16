# 修改报告：Google OAuth Web 回跳地址配置样例补全

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-15
- 本包性质：**配置样例补全**，补上后端已读取但 `.env.example` 缺失的一个变量，不改动任何业务代码
- 允许且修改的文件：`.env.example`
- 允许且新增的文件：`change_report/google-oauth-web-return-config-example.md`（本报告）
- 明确禁止且未改动：任何真实 `.env` / `.env.local`、`backend/src/**`、`web/src/**`、`android/**`、OAuth 业务逻辑、API、数据库迁移、OpenAPI、依赖、Docker、Admin、ML、Agent、`web/dist`、`web/node_modules`
- 未提交、未推送；未读取任何真实 `.env`；未执行真实 Google OAuth；未发起任何网络请求

## 问题与根因

- `backend/src/main/resources/application.yml` 已声明 `web-return-uri: ${GOOGLE_OAUTH_WEB_RETURN_URI:}`。
- `GoogleOAuthProperties.isConfigured()` 依赖 `resolvedWebReturnUri() != null`，即只有 `GOOGLE_OAUTH_WEB_RETURN_URI` 配置合法时 OAuth 才会被判定为「已配置」。
- 但 `.env.example` 此前缺少 `GOOGLE_OAUTH_WEB_RETURN_URI`，导致照抄样例配置的本机环境会缺少该变量，OAuth 无法进入已配置状态。

## 两个 URI 的不同职责（本次修正的核心）

| 变量 | 职责 | 必须满足 |
| --- | --- | --- |
| `GOOGLE_OAUTH_REDIRECT_URI` | Google 授权完成后回到**后端 callback** 的地址 | 必须在 Google Cloud 中精确登记 |
| `GOOGLE_OAUTH_WEB_RETURN_URI` | 后端 callback 成功后，将浏览器**安全跳回招聘者 Web 页面**的固定地址 | 必须指向现有 `/recruiter/google-oauth` 路由 |

两者不是同一个地址，不能混用：前者是「回后端」的 OAuth 授权回调，后者是「回前端」的最终落地页。

## 修改内容

在 `.env.example` 的 `GOOGLE_OAUTH_*` 配置块中，紧邻 `GOOGLE_OAUTH_REDIRECT_URI` 之后新增安全示例：

```dotenv
# Fixed recruiter Web page used after the backend OAuth callback; replace with the exact active Web origin.
GOOGLE_OAUTH_WEB_RETURN_URI=http://localhost:5173/recruiter/google-oauth
```

- 示例路径 `/recruiter/google-oauth` 与 `web/src/router/index.tsx` 中既有路由 `{path:'google-oauth',element:<GoogleOAuthPage/>}`（挂载于 `/recruiter` 下）一致，即 `/recruiter/google-oauth`。
- 保持所有既有占位符与规则不变；未填入真实 client ID、secret、加密密钥、生产域名或真实回调地址。
- 未把 Web return URI 错写成后端 callback URI（示例值明确为前端页面路径，而非后端 `/api/v1/.../callback`）。
- 未修改后端逻辑；这是配置样例补全，不是新功能。

## API 与数据库变化

- 无。未改动任何接口、DTO、数据库字段、OpenAPI、Flyway 迁移或实体结构，也未改动任何后端 / 前端业务代码。

## 验证结果

执行：

```powershell
git diff --check -- .env.example
git diff -- .env.example
git status --short -- .env.example change_report/google-oauth-web-return-config-example.md
```

真实结果：

- `git diff --check -- .env.example`：**无输出**（无空白错误）。
- `git diff -- .env.example`：仅新增 `GOOGLE_OAUTH_WEB_RETURN_URI` 一行及其注释，未改动其他占位符。
- `git status --short -- .env.example change_report/google-oauth-web-return-config-example.md`：` M .env.example`（报告文件在生成后将为未跟踪新增）。

人工确认：

- `.env.example` 现在同时包含五个变量：`GOOGLE_OAUTH_CLIENT_ID`、`GOOGLE_OAUTH_CLIENT_SECRET`、`GOOGLE_OAUTH_REDIRECT_URI`、`GOOGLE_OAUTH_WEB_RETURN_URI`、`GOOGLE_TOKEN_ENCRYPTION_KEY` ✓
- Web return 示例路径为现有 `/recruiter/google-oauth` ✓
- 无真实凭据（全部为 `replace-with-*` 占位符或 localhost 示例） ✓

## 限制

- 本改动仅补全**配置样例**，不修复任何业务缺陷，也不改变 OAuth 状态机。
- 示例中的 `http://localhost:5173/recruiter/google-oauth` 仅为本地开发默认值；部署到真实环境时，项目所有者须将其替换为实际生效的 Web origin，并确保该值与 Google Cloud 中登记的授权回调域名约束一致（`GOOGLE_OAUTH_WEB_RETURN_URI` 本身不需要在 Google Cloud 精确登记，但需与前端实际部署地址一致）。

## 下一步（真实双账号演示前需人工配置）

1. 项目所有者在本机**未跟踪**环境中，从 `.env.example` 复制一份本地环境文件，并填入真实值：
   - `GOOGLE_OAUTH_CLIENT_ID`、`GOOGLE_OAUTH_CLIENT_SECRET`（来自 Google Cloud OAuth 客户端）；
   - `GOOGLE_OAUTH_REDIRECT_URI`（与 Google Cloud 精确登记的授权回调地址一致）；
   - `GOOGLE_OAUTH_WEB_RETURN_URI`（与招聘者 Web 实际部署地址一致，落地到 `/recruiter/google-oauth`）；
   - `GOOGLE_TOKEN_ENCRYPTION_KEY`（可解码为 32 字节 AES 密钥）。
2. 配置完成后确认 `git status` 不出现该本地环境文件（已被 `.gitignore` 忽略）。
3. 前置条件满足后，再进行真实双账号演示（招聘者连接 Google → 创建 Meet 面试 → 候选人 Android 刷新打开链接 → 改期 / 取消或完成）。
