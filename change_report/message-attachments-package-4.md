# 消息附件（Package 4）交付报告

> 状态：实现完成，等待 Codex/主协调人复核；未 commit / push。
> 目标：补齐招聘者 Web 端与求职者 Android 端的消息附件上传、展示和下载。每条普通（非 SYSTEM）消息可上传至多一个附件，可仅含附件（无正文）；附件仅对会话参与者可见。

## 完成内容

### 后端

- 新增 Flyway 迁移 `V12__create_message_attachments.sql`：
  - 放宽 `messages` 的 `chk_messages_body` 约束为 `LENGTH(body) BETWEEN 0 AND 5000`（原为「非空」），使「仅附件」消息合法；约束名保持不变（`chk_messages_body`），不影响既有校验测试。
  - 新建 `message_attachments` 表：`id`、`message_id`（`uk_message_attachments_message` 唯一，即每消息至多一个附件）、`file_name`、`content_type`、`size_bytes`、`content LONGBLOB`、`created_at`，外键 `fk_message_attachments_message` 指向 `messages`。
  - 附件内容存 MySQL `LONGBLOB`（演示期可靠性优先），无云存储 / 第三方存储 / 新依赖。
- 新增 `MessageAttachmentEntity` / `MessageAttachmentRepository`（`findByMessageId`、`findByMessageIdIn`）。
- `ConversationDtos.java`：新增 `Attachment(attachmentId, fileName, sizeBytes, contentType)`；`Message` 记录第 8 个字段为可空 `Attachment`（消息列表/创建响应返回元数据，**不含**二进制内容）。
- `ConversationService.java`：
  - 常量 `MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024`、`ATTACHMENT_CONTENT_TYPES` 白名单映射（PDF/DOC/DOCX/TXT/PNG/JPG/JPEG → content-type）。
  - 新增 `sendCandidateAttachment` / `sendRecruiterAttachment` / `downloadCandidateAttachment` / `downloadRecruiterAttachment`，共用 `sendWithAttachment`、`downloadAttachment`、`validateAttachment`、`sanitizeFileName`、`extensionOf`、`matchesMagicBytes`。
  - `validateAttachment`：空附件 → 422 `VALIDATION_ERROR`；`size > 10MB` → 413 `FILE_TOO_LARGE`；扩展名不在白名单 → 422；再按内容校验（**不信任客户端文件名/MIME**）：PDF `%PDF-`、PNG `89 50 4E 47`、JPEG `FF D8 FF` 魔数；DOC 校验 OLE 复合文档头 `D0 CF 11 E0 A1 B1 1A E1`；DOCX 校验 ZIP 头且压缩包内含 `[Content_Types].xml` 与 `word/document.xml`（解析失败即拒）；TXT 严格 UTF-8 解码，拒绝非法字节序列、NUL 与不可打印控制字符（允许换行/Tab）——声明类型与内容不符 → 422。
  - 上传与消息创建在同一事务内原子提交；复用消息幂等（精确重放返回同一条消息，同 key + 不同附件 → 409 `IDEMPOTENCY_KEY_REUSED`）。
  - 下载响应设置存储的 `Content-Type` 与 `X-Content-Type-Options: nosniff`；文件名经 `sanitizeFileName` 清洗。
- `CandidateConversationController` / `RecruiterConversationController`：各新增两个端点（见下）。
- `application.yml`：`spring.servlet.multipart.max-file-size: 10MB`、`max-request-size: 11MB`。
- `GlobalExceptionHandler.java`：`MaxUploadSizeExceededException` → 413 `FILE_TOO_LARGE`。

### Web（招聘者）

- `conversationHttpClient.ts`：新增 `ConversationAuthClient` 类型（`requestWithAuth` / `requestWithAuthForm` / `requestWithAuthDownload`）、`sendMessageWithAttachment`、`downloadAttachment`、`parseMessage`（含 `isMessageAttachment`）。
- `MessagesPage.tsx`：输入框旁「+」选择文件 → 预览文件名/大小 → 可移除；上传中禁用、失败可重试且不丢失正文；消息气泡展示附件并可点击下载。
- `models/recruiter.ts`、`api/contract.ts`、`api/authClient.ts`、`api/recruiterRepository.ts`、`api/repository.ts`、`mocks/mockRecruiterRepository.ts`、`api/queries.ts`：附件元数据贯通 + 上传/下载调用。
- `theme/global.css`：附件预览相关样式。

### Android（求职者）

- `ApiContract.kt`：`Message` 新增可空 `attachment: MessageAttachment? = null`；新增 `MessageAttachment(attachmentId, fileName, sizeBytes, contentType)`。
- `HttpApis.kt`：新增 `@Multipart sendMessageWithAttachment`（`clientMessageId`/`body` 可选/`file` 部分）与 `downloadAttachment`（返回 `ResponseBody`）。
- `RealRepositories.kt`：新增 `AttachmentUpload` / `DownloadedAttachment` 与接口方法，实现中映射 `FILE_TOO_LARGE` → 「This file is larger than 10 MB.」、`VALIDATION_ERROR` → 「Only PDF, DOC, DOCX, TXT, PNG, JPG or JPEG files are supported.」。
- `MessagesViewModel.kt`：新增 `PendingAttachment` / `DownloadEvent`、`ChatUiState` 附件状态字段、`selectAttachment` / `removeAttachment` / `consumeDownload` / `download`，并改造 `send()`（附件存在时正文可选）。
- `MessagesScreens.kt`：系统文件选择器（`ActivityResultContracts.OpenDocument`，不申请存储权限）、附件预览/移除、消息气泡附件标签、点击下载后经 FileProvider 打开。
- `AndroidManifest.xml` + `res/xml/file_paths.xml`：注册 `FileProvider`（`${applicationId}.fileprovider`，`cache-path attachments/`）用于打开下载文件。
- `AdCandidateApp.kt`：向 `ChatScreen` 传入附件相关回调。

### 文档

- `docs/openapi-v1.yaml`：`Message` schema 增加 `attachment`（`MessageAttachment` / null 的 oneOf）、新增 `MessageAttachment` schema、新增 4 个附件端点（候选/招聘者上传+下载），413 采用内联响应（未新增共享 `PayloadTooLarge` 组件，最小化对 Admin 未提交段落的侵入）。
- `docs/API_COVERAGE.csv`：新增 4 行（候选上传/下载、招聘者上传/下载）。

## 实际修改文件

### 后端

- `backend/src/main/resources/db/migration/V12__create_message_attachments.sql`（新增）
- `backend/src/main/java/com/adproject/conversation/infrastructure/MessageAttachmentEntity.java`（新增）
- `backend/src/main/java/com/adproject/conversation/infrastructure/MessageAttachmentRepository.java`（新增）
- `backend/src/main/java/com/adproject/conversation/api/ConversationDtos.java`
- `backend/src/main/java/com/adproject/conversation/application/ConversationService.java`
- `backend/src/main/java/com/adproject/conversation/api/CandidateConversationController.java`
- `backend/src/main/java/com/adproject/conversation/api/RecruiterConversationController.java`
- `backend/src/main/java/com/adproject/common/api/GlobalExceptionHandler.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/adproject/conversation/MessageAttachmentIntegrationTest.java`（新增，18 用例）

### Web

- `web/src/api/conversationHttpClient.ts`
- `web/src/api/conversationHttpClient.test.ts`
- `web/src/pages/MessagesPage.tsx`
- `web/src/pages/MessagesPage.test.tsx`
- `web/src/models/recruiter.ts`
- `web/src/api/contract.ts`
- `web/src/api/authClient.ts`
- `web/src/api/recruiterRepository.ts`
- `web/src/api/repository.ts`
- `web/src/mocks/mockRecruiterRepository.ts`
- `web/src/api/queries.ts`
- `web/src/theme/global.css`

### Android

- `android/app/src/main/java/com/adproject/candidate/data/contract/ApiContract.kt`
- `android/app/src/main/java/com/adproject/candidate/data/api/HttpApis.kt`
- `android/app/src/main/java/com/adproject/candidate/data/api/RealRepositories.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesViewModel.kt`
- `android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesScreens.kt`
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/res/xml/file_paths.xml`（新增）
- `android/app/src/test/java/com/adproject/candidate/feature/messages/MessagesViewModelTest.kt`
- `android/app/src/test/java/com/adproject/candidate/data/api/CandidateConversationRepositoryTest.kt`
- `android/app/src/test/java/com/adproject/candidate/feature/messages/MessagesPollingTest.kt`

### 文档

- `docs/openapi-v1.yaml`
- `docs/API_COVERAGE.csv`

## API / 数据库 / Flyway 变化

- 新增端点（4 个）：

| Method | Path | operationId |
|---|---|---|
| POST | `/candidate/conversations/{conversationId}/messages/attachment` | `sendCandidateMessageWithAttachment` |
| GET | `/candidate/conversations/{conversationId}/messages/{messageId}/attachment` | `downloadCandidateMessageAttachment` |
| POST | `/recruiter/conversations/{conversationId}/messages/attachment` | `sendRecruiterMessageWithAttachment` |
| GET | `/recruiter/conversations/{conversationId}/messages/{messageId}/attachment` | `downloadRecruiterMessageAttachment` |

- 上传请求：`multipart/form-data`，字段 `clientMessageId`（必填）、`body`（可选，为空即「仅附件」）、`file`（必填）；请求头 `Idempotency-Key`（必填）。
- 上传响应 `201`：消息对象（含 `attachment` 元数据，不含二进制）。
- 下载响应 `200`：原始字节 + 存储的 `Content-Type` + `X-Content-Type-Options: nosniff`。
- 权限：上传/下载仅会话参与者；未登录 401、角色不符 403、非参与者/跨公司资源 404。SYSTEM 消息不可附加附件。
- 数据库 / Flyway：新增迁移 `V12`（下一个未占用编号；V11 为同事未提交的招聘者资料迁移，未触碰）。新增 `message_attachments` 表；`messages.chk_messages_body` 由「非空」放宽为 `0..5000`。
- 限制：类型白名单 PDF/DOC/DOCX/TXT/PNG/JPG/JPEG；单文件上限 10 MB（请求上限 11 MB）。

## 测试

后端（Maven，JDK 21，全量）：

```bash
export JAVA_HOME="C:/Users/14188/.jdks/ms-21.0.8"; export PATH="$JAVA_HOME/bin:$PATH"
cd backend
"C:/Users/14188/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn.cmd" test
```

结果：`Tests run: 222, Failures: 0, Errors: 0, Skipped: 6` / `BUILD SUCCESS`。

### 真实 MySQL / Flyway 验证（本轮单独执行）

Docker Engine 当前**正在运行**（`docker version` 显示 Server `Docker Desktop 4.76.0` / Engine `29.5.2`，`desktop-linux` 上下文指向 `npipe:////./pipe/dockerDesktopLinuxEngine`）。但 Testcontainers 仍无法建立连接，`MySqlFlywayIntegrationTest` 的 6 例**全部跳过、未真实执行**（`disabledWithoutDocker = true` 触发跳过），因此真实 MySQL 迁移**仍未通过/未验证**。

根因（非本包代码问题）：本机 Testcontainers `1.21.3` 捆绑的 `docker-java`（3.4.0）未实现 Docker Desktop 4.36+ 的命名管道重定向协议——Docker Desktop 对命名管道返回 `HTTP 400` 与重定向标签 `com.docker.desktop.address=npipe://\\.\pipe\docker_cli`，Go SDK（`docker` CLI）会透明跟随，但 docker-java 将其当作连接失败。错误摘要（不含敏感信息）：

```
Could not find a valid Docker environment. Attempted configurations were:
  NpipeSocketClientProviderStrategy: failed with exception BadRequestException
  (Status 400: { ... "Labels":["com.docker.desktop.address=npipe://\\.\pipe\\docker_cli"] ... })
```

环境处理建议（二选一，均不改业务代码/迁移）：

1. 将 `pom.xml` 的 `testcontainers.version` 升级到 `1.21.4` 及以上（内含修复该重定向的 `docker-java` 3.4.2）；或
2. 在 Docker Desktop → Settings → General 勾选 “Expose daemon on tcp://localhost:2375 without TLS”，并以 `DOCKER_HOST=tcp://localhost:2375` 重跑（当前 2375 未开放，需手动开启）。

- `MySqlFlywayIntegrationTest` 结果：`Tests run: 6, Skipped: 6`（**未真实执行**）。
- `MessageAttachmentIntegrationTest` 结果：`Tests run: 18, Failures: 0, Errors: 0`（H2 上运行，独立于 MySQL，已通过）。

本轮**未新增任何代码改动**（未改业务代码 / 迁移脚本 / DB 配置 / Flyway 配置 / 迁移编号 / pom 依赖）。

### 实体映射修复：`content` LONGBLOB 与 Hibernate 期望类型不一致（本轮单独执行）

**根因**：`MessageAttachmentEntity.content` 原标注 `@Lob byte[]`，Hibernate 在 MySQL 下将其解析为 JDBC `BLOB`，MySQLDialect 渲染为 `tinyblob`；而 V12 建表为 `content LONGBLOB`（MySQL JDBC 驱动上报为 `longblob` / `Types#LONGVARBINARY`）。二者不一致，`spring.jpa.hibernate.ddl-auto: validate` 在启动时失败：

```
Schema-validation: wrong column type encountered in column [content] in table [message_attachments];
found [longblob (Types#LONGVARBINARY)], but expecting [tinyblob (Types#BLOB)]
```

**最小修复**（仅改 `MessageAttachmentEntity.java`，未改 V12 / 迁移 / DB 配置 / 测试配置）：

- `@Lob byte[]` → `@JdbcTypeCode(Types.BLOB) @Column(nullable = false, columnDefinition = "LONGBLOB")`。
- 说明：`@JdbcTypeCode(Types.BLOB)` 固定 JDBC 类型为 `BLOB`，使 H2 测试库（H2 MySQL 模式下 `LONGBLOB` 上报为 `BLOB`）通过 schema 校验；`columnDefinition = "LONGBLOB"` 使 Hibernate 对 `content` 的期望类型名与 MySQL 的 `longblob` 一致（Hibernate 6 校验按类型名做大小写不敏感比较），从而在真实 MySQL 上也通过校验。二者缺一不可：仅 `LONGVARBINARY` 会在 H2 下映射为 `varbinary` 而失败；仅 `BLOB`/`@Lob` 会在 MySQL 下映射为 `tinyblob` 而失败。

**验证结果**：

- `MessageAttachmentIntegrationTest`（JDK 21，H2）：`Tests run: 18, Failures: 0, Errors: 0` / `BUILD SUCCESS`。
- 真实 MySQL（`adproject-local-mysql` mysql:8.4，库 `adproject`，未改动其数据）：
  - `flyway_schema_history` 已执行到 V12（12 条，全部 `success=1`）。
  - 重启 `adproject-local-backend`（`mvn spring-boot:run`，源目录挂载）：`Started BackendApplication in 23.31 seconds`，Flyway 日志 `Schema 'adproject' is up to date. No migration necessary.`，**无 `Schema-validation` / `wrong column type` 错误**。
  - 启动后受保护 API 未登录均返回 `401`：`GET /api/v1/candidate/conversations`、`/api/v1/recruiter/dashboard`、`/api/v1/recruiter/conversations`。

**剩余限制**：`MySqlFlywayIntegrationTest` 仍因 Testcontainers ↔ Docker Desktop 命名管道重定向问题而跳过（见上一小节）；真实 MySQL 迁移校验本轮以「启动本地后端容器 + 读 `flyway_schema_history`」方式替代验证，未运行 Testcontainers 用例。

新增 `MessageAttachmentIntegrationTest`（18 用例，全部通过）：

- `recruiterUploadsAndCandidateDownloadsAttachment` —— 招聘者上传、求职者下载，字节一致，`Content-Type` 与 `X-Content-Type-Options: nosniff` 正确。
- `candidateUploadsAttachmentAndRecruiterDownloads` —— 求职者上传、招聘者下载。
- `attachmentOnlyMessageIsSendable` —— 仅附件（无正文）可发送，正文为空串，附件元数据完整且无 `content` 字段。
- `messageResponseExposesMetadataNotBinaryContent` —— 消息响应仅含元数据，不含二进制。
- `rejectsDisallowedFileType` —— `.exe` → 422 `VALIDATION_ERROR`，不落库。
- `rejectsContentThatDoesNotMatchDeclaredType` —— 名为 `.pdf` 但非 PDF 魔数 → 422。
- `rejectsOversizeFile` —— `10MB + 1` → 413 `FILE_TOO_LARGE`，不落库。
- `uploadAndDownloadRequireAuthentication` —— 未登录上传/下载均 401。
- `wrongRoleCannotUploadOrDownload` —— 角色不符 403。
- `nonParticipantCannotUploadOrDownload` —— 非参与者/跨公司 404。
- `uploadIsIdempotentAndDetectsReusedKey` —— 精确重放返回同一消息不重复；同 key 不同附件 → 409 `IDEMPOTENCY_KEY_REUSED`。
- `acceptsValidDocAttachment` —— 有效 DOC（OLE 头 `D0 CF 11 E0 A1 B1 1A E1`）可上传并落库。
- `acceptsValidDocxAttachment` —— 有效 DOCX（含 `[Content_Types].xml` 与 `word/document.xml` 的 ZIP）可上传并落库。
- `acceptsValidTxtAttachment` —— 有效 UTF-8 TXT（含换行/Tab）可上传并落库。
- `rejectsFakeDocWithWrongHeader` —— `.doc` 伪扩展名（非 OLE 头二进制）→ 422，不落库。
- `rejectsPlainZipDisguisedAsDocx` —— 普通 ZIP 冒充 `.docx` → 422，不落库。
- `rejectsInvalidUtf8Txt` —— 含非法 UTF-8 字节的 `.txt` → 422，不落库。
- `rejectsTxtWithNulByte` —— 含 NUL 字节的 `.txt` → 422，不落库。

Web（typecheck / lint / test / build 全绿）：

```bash
cd web && npm run typecheck   # 通过
cd web && npm run lint        # 无告警
cd web && npm test            # 172 passed
cd web && npm run build       # tsc -b + vite build 成功
```

Android（JDK 21，`testDebugUnitTest`）：

```bash
export JAVA_HOME="C:/Users/14188/.jdks/ms-21.0.8"
cd android && ./gradlew :app:testDebugUnitTest
```

结果：`BUILD SUCCESSFUL`，78 用例 0 失败；`processDebugManifest` / `processDebugResources`（FileProvider + `file_paths.xml`）处理成功。唯一告警为既有的 `LocalLifecycleOwner` 弃用提示（`AdCandidateApp.kt:92`，非本包引入）。

## 未完成内容（按任务边界）

- 未修改 Google Meet / OAuth / Calendar、Admin、ML、Agent 相关代码。
- 未做端到端 UI 手测（本轮已启动本地后端容器验证 MySQL 启动与受保护 API 401；未通过 Web/Android UI 手动上传/下载附件，未启动模拟器）。
- 附件存 MySQL `LONGBLOB`，为演示期简化方案；未做云端/对象存储、未做分块/断点、未做缩略图/预览（图片内联预览）。
- 仅「普通人类消息」支持附件；SYSTEM 面试消息不可附件（符合需求）。

## 关键一致性说明

- 「上传 + 消息创建」同一事务原子提交：校验失败（类型/大小/魔数/权限/幂等冲突）均在写消息前抛出，不产生消息、不产生附件行。
- 权限边界：上传与下载共用「会话参与者」判定（候选为会话候选、招聘者为本公司会话成员），未登录 401、角色不符 403、非参与者 404，附件不外泄给第三方。
- 下载安全：响应头固定 `X-Content-Type-Options: nosniff`，`Content-Type` 取存储值而非用户声明，文件名经 `sanitizeFileName` 清洗。
- 内容校验不信任客户端：DOC/DOCX/TXT 的校验基于文件字节（OLE 头 / ZIP 头与必需条目 / 严格 UTF-8 解码），而非扩展名或客户端 MIME；任一不匹配统一 422 `VALIDATION_ERROR` 且不产生消息/附件记录。
- 消息响应不含二进制：列表/创建响应只回 `attachment` 元数据，二进制仅经下载端点提供。
