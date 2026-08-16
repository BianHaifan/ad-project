# 修改报告：Google Meet 创建流程校验顺序修复

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 修改范围：`backend/`（`application/application/InterviewService.java` 校验顺序 + `integration/google` 回归测试）、`change_report/`
- 明确禁止且未改动：事务拆分、event ID 派生、token refresh、OAuth 状态、API 契约、数据库迁移、Web/Android UI、`web/dist`、`web/node_modules`；未接入真实 Google 凭据。
- 未提交、未推送（等待 Codex 安全复核）。

## 根因

`InterviewService.create` 的 GOOGLE_MEET 分支先执行 `ensureMeetingConnectionUsable(...)`（Google 连接预检），后执行 `validateTimezone(timezone)`。因此当用户提交无效 IANA 时区时，若 Google access token 临近过期，系统会**先尝试 refresh token**；更糟的是 `invalid_grant` 可能把连接标记为 `REVOKED`，随后才应返回 422 校验错误。无效请求本不应触发任何外部 OAuth 调用或改变连接状态。

## 修复

调整 `InterviewService.create` 中 GOOGLE_MEET 分支的校验顺序，使**所有纯输入校验完成后才执行连接预检**：

1. 校验 provider 规则：`mode=ONLINE`、不允许客户端提供 `locationOrMeetingUrl`；
2. 校验时区 `validateTimezone(timezone)`；
3. 校验通过后才调用 `ensureMeetingConnectionUsable(recruiterId)`；
4. 最后进入既有本地创建事务。

`MANUAL` 分支保持原行为不变（`validateLocation` → `validateTimezone`）。未改动既有的 `TransactionTemplate` 两段提交、event ID 派生、token refresh、OAuth 状态、API 契约或数据库迁移。

## 测试

新增 API 级回归测试 `GoogleOAuthIntegrationTest.invalidTimezoneRejectedBeforeConnectionPreflight`：

- 以 `meetingProvider=GOOGLE_MEET` + 无效时区 `Not/AZone` + 已过期 access token 的已连接招聘者发起创建；
- 断言返回 422 `VALIDATION_ERROR`，字段为 `timezone`；
- 断言 `GoogleTokenClient.refreshAccessToken` **从未被调用**（`verify(..., never())`）；
- 断言连接状态、token 密文、version 均保持不变；
- 断言未创建 interview、未写 application 状态事件，application 仍为 `IN_REVIEW`。

## 测试与验证

### 命令

```
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -o test
```

（Maven 3.9.16 wrapper dist + Microsoft OpenJDK 21.0.8，离线模式；从 `backend/` 目录执行）

### 结果

- 全量后端：`Tests run: 132, Failures: 0, Errors: 0, Skipped: 6`，`BUILD SUCCESS`（较上次 131 新增 1 个回归用例）。
- 6 个跳过为 `MySqlFlywayIntegrationTest`（本机未检测到 Docker/Testcontainers；本次未新增 Flyway 迁移）。

## API / 数据库

- 无 API 契约变化、无数据库迁移、无新增端点、无新增 Google SDK/真实密钥。
- `InterviewService.create` 仅重排纯输入校验与连接预检的执行顺序，外部可见行为除"无效请求不再触发外部调用/状态变更"外完全一致。

## 限制

- 未接入真实 Google 凭据，未执行真实授权/建会；token refresh 与预检由测试 fake 模拟。
- 面试重排/取消的 Google 同步、后台定时补齐、Web 招聘者建会 UI、Android 最终状态展示仍不在本包范围。
- `MySqlFlywayIntegrationTest` 因本机 Docker 环境不可用而跳过，建议在具备 Docker 的环境补跑端到端迁移校验。
