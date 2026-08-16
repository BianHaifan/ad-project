# 修改报告：核心后端时间统一为微秒精度

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 范围：`backend/` + 相关 `docs/` + 本报告
- 明确禁止且未改动：Android、Web、`ml-service/`、Agent、Admin、认证（`auth`）、Messages（`conversation`）、Google 集成、历史 Flyway 迁移（V1–V8 未改）、本地数据
- 未提交、未推送

## 问题与修复

### 问题

Java `Instant` 携带纳秒（9 位）精度，而 MySQL 时间列统一为 `DATETIME(6)`（微秒，6 位）。所有业务时间在写入前未归一化，导致：

1. 同一事务内内存中的 `Instant`（9 位）与后续从数据库读回的值（6 位）不一致。
2. `RecruiterJobIntegrationTest` 中两个 `publishedAt` 断言失败：发布响应返回 `...294214400Z`（9 位纳秒），随后 GET/状态变更读回 `...294214Z`（6 位微秒），两者字符串不相等。

### 修复说明

- 新增统一工具 `DatabaseTimePrecision.micros(Instant)`，以 `Instant.truncatedTo(ChronoUnit.MICROS)` 为唯一规范（只截断、不四舍五入、null 透传）。
- 在服务层所有「写入 Entity 的 `Instant`」来源点显式归一化：既包括 `clock.instant()`，也包括客户端传入并被持久化的 `Instant`（职位 `deadline`、面试 `scheduledAt`）。
- 不使用全局 JPA Converter 做隐式改写；采用可审查、显式的服务层归一化，读路径和 API 返回自然保持数据库微秒精度。

## 审查到的写入点与修改范围

### 新增

- `backend/src/main/java/com/adproject/common/time/DatabaseTimePrecision.java`：公共时间精度工具，`micros(Instant)`。
- `backend/src/test/java/com/adproject/common/time/DatabaseTimePrecisionTest.java`：5 条单元测试（纳秒截断为微秒、已微秒保持不变、整秒保持不变、UTC 不变、null 透传）。

### 修改（全部为服务层归一化）

| 文件 | 归一化的写入点 |
|---|---|
| `job/application/JobService.java` | `create`/`update`/`publish`/`changeStatus` 共 4 处 `clock.instant()`；`create`/`update` 共 2 处 `Instant.parse(deadline)` |
| `application/application/InterviewService.java` | `create`/`update` 共 2 处 `clock.instant()`；`create`/`update`(reschedule) 共 2 处 `scheduledAt` |
| `application/application/CandidateApplicationService.java` | `submit` 1 处 `clock.instant()`（appliedAt/updatedAt/occurredAt/snapshot.capturedAt/idempotency.createdAt 同一 `now`） |
| `application/application/RecruiterApplicationService.java` | `transition` 1 处 `clock.instant()` |
| `application/application/CandidateApplicationQueryService.java` | `withdraw` 1 处 `clock.instant()` |
| `profile/application/CandidateProfileService.java` | `update` 1 处 `clock.instant()` |
| `resume/application/CandidateResumeService.java` | `save` 1 处 `clock.instant()` |

说明：`clock.instant()` 产生的 `now` 被多处实体复用（`createdAt`、`updatedAt`、`publishedAt`、`occurredAt`、`capturedAt`、审计事件等），在源头归一化后全部写入点自动覆盖。客户端 `Instant`（`deadline`、`scheduledAt`）在解析后、传入实体前归一化。

### 未改（按范围排除）

- 认证模块：`AuthService`、`JwtService`、`RefreshTokenService` 的 `clock.instant()`（RefreshToken 的 `expiresAt`/`createdAt`/`revokedAt` 属认证域）。
- Messages/conversation 模块：`ConversationService`、`ConversationProvisioningService`、`MessageEntity.sentAt` 等。
- 全局 JPA Converter：未引入。

## 文档

- `docs/architecture.md`（第 7 节「数据和一致性」）：时间句补充「持久化精度统一为微秒（`DATETIME(6)`）」。
- `docs/database-design.md`（第 1 节「设计约定」）：时间句补充「持久化精度统一为微秒（`DATETIME(6)`）」。

## API / 数据库变化

- API：端点、请求/响应形状**不变**。仅行为变化：写入值的小数秒被截断到 6 位，因此「写响应」与「后续读」的 `publishedAt`/`updatedAt` 等时间字符串一致，不再出现 9 位纳秒。
- 数据库：**无迁移**。现有时间列已是 `DATETIME(6)`（见 `docs/database-design.md`），本次仅规范应用层写入值，无需改 schema。

## 测试与验证（实际运行命令及结果）

后端（`JAVA_HOME` = IntelliJ JBR 21；`mvn` 为 `~/.m2/wrapper/dists/apache-maven-3.9.16/.../bin/mvn`）：

- `mvn -o test -Dtest='DatabaseTimePrecisionTest,RecruiterJobIntegrationTest'`
  - 结果：`Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`（BUILD SUCCESS）。
  - 其中 `DatabaseTimePrecisionTest` 5/5，`RecruiterJobIntegrationTest` 24/24（原 2 个 `publishedAt` 失败已修复）。
- `mvn -o test`（全量）
  - 结果：`Tests run: 91, Failures: 0, Errors: 0, Skipped: 3`（BUILD SUCCESS）。
  - 各测试类明细（全部通过）：
    - `CandidateApplicationIntegrationTest` 5、`CandidateApplicationQueryIntegrationTest` 5、`RecruiterApplicationIntegrationTest` 4、`RecruiterInterviewIntegrationTest` 10、`AuthIntegrationTest` 13、`DatabaseTimePrecisionTest` 5、`ConversationIntegrationTest` 9、`DashboardIntegrationTest` 4、`CandidateJobIntegrationTest` 5、`RecruiterJobIntegrationTest` 24、`CandidateProfileResumeIntegrationTest` 4。
  - 3 个跳过为 `MySqlFlywayIntegrationTest`（Testcontainers 在离线 Maven 下未自动拉起 Docker）。

### 失败项说明

- 无失败。修复前全量存在 2 个 `RecruiterJobIntegrationTest` 的 `publishedAt` 精度失败（`statusTransitionsPauseResumeAndClosePersistVersionTimesAndAudit:332` 与 `approvedRecruiterPublishesDraftAndPersistsAuditAndVersion:231`），根因为写入值纳秒 vs 数据库微秒读回不一致；本包通过源头归一化写入值，使写入值本身即为微秒精度，未使用测试容差掩盖，已全部通过。

## 未运行项及原因

- `MySqlFlywayIntegrationTest`（Testcontainers）：离线 Maven（`-o`）下 Testcontainers 无法自动检测/拉起 Docker，仍被跳过；本次无数据库 schema 变更，无需重复手动 MySQL 验证。
- Android `testDebugUnitTest`/`assembleDebug`/`lintDebug`：本次无 Android 改动，未重跑。
- Web `typecheck`/`lint`/`test`/`build`：本次无 Web 改动，未重跑。

## 风险与注意事项

- `DatabaseTimePrecision.micros` 只截断不四舍五入；客户端如需「四舍五入」语义应另作处理（当前需求明确为截断）。
- 认证与 Messages/conversation 模块的 `Instant` 写入点按范围未纳入；若后续要求全站统一，需另开需求，且 `RefreshToken.expiresAt` 等若被截断会影响令牌过期语义，需单独评审。
- 新代码写入 Entity 前必须复用 `DatabaseTimePrecision.micros(...)`，不得再直接写入未归一化的 `Instant`。
