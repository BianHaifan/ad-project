# 修改报告：Candidate Jobs 列表接口 500 修复

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-13
- 对应任务：修复本地 Docker 环境 `GET /api/v1/candidate/jobs`（即 `GET /api/v1/jobs`，候选人岗位列表）返回 `500 INTERNAL_ERROR` 的问题
- 修改范围：`backend/` 中 Candidate Jobs 相关模块（`CandidateJobQueryService`）及其集成测试、`change_report/candidate-jobs-500-fix.md`、`tasks/todo.md`
- 明确禁止且未改动：`android/`、`web/`、`ml-service/`、Agent、Admin；认证方案、角色模型、数据库既有业务数据、既有迁移；未通过手工插入/修改数据掩盖问题

## 根因

`GET /api/v1/jobs` 的列表映射路径 `CandidateJobQueryService.toSummary(...)` 依赖 `readList(String)` 解析岗位的 `requirements_json` / `skills_json` 两个 JSON 字符串数组字段。原实现：

```java
private List<String> readList(String value) {
    try {
        return objectMapper.readValue(value, STRING_LIST);
    } catch (JsonProcessingException exception) {
        throw new IllegalStateException("Stored job list field is invalid", exception);
    }
}
```

- 当存储值为**空字符串** `""` 或**非法 JSON**（如 `"not-json"`）时，`readValue` 抛出 `MismatchedInputException`（`JsonProcessingException` 子类），被捕获后重新抛出 `IllegalStateException`。
- 当存储值为 `null` 时，`readValue(null, ...)` 抛出 `IllegalArgumentException`（**不是** `JsonProcessingException`，未被捕获）。

这两类异常都不是 `ApiException`，落入全局 `GlobalExceptionHandler.handleUnexpected`，对整个列表返回 `500 INTERNAL_ERROR`（`{"error":{"code":"INTERNAL_ERROR","message":"An unexpected error occurred",...}}`）。即**任何单条异常数据都会使整个列表 500**，而非只跳过/降级该行。

### 复现证据

新增回归测试后，先运行确认（修复前）：

```
Resolved Exception:
             Type = java.lang.IllegalStateException
MockHttpServletResponse:
           Status = 500
           Body = {"error":{"code":"INTERNAL_ERROR","message":"An unexpected error occurred","fieldErrors":{},"requestId":"req_..."}}
```

任务字段 `requirements_json`/`skills_json` 在迁移中为 `TEXT NOT NULL`，空字符串满足 `NOT NULL` 约束却会被 `readList` 抛异常，属于可被真实数据触发的 500。

## 修复方式

将 `CandidateJobQueryService.readList` 改为对空值/非法 JSON 容错：

```java
private List<String> readList(String value) {
    if (value == null || value.isBlank()) {
        return List.of();
    }
    try {
        List<String> parsed = objectMapper.readValue(value, STRING_LIST);
        return parsed == null ? List.of() : parsed;
    } catch (JsonProcessingException exception) {
        log.warn("Stored job list field is not a valid JSON string array; treating as empty", exception);
        return List.of();
    }
}
```

- `null`/空白 → 空列表；
- 非法 JSON → 记一条 `WARN` 日志（不掩盖问题）并返回空列表；
- 字面量 `"null"`（`readValue` 返回 Java `null`）→ 归一化为空列表。

行为不变的部分：候选人的权限与状态规则（仅 `ACTIVE` + `PUBLIC`）、搜索/`employmentType` 过滤、`publishedAt`/`id` 排序、分页与 `meta` 结构、`get` 详情的 `applicationState` 均未改动。坏数据行不再导致整表 500，而是以空 `requirements`/`skills` 正常返回该岗位。

## 修改文件

### 后端（修改）

- `backend/src/main/java/com/adproject/job/application/CandidateJobQueryService.java`
  - 主要变化：`readList` 空值/非法 JSON 容错；新增 `org.slf4j.Logger`/`LoggerFactory` 静态字段，异常时 `log.warn` 并返回空列表。

### 后端（测试）

- `backend/src/test/java/com/adproject/job/CandidateJobIntegrationTest.java`
  - 主要变化：新增 `listSurvivesJobsWithEmptyOrMalformedListJson`，插入空 `requirements_json`、空 `skills_json`、非法 JSON 三种坏数据行，断言列表仍返回 `200` 且 `meta.total=4`；新增辅助方法 `insertJobWithListJson` 以直接写入自定义 JSON 字段。

## API / 数据库变化

- API：无变化。路径、请求参数、响应结构、状态码语义、分页与过滤语义均不变。唯一差异是：含坏 JSON 字段的单行不再令整表 500，而是以空列表字段正常返回。
- 数据库：无变化。不新增/修改迁移、表、索引或约束；未改动既有业务数据。
- 契约一致性：与 `docs/openapi-v1.yaml` 的 `/jobs`（`CandidateJobSummary`）一致，未触碰契约。

## 测试与验证

运行环境：`backend/` 目录，IntelliJ 内置 Maven 3.9.16 + Microsoft OpenJDK 21（`C:\Users\14188\.jdks\ms-21.0.8`；`mvn` 不在 PATH，已显式指定）。

- `mvn -Dtest=CandidateJobIntegrationTest test`：通过，`5` 个用例全部通过（新增 1 个坏数据回归用例；既有 4 个覆盖成功、未登录、错误角色、空列表、分页/搜索/`employmentType` 过滤）。
- `mvn test`（全量）：`Tests run: 76, Failures: 1, Errors: 0, Skipped: 3`
  - `1` 个失败为**既有、与本任务无关**的 H2 纳秒精度问题：`RecruiterJobIntegrationTest.approvedRecruiterPublishesDraftAndPersistsAuditAndVersion`（第 231 行，`expected "...154389600Z" but was "...154390Z"`），属 H2 `DATETIME(6)` 微秒精度与 Java `Instant` 纳秒精度的既有偏差（与既往报告的同类失败一致，本次具体命中的测试方法随 `clock.instant()` 纳秒分量在运行间浮动）。按任务要求列出但不混入本任务修复。
  - `3` 个跳过为 `MySqlFlywayIntegrationTest`（`@Testcontainers(disabledWithoutDocker = true)`，本机 JVM 无法访问 Docker 守护进程）。
- `mvn -DskipTests package`：通过（exit 0）。

### 真实 Docker MySQL 验证

- 重启 `ad-project-backend` 容器（`docker restart`，触发从最新源码重编译 `Compiling 97 source files`，新进程 PID 130），使修复生效；`ad-project-mysql`（mysql:8.4，Flyway 现为 V6）未改动。
- 真实候选人登录 `POST /api/v1/auth/login`（`fix.candidate@example.com`）→ 取得 accessToken。
- `GET /api/v1/jobs`（Bearer 候选人 token）→ **`200`**，返回既有 ACTIVE/PUBLIC 岗位 `hyc_test`（`meta.total=1`）。
- 未登录请求 → **`401`**。
- `GET /api/v1/jobs?q=...`（无匹配）→ **`200`** 空列表（`meta.total=0`）。

## 已知限制

- **无效枚举字符串（数据完整性）未在本次修复**：`jobs` 表的枚举列在迁移中是 `VARCHAR` 而非 MySQL `ENUM` 类型，若手工写入非法值（如 `employment_type='FULL-TIME'`），Hibernate 会在 `findAll` 加载实体阶段抛异常（早于 `toSummary`），`readList` 无法兜底。修复该场景需改数据或把 `JobEntity` 的 `@Enumerated(EnumType.STRING)` 改为原始 `String` 手动映射，属更大改动，超出本任务「Candidate Jobs 列表 500」范围，未处理。
- **招聘者端同款隐患未处理**：`JobService.readList`（招聘者岗位模块）存在与 `CandidateJobQueryService.readList` 相同的抛异常逻辑，但不在本任务允许修改的 Candidate Jobs 模块范围内，仅记录。
- `requireCompany` 对孤儿 `company_id` 抛 `404 NOT_FOUND`（非 500），且外键约束在实践上阻止孤儿数据，故未改动。

## 下一步建议

- 由掌握既有招聘者账户凭据的人员在真实 MySQL 补齐投递关系后，走通「浏览岗位 → 投递 → 自动建会话 → 双端互发」验收，再勾选 Messages 相关条目（本任务不涉及、也不勾选 Messages 功能）。
- 若确需兜底非法枚举字符串，可单独立项：将 `JobEntity` 枚举列改为原始字符串并做防御性映射（需同步招聘者 `JobService`），或执行一次性数据清洗。
- 招聘者端 `JobService.readList` 可参照本次修复做同等容错，避免招聘者岗位列表/详情出现同类 500。
