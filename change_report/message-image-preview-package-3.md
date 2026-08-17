# Package 3 — 消息图片预览

> 范围：仅 Web 招聘者消息页、Android 求职者消息功能、两者测试与样式，以及本报告。
> 未做任何 git 写操作（无 commit/push/pull/merge/reset）。

## 1. 完成的 Web 行为（招聘者端）

- 仅当附件 `contentType` 严格为 `image/png` 或 `image/jpeg`（以契约为准）时，作为可预览图片处理。
- 图片数据通过现有 `useDownloadAttachment` → `downloadAttachment(conversationId, messageId)` 认证下载取得 `Blob`，绝不拼接或猜测后端附件 URL，绕过鉴权与所有权校验。
- 消息气泡内：加载中显示占位；成功后显示带最大宽高、保持比例、圆角、`object-fit: contain` 的缩略图；`alt` 为文件名。
- 下载失败、`<img onError>`（Blob 不可解码）时：显示安全文案「Image preview unavailable.」，并保留原「下载附件」入口，单张失败不拖垮整页。
- `URL.createObjectURL` 的地址在组件卸载、图片替换、解码失败时均 `URL.revokeObjectURL`，无泄漏。
- 依赖 React Query 稳定 key（`messageId`）与 effect 稳定依赖，避免 1 秒轮询重渲染重复下载同一图片。
- 发送区（推荐项）：刚选中的 PNG/JPEG 在发送区显示本地缩略图，临时 Object URL 在文件替换/移除/卸载时正确释放；不影响既有文件选择、移除、发送、失败重试。

## 2. 完成的 Android 行为（求职者端）

- 复用 `CandidateConversationRepository.downloadAttachment(...)`，未新建网络接口。
- 点击 PNG/JPEG 附件：`ChatViewModel.download` 分支为内置预览（`ImagePreview` 状态，含 fileName/contentType/bytes），不再走外部打开。
- 预览用 Compose `Dialog`：下载中的 loading（附件 chip 的进度指示）、关闭入口、`BitmapFactory.decodeByteArray` + `asImageBitmap` 按比例缩放并限制在 420dp 高、屏幕可视范围内；解码失败（`runCatching` → null）显示「This image could not be displayed.」不崩溃。
- 仅对 `image/png`、`image/jpeg` 内置预览；PDF/Word/TXT 等非图片仍走既有 `FileProvider + ACTION_VIEW` 下载/外部打开流程。
- 未引入 Coil/Glide 或任何新图片依赖（使用已有 `BitmapFactory`/`asImageBitmap`；项目中已有的 Coil 仅用于他处，未用于本预览）。
- 预览字节仅在预览生命周期内保留；关闭（`closeImagePreview`）后清空状态；解码失败不崩溃。
- 上传、删除待发送附件、发送、轮询、未读、错误提示行为不变。

## 3. 具体修改文件

**Web**
- `web/src/pages/MessagesPage.tsx`：新增 `isPreviewableImage`、`MessageImageAttachment`（下载一次 + object URL 生命周期 + 失败降级）、`ComposerImagePreview`（发送区本地缩略图）；消息气泡按内容类型分支渲染；发送区缩略图。
- `web/src/pages/MessagesPage.test.tsx`：新增 5 个用例（图片下载渲染、非图片仍走原下载、图片失败降级、卸载 revoke、发送区缩略图 revoke）；引入 `beforeEach` 设置 `URL.createObjectURL/revokeObjectURL` mock。
- `web/src/theme/global.css`：新增 `.message-image`、`.message-image-loading`、`.message-image-fallback`、`.composer-file`、`.composer-image`（该文件在本次改动前已有 Package 2W 的头像样式改动，本次为增量追加）。

**Android**
- `android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesViewModel.kt`：`ChatUiState` 新增 `imagePreview`；新增 `ImagePreview` 数据类、`closeImagePreview()`；`download()` 按声明 `contentType` 分支为内置预览或外部打开；新增私有 `isPreviewableImage`。
- `android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesScreens.kt`：`ChatScreen` 新增 `onCloseImagePreview` 参数；新增 `ImagePreviewDialog`（Compose `Dialog` + `BitmapFactory` 解码 + `runCatching` 降级 + 关闭）。
- `android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`：接线 `onCloseImagePreview = chatViewModel::closeImagePreview`（该文件在本次改动前已有其它 agent 的未提交改动，本次为单行增量，未触碰其既有内容）。
- `android/app/src/test/java/com/adproject/candidate/MessagesViewModelTest.kt`：`attachmentMeta` 增加 `contentType` 参数；新增 4 个用例（图片下载进入预览态、图片下载失败显示错误且无预览、非图片仍发外部打开事件、关闭预览清理状态）。

**报告**
- `change_report/message-image-preview-package-3.md`

## 4. API / 数据库 / Flyway

- API：无变化（未新增接口、未改路径/请求体/响应 DTO/鉴权/所有权规则）。图片仍通过现有带登录鉴权与所有权校验的下载接口读取，未暴露直连公开附件 URL。
- 数据库 / Flyway：无变化。
- 未把图片 Base64 写入消息 DTO、localStorage 或数据库。

## 5. 实际执行的命令与结果

**Web**
- `npm run typecheck`：通过（0 错误）。
- `npm test`（Vitest）：**23 files，201 tests，0 失败**。期间 jsdom 对 `<a download>` 点击输出一条非致命的「Not implemented: navigation」日志（来自非图片下载用例触发真实下载锚点），不影响测试结果。
- `npm run build`：成功（`tsc -b && vite build`，129 modules，`✓ built in 1.15s`）。

**Android**（JDK 21.0.8：`C:\Users\14188\.jdks\ms-21.0.8`）
- `./gradlew testDebugUnitTest --tests "*MessagesViewModelTest" --tests "*MessagesPollingTest"`：**BUILD SUCCESSFUL**（含 `compileDebugKotlin`，仅一条与本次改动无关的既有 `LocalLifecycleOwner` 弃用警告）。
- `./gradlew assembleDebug`：**BUILD SUCCESSFUL**（debug 编译/打包通过）。

## 6. 未执行的测试及原因

- 未运行 Android 完整单元测试套件（仅运行消息相关 `MessagesViewModelTest`/`MessagesPollingTest`）——本次改动只涉及消息模块，其余模块无需回归。
- 未运行 Android instrumented/Compose UI 测试（项目未配置 UI 测试基础设施，测试依赖仅 `junit`/`mockwebserver`/`coroutines-test`）。
- 未运行后端测试——后端未改动。
- 未做真机端到端手测（真实图片附件在真机/浏览器上的解码与显示），以下第 7 节给出人工验收步骤。

## 7. 手动验收步骤

**Web（招聘者端）**
1. 登录招聘者账号，进入与某候选人的消息会话。
2. 点击「+」选择一张 PNG 或 JPEG，发送区出现本地缩略图；点「Remove」缩略图消失；点「Send」发送。
3. 发送后，消息气泡中图片附件先显示「Loading image…」，随后显示带圆角、限制尺寸的缩略图。
4. 每约 1 秒轮询刷新时，观察已预览图片不被重复下载/闪烁。
5. 发送一个 PDF/TXT，气泡仍显示文件名+大小，点击触发浏览器下载（不显示图片预览）。
6. 断网或对图片附件制造失败，气泡显示「Image preview unavailable.」且仍可点击下载文件。

**Android（求职者端）**
1. 登录求职者账号，进入消息会话，收到或发送一张 PNG/JPEG。
2. 点击该图片附件：附件 chip 短暂显示进度，随后弹出应用内图片预览（可缩放、关闭按钮）。
3. 点击「Close」或返回关闭预览，状态清空，可再次点击重新预览。
4. 点击 PDF/Word/TXT 附件：仍走系统外部打开（FileProvider + ACTION_VIEW）下载/查看。
5. 构造下载失败/损坏图片，预览显示可理解错误且应用不崩溃；正常文字/附件发送与未读、轮询行为不受影响。

## 8. 禁止范围确认（均未触碰）

未改：`backend/**`、Flyway、OpenAPI、数据库结构或初始化数据；Android 登录/注册/个人资料/简历/职位推荐；Web Profile/Integration/Google OAuth/面试/Dashboard；ML/Agent/Admin；既有附件上传接口、消息接口、轮询间隔、鉴权或资源所有权规则；`.env`/密钥/Token。未新增第三方图片加载库，未将图片 Base64 写入消息 DTO/localStorage/数据库。
