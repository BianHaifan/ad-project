# 修改报告：Google Meet foundation 审查问题修复

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 对应任务：`tasks/google-meet-integration-plan.md` / `tasks/google-meet-integration-todo.md`「Provider-aware interview foundation」第一包的审查修复
- 修改范围：`backend/`（`V9__add_interview_meeting_provider.sql`、`integration.google` 端口、`InterviewService`、两个测试类）、`docs/openapi-v1.yaml`、`change_report/`
- 明确禁止且未改动：未新增 V10、未开始 OAuth/Google API/Calendar/Meet 调用、未改 ML/Agent/Admin/认证/Messages/Android、未改历史 V1–V8
- 未提交、未推送（等待 Codex 复核）

## 为什么 V9 可以直接修订（而非新增 V10）

- V9 及整套 interview/Google foundation 后端代码目前均为**未提交**的工作树内容（V7/V8/V9、interview 领域层、`integration.google` 端口、测试均未进入版本历史）。审查发现问题后直接修订未提交的 V9 文件是安全的，不需要以 V10 追加修正。

## 修复内容

### 1. V9 数据完整性

- `V9__add_interview_meeting_provider.sql` 直接修订：
  - 将 `meeting_correlation_id` 的普通索引 `idx_interviews_meeting_correlation` 改为唯一索引 `uk_interviews_meeting_correlation`。允许多个 `NULL`，但同一个非空关联键只能对应一场面试（重试复用关联键不会产生第二个面试）。
  - 新增 provider/status 组合 CHECK `chk_interviews_meeting_provider_sync`：
    - `MANUAL` 只能搭配 `NOT_APPLICABLE`；
    - `GOOGLE_MEET` 只能搭配 `PENDING`、`READY`、`FAILED`。
  - 保留既有 `uk_interviews_meeting_event` 唯一索引、`chk_interviews_meeting_provider`、`chk_interviews_meeting_sync`，以及所有手动面试默认值（`MANUAL` + `NOT_APPLICABLE`）。

### 2. 防止下一包 OAuth 造成半成品面试

- `MeetingProvisioningPort` 新增 `boolean isProvisioningAvailable(String recruiterId)`——「系统当前能否实际创建会议」的能力判断，与「已连接」解耦。`isConnected()` 不再是 Google Meet 排期的唯一放行条件。
- `UnavailableMeetingProvisioningPort`（fallback）两个方法均返回 `false`。
- `InterviewService.create()` 对 `GOOGLE_MEET`：
  - 未连接 → 保持 `409 GOOGLE_MEET_NOT_CONNECTED`；
  - 已连接但 `isProvisioningAvailable == false` → 返回稳定 `409 GOOGLE_MEET_PROVISIONING_UNAVAILABLE`，**不创建 Interview、不把申请转为 `INTERVIEW`、不写审计**（异常在保存面试/转换状态/写审计之前抛出）。

### 3. 契约文档

- `docs/openapi-v1.yaml`：`MeetingProvider` 描述补充第二个拒绝码 `GOOGLE_MEET_PROVISIONING_UNAVAILABLE`。

### 4. 测试

- `RecruiterInterviewIntegrationTest.java`：
  - 新增 `@MockitoBean MeetingProvisioningPort`（测试替身，不接入真实 Google）。
  - 既有 `googleMeetRejectedWhenNotConnectedAndManualProviderAccepted` 继续覆盖「未连接 → `GOOGLE_MEET_NOT_CONNECTED`」（mock 默认 `false`）。
  - 新增 `googleMeetRejectedWhenConnectedButProvisioningUnavailable`：stub `isConnected=true`、`isProvisioningAvailable=false`，断言返回 409 `GOOGLE_MEET_PROVISIONING_UNAVAILABLE`，且 `interviews` 无行、申请仍 `IN_REVIEW`、`interview_audit_events` 无审计。
- `MySqlFlywayIntegrationTest.java`：
  - `v9MigrationAddsMeetingProviderColumnsAndManualDefaults` 更新为校验 3 个 CHECK（含新组合约束）与 2 个唯一索引（`non_unique = 0`）。
  - `v9MigrationMigratesExistingInterviewsToManualDefaults` 追加组合 CHECK 的**实际执行**断言：对 MANUAL 面试 `UPDATE meeting_sync_status='PENDING'` 与 `UPDATE meeting_provider='GOOGLE_MEET'` 均抛 `DataAccessException`。

## 测试与验证

### 后端（IntelliJ 内置 Maven + JBR 21，离线 `mvn -o test`）

- 全量：`Tests run: 95, Failures: 0, Errors: 0, Skipped: 5`，`BUILD SUCCESS`。
  - `RecruiterInterviewIntegrationTest`：`Tests run: 12`（含新增 connected-but-unavailable 用例）。
  - 5 个跳过为 `MySqlFlywayIntegrationTest`：Testcontainers 因 **Windows Docker 命名管道问题**（`disabledWithoutDocker` 无法连接 Docker Desktop 管道）被跳过；**未把该跳过当作 V9 已通过真实 MySQL 验证**，改由下述真实 MySQL CLI 验证补足。

### 真实 MySQL 验证（一次性临时数据库）

- 环境：现有 Docker MySQL 容器 `adproject-local-mysql`（`mysql:8.4`，host 端口 13306，root 密码来自容器 env）。**只新建带随机后缀的临时库，绝不操作 `adproject` 或其他既有库。**
- 临时库名：`adproject_v9verify_1786704907_5369`（`date +%s` + `$RANDOM` 后缀）。

实际命令（节选）与结果：

1. 建库：
   - `docker exec adproject-local-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE \`adproject_v9verify_1786704907_5369\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"` → 退出码 0。
2. 顺序应用 V1–V9：
   - `for f in $(ls db/migration/V*.sql | sort); do docker exec -i adproject-local-mysql mysql ... "$DB" < "$f"; done` → 全部成功，无 `MIGRATION_FAILED`。
3. 插入一条**旧格式手动面试**（仅 V7 列，省略 V9 列），随后查询：
   - `SELECT meeting_provider, meeting_sync_status, meeting_event_id, meeting_sync_error, meeting_correlation_id, location_or_meeting_url FROM interviews WHERE id='iv-1';`
   - 结果：`MANUAL	NOT_APPLICABLE	NULL	NULL	NULL	https://meet.example.com/manual` → 默认值与 `location_or_meeting_url` 保留均正确。
4. CHECK 约束（`information_schema.table_constraints`，`constraint_type='CHECK'`）：
   - 含 `chk_interviews_meeting_provider`、`chk_interviews_meeting_sync`、`chk_interviews_meeting_provider_sync`（另有两个 V7 既有约束）。
5. 唯一索引（`information_schema.statistics`）：
   - `uk_interviews_meeting_correlation` 与 `uk_interviews_meeting_event` 的 `non_unique` 均为 `0`（UNIQUE）。
6. 组合 CHECK 实际执行：
   - `UPDATE interviews SET meeting_sync_status='PENDING' WHERE id='iv-1';`
   - → `ERROR 3819 (HY000): Check constraint 'chk_interviews_meeting_provider_sync' is violated.`（退出码 1）。
7. 关联键唯一索引实际执行：
   - 先 `UPDATE ... meeting_correlation_id='corr-dup-1'`，再插入第二场面试复用 `corr-dup-1`：
   - → `ERROR 1062 (23000): Duplicate entry 'corr-dup-1' for key 'interviews.uk_interviews_meeting_correlation'`（退出码 1）。
8. 清理：
   - `DROP DATABASE \`adproject_v9verify_1786704907_5369\`;` → 退出码 0。
   - 复检 `SHOW DATABASES`：仅 `adproject` + 系统库，无残留临时库。

### Web / Android

- 本包只改后端（V9 迁移、端口、服务）与 OpenAPI 描述，未改 Web/Android 代码，故未重跑其 typecheck/lint/test/build。前次 foundation 交付时 Web（typecheck/lint/`122 tests`/build）与 Android 均已通过，本次不影响。

## 已知限制

- 本修复仍不实现真实 OAuth/Google API/Calendar/Meet 调用；`GOOGLE_MEET` 目前只有两个拒绝分支，无成功分支（`isProvisioningAvailable` 恒为 `false`）。
- Testcontainers 的 `MySqlFlywayIntegrationTest` 在本机仍因 Windows Docker 管道问题跳过；已用真实 MySQL CLI 一次性临时库等价验证 V1–V9 迁移、默认值、约束与唯一索引。

## 风险与注意事项

- MySQL CHECK 约束违规（错误 3819）SQLSTATE 为 `HY000` 而非 `23000`，Spring 会翻译为 `UncategorizedSQLException`（`DataAccessException` 子类）而非 `DataIntegrityViolationException`。测试因此断言 `DataAccessException`，以同时兼容两种 SQLSTATE。
- `uk_interviews_meeting_correlation` 唯一索引建立在可空列上：MySQL 允许多个 `NULL`，仅约束非空关联键唯一，符合「重试复用关联键不重复建会」的语义。
- 请 Codex 复核：`isConnected` + `isProvisioningAvailable` 两个端口方法的语义边界是否清晰（「已连接」vs「可建会」），以及 `GOOGLE_MEET_PROVISIONING_UNAVAILABLE` 归入 409 是否符合既有业务冲突约定。

## 下一步建议

- Docker 管道可用后，补跑 `MySqlFlywayIntegrationTest` 的 Testcontainers 用例，与真实 MySQL CLI 验证交叉印证。
- 下一包（Task 2）接真实 Google OAuth 连接后，让 `isConnected`/`isProvisioningAvailable` 返回真实状态，并扩展 `createMeeting`/`reschedule`/`cancel`。
