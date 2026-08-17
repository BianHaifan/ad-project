# Package 2WA — 招聘者头像缓存刷新

> 范围：仅头像读取 Controller、对应后端集成测试、OpenAPI 该读取接口的响应 Header 说明，以及本报告。
> 本次会话未做任何 git 写操作（无 commit/push/pull/merge/reset）。

## 1. 背景与目标

头像读取地址固定为 `/api/v1/avatars/{userId}`。用户重新上传或删除头像后，浏览器可能复用旧缓存，导致个人页与顶部 AppShell 仍显示旧头像。本包保证同一用户替换或删除头像后，网页重新加载该地址时不会被浏览器错误复用旧图片。

## 2. 修改内容

### 2.1 头像读取 Controller
- 文件：`backend/src/main/java/com/adproject/profile/api/AvatarMediaController.java`
- 原因：`GET /api/v1/avatars/{userId}` 成功响应需要禁止陈旧缓存。
- 变更：在已有成功响应链上增加 `.header("Cache-Control", "no-store")`。
- 保持不变：`Content-Type`（`image/png` / `image/jpeg`）、`Content-Length`（`contentLength(avatar.getSizeBytes())`）、`X-Content-Type-Options: nosniff`、图片二进制 `body(avatar.getContent())`，以及图片不存在时的 404 行为。

### 2.2 头像集成测试
- 文件：`backend/src/test/java/com/adproject/profile/AvatarIntegrationTest.java`
- 变更：在候选人与招聘者两个「上传成功后读取图片」断言块中，各新增：
  - `.andExpect(header().string("Cache-Control", "no-store"))`
  - `.andExpect(header().longValue("Content-Length", <bytes>.length))`
- 保留：`status().isOk()`、`content().contentType(...)`、`header().string("X-Content-Type-Options", "nosniff")`、`content().bytes(...)` 均原样保留。
- 说明：`Content-Length` 断言为补强（原测试未显式断言长度，但 Controller 一直通过 `contentLength(...)` 设置）；未引入测试专用分支，未改动真实缓存策略。

### 2.3 OpenAPI 响应 Header 说明
- 文件：`docs/openapi-v1.yaml`
- 变更：在 `/avatars/{userId}` 的 `200` 响应的 `headers` 中新增 `Cache-Control`，`description: Always no-store; the image must not be served from cache after replacement or deletion`。
- 不改接口路径、请求体、响应体或权限描述。

## 3. API / 数据库变化

- API：仅 `GET /api/v1/avatars/{userId}` 成功响应新增 `Cache-Control: no-store` 头；请求路径、参数、响应体、状态码、公开读取策略不变。
- 数据库：无变化（未触碰 Flyway 迁移、表或数据）。
- 上传/删除接口路径、请求体、响应 DTO、权限规则均未改动；未把头字节或 Base64 放入 profile DTO。

## 4. 验证

- 使用项目指定 JDK 21：`C:\Users\14188\.jdks\ms-21.0.8`（OpenJDK 21.0.8，Microsoft build）。
- 构建工具：Maven 3.9.11（来自 `~/.m2/wrapper/dists/apache-maven-3.9.11`，项目无 `mvnw` 且系统 PATH 无 `mvn`）。

实际运行命令与结果：

1. `mvn -B -Dtest=AvatarIntegrationTest test`
   - 结果：`Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS。
2. `mvn -B -Dtest=RecruiterProfileIntegrationTest test`
   - 结果：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS。

## 5. 未运行全量测试的原因

本包改动面窄（单个读取 Controller 响应头 + 对应测试 + OpenAPI 文档），故仅运行头像相关集成测试与相关 profile 集成测试；未运行后端全量测试以节省时间，且改动不触及认证、上传/删除、Google OAuth、推荐等其余模块。

## 6. 禁止范围确认（均未触碰）

- 未改 Flyway 迁移、数据库表和数据。
- 未改 Android、ML、Agent、Admin、Google OAuth 逻辑。
- 未改头像上传/删除接口的路径、请求体、响应 DTO 或权限规则（上传/删除仍仅限本人且需登录）。
- 未改现有 Web Profile/Integration 页面功能。
- 未改 `.env`、密钥、Token、测试数据初始化逻辑。
- 未把头字节或 Base64 放进 profile DTO；图片读取保持现有公开读取规则（`security: []`，未知用户统一 404）。
