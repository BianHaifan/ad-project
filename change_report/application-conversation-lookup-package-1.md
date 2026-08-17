# 招聘者申请详情页直达聊天（Package 1）交付报告

> 状态：实现完成，等待复核；未 commit / push。
> 目标：招聘者后续可根据 `applicationId` 在 `GET /api/v1/recruiter/conversations` 上精确获得其公司内的对应会话，避免网页端只扫描前 20 条会话记录。

## 完成内容

### 后端

- `GET /api/v1/recruiter/conversations` 新增可选查询参数 `applicationId`（UUID），复用既有列表端点，未新增临时接口。
- `ConversationService.listRecruiter(...)` 签名新增 `String applicationId`：
  - 参数缺失/空白：行为与原来完全一致（`q`、`unreadOnly`、分页、排序 `updatedAt desc, id desc` 均不变）。
  - 参数存在：在既有「公司作用域」`Specification`（`companyId == 招聘者所属公司`）之上追加 `applicationId == 归一化后的 UUID`，单条 SQL 返回该申请对应的 0 或 1 条会话。
  - 非法 UUID：复用项目统一错误码 `VALIDATION_ERROR`（HTTP 422，字段 `applicationId` → `must be a UUID`），与 `send` 中 `clientMessageId` / `Idempotency-Key` 校验同源。
- 新增私有助手 `ConversationService.optionalUuid(value, field)`：空值返回 `null`（视为「未传」），非空走既有 `requireUuid` 校验与归一化。
- 纯只读：不创建会话、不写消息、不修改申请状态；不触碰 `ConversationProvisioningService`、`MessageRepository`、`ApplicationRepository` 写入路径。

### 权限边界

- 沿用既有 `requireCompany(principal)`：未登录 → 401；非 `RECRUITER` 角色或非公司成员 → 403。
- `applicationId` 存在时仅返回「招聘者所属公司 + 该申请」的交集：
  - 该申请属于他司、或该 UUID 无对应申请 → 会话列表为空（HTTP 200，`data: []`、`meta.total: 0`），不额外暴露资源存在性。
  - 不泄露他司、他人、或不存在申请的会话信息。

### 文档

- `docs/openapi-v1.yaml`：`/recruiter/conversations` GET 增加 `applicationId` query 参数（`type: string, format: uuid`，`required: false`）及 `422` 响应引用。
- `docs/API_COVERAGE.csv`：更新 `listRecruiterConversations` 行的权限、`params`（追加 `applicationId`）、`Main Errors`（追加 `422`）与 Summary。

## 实际修改文件

### 后端

- `backend/src/main/java/com/adproject/conversation/api/RecruiterConversationController.java`（`list` 增加 `applicationId` 参数）
- `backend/src/main/java/com/adproject/conversation/application/ConversationService.java`（`listRecruiter` 增加 `applicationId` 过滤 + `optionalUuid` 助手）
- `backend/src/test/java/com/adproject/conversation/ConversationIntegrationTest.java`（新增 6 用例）

### 文档

- `docs/openapi-v1.yaml`
- `docs/API_COVERAGE.csv`

## API / 数据库 / Flyway 变化

- API：`GET /api/v1/recruiter/conversations` 新增可选 query `applicationId`（UUID）；响应结构不变（`ConversationSummary` 本就含 `applicationId`）。
- 数据库 / Flyway：无新增表、无新增迁移、无数据库结构改动。
- DTO：无改动。

## 测试

后端（在线 Maven，JDK 21，H2 `MODE=MySQL`；`@ActiveProfiles("test")`）：

```bash
export JAVA_HOME="/c/Users/14188/.jdks/ms-21.0.8"; export PATH="$JAVA_HOME/bin:$PATH"
cd backend
MVN="/c/Users/14188/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn"
"$MVN" -B -Dtest=ConversationIntegrationTest test
```

结果：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`（15 = 既有 9 例 + 新增 6 例）。

新增 6 用例（全部通过）：

- `recruiterFindsUniqueConversationByApplicationId` —— 同公司多个会话下，按 `applicationId` 精确返回唯一正确会话（`data.length()==1`、`conversationId` 匹配）。
- `listWithoutApplicationIdPreservesExistingBehavior` —— 不传 `applicationId` 时保留原列表行为（返回全部本公司会话，`data.length()==2`、`meta.total==2`）。
- `applicationIdDoesNotLeakCrossCompanyOrMissingApplication` —— 他司招聘者查本司申请 → 空列表；任意随机 UUID（无对应申请）→ 空列表。
- `applicationIdRequiresAuthentication` —— 无 token → 401。
- `applicationIdRejectsCandidateRole` —— 求职者 token → 403。
- `applicationIdRejectsInvalidUuid` —— `applicationId=not-a-uuid` → 422 `VALIDATION_ERROR`。

全量回归（`"$MVN" -B test`）：

```text
Tests run: 231, Failures: 0, Errors: 0, Skipped: 6
BUILD SUCCESS  (Total time: 01:16 min)
```

- 跳过 6 例为 `MySqlFlywayIntegrationTest`（`@Testcontainers(disabledWithoutDocker = true)`，本机无 Docker 的 Testcontainers 自动跳过），其余全量通过，无回归。

## 未完成内容（按任务边界）

- 未实现网页 UI / Android UI（本包仅后端查询能力）。
- 未新增数据库迁移、未改动数据库结构、未改动会话 DTO。
- 未涉及 Google Meet / OAuth / Admin / ML / Agent。
- 未本地手测（未启动本地服务）；未 commit、未 push。

## 下一步

1. 网页端在「申请详情页」改用 `GET /api/v1/recruiter/conversations?applicationId={id}` 精确获取会话（替代当前扫描前 20 条会话的做法）。
2. 如需 Android 侧直达聊天，另行在候选人/招聘者 Android 端接入同一参数。
3. 复核后按需提交并推送。
