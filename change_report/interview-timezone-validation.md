# 修改报告：面试时区输入与校验

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 范围：仅「面试时区输入与校验」这一项问题
- 明确禁止且未改动：Google OAuth / Google Calendar / Google Meet（未开始）、`ml-service/`、Agent、Admin、认证/JWT/刷新令牌、Messages、历史 Flyway 文件（V1–V8 未改）、Android（本次无改动）、Docker volume、本地已有数据
- 未提交、未推送

## 问题与修复

### 问题

1. Web 的 TIMEZONE 输入框可输入任意字符串；`localToUtcIso()` 直接调用 `Intl.DateTimeFormat`，非法时区会抛 `RangeError` 并导致页面崩溃。
2. 后端只校验 timezone 非空（`@NotBlank`），允许非法 IANA 时区入库。

### 修复说明

#### Backend（`InterviewService`）

- 新增 `validateTimezone(String)`，用 `java.time.ZoneId.of(...)` 校验；非法值抛 `ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", ..., Map.of("timezone", "must be a valid IANA timezone"))`。
- `create()`：`validateLocation` 之后、保存实体之前调用 `validateTimezone(timezone.trim())`。
- `update()` 改期分支：取出生效时区（`request.timezone() != null ? request.timezone().trim() : interview.getTimezone()`），先 `validateLocation` 再 `validateTimezone`，再 `reschedule`。
- 终态分支（仅 COMPLETED / CANCELLED）不校验、不要求 timezone，符合「终态操作不需要重复传 timezone」。

#### Web（`interviewTime.ts` + `ApplicationDetailPage.tsx`）

- 新增 `isValidTimeZone(timeZone)`：用 `new Intl.DateTimeFormat('en-US', {timeZone})` 的 try/catch 判定，非法返回 `false`。
- `localToUtcIso` / `utcToLocalInput` 在进入换算前先做 `isValidTimeZone` 守卫：非法时区分别返回 `null` / `''`，不再抛异常。
- 排期/改期弹窗的 TIMEZONE 字段改为 `readOnly`（不可编辑、保留展示），值为浏览器实际 IANA 时区 `resolvedTimeZone()`。
- `openReschedule` 打开时校验已存 `interview.timezone`：非法则回退到浏览器时区，并设置一次明确提示 `timezoneNotice`（「The saved timezone "…" is not recognized. Using your browser timezone (…).」），弹窗内 `role="status"` 展示；排期弹窗打开时清空该提示。
- 详情卡片对非法已存时区展示提示「Saved timezone is not recognized; times are shown in your browser timezone.」。

## 修改文件

### 后端

- `backend/src/main/java/com/adproject/application/application/InterviewService.java`：新增 `validateTimezone`、`ZoneId`/`DateTimeException` import；create 与 update 改期分支调用校验。
- `backend/src/test/java/com/adproject/application/RecruiterInterviewIntegrationTest.java`：新增 `createAndRescheduleRejectInvalidTimezoneButAcceptValidIanaZones`。

### Web

- `web/src/lib/interviewTime.ts`：新增 `isValidTimeZone`；`localToUtcIso` / `utcToLocalInput` 增加非法时区守卫。
- `web/src/lib/interviewTime.test.ts`：新增非法时区不抛异常、`isValidTimeZone` 判定共 3 条（由 6 → 9）。
- `web/src/pages/ApplicationDetailPage.tsx`：TIMEZONE 字段只读、`timezoneNotice` 状态与提示展示、`openReschedule` 回退浏览器时区、详情卡片非法时区提示。
- `web/src/pages/ApplicationPages.test.tsx`：`vi.mock` 固定 `resolvedTimeZone()` 为 `Asia/Singapore`；创建测试断言只读 + 浏览器时区；新增「非法已存时区回退浏览器时区」测试（由 12 → 13）。

## API / 数据库变化

- API：端点、请求/响应形状**不变**。仅行为变化：create 与 reschedule 新增 timezone 合法性校验，非法返回 `422 VALIDATION_ERROR`，`fieldErrors.timezone` 存在；终态（COMPLETED/CANCELLED）不要求 timezone。与 OpenAPI 一致（无需改 `docs/openapi-v1.yaml`）。
- 数据库：**无变化**（未新增/修改迁移，未改 V7/V8）。

## 测试与验证（实际运行命令及结果）

### 后端（`JAVA_HOME` = IntelliJ JBR 21；`mvn` 为 `~/.m2/wrapper/dists/apache-maven-3.9.16/.../bin/mvn`）

- `mvn -o test -Dtest='RecruiterInterviewIntegrationTest'`
  - 结果：`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`（BUILD SUCCESS）。
- `mvn -o test`（全量）
  - 结果：`Tests run: 86, Failures: 2, Errors: 0, Skipped: 3`（BUILD FAILURE）。
  - 面试相关全部通过（`RecruiterInterviewIntegrationTest` 10/10）。
  - 2 个失败均在 `RecruiterJobIntegrationTest`（Job 域，与面试无关、非本改动引入）：
    - `statusTransitionsPauseResumeAndClosePersistVersionTimesAndAudit:332`：`publishedAt` 期望 `...083999700Z` 实际 `...084Z`。
    - `approvedRecruiterPublishesDraftAndPersistsAuditAndVersion:231`：`publishedAt` 期望 `...294214400Z` 实际 `...294214Z`。
    - 两者同根因：H2 存储微秒（6 位）精度，与 `Instant.now()` 纳秒（9 位）序列化不一致；为既有问题，非本次 timezone 改动引入（本次未改动 `RecruiterJobIntegrationTest` 或 Job 域）。
  - 3 个跳过为 `MySqlFlywayIntegrationTest`（Testcontainers 在离线 Maven 下未自动拉起 Docker）。

### Web（`web/` 目录）

- `npm run typecheck`：通过（`tsc -b --pretty false` 无错误输出）。
- `npm run lint`：通过（`eslint .` 无告警输出）。
- `npm test`：`15 files, 122 tests` 全部通过（含 `interviewTime.test.ts` 9 条、`ApplicationPages.test.tsx` 13 条）。
- `npm run build`：`tsc -b && vite build` 成功（115 modules transformed）。

### Android

- 本次未改动 Android 代码，未重跑（按任务要求，在报告中说明）。

## 未运行项及原因

- `MySqlFlywayIntegrationTest`（Testcontainers）：离线 Maven（`-o`）下 Testcontainers 无法自动检测/拉起 Docker，仍被跳过；本次无数据库变更，无需重复手动 MySQL 验证。
- Android `testDebugUnitTest`/`assembleDebug`/`lintDebug`：本次无 Android 改动，未重跑。
- `RecruiterJobIntegrationTest` 的 2 个既有 `publishedAt` 精度失败：与面试时区无关、非本改动引入，按范围不修复。

## 风险与注意事项

- `isValidTimeZone` 基于 `Intl.DateTimeFormat` 的 try/catch；浏览器对时区的接受范围可能比 Java `ZoneId` 略宽/略窄，但 Web 侧只影响「是否回退浏览器时区」的展示，最终入库仍由后端 `ZoneId.of` 兜底校验。
- TIMEZONE 字段现在固定为浏览器 IANA 时区且只读，用户无法再手动输入；如需支持「按候选人时区排期」，后续可另开需求。
- 请 Codex 复核：非法 timezone 返回 `422 VALIDATION_ERROR`（`fieldErrors.timezone`）的语义，以及「终态更新不要求 timezone」是否与契约一致。
