# Package 1A — 头像上传解码前尺寸防护

> 任务：`tasks/profile-media-and-resume-hub-plan.md` 的 Package 1 的加固子项（解压炸弹防护）。
> 范围：仅 `AvatarService` 的校验逻辑与对应测试，不扩展功能。
> 本次会话未做任何 git 写操作（无 commit/push/pull/merge/reset）。

## 1. 修改内容

`backend/src/main/java/com/adproject/profile/application/AvatarService.java`：

- 原 `validateDecodable` 一步到位执行 `ImageIO.read()`（完整解码），**之后**才检查宽高/像素数——存在解压炸弹在尺寸检查前就触发大内存分配的问题。
- 现拆分为两步，并在 `upload` 中先尺寸后解码：

  1. `validateDimensions(byte[])`：通过 `ImageIO.createImageInputStream` + `ImageIO.getImageReaders` + `ImageReader.getWidth/getHeight` **只读取图片头部元数据**，不解码像素。先校验单边 ≤ 8192、总像素 ≤ 25,000,000；不通过即抛 422 `VALIDATION_ERROR`（`image dimensions are not supported`）。
  2. `validateDecodable(byte[])`：**只有通过尺寸检查后**才执行完整可解码性验证（`ImageIO.read() != null`）。

- 资源管理：`readDimensions` 在 `finally` 中 `reader.dispose()` 并 `input.close()`（`ImageInputStream`）；解析失败（含损坏图片，`IOException` 或读者抛出的运行时异常）统一返回 `null` → 422「must be a decodable image」，不产生 500。

## 2. 保留的既有行为

未改动：5 MiB 上限、PNG/JPEG 声明 MIME、魔数匹配、损坏图片拒绝、`nosniff` 响应头、Candidate/Recruiter 本人权限、公开读取与统一 404。

## 3. 修改文件

- `backend/src/main/java/com/adproject/profile/application/AvatarService.java`（仅校验逻辑）
- `backend/src/test/java/com/adproject/profile/AvatarIntegrationTest.java`（新增 2 个回归测试 + 构造头部图像的辅助方法）
- 新增本报告

未改：Flyway、数据库实体、OpenAPI、接口路径、响应结构、Android/Web、消息附件、Google OAuth、ML、Agent、Admin。

## 4. 测试真实结果（JDK 21 / `ms-21.0.8`）

新增回归测试：
- `rejectsOversizedPngDimensionsBeforeFullDecode`：构造仅含有效 PNG 头（签名 + IHDR 声明 20000×20000）而无像素数据的文件 → 422，且 `$.error.fieldErrors.file == "image dimensions are not supported"`（证明在完整解码前即被尺寸检查拦截）。
- `rejectsOversizedJpegDimensionsBeforeFullDecode`：构造仅含 SOI + SOF0（声明 20000×20000）而无扫描数据的 JPEG → 422 `VALIDATION_ERROR`。

结果：
- `AvatarIntegrationTest`：**14 tests，0 失败**（原 12 + 新增 2）。
- 相关 Profile 测试：`RecruiterProfileIntegrationTest`(5) + `CandidateProfileResumeIntegrationTest`(4) + `CandidatePublicProfileIntegrationTest`(6) = **15，0 失败**。
- 全量 `mvn test`：**251 tests，0 failures，0 errors，6 skipped**（跳过项为 `MySqlFlywayIntegrationTest`，因 `@Testcontainers(disabledWithoutDocker=true)` 且 Maven 测试 JVM 无法触达 Docker，属既有环境限制）。

## 5. 边界与限制

- 仅加固头像上传的解码前尺寸防护，不新增任何功能、接口或数据变更。
- 仍受 Package 1 的既有限制：真实 MySQL Testcontainers 验证未运行；上传/删除端到端人工验收属 Package 2。
