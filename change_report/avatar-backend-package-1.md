# Package 1 — 头像后端契约与存储

> 任务：`tasks/profile-media-and-resume-hub-plan.md` 的 Package 1。
> 范围：后端 profile/user 媒体模块、测试、OpenAPI、一条新 Flyway 迁移、报告。
> 本次会话未做任何 git 写操作（无 commit/push/pull/merge/reset）。

## 1. Flyway 编号审计结果

- 实施前重读 `backend/src/main/resources/db/migration/`，确认仓库中最大连续编号为 **V13**（`V13__create_candidate_recommendations.sql`）。
- `git status` 显示工作区有大量其它 agent 的未提交改动（conversation、recommendation、web 等），但**没有任何未跟踪/已暂存的 Flyway 迁移文件**占用下一编号（迁移目录中无 `V14__*`）。
- 因此采用下一个连续编号 **V14**，无冲突、无需重编号。

## 2. 数据库变化

新增迁移 `backend/src/main/resources/db/migration/V14__create_user_avatars.sql`，创建表 `user_avatars`：

| 列 | 类型 | 说明 |
|---|---|---|
| `user_id` | `CHAR(36)` PK + FK→`users(id)` | 每用户一行，天然「至多一张头像」 |
| `content_type` | `VARCHAR(32)` | 仅 `image/png` / `image/jpeg`，带 CHECK 约束 |
| `size_bytes` | `BIGINT` | 字节数 |
| `content` | `LONGBLOB` | 二进制内容 |
| `created_at` / `updated_at` | `DATETIME(6)` | 微秒精度 UTC |

- 不存磁盘路径、外部 URL、Base64 或密钥；不删除/改写历史用户数据（`users.avatar_url` 历史值仍保留，仅由服务端在新上传/删除时更新）。

## 3. API 变化

| Method | Path | 鉴权 | 行为 |
|---|---|---|---|
| POST | `/api/v1/profile/avatar` | Candidate 或 Recruiter 本人 | 上传/替换头像，返回安全元数据 |
| DELETE | `/api/v1/profile/avatar` | Candidate 或 Recruiter 本人 | 删除头像 + 清空 `users.avatar_url`，返回 204 |
| GET | `/api/v1/avatars/{userId}` | 公开 | 只返回头像二进制；缺失/未知统一 404 |

- 上传成功响应 `{ data: { userId, avatarUrl, contentType, sizeBytes, updatedAt } }`，其中 `avatarUrl` 恒为服务端生成的稳定路径 `/api/v1/avatars/{userId}`。
- 删除成功返回 `204 No Content`（无响应体，等价于「只返回安全元数据」）。
- 读取接口不返回用户资料、邮箱、Token、文件系统路径或数据库错误；仅设置 `Content-Type`（image/png 或 image/jpeg）、`Content-Length`、`X-Content-Type-Options: nosniff`，**不设置 `Content-Disposition`**，即不强制下载。

### 兼容现有 DTO（移除 `avatarUrl` 可写字段）

- `RecruiterProfileDtos.UpdateRecruiterProfileRequest` 移除 `avatarUrl` 字段/`@JsonSetter`/getter；`RecruiterProfileService.update` 移除 `updateAvatarUrl` 调用与 `avatarUrl` 校验分支。
- 由于全局 `spring.jackson.deserialization.fail-on-unknown-properties=true`，PATCH 提交 `avatarUrl` 现按未知字段返回 **400 `INVALID_REQUEST`**（字段错误 `avatarUrl`）。
- 读取 DTO（`RecruiterProfileData.avatarUrl`、`CandidateProfile.avatarUrl`、`RecruiterPublicProfile.avatarUrl`、消息 `Participant.avatarUrl`）保持不变，继续返回服务端生成的路径或 `null`。

## 4. 权限与文件验证策略

**权限**：上传/删除接口不接受任何 target user ID（无路径/查询/请求体目标字段），始终作用于 `@AuthenticationPrincipal` 的 `userId`；角色仅允许 `CANDIDATE` 或 `RECRUITER`（其它角色/未登录分别 403/401）。读取接口公开，但缺失头像（含不存在用户、无头像用户、非法 ID）统一返回安全 404。

**文件验证（服务端，顺序执行）**：
1. `file` 缺失/空 → 422 `VALIDATION_ERROR`；
2. 空字节 → 422；
3. 超过 5 MiB → 413 `FILE_TOO_LARGE`；
4. 声明 MIME（去参数、小写）必须是 `image/png` 或 `image/jpeg` → 否则 422（拒绝 SVG/GIF/HTML/伪造 MIME）；
5. 魔数校验（PNG `89 50 4E 47…` / JPEG `FF D8 FF`）且必须与声明类型一致 → 否则 422（拒绝伪图片、伪造 MIME）；
6. `ImageIO` 可解码 + 像素上限（单边 ≤ 8192，总像素 ≤ 2500 万）→ 否则 422（拒绝损坏图片、解压炸弹）。

## 5. 修改文件

**新增**
- `backend/src/main/resources/db/migration/V14__create_user_avatars.sql`
- `backend/src/main/java/com/adproject/profile/infrastructure/UserAvatarEntity.java`
- `backend/src/main/java/com/adproject/profile/infrastructure/UserAvatarRepository.java`
- `backend/src/main/java/com/adproject/profile/api/AvatarDtos.java`
- `backend/src/main/java/com/adproject/profile/application/AvatarService.java`
- `backend/src/main/java/com/adproject/profile/api/AvatarController.java`
- `backend/src/main/java/com/adproject/profile/api/AvatarMediaController.java`
- `backend/src/test/java/com/adproject/profile/AvatarIntegrationTest.java`

**修改**
- `backend/src/main/java/com/adproject/common/config/SecurityConfig.java`（`/api/v1/avatars/**` 加入 permitAll）
- `backend/src/main/java/com/adproject/profile/api/RecruiterProfileDtos.java`（移除可写 `avatarUrl`）
- `backend/src/main/java/com/adproject/profile/application/RecruiterProfileService.java`（移除可写 `avatarUrl`）
- `backend/src/test/java/com/adproject/profile/RecruiterProfileIntegrationTest.java`（happy path 不再写 avatarUrl；新增 avatarUrl 被拒绝断言）
- `docs/openapi-v1.yaml`（3 个新 path + `AvatarMetadata` schema + 从 `UpdateRecruiterProfileRequest` 移除 avatarUrl + 更新描述）
- `docs/API_COVERAGE.csv`（3 行新接口 + 更新 recruiter profile PATCH 摘要）

## 6. 测试真实结果（JDK 21 / `ms-21.0.8`）

`mvn test`（full backend suite）结果：**249 tests，0 failures，0 errors，6 skipped**。

新增/改动测试：
- `AvatarIntegrationTest`：**12 tests，0 失败**（Candidate/Recruiter 上传→读取→删除；未登录 401；未知用户读取 404；不支持类型、伪造 MIME、伪图片、损坏图片、空文件、超 5 MiB 拒绝；读取 MIME + `nosniff`；删除不影响他人；伪造 target user ID 被忽略）。
- `RecruiterProfileIntegrationTest`：**5 tests，0 失败**（含 PATCH `avatarUrl` → 400 `INVALID_REQUEST`）。

**跳过说明**：`MySqlFlywayIntegrationTest` 的 6 个测试被跳过，原因是该测试类标注 `@Testcontainers(disabledWithoutDocker = true)`，而 Maven 测试 JVM 无法触达 Testcontainers 所需的 Docker 守护进程（与本次改动无关的既有环境限制）。V14 迁移已由其余全部集成测试在 H2（`MODE=MySQL`）上通过 Flyway 完整执行验证（含 `LONGBLOB` 与 CHECK 约束），但在真实 MySQL 8.4 上的 Testcontainers 验证未运行。

## 7. 未改动模块与限制

- 未改动：`android/**`、`web/**`、`ml-service/**`、`agent/**`；消息附件、Google Meet/OAuth、Admin、认证流程、职位/申请逻辑；现有迁移文件（V1–V13）。
- 未删除/改写任何历史用户 `avatar_url` 数据或历史消息附件。
- 限制：未做专用 OpenAPI lint（环境无 YAML 工具，编辑严格沿用既有缩进风格）；未做真实 MySQL Testcontainers 验证（见上）；上传/删除端到端人工验收（真实图片选择器、预览）属 Package 2 范畴。
