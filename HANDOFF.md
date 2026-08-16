# 移交会话提示词（Google Meet foundation 第一包 + 审查修复）

> 把下面整段贴给新会话，即可继续/复核当前工作。

## 背景与当前状态

- 项目：`ad-project`（Java 21 + Spring Boot 3.5.4 后端；React + TypeScript Web；Android 客户端）。
- 分支：`codex/recruiter-candidate-improvements`。
- 工作流：Codex/Claude 多智能体协作，Claude 做代码实现，Codex 复核。
- **Git 状态：大量工作未提交**——`V7/V8/V9` 迁移、interview 领域层、`integration.google` 端口、`tasks/`、`change_report/` 均为未提交/未跟踪文件。**不要提交、不要推送，等 Codex 复核。**

## 已完成（不要重做）

第一包「Provider-aware interview foundation」+ 第二轮审查修复，已全部完成：

1. **V9 数据模型**（`backend/src/main/resources/db/migration/V9__add_interview_meeting_provider.sql`，未提交可直接修订，勿新增 V10）：
   - `interviews` 新增 `meeting_provider`、`meeting_sync_status`、`meeting_event_id`、`meeting_sync_error`、`meeting_correlation_id`；无 OAuth token。
   - 唯一索引 `uk_interviews_meeting_event`、`uk_interviews_meeting_correlation`（多 NULL 允许，非空键唯一）。
   - CHECK：`chk_interviews_meeting_provider`、`chk_interviews_meeting_sync`、`chk_interviews_meeting_provider_sync`（`MANUAL`↔`NOT_APPLICABLE`；`GOOGLE_MEET`↔`PENDING/READY/FAILED`）。
2. **领域/端口**：`MeetingProvider`、`MeetingSyncStatus` 枚举；`MeetingProvisioningPort`（`isConnected` + `isProvisioningAvailable`）+ `UnavailableMeetingProvisioningPort`（两者恒 `false`）。
3. **InterviewService**：`GOOGLE_MEET` 未连接 → `409 GOOGLE_MEET_NOT_CONNECTED`；已连接但不可建会 → `409 GOOGLE_MEET_PROVISIONING_UNAVAILABLE`（不建 Interview / 不转 `INTERVIEW` / 不写审计）。手动流程不变，响应显式 `MANUAL`+`NOT_APPLICABLE`。
4. **契约同步**：`InterviewDtos`、`InterviewService`、`CandidateApplicationResponseMapper`、`RecruiterApplicationService`、`docs/openapi-v1.yaml`、Web 类型/解析器/mock 均已同步。
5. **测试**：`RecruiterInterviewIntegrationTest`（12 用例，含 `@MockitoBean` 测试替身覆盖两个拒绝分支）；`MySqlFlywayIntegrationTest`（V9 列/默认值/3 CHECK/2 唯一索引/组合 CHECK 执行，Testcontainers 离线跳过）。
6. **真实 MySQL 验证**：已用一次性临时库 `adproject_v9verify_*`（随机后缀，已 DROP）验证 V1–V9 迁移、默认值、组合 CHECK 执行、唯一索引执行。

**测试结果**：后端 `mvn -o test` = `95 run / 0 fail / 0 error / 5 skip`（5 跳过为 MySqlFlywayIntegrationTest，Windows Docker 命名管道问题）。Web `typecheck/lint/test(122)/build` 均通过（本次审查未改 Web/Android，未重跑）。

## 关键命令 / 环境

- `JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.3/jbr"`
- `MVN="/c/Users/14188/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn"`
- 后端测试：`mvn -o test`（离线）
- Web（在 `web/` 下）：`npm run typecheck` / `npm run lint` / `npm test` / `npm run build`
- Docker MySQL 容器 `adproject-local-mysql`（mysql:8.4，host 端口 13306，root 密码在容器 env `MYSQL_ROOT_PASSWORD`），库名 `adproject`。**绝不操作 `adproject` 库**；验证时新建带随机后缀的临时库并用完 `DROP`。

## 交接文件

- `change_report/google-meet-foundation.md`（第一包交付）
- `change_report/google-meet-foundation-review-fixes.md`（审查修复，含真实 MySQL 命令与结果）

## 待办 / 下一步

1. **等 Codex 复核**（当前阻塞点，不提交不推送）。
2. 复核点（见报告「风险与注意事项」）：
   - `locationOrMeetingUrl` 由 DTO `@NotBlank` 改为服务层 `validateLocation` 仅 `MANUAL` 强制。
   - MySQL CHECK 违规 SQLSTATE 为 `HY000`（非 `23000`），测试用 `DataAccessException` 断言。
   - `isConnected`（已连接）与 `isProvisioningAvailable`（可建会）语义边界是否清晰。
3. 下一包 Task 2：真实 Google OAuth 连接存储 + 回调，让 `isConnected`/`isProvisioningAvailable` 返回真实状态；再下一包 Task 3：真实建会 `createMeeting`/`reschedule`/`cancel`。
4. Docker 管道可用后，补跑 Testcontainers 用例与真实 MySQL 验证交叉印证。

## 边界（不要做）

- 不实现真实 OAuth/Google API/Calendar/Meet 调用、不要求/写入任何 Google 密钥。
- 不新增 V10（V9 未提交，可直接修订）。
- 不改 ML/Agent/Admin/认证/Messages/Android/历史 V1–V8。
