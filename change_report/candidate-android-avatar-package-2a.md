# Package 2a — Android 求职者头像上传

> 范围：仅 Android 求职者端 `data/api/**`、`core/network/CandidateAppContainer.kt`（仅注入仓库）、
> `feature/profile/**`、`AdCandidateApp.kt`（仅 Profile ViewModel 与回调接线）、Android 单元测试，以及本报告。
> 未做任何 git 写操作（无 commit/push/pull/merge/reset）。

## 1. 复用的后端契约（未修改）

- `POST /api/v1/profile/avatar`（multipart 字段 `file`）：返回 `data.userId` / `data.avatarUrl` / `data.contentType` / `data.sizeBytes` / `data.updatedAt`。
- `DELETE /api/v1/profile/avatar`：204 No Content。
- 仅接受 PNG/JPEG，单文件 ≤ 5 MiB（服务端按魔数校验并返回 `FILE_TOO_LARGE` / `VALIDATION_ERROR`）。
- `GET /api/v1/avatars/{userId}` 公开可读，响应 `Cache-Control: no-store`。
- 客户端不提交、不编辑 `avatarUrl` 字段。

## 2. 完成的 Android 行为（求职者端）

- 头像入口在「我的」资料卡：头像圆形区域可点击，或下方「Add photo / Change photo」按钮，通过系统照片选择器
  `ActivityResultContracts.PickVisualMedia`（`ImageOnly`，无需宽泛存储权限）选择图片。
- 选中后本地预览：图片字节仅存于 ViewModel 的瞬态内存状态 `AvatarUiState.pending`，用 `BitmapFactory.decodeByteArray`
  解码为 `ImageBitmap` 显示在头像圆形区域；解码失败静默回退，不崩溃。
- 上传 / 替换 / 删除：
  - 选中后出现「Upload photo / Cancel」；已有头像时显示「Change photo / Remove photo」。
  - 上传前客户端校验：仅 `image/png` / `image/jpeg`（否则提示「Only PNG or JPEG images are supported.」），
    单文件 ≤ 5 MiB（否则提示「This image is larger than 5 MB.」）。
  - 上传成功用响应里的 `avatarUrl` 立即回写 `CandidateProfileDto.avatarUrl`，资料卡即时刷新；删除成功置空 `avatarUrl`。
  - 上传/删除中按钮置灰（Uploading… / Removing…），失败保留待上传预览并显示安全错误文案。
- 无头像时显示首字母圆形占位（initials fallback）。
- 相对路径解析：`/api/v1/avatars/{userId}` 由 `resolveAvatarUrl` 拼到 `BuildConfig.API_BASE_URL` 的 origin
  （scheme + host + port）上；拒绝空值、绝对 URL、`file:`/`content:`/`javascript:`/`data:`、协议相对 `//` 与无前导斜杠路径，
  任意不可信 `avatarUrl` 无法把图片加载器指到任意主机或协议。
- 无陈旧 Coil 缓存：每次成功上传/删除使 `avatar.revision` 递增，`resolveAvatarUrl` 以 `?v=<revision>` 改变加载 key；
  同时头像 `AsyncImage` 显式 `memoryCachePolicy(DISABLED)` + `diskCachePolicy(DISABLED)`，替换后不显示旧图。
- 图片字节/URI 不写入 DataStore、日志、持久化 session 或 DTO；不提交/编辑 `avatarUrl` 字段；无任意 URL 输入框或外链图床上传。

## 3. 具体修改文件

**网络 / 契约 / 仓库**
- `android/.../data/contract/ApiContract.kt`：新增 `AvatarMetadata(userId, avatarUrl, contentType, sizeBytes, updatedAt)`。
- `android/.../data/api/HttpApis.kt`：`CandidateProfileHttpApi` 新增 `@Multipart @POST("profile/avatar") uploadAvatar(@Part file)` 与
  `@DELETE("profile/avatar") deleteAvatar()`；新增 `retrofit2.http.DELETE` 导入。
- `android/.../data/api/RealRepositories.kt`：新增 `AvatarUpload`、`CandidateAvatarRepository` 与 `RealCandidateAvatarRepository`
  （multipart 组装、`FILE_TOO_LARGE`/`VALIDATION_ERROR` 映射、IO/通用异常安全文案）。
- `android/.../core/network/CandidateAppContainer.kt`：注入 `candidateAvatarRepository`（复用 `CandidateProfileHttpApi`）。

**ViewModel**
- `android/.../feature/profile/ProfileViewModels.kt`：`ProfileUiState` 新增 `avatar: AvatarUiState`；新增 `AvatarUiState`、
  `PendingAvatar`；`CandidateProfileViewModel` 新增 `avatarRepository` 依赖与 `selectAvatar`/`cancelAvatar`/`uploadAvatar`/`deleteAvatar`；
  `factory` 增加 `avatarRepository`；新增私有 `MAX_AVATAR_BYTES`、`isSupportedAvatarType`。

**URL 解析**
- `android/.../feature/profile/AvatarMediaUrl.kt`（新增）：`resolveAvatarUrl(avatarUrl, revision, apiBaseUrl)` 纯函数 + origin 提取。

**UI**
- `android/.../feature/profile/RealProfileScreens.kt`：`RealProfileScreen`/`ProfileContent`/`IdentityCard` 增加头像回调；新增
  `rememberAvatarPicker`（PickVisualMedia + 字节读取）、`AvatarActions`（Add/Change/Remove/Upload/Cancel）；`CandidateAvatar`
  支持本地预览优先级、首字母占位与禁用缓存；新增 `AvatarImage`（Coil `ImageRequest` 禁用内存+磁盘缓存）。
- `android/.../AdCandidateApp.kt`：`CandidateProfileViewModel.factory(...)` 增加 `candidateAvatarRepository`，并接线
  `onSelectAvatar/onUploadAvatar/onDeleteAvatar/onCancelAvatar`。

**测试**
- `android/.../ProfileResumeViewModelTest.kt`：`profile()` 增加 `avatarUrl` 参数；新增 `avatarMetadata`/`pendingAvatar` 助手与
  `AvatarFake`；7 个既有 `CandidateProfileViewModel(...)` 构造点补齐 `AvatarFake()`；新增 7 个头像用例（选择/取消、非 PNG/JPEG 拒绝、
  超 5MiB 拒绝、上传成功回写 URL 并递增 revision、上传失败保留预览、删除成功清空并递增 revision、删除失败保留并提示）。
- `android/.../AvatarMediaUrlTest.kt`（新增）：`resolveAvatarUrl` 相对路径+revision 解析、拒绝空/绝对/不安全协议、去除 base 尾斜杠 3 个用例。

**报告**
- `change_report/candidate-android-avatar-package-2a.md`

## 4. API / 数据库 / Flyway

- API：无变化（未新增后端接口、未改路径/请求体/响应 DTO/鉴权/所有权规则；仅复用既有头像契约）。
- 数据库 / Flyway / OpenAPI：无变化。
- 未把头像 Base64、图片字节或 URI 写入 DataStore、日志、持久化 session 或 DTO。

## 5. 实际执行的命令与结果

**Android**（JDK 21.0.8：`C:\Users\14188\.jdks\ms-21.0.8`）
- `gradlew testDebugUnitTest --tests "*ProfileResumeViewModelTest" --tests "*AvatarMediaUrlTest"`：**BUILD SUCCESSFUL**
  （20 个测试通过：7 个头像用例 + 3 个解析用例 + 10 个既有 Profile/Resume/Preference 用例）。
- `gradlew testDebugUnitTest`（全量单元测试）：**BUILD SUCCESSFUL**。
- `gradlew assembleDebug`：**BUILD SUCCESSFUL**（debug 编译/打包通过）。
- 编译期仅一条与本次改动无关的既有 `LocalLifecycleOwner` 弃用警告（`AdCandidateApp.kt:95`，来自此前其它改动）。

## 6. 未执行的测试及原因

- 未运行后端测试——后端未改动。
- 未运行 Android instrumented / Compose UI 测试（项目未配置 UI 测试基础设施；测试依赖仅 `junit`/`mockwebserver`/`coroutines-test`，
  照片选择器与 Compose 图片渲染需真机/模拟器，以下第 7 节给出人工验收步骤）。
- 未做真机端到端手测（真实照片选择、上传、替换后图片刷新），以下第 7 节给出人工验收步骤。

## 7. 手动验收步骤

1. 登录求职者账号，进入「我的」页。
2. 无头像时头像显示首字母占位，下方有「Add photo」；点击头像或按钮打开系统照片选择器（仅图片）。
3. 选择一张 PNG/JPEG：头像区域立即显示本地预览，出现「Upload photo / Cancel」。
4. 点「Upload photo」：短暂显示「Uploading…」，成功后头像即时变为新图，预览消失，按钮恢复「Change photo / Remove photo」。
5. 再次点「Change photo」选择另一张图并上传，观察头像立即替换为新图（无旧图残留/闪烁）。
6. 点「Remove photo」：短暂显示「Removing…」，成功后头像回退为首字母占位。
7. 选择非 PNG/JPEG（如 GIF/WebP）或超 5MiB 图片并尝试上传，出现对应错误提示且不上传。
8. 断网上传/删除：显示安全错误文案，待上传预览保留，不崩溃；既有「Edit profile」文本编辑、简历预览、登出等行为不变。

## 8. 禁止范围确认（均未触碰）

未改：`backend/**`、Flyway、OpenAPI、数据库、Docker；Web、Admin、ML、Agent；Android 登录、注册、消息、职位、投递、面试、
Google Meet/OAuth；现有 Profile PATCH 与 Resume API 的字段、版本控制或保存逻辑；Gradle 依赖、版本或构建配置；`.env`、密钥、Token、
真实测试账号。未把头像 Base64/图片字节/URI 写入 DataStore、日志、持久化 session 或 DTO；未使用外部图床、任意 URL 输入框或任意 URL
上传替代真实文件上传。
