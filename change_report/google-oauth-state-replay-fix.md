# 修改报告：Google OAuth callback state 一次性语义修复

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 修改范围：`backend/`（`integration.google.application` 的 state 消费逻辑 + 对应回归测试）、`change_report/`
- 明确禁止且未改动：Calendar/Meet 建会、前端 OAuth UI、V10 数据库迁移、任何第三方依赖、`web/dist`、`web/node_modules`、项目登录/JWT、Android/ML/Agent/Admin/Messages
- 未提交、未推送（等待 Codex 安全复核）；未启动真实授权，未填入真实 OAuth 密钥

## 根因

`GoogleOAuthService.handleCallback` 整体标注 `@Transactional`，其中对 state 的消费（`oauthState.consume(now)` + `flush()`）与后续的 token exchange、连接保存处于**同一个事务**。

当 token exchange 失败（或任何后续步骤抛 `ApiException`）时，整个事务回滚，`google_oauth_states.consumed_at` 被恢复到 `null`——旧 state 重新变得"未消费"，可被重放。此外，原实现对 `error` 参数的处理早于 state 校验，导致带 `error=access_denied` 的回调无需有效 state 就直接返回 `GOOGLE_OAUTH_DENIED`，绕过了 state 一次性校验。二者叠加构成安全缺陷。

## 修复方式

新增独立 Spring Bean `GoogleOAuthStateConsumer`，把 state 的"加锁查询 → 有效性检查 → 标记 `consumed_at` → `flush` → 提交 → 返回 `recruiterId` 与密文 verifier"放进 `@Transactional(propagation = REQUIRES_NEW)` 方法，使其在独立且已提交的事务中完成，不再受外层回调事务回滚影响。

`GoogleOAuthService.handleCallback` 的顺序调整为：

1. `state` 缺失/为空 → `GOOGLE_OAUTH_STATE_INVALID`（即使带 `error` 也先校验 state）。
2. 调用 `stateConsumer.consume(state)`，在独立事务中永久消费 state（缺失/查不到/过期/已消费 → `GOOGLE_OAUTH_STATE_INVALID`）；悲观锁随该事务提交而释放，后续 token exchange **不持有任何数据库锁**。
3. `error` 非空 → `GOOGLE_OAUTH_DENIED`（此时 state 已被消费）。
4. `requireConfigured()` → 配置缺失时 `GOOGLE_OAUTH_NOT_CONFIGURED`（fail-closed）。
5. `code` 缺失/为空 → `GOOGLE_OAUTH_STATE_INVALID`。
6. 解密 PKCE verifier（消费之后进行；解密失败不影响 state 已消费的事实）。
7. token exchange 失败 → `GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED`（502，通用信息）。
8. 成功：加密并写入连接，返回 `{connected, status, connectedAt}`。

保持既有 AES-GCM 加密、连接状态接口、断开接口与权限边界不变。

## callback 各失败路径现在的行为

| 场景 | state 是否被消费 | 返回 |
| --- | --- | --- |
| `error` 但无 `state` | 否（无有效 state 可消费） | 400 `GOOGLE_OAUTH_STATE_INVALID` |
| 有效 state + `error=access_denied` | **是（已提交）** | 400 `GOOGLE_OAUTH_DENIED` |
| 有效 state + token exchange 失败 | **是（已提交）** | 502 `GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED` |
| 有效 state + verifier 解密失败 | **是（已提交）** | 500（密钥缺失/密文损坏，通用） |
| 有效 state + 连接写入失败 | **是（已提交）** | 500（通用） |
| 有效 state + 成功 | **是（已提交）** | 200 `{connected,status,connectedAt}` |
| 重放已消费 state | 已在首次消费 | 400 `GOOGLE_OAUTH_STATE_INVALID` |

所有失败路径下，state 一旦被消费即保持不可再次使用，用户只能重新发起授权生成新 state。不记录 authorization code、state 原文、PKCE verifier、access token 或 refresh token 到日志或错误响应。

## 修改文件

- `backend/src/main/java/com/adproject/integration/google/application/GoogleOAuthStateConsumer.java`（新增）
  - `@Component`；`consume(String state)` 以 `REQUIRES_NEW` 独立事务加锁消费 state 并返回 `ConsumedGoogleOAuthState`。
- `backend/src/main/java/com/adproject/integration/google/application/ConsumedGoogleOAuthState.java`（新增）
  - record `(recruiterId, pkceVerifierEncrypted)`，verifier 仍为密文，由调用方在消费提交后再解密。
- `backend/src/main/java/com/adproject/integration/google/application/GoogleOAuthService.java`（修改）
  - 注入 `GoogleOAuthStateConsumer`；重写 `handleCallback` 顺序，先校验/消费 state，再处理 `error`/配置/交换/落库。
- `backend/src/test/java/com/adproject/integration/google/application/GoogleOAuthIntegrationTest.java`（修改）
  - 删除旧的 `callbackRejectsProviderDenied`（无 state 却断言 DENIED）与 `callbackRejectsTokenExchangeFailure`（断言 state 未消费可重试）。
  - 新增/改写：`callbackWithErrorButNoStateReturnsStateInvalid`、`callbackConsumesStateBeforeDenied`、`callbackConsumesStateBeforeTokenExchangeFailure`；新增 `consumedCount` 辅助查询。

## 测试与验证

### 命令

```
export JAVA_HOME="/c/Users/14188/.jdks/ms-21.0.8"
cd backend
mvn -o test -Dtest='GoogleOAuthIntegrationTest,GoogleOAuthNotConfiguredIntegrationTest'   # OAuth 聚焦
mvn -o test                                                                                # 全量
```

（Maven 3.9.16 wrapper dist + Microsoft OpenJDK 21.0.8，离线模式）

### 结果

- OAuth 聚焦：`GoogleOAuthIntegrationTest` 12 用例、`GoogleOAuthNotConfiguredIntegrationTest` 1 用例，全部通过，`BUILD SUCCESS`。
  - 新增覆盖：provider denied 后 `consumed_at` 已设置且重放同 state 返回 `GOOGLE_OAUTH_STATE_INVALID`；token exchange 失败后 `consumed_at` 已设置、不创建连接、重放同 state 返回 `GOOGLE_OAUTH_STATE_INVALID`；带 `error` 但无 `state` 返回 `GOOGLE_OAUTH_STATE_INVALID`（而非 `DENIED`）。
  - 保留：成功、未登录、Candidate/Recruiter 角色、状态不泄露 token、加密落库、未知/篡改/过期 state、成功回调后重放 state 失败、断开连接、已连接但建会不可用仍阻止面试。
- 全量后端：`Tests run: 109, Failures: 0, Errors: 0, Skipped: 6`，`BUILD SUCCESS`。
  - 6 个跳过为 `MySqlFlywayIntegrationTest`（本机 Windows 环境未检测到 Docker/Testcontainers，故跳过；V10 迁移测试随该类跳过，未改动任何迁移）。

## API / 数据库

- 无新增接口；现有 4 个端点（authorize/status/disconnect/callback）语义不变，仅 callback 的错误优先级与 state 一次性行为修正。
- 无新增迁移；`google_oauth_states`/`google_recruiter_connections` 表结构不变。

## 限制

- 仍未接入真实 Google 凭据，未执行真实 Google 授权；token exchange 仅由测试 fake client 模拟。
- 尚未开始 Calendar/Meet 建会：`MeetingProvisioningPort.isProvisioningAvailable` 仍恒返回 false。
- `MySqlFlywayIntegrationTest` 因本机 Docker 环境不可用而跳过，建议在具备 Docker 的环境补跑端到端迁移校验。
