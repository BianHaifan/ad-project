# 修改报告：面试核心流程「修复与验证包」

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-14
- 对应：`change_report/interview-core.md` 基础上的「修复与验证包」，修复四类问题（时区正确性、真实审计、按模式显示地点、验证）
- 修改范围：`backend/`（V8 迁移、面试审计、地点校验、测试）、`web/`（时区换算、按模式显示/校验、测试）、`android/`（按模式显示地点）
- 明确禁止且未改动：`ml-service/`、Agent、Admin、认证/JWT/刷新令牌、Messages API/表/轮询、`google-meet-integration-*`（下一包）、历史 Flyway 文件（V1–V7 未改）、Docker volume（未删除）、本地已有数据（未清空/覆盖）

## 四项修复说明

### 1. 时区正确性（Web）

**问题**：`ApplicationDetailPage.tsx` 的 `toIsoUtc` 直接给 `datetime-local` 输入拼 `Z`（把本地时间当 UTC），`toLocalInput` 直接去掉 `Z`，标签误导为 "DATE / TIME (UTC)"。

**修复**：
- 新增 `web/src/lib/interviewTime.ts`，基于 `Intl.DateTimeFormat` 的 IANA 时区换算：
  - `localToUtcIso(local, timeZone)`：本地墙钟时间 → UTC ISO（双次解析偏移，正确处理 DST 模糊/不存在时间）。
  - `utcToLocalInput(iso, timeZone)`：已存 UTC → 本地 `datetime-local`。
  - `resolvedTimeZone()`：`Intl.DateTimeFormat().resolvedOptions().timeZone`。
- 排期表单 `timezone` 默认填充浏览器 IANA 时区；创建与改期提交均用 `localToUtcIso` 把本地时间换算成 UTC 提交；改期回填用 `utcToLocalInput` 按已存时区换算回本地。
- 标签改为 "DATE / TIME (LOCAL)"，旁边 TIMEZONE 字段显示/固定实际时区，不再出现误导性 "(UTC)"。
- 删除旧 `toIsoUtc` / `toLocalInput` / `safeUrl`。

**测试**：
- 新增 `web/src/lib/interviewTime.test.ts`（6 条）：新加坡 UTC+8、纽约夏令时/冬令时、回填、往返、非法输入。
- `web/src/pages/ApplicationPages.test.tsx`：更新创建测试断言为正确 UTC（`2026-08-20T09:00` Asia/Singapore → `2026-08-20T01:00:00Z`）；新增改期回填+提交换算测试（`09:00Z` 回填为 `17:00` 本地，改期提交 `2026-08-21T09:00` → `2026-08-21T01:00:00Z`）。

### 2. 真实审计（Backend）

**问题**：OpenAPI 对创建/更新面试均声明 `x-audit: true`，但此前只有创建写申请状态审计，改期/完成/取消没有满足「操作人、公司、前后值、时间、原因、requestId」的审计。

**修复**：
- 新增追加式 Flyway `V8__create_interview_audit_events.sql`（**未改 V7**），独立可追溯的 `interview_audit_events` 表：`interview_id`/`application_id`/`actor_id`/`company_id`/`action`/`before_value`(JSON)/`after_value`(JSON)/`occurred_at`/`reason`/`request_id`，含 4 个外键（interviews/applications/users/companies）与 2 个索引。
- 新增 `InterviewAuditAction`（CREATED/RESCHEDULED/COMPLETED/CANCELLED）、`InterviewAuditEventEntity`、`InterviewAuditEventRepository`。
- `InterviewService.create/update` 在同一 `@Transactional` 内写审计：创建（before=null）、改期/完成/取消（before=变更前快照，after=变更后快照），记录动作、before/after 值、actorId、companyId、occurredAt、requestId 与系统原因（"Interview scheduled/rescheduled/completed/cancelled"）。
- 快照为 JSON 字符串（scheduledAt/timezone/durationMinutes/mode/locationOrMeetingUrl/note/status/version），不新增审计 UI 或查询接口。

**测试**：
- `RecruiterInterviewIntegrationTest.auditEventsAreWrittenForAllFourActions`：创建/改期/完成/取消四类动作各产生正确审计行（action、actor、company、requestId、occurredAt 非空、before/after 状态快照、改期前后 scheduledAt 变化）。

### 3. 按面试模式显示地点

**问题**：ONLINE/ONSITE/PHONE 三种模式的地点被统一按 URL 处理，PHONE 会被拼成 `https://+65...`，ONSITE 被强制当链接。

**修复**：
- **Backend**：`InterviewService` 新增 `validateLocation(mode, location)`——非空校验（空白地点拒绝，即使绕过 Web 也不能写空白），ONLINE 要求 http/https；更新仅传 COMPLETED/CANCELLED 时不要求重复提交地点（终态分支不校验地点）。
- **Web**：表单按模式切换字段标签/占位符（ONLINE→"MEETING LINK"，ONSITE→"LOCATION"，PHONE→"PHONE / CONTACT"），ONLINE 校验 http(s)；详情卡片按模式渲染（ONLINE 且 http(s) 才渲染可点击链接，ONSITE/PHONE 渲染纯文本，不再强制加 `https://`）。
- **Android**：`InterviewCard` 按模式渲染——ONLINE 且 http(s) 可点击打开系统浏览器，ONSITE/PHONE 显示纯文本，不再 `https://` 前缀。

**测试**：
- 后端 `rejectsBlankOrNonHttpLocationAndAllowsStatusOnlyUpdates`：创建 ONLINE 非 http → 422；创建 ONSITE 纯文本 → 201；改期空白地点 → 422；改期切 ONLINE 非 http → 422；仅传 COMPLETED → 200。
- Web：新增 ONSITE/PHONE 纯文本（非链接）、模式切换标签/占位符、ONLINE 非 http 提交禁用等测试。

### 4. 验证

见下文「测试与验证」。额外在真实 MySQL 8.4 上验证 V7/V8 能在已有数据的库上正常迁移。

## 修改文件

### 后端（新增）

- `backend/src/main/resources/db/migration/V8__create_interview_audit_events.sql`：`interview_audit_events` 表 + 4 外键 + 2 索引（追加式，未改 V7）。
- `backend/src/main/java/com/adproject/application/domain/InterviewAuditAction.java`：CREATED/RESCHEDULED/COMPLETED/CANCELLED。
- `backend/src/main/java/com/adproject/application/infrastructure/InterviewAuditEventEntity.java`、`InterviewAuditEventRepository.java`。

### 后端（修改）

- `backend/src/main/java/com/adproject/application/application/InterviewService.java`：注入审计仓库与 `ObjectMapper`；创建/改期/完成/取消写审计；`validateLocation`（非空 + ONLINE http/https）；`snapshot`/`reasonFor` 辅助方法。

### 测试（后端）

- `backend/src/test/java/com/adproject/application/RecruiterInterviewIntegrationTest.java`：新增 `auditEventsAreWrittenForAllFourActions`、`rejectsBlankOrNonHttpLocationAndAllowsStatusOnlyUpdates` 与 `assertAudit` 辅助方法（面试测试由 7 增至 9）。

### Web

- `web/src/lib/interviewTime.ts`（新增）：时区换算工具。
- `web/src/lib/interviewTime.test.ts`（新增）：6 条换算测试。
- `web/src/pages/ApplicationDetailPage.tsx`：默认时区、本地↔UTC 换算、标签 "(LOCAL)"、按模式字段标签/占位符/校验与详情渲染。
- `web/src/pages/ApplicationPages.test.tsx`：更新创建断言；新增改期回填+提交、ONSITE/PHONE 纯文本、模式标签/占位符、ONLINE 非 http 禁用。

### Android

- `android/app/src/main/java/com/adproject/candidate/feature/applications/RealApplicationTrackingScreens.kt`：`InterviewCard` 按模式渲染地点（ONLINE 可点击、ONSITE/PHONE 纯文本），新增 `locationLabel(mode)`。

## API 变化

- 是否变化：**端点/请求/响应形状不变**（无破坏性变更），仅行为变化：
  - 后端新增地点校验：空白地点拒绝、ONLINE 强制 http(s)，均返回 `422 VALIDATION_ERROR`（`fieldErrors.locationOrMeetingUrl`）。
  - 创建/改期/完成/取消现在都会写 `interview_audit_events` 审计行（不新增对外查询接口）。
- 与 OpenAPI 是否一致：是（`x-audit: true` 的契约已由本包真实满足；无需改 `docs/openapi-v1.yaml` 或 `docs/API_COVERAGE.csv`）。

## 数据库变化

- 是否变化：是（仅新增表）
- 新增迁移：`V8__create_interview_audit_events.sql`
- 新增表：`interview_audit_events`
- 新增约束/索引：`fk_interview_audit_interview`、`fk_interview_audit_application`、`fk_interview_audit_actor`、`fk_interview_audit_company`；`idx_interview_audit_interview_occurred`、`idx_interview_audit_application`。
- 未改动既有表结构、既有迁移文件（V1–V7 未改）。

## 测试与验证（实际运行命令及结果）

### 后端（`JAVA_HOME` = IntelliJ JBR 21；`mvn` 为 `~/.m2/wrapper/dists/apache-maven-3.9.16/.../bin/mvn`）

- `mvn -o test -Dtest='RecruiterInterviewIntegrationTest'`
  - 结果：`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`（BUILD SUCCESS）。
- `mvn -o test`（全量）
  - 结果：`Tests run: 85, Failures: 1, Errors: 0, Skipped: 3`（BUILD FAILURE）。
  - 面试相关全部通过（`RecruiterInterviewIntegrationTest` 9/9、`RecruiterApplicationIntegrationTest` 4/4、`CandidateApplicationIntegrationTest` 5/5 等）。
  - 1 个失败为既有 `RecruiterJobIntegrationTest.statusTransitionsPauseResumeAndClosePersistVersionTimesAndAudit:332`（`publishedAt` 的 H2 纳秒 9 位 vs 微秒 6 位精度断言），与面试改动无关、非本包引入、不在修复范围。
  - 3 个跳过为 `MySqlFlywayIntegrationTest`（Testcontainers 在离线 Maven 环境下未自动拉起 Docker，`@Testcontainers(disabledWithoutDocker=true)` 跳过）。

### Web（`web/` 目录）

- `npm run typecheck`：通过。
- `npm run lint`：通过。
- `npm test`：`15 files, 118 tests` 全部通过（含新增 `interviewTime.test.ts` 6 条、`ApplicationPages.test.tsx` 12 条）。
- `npm run build`：`tsc -b && vite build` 成功。

### Android（`JAVA_HOME=/c/Users/14188/.jdks/ms-21.0.8`，JBR 无 `jlink`）

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`：BUILD SUCCESSFUL。
- `./gradlew :app:lintDebug`：BUILD SUCCESSFUL。

### 真实 MySQL 迁移验证（V7/V8 在已有数据的库上）

- 环境：本地 Docker `mysql:8.4`（`adproject-local-mysql`，端口 13306）。未改动其 `adproject` 库数据，使用一次性 `adproject_v8check` 库验证后已删除。
- 步骤：新建 `adproject_v8check` → 依次应用 V1–V6 → 插入一条 `users` + 一条 `companies`（代表既有数据）→ 应用 V7、V8 → 校验 → 删除临时库。
- 结果：V7、V8 均成功应用；`interviews`（2 个 CHECK + 1 个外键）与 `interview_audit_events`（4 个外键 + 2 个索引）均正确创建，既有 `users` 数据不受影响。

## 未运行项及原因

- `MySqlFlywayIntegrationTest`（Testcontainers）：离线 Maven（`-o`）环境下 Testcontainers 无法自动检测/拉起 Docker 容器，仍被跳过；已改用上述手动真实 MySQL 8.4 验证 V7/V8 迁移补足。
- 真实双账号 Web→Android 手动演示：本环境无浏览器/真机交互，未执行；功能由三端构建与测试覆盖。
- `RecruiterJobIntegrationTest` 的既有 `publishedAt` 精度失败（1 个）：与面试无关、非本包引入，按范围不修复。

## 风险与注意事项

- `interview_audit_events` 的 `before_value`/`after_value` 存 JSON 字符串（`VARCHAR(2000)`），供审计追溯，不对外暴露查询接口；如后续需要查询/展示可再加只读接口。
- V8 仅新增表，未改已发布迁移；请确认目标环境 Flyway 兼容（沿用既有命名与风格）。
- 请 Codex 复核：ONLINE 强制 http/https 校验（`422 VALIDATION_ERROR`）、改期/完成/取消在终态不重复提交地点的语义是否符合预期。

## 下一步建议

- 下一包接 Google OAuth / Google Calendar / Google Meet 自动创建（复用本包的时区换算与审计基础设施）。
- 可单独修复既有 `RecruiterJobIntegrationTest` 的 H2 纳秒精度断言（与本包无关）。
