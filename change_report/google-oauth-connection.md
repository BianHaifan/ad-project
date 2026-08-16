# 修改报告：Google Meet 第二包（Recruiter Google OAuth connection，仅连接账号）

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 对应计划/任务：`tasks/google-meet-integration-plan.md` Task 2「Secure connection slice」
- 修改范围：`backend/`（`integration.google` 模块、V10 迁移、配置、测试）、`web/`（模型类型、API 客户端、mock）、`docs/openapi-v1.yaml`、`.env.example`、`change_report/`
- 明确禁止且未改动：面试排期实际建会逻辑、Calendar/Meet 事件创建、改期/取消同步、Android、ML、Agent、Admin、项目登录/JWT、Messages；未提交/未推送（等待 Codex 安全复核）；**未写入任何真实 Client Secret、Token、加密密钥或真实回调 URL**

## 完成内容

本包实现招聘者 Google 账号的「仅连接」能力：发起授权、回调落库、读取状态、断开连接，为下一包「自动创建 Meet」做安全铺垫。**本包绝不创建 Calendar 事件或 Meet 链接。**

- 新增 V10 迁移，两张表：`google_recruiter_connections`（每招聘者至多一行，加密的 access/refresh token、过期时间、状态、版本、创建/更新时间）与 `google_oauth_states`（仅存 SHA-256 状态哈希、发起招聘者、加密 PKCE verifier、创建/过期/消费时间；10 分钟过期、单次使用、绑定招聘者）。
- token 与 PKCE verifier 一律用 JDK AES-GCM（12 字节随机 IV + 128-bit tag）加密后落库；密钥只来自本地环境变量 `GOOGLE_TOKEN_ENCRYPTION_KEY`（Base64 编码的 256-bit 密钥）。**数据库、响应、日志、异常信息、测试输出、本报告中均无任何明文 token / verifier / secret。**
- 后端 OAuth 流程：`authorize` / `status` / `disconnect` 三个受保护端点 + 一个固定服务端回调。仅 `RECRUITER` 角色且仅本人连接；Candidate/未登录 → 403/401。授权 URL 固定 Google 主机、固定 redirect URI、最小 Calendar scope（`calendar.events`），绝不接受客户端提供的 URL/scope/redirect；采用授权码 + PKCE + `access_type=offline`。
- 回调校验状态哈希/过期/单次使用/绑定，拒绝重放、过期、未知状态、provider 拒绝、token 交换失败；配置缺失时 fail-closed，返回稳定错误码 `GOOGLE_OAUTH_NOT_CONFIGURED`。
- `status` 仅返回 `connected`、`status`、`connectedAt`，绝不返回 token。
- `disconnect` 永久删除连接与未消费状态。
- 真实 OAuth HTTP 交换封装在 `integration.google` 内的 `HttpGoogleOAuthClient`（JDK `HttpClient`，固定 token 端点、显式 10s 超时、不记录敏感信息）；测试用 fake client 替换，绝不打真实 Google。
- 用真实连接存储实现 `MeetingProvisioningPort.isConnected(recruiterId)`；`isProvisioningAvailable(recruiterId)` **仍恒返回 false**（建会逻辑留待下一包），因此「已连接但未实现建会」的招聘者仍被稳定拒绝，不会产出半成品面试。

## 为什么 V10（而非其他版本号）

- `V9__add_interview_meeting_provider.sql` 已被上一包占用（interviews 的 provider/sync 列）。本包新增的两张连接相关表必须从 **V10** 开始；未改动 V1–V9。

## 修改文件

### 后端（新增）

- `backend/src/main/resources/db/migration/V10__create_google_oauth_connection.sql`
  - 主要变化：`google_recruiter_connections`（`uk_google_connections_recruiter` 唯一、`fk_google_connections_recruiter`→users、`chk_google_connections_version CHECK(version>=1)`）与 `google_oauth_states`（`uk_google_oauth_states_hash` 唯一、`fk_google_oauth_states_recruiter`→users、`idx_google_oauth_states_expires`）。不改 V1–V9。
- `com/adproject/integration/google/domain/GoogleConnectionStatus.java`（枚举 `CONNECTED`）
- `com/adproject/integration/google/infrastructure/GoogleRecruiterConnectionEntity.java`、`GoogleRecruiterConnectionRepository.java`
  - 主要变化：实体含全部列、`replaceTokens(...)`（更新 token/过期/updatedAt 且 `version+1`）、`version=1` 建行；仓储 `findByRecruiterId` / `existsByRecruiterIdAndStatus` / `deleteByRecruiterId`。
- `com/adproject/integration/google/infrastructure/GoogleOAuthStateEntity.java`、`GoogleOAuthStateRepository.java`
  - 主要变化：实体含 `state_hash`（char(64) 唯一）、`pkce_verifier_encrypted`（TEXT）、`consumed_at`（可空）、`consume(now)`；仓储 `findByStateHashForUpdate`（`@Lock(PESSIMISTIC_WRITE)` + `@Query`，单次使用并发安全）、`deleteByRecruiterId`。
- `com/adproject/integration/google/application/GoogleOAuthProperties.java`
  - 主要变化：`@ConfigurationProperties(prefix="app.google-oauth")` 记录 `(clientId, clientSecret, redirectUri, tokenEncryptionKey)`；字段可空（启动不因缺配置失败）；`isConfigured()` 仅当四项非空且密钥为有效 32 字节 Base64 时返回 true。
- `com/adproject/integration/google/application/SecretCipher.java`
  - 主要变化：AES-GCM 加解密；`resolveKey` 对缺失/非法密钥返回 null 且永不抛异常；`encrypt`/`decrypt` 在密钥缺失时 fail-closed 抛 `IllegalStateException`。
- `com/adproject/integration/google/application/OAuthUtil.java`
  - 主要变化：包私有工具：随机 state / PKCE verifier（Base64url 32 字节）、`codeChallenge`（S256）、`sha256Hex`。
- `com/adproject/integration/google/application/GoogleOAuthClient.java`、`TokenExchangeResult.java`、`GoogleOAuthTokenExchangeException.java`、`HttpGoogleOAuthClient.java`
  - 主要变化：端口接口 `exchangeAuthorizationCode` + 记录 `TokenExchangeResult` + 无敏感信息异常 + JDK `HttpClient` 实现（固定 `https://oauth2.googleapis.com/token`、10s 超时、非 2xx 仅记录状态码、要求 access+refresh token 非空）。
- `com/adproject/integration/google/application/GoogleOAuthService.java`
  - 主要变化：`beginAuthorization` / `status` / `disconnect` / `handleCallback`；常量固定授权端点、scope、600s TTL；`requireRecruiter`（非 RECRUITER→403）、`requireConfigured`（未配置→503）、`invalidState`（→400）。
- `com/adproject/integration/google/application/MeetingProvisioningService.java`
  - 主要变化：实现 `MeetingProvisioningPort`；`isConnected` 读真实连接存储，`isProvisioningAvailable` 恒返回 false。
- `com/adproject/integration/google/api/GoogleOAuthDtos.java`、`GoogleOAuthController.java`
  - 主要变化：DTO 信封（`AuthorizationEnvelope` / `ConnectionStatusEnvelope`）+ 四个端点（`authorize`/`status`/`disconnect`/`callback`，回调 `code`/`state`/`error` 均 `@RequestParam(required=false)`）。

### 后端（既有文件修改 / 删除）

- 删除 `com/adproject/integration/google/UnavailableMeetingProvisioningPort.java`（避免 `MeetingProvisioningPort` 双 bean；由 `MeetingProvisioningService` 取代）。
- `com/adproject/common/config/SecurityConfig.java`：回调路径 `/api/v1/recruiter/google-oauth/callback` 加入 `permitAll`（Google 浏览器重定向不带 JWT，安全由一次性高熵 state + PKCE + 绑定保证）。
- `com/adproject/BackendApplication.java`：`@EnableConfigurationProperties({AuthProperties.class, GoogleOAuthProperties.class})`。
- `backend/src/main/resources/application.yml`：新增 `app.google-oauth.client-id/client-secret/redirect-uri/token-encryption-key`，均默认空（`${GOOGLE_OAUTH_CLIENT_ID:}` 等）。
- `backend/src/test/resources/application-test.yml`：新增 `app.google-oauth` 测试值（`test-client-id`/`test-client-secret`/本地回调/确定 32 字节密钥 `MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=`）。

### 契约与文档

- `docs/openapi-v1.yaml`：新增 4 条路径（`authorize`/`status`/`disconnect`/`callback`）；新增 `GoogleAuthorizeResponse`、`GoogleConnectionStatus`、`GoogleConnection` schema；`GoogleConnection` 的 `connectedAt` 标注 `nullable: true`。
- `.env.example`：新增 4 个 Google OAuth 占位符（无真实值）。
- `.gitignore`：确认 `.env` 规则已覆盖 `backend/.env`（`git check-ignore backend/.env` 命中），`.env.example` 不受影响。

### 招聘者 Web

- `web/src/models/recruiter.ts`：新增 `GoogleConnectionStatus`、`GoogleAuthorizeResponse`、`GoogleConnection` 类型。
- `web/src/api/contract.ts`：新增 `googleOAuthAuthorize`/`googleOAuthStatus`/`googleOAuth` 路径。
- `web/src/api/recruiterRepository.ts`：接口新增 `beginGoogleConnection`/`getGoogleConnection`/`disconnectGoogle`。
- `web/src/api/googleOAuthHttpClient.ts`（新增）：真实 HTTP 客户端，解析授权与连接状态信封。
- `web/src/api/repository.ts`：将三个方法接入 `googleOAuthHttpClient`。
- `web/src/mocks/mockRecruiterRepository.ts`：Omit 列表加入三个方法（由真实客户端提供，不再 mock）。

### 测试

- `backend/src/test/java/com/adproject/integration/google/application/GoogleOAuthIntegrationTest.java`（新增，11 用例）
  - 覆盖：未登录/Candidate/Recruiter 角色；授权 URL 固定主机/scope/redirect/`access_type=offline`/PKCE；状态 disconnected→connected 且不泄露 token；回调加密落库（密文不含明文 + 解密回环）；未知/篡改 state、重放、过期 state；provider 拒绝；token 交换失败（回滚后连接不写、state 未消费可重试）；disconnect 删除连接与状态；「已连接但建会不可用」仍阻止面试（真实 `MeetingProvisioningPort` 接入验证）。
- `backend/src/test/java/com/adproject/integration/google/application/GoogleOAuthNotConfiguredIntegrationTest.java`（新增）
  - 覆盖：配置缺失时 `authorize` fail-closed 返回 503 `GOOGLE_OAUTH_NOT_CONFIGURED`。
- `backend/src/test/java/com/adproject/auth/MySqlFlywayIntegrationTest.java`（修改）
  - 新增 `v10MigrationCreatesGoogleOAuthConnectionSchema`：校验 2 表、2 唯一、2 外键、1 CHECK、1 索引。

## API 变化

- 是否变化：是（新增端点，非破坏性）
- `POST /api/v1/recruiter/google-oauth/authorize` → `200 {data:{authorizationUrl}}`；非 RECRUITER→403/401；未配置→503 `GOOGLE_OAUTH_NOT_CONFIGURED`。
- `GET /api/v1/recruiter/google-oauth/status` → `200 {data:{connected,status,connectedAt}}`（仅这三个字段，无 token）。
- `DELETE /api/v1/recruiter/google-oauth` → `204`（永久删除连接与未消费状态）。
- `GET /api/v1/recruiter/google-oauth/callback?code&state&error`（permitAll）→ `200 {data:{connected,status,connectedAt}}`；`error`→400 `GOOGLE_OAUTH_DENIED`；state 无效→400 `GOOGLE_OAUTH_STATE_INVALID`；交换失败→502 `GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED`；未配置→503 `GOOGLE_OAUTH_NOT_CONFIGURED`。
- 与 OpenAPI 是否一致：是（已同步 schema 与字段）。

## 数据库变化

- 是否变化：是
- 新增迁移：`V10__create_google_oauth_connection.sql`（仅建两张新表，不改 V1–V9）
- 新表：`google_recruiter_connections`、`google_oauth_states`
- 约束/索引：`uk_google_connections_recruiter`、`uk_google_oauth_states_hash`（唯一）；`fk_google_connections_recruiter`、`fk_google_oauth_states_recruiter`（外键）；`chk_google_connections_version`（CHECK）；`idx_google_oauth_states_expires`（索引）

## 权限与安全

- 涉及角色：Recruiter（连接本人账号）。
- 认证/角色/所有权：`authorize`/`status`/`disconnect` 走既有 JWT 过滤器 + RECRUITER 角色校验 + 本人 `recruiterId` 所有权；`callback` 为 permitAll，安全锚点是一次性高熵 state（仅存 SHA-256 哈希）+ PKCE + 发起招聘者绑定（回调使用 state 行内存储的 `recruiter_id`，不接受客户端提供的招聘者 id）。
- 敏感信息：token 与 PKCE verifier 以 AES-GCM 加密落库，密钥仅来自本地环境变量；状态哈希只存 SHA-256；`status` 只返回连接状态三字段；日志/异常仅记录 HTTP 状态码，不记录 token/secret/响应体；本报告与测试输出均无明文 secret。
- 并发：state 单次消费用 `PESSIMISTIC_WRITE` + `consumed_at` 检查；连接 upsert 在事务内，交换失败回滚不消费 state（可重试）。
- 新增错误码：`GOOGLE_OAUTH_NOT_CONFIGURED`(503)、`GOOGLE_OAUTH_DENIED`(400)、`GOOGLE_OAUTH_STATE_INVALID`(400)、`GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED`(502)。

## 测试与验证

### 后端（Maven 3.9.16 + Microsoft OpenJDK 21.0.8，离线 `mvn -o test`）

- 全量：`Tests run: 108, Failures: 0, Errors: 0, Skipped: 6`，`BUILD SUCCESS`
  - `GoogleOAuthIntegrationTest`：11 用例通过。
  - `GoogleOAuthNotConfiguredIntegrationTest`：1 用例通过。
  - `RecruiterInterviewIntegrationTest`：12 用例通过（含既有 `googleMeetRejectedWhenConnectedButProvisioningUnavailable`，用 `@MockitoBean` 验证面试服务层阻止逻辑）。
  - 6 个跳过为 `MySqlFlywayIntegrationTest`（Testcontainers 在本机未检测到 Docker，V10 新迁移测试随该类跳过）。
  - 说明：`ddl-auto: validate` + H2（MySQL 模式）下 Flyway V1–V10 全量迁移与实体映射校验通过。

### 真实 MySQL 8.4（临时库，绝不触碰既有 `adproject` 库）

- 在 `adproject-local-mysql`（mysql:8.4）上创建临时库 `adproject_v10verify_<ts>`，按版本序依次应用 V1–V10，全部成功。
- V10 校验结果：`tables=2`、`unique=2`、`fk=2`、`check=1`、`idx=1`、`total_tables=19`。
- 校验后立即 `DROP DATABASE` 清理，未操作既有 `adproject` 库/卷。

### Web

- `npm run typecheck`：通过。
- `npm run lint`：通过。
- `npm test`：`15 files, 122 tests` 全部通过。
- `npm run build`：`tsc -b && vite build` 成功。

## 已知限制

- 本包只做「连接账号」：**未实现**真实 Calendar/Meet 事件创建、改期/取消同步、token 刷新（access token 过期后暂不续期）、revoke 状态、email/消息推送、候选人 accept/reject、Microsoft Teams。
- `isProvisioningAvailable` 仍恒返回 false，因此已连接招聘者发起 `GOOGLE_MEET` 面试仍被 `GOOGLE_MEET_PROVISIONING_UNAVAILABLE`(409) 拒绝——这是有意为之，避免半成品面试。
- Web 只新增了 API 客户端与类型，**未新增任何连接入口 UI**（连接入口留待下一包「自动创建 Meet」UI 一并处理）。
- 回调端点 `permitAll` 返回 JSON（而非浏览器友好的重定向页）；真实浏览器连接体验需在下一包补充登录态续接的前端回调页面。

## Google Cloud 前置条件（未完成，需项目负责人本地配置）

- Google Cloud OAuth client ID / client secret（仅本地环境变量，不进仓库）
- 已审批的 HTTPS 回调 URL（部署演示 + 本地开发各一），与 `GOOGLE_OAUTH_REDIRECT_URI` 一致
- 随机 `GOOGLE_TOKEN_ENCRYPTION_KEY`（Base64 编码 32 字节，即 256-bit AES 密钥）
- 启用 Calendar API、OAuth 同意屏设为 Testing 并加入测试账号、`access_type=offline` 需在生产开启 refresh token

## 风险与注意事项

- `GOOGLE_TOKEN_ENCRYPTION_KEY` 一旦生成必须妥善保存并固定不变；密钥更换会导致已存密文无法解密（本包未做密钥轮换）。
- V10 为追加式建表，不改已发布 V1–V9；请确认目标环境 Flyway 兼容（沿用既有命名与风格）。
- `state_hash` 唯一索引建立在 `CHAR(64)` 上，`state` 原始值不入库、只在授权 URL 中返回一次，回调后即凭哈希定位并消费。
- 回调为 `permitAll`，安全边界完全依赖一次性高熵 state + PKCE + 绑定；请 Codex 重点复核该认证边界与 token 交换失败后的「不消费 state 可重试」语义。
- 交换失败抛 `GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED`(502) 且事务回滚；若需限制重试次数需在下一包补充。

## 下一步建议

- 下一包（Task 3/4）实现真实建会：`MeetingProvisioningPort` 增加 `createMeeting`/`reschedule`/`cancel`，在 `isProvisioningAvailable` 中返回真实能力，并把 access token 刷新接入连接生命周期。
- 在 Testcontainers 可用的环境补跑 `MySqlFlywayIntegrationTest`，端到端确认 V1–V10 全量迁移。
- 补充前端连接入口 UI（发起授权、展示连接状态、断开）与回调后的登录态续接页面。
