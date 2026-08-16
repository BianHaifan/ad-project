# 修改报告：Google OAuth 授权完成后的安全网页回跳（OAuth browser handoff）

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-15
- 修改范围：`backend/`（`integration.google` 的 callback 回跳逻辑 + 配置校验 + 对应测试）、`docs/openapi-v1.yaml`、`tasks/google-meet-integration-*.md`、`change_report/`
- 明确禁止且未改动：`web/`、Android、Admin、ML、Agent、数据库迁移、用户登录认证、Google Calendar 建会逻辑、依赖版本、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 安全复核）；未启动真实授权，未填入真实 OAuth 密钥/URL/加密密钥

## 背景

此前 OAuth callback 成功/失败后仍返回 JSON 连接对象（含 `connected/status/connectedAt`），浏览器停留在后端 callback 端点，无法回到 recruiter Web 应用；且失败路径的错误码可能携带细节。本包把 callback 改为**浏览器安全回跳**：始终以 HTTP 303 See Other 跳到服务端固定配置的 Web 返回 URI，只带一个非敏感结果参数 `googleOAuth=connected|denied|failed`，回跳 URL 中永不出现 code / state / access token / refresh token / Google 原始错误 / 异常 / recruiter ID / email / Meet 链接。

## 回跳安全模型

- **返回 URI 只来自服务端配置**：新增配置项 `GOOGLE_OAUTH_WEB_RETURN_URI`（`app.google-oauth.web-return-uri`），绝不从 query / header / cookie / state / 请求体读取。它独立于 `GOOGLE_OAUTH_REDIRECT_URI`（token exchange 的回调地址），两者不互相替换。
- **固定结果值**：callback 只向该 URI 追加一个参数 `googleOAuth=connected|denied|failed`（`GoogleOAuthCallbackOutcome` 枚举），由 presenter 统一拼接 `Location` 并设置 `Cache-Control: no-store`。分类结果由 service 计算，presenter/controller 只负责映射，不接触 token/Google SDK/HTTP 重定向细节。
- **严格启动校验**：`GoogleOAuthConfiguration` 在 `@PostConstruct` 调用 `properties.webReturnUri()`，配置存在但非法时启动即失败。`WebReturnUriValidator` 只接受：HTTPS 绝对 URI；或仅限回环（`localhost`/`127.0.0.1`/`::1`）的 HTTP。拒绝：相对 URI、空 host、userinfo、fragment、非 http/https scheme、外部 HTTP。
- **fail-closed**：配置缺失或非法时，callback 不产生任意跳转——未配置返回 JSON 503 `GOOGLE_OAUTH_NOT_CONFIGURED` 且无 `Location` 头（`requireConfigured()` 最先执行）。
- **无开放重定向**：callback 完全忽略客户端带来的任意参数（`returnUrl`/`redirect_uri`/`next` 等一律丢弃），回跳目标永远是服务端固定值。
- **state 一次性不被吞掉**：先在同一独立事务中消费 state，再处理 `error`/`code`。denied 也必须先校验并消费有效 state；token exchange / 解密 / 连接写失败只返回 `failed`，但 state 保持已消费、不可重放。

## 模块边界

- OAuth service 继续持有 state / PKCE / token / 持久化；新增的 `GoogleOAuthCallbackPresenter`（api 层）把分类结果映射为固定回跳，`GoogleOAuthController` 仅转发。
- 无 token / Google SDK / HTTP 重定向细节泄漏到业务模块；authorize / status / disconnect 的 recruiter-only 权限不变；无新迁移。

## callback 各路径现在的行为

| 场景 | state 是否被消费 | 返回 |
| --- | --- | --- |
| 未配置（含 webReturnUri 非法） | — | 503 `GOOGLE_OAUTH_NOT_CONFIGURED`，无 `Location` |
| `state` 缺失/为空 | 否 | 303 `googleOAuth=failed` |
| 未知/篡改/过期/重放 state | 否（重放=首次已消费） | 303 `googleOAuth=failed` |
| 有效 state + `error=access_denied` | 是（已提交） | 303 `googleOAuth=denied` |
| 有效 state + `error` 其他值 | 是（已提交） | 303 `googleOAuth=failed` |
| 有效 state + `code` 缺失 | 是（已提交） | 303 `googleOAuth=failed` |
| 有效 state + token exchange / 解密 / 连接写失败 | 是（已提交） | 303 `googleOAuth=failed` |
| 有效 state + 成功 | 是（已提交） | 303 `googleOAuth=connected` |

所有 303 均带 `Cache-Control: no-store`；`Location` 中只有 `googleOAuth` 参数，绝不包含 code/state/token/错误/身份信息。

## 修改文件

- `backend/src/main/java/com/adproject/integration/google/application/WebReturnUriValidator.java`（新增）
  - 纯静态校验器：`parse(String)` 空白返回 `null`，非法抛 `IllegalArgumentException`；HTTPS 绝对 URI 或仅回环 HTTP 通过。
- `backend/src/main/java/com/adproject/integration/google/application/GoogleOAuthProperties.java`（修改）
  - record 增加 `webReturnUri` 字段；新增 `webReturnUri()` 返回校验后的 `URI`；`isConfigured()` 要求 `webReturnUri() != null`。
- `backend/src/main/java/com/adproject/integration/google/application/GoogleOAuthConfiguration.java`（新增）
  - `@PostConstruct` 校验 webReturnUri，存在但非法时启动失败（fail-fast）。
- `backend/src/main/java/com/adproject/integration/google/application/GoogleOAuthCallbackOutcome.java`（新增）
  - 枚举 `CONNECTED/DENIED/FAILED`，`value()` 为 `connected/denied/failed`。
- `backend/src/main/java/com/adproject/integration/google/api/GoogleOAuthCallbackPresenter.java`（新增）
  - `redirect(outcome)` 构建 303 + `Location`（`base` + `?`/`&` + `googleOAuth=`）+ `Cache-Control: no-store`。
- `backend/src/main/java/com/adproject/integration/google/api/GoogleOAuthController.java`（修改）
  - 注入 presenter；`callback` 返回 `ResponseEntity<Void>`。
- `backend/src/main/java/com/adproject/integration/google/application/GoogleOAuthService.java`（修改）
  - 移除 `handleCallback` 上的 `@Transactional`，注入 `PlatformTransactionManager` 构建 `TransactionTemplate`；返回 `GoogleOAuthCallbackOutcome`；`persistConnection` 改用 `transactionTemplate.executeWithoutResult`，避免后置失败触发 `UnexpectedRollbackException` 且不吞掉 state 消费。
- `backend/src/main/resources/application.yml`（修改）
  - `app.google-oauth` 增加 `web-return-uri: ${GOOGLE_OAUTH_WEB_RETURN_URI:}`（仅环境变量，无真实 URL）。
- `backend/src/test/resources/application-test.yml`（修改）
  - 增加 `web-return-uri: http://localhost:3000/recruiter/google-oauth`（回环）。
- `backend/src/test/java/com/adproject/integration/google/application/GoogleOAuthIntegrationTest.java`（修改）
  - callback 相关用例改为断言 303 + 固定 `Location` + `Cache-Control: no-store`；新增成功不泄露、忽略恶意参数、未知 error 等用例。
- `backend/src/test/java/com/adproject/integration/google/application/GoogleOAuthNotConfiguredIntegrationTest.java`（修改）
  - 新增 `callbackFailsClosedWithoutRedirectWhenNotConfigured`（503 + 无 `Location`）。
- `backend/src/test/java/com/adproject/integration/google/application/WebReturnUriValidatorTest.java`（新增）
  - HTTPS / 回环 HTTP / 外部 HTTP / fragment / userinfo / 相对 / 空 host / 不安全 scheme / 空白 覆盖。
- `docs/openapi-v1.yaml`（修改）
  - callback 描述改为 303 浏览器回跳，三种安全结果值；响应由 200/400/502/503 改为 `303`（带 Location + Cache-Control）与 `503`（未配置，无重定向），不再返回 JSON 连接对象。
- `tasks/google-meet-integration-plan.md`（修改）
  - Task 2 标注后端完成（含回跳），新增 `googleOAuth=connected|denied|failed` 验收项，Web UI 项保持未勾选。
- `tasks/google-meet-integration-todo.md`（修改）
  - 新增 `- [x]` 安全 callback 回跳条目。

## 测试与验证

### 命令

```
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -o test
```

（Maven 3.9.16 wrapper dist + Microsoft OpenJDK 21.0.8，离线模式）

### 结果

- 本包 OAuth 相关测试全部通过（50 用例，0 失败）：
  - `GoogleOAuthIntegrationTest` 19 用例（含成功 303 固定 URI + `connected` + `no-store` + 不泄露；denied 消费 state；token exchange 失败 `failed` 且不可重放；未知/篡改/过期/重放 state；`error` 但无 state；未知 error；恶意 `returnUrl`/`redirect_uri`/`next` 忽略）。
  - `GoogleOAuthNotConfiguredIntegrationTest` 2 用例（含未配置 callback 503 且无 `Location`）。
  - `WebReturnUriValidatorTest` 6 用例（HTTPS / 回环 HTTP / 外部 HTTP / fragment / userinfo / 相对 / 空 host / 不安全 scheme / 空白）。
  - `GoogleMeetProvisioningIntegrationTest` 23 用例（既有日历建会回归，未受影响）。
- 全量后端：`Tests run: 170, Failures: 5, Errors: 0, Skipped: 6`，`BUILD FAILURE`。
  - 5 个失败全部位于 `com.adproject.job.RecruiterJobIntegrationTest`（岗位状态流转 `changeStatus`），与本包（OAuth 回跳）**无关**：本包仅改动 `integration.google.*`、`application.yml`/`application-test.yml` 的 OAuth 配置、文档，未触碰任何 `job` 模块代码。该 5 个用例在单独运行 `RecruiterJobIntegrationTest` 时同样失败（非测试顺序/共享状态导致），属既有问题。
  - 6 个跳过为 `MySqlFlywayIntegrationTest`（本机 Windows 环境未检测到 Docker/Testcontainers，故跳过；未改动任何迁移）。

## API 行为变化

- `GET /api/v1/recruiter/google-oauth/callback` 成功/失败路径不再返回 JSON，统一为 HTTP 303 See Other 跳转到服务端固定 Web 返回 URI（仅 `googleOAuth=connected|denied|failed`）。
- 未配置仍返回 JSON 503 `GOOGLE_OAUTH_NOT_CONFIGURED`，且无 `Location` 头。
- authorize / status / disconnect 端点语义与权限不变。

## 限制与未做事项

- 仍未接入真实 Google 凭据，未执行真实 Google 授权；token exchange 仅由测试 fake client 模拟。
- Web 端的连接状态页与排程 UI（connecting/connected/denied/expired/retry）仍待后续包实现（见 plan Task 2 / Task 4 的未勾选项）。
- 无新增数据库迁移。
