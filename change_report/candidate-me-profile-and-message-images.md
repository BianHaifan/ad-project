# Package C — 求职者 Me 重构 + Profile 扩展 + 消息图片内联显示

**Date:** 2026-08-16
**范围：** 仅 Android 求职者端（Me 页重构、独立 Profile/Resume 编辑页、消息图片内联缩略图）、支撑它的后端 Profile 扩展（gender/phone/birthplace）、对应 OpenAPI/测试，以及本报告。
**性质：** 完成「Android 求职者 Me 重构 + Profile 扩展 + 消息图片内联显示」完整功能包。未做任何 git 写操作（无 commit/push/pull/merge/reset），未清理他人改动，未新增第三方依赖，未提交密钥。

---

## 1. 完成的行为

### 1.1 后端 — Candidate Profile 扩展（gender / phone / birthplace）

- 新增 [`V15__add_candidate_profile_contact_and_gender.sql`](../backend/src/main/resources/db/migration/V15__add_candidate_profile_contact_and_gender.sql)：`candidate_profiles` 增加 `gender VARCHAR(32) NULL`、`phone VARCHAR(32) NULL`、`birthplace VARCHAR(100) NULL`，并对 `gender` 加 `CHECK` 约束（仅允许 `MALE/FEMALE/OTHER/PREFER_NOT_TO_SAY` 或 NULL）。
  - 迁移拆成**多条单列 `ALTER TABLE`** 语句，规避 H2 测试库（`MODE=MySQL`）对「多列逗号分隔 `ADD COLUMN`」的语法拒绝。
- 新增 [`Gender.java`](../backend/src/main/java/com/adproject/profile/domain/Gender.java) 受控枚举（`MALE/FEMALE/OTHER/PREFER_NOT_TO_SAY`）。
- [`CandidateProfileEntity.java`](../backend/src/main/java/com/adproject/profile/infrastructure/CandidateProfileEntity.java)、[`ProfileDtos.java`](../backend/src/main/java/com/adproject/profile/api/ProfileDtos.java) 的 `CandidateProfile` / `UpdateProfileRequest` 增加 gender/phone/birthplace（可空）。
- [`CandidateProfileService.java`](../backend/src/main/java/com/adproject/profile/application/CandidateProfileService.java)：
  - `PHONE_PATTERN = ^\+?[0-9][0-9\s\-()]{4,19}$`；`validateOptionalFields` 校验 phone 格式与 birthplace ≤100；`normalizeOptional` 把空白串归一为 null。
  - 版本冲突 409 `VERSION_CONFLICT`；仅本人读写（`requireCandidate`，`FORBIDDEN`）。
  - **保留 legacy `location` 字段读取/写入兼容**（`isLocationPresent`/`getLocation`），但 Me UI 不再展示/编辑它；Android 请求不再携带 `location`。
- OpenAPI [`docs/openapi-v1.yaml`](../docs/openapi-v1.yaml) 的 candidate profile 段落同步扩展。

### 1.2 Android — Me 页重构（恰好 5 项）

[`RealProfileScreens.kt`](../android/app/src/main/java/com/adproject/candidate/feature/profile/RealProfileScreens.kt) 的 `MeContent` 渲染 5 个入口：

1. **Profile** — 只读头像 + 姓名 + 「Add photo」/headline 副标题，**不含** location/email；点击进入独立 Profile 编辑页。
2. **My applications** — 三组计数磁贴：In progress（APPLIED+IN_REVIEW）、Interview、Archived（REJECTED+WITHDRAWN）；点击进入既有 applications 页。
3. **Resume** — 入口 + 简短状态；点击进入独立 Resume 编辑页（不再内联表单）。
4. **Job preferences** — 保留。
5. **Sign out** — 保留。

删除旧的 inline 快照卡片 / 身份卡 / Resume Hub / 统计瓦片等死 UI；移除无路由的 [`ResumeEditScreen.kt`](../android/app/src/main/java/com/adproject/candidate/feature/profile/ResumeEditScreen.kt) 残留。

### 1.3 Android — 独立 Profile 编辑页

- 字段：头像（选择/上传/删除）、full name、gender、headline、phone、birthplace；**不含** location/email。
- `GenderSelector` 用 Material3 `FilterChip` 每枚举值一枚，重复点击置 null；`genderLabel` 映射 Male/Female/Other/Prefer not to say。
- [`ProfileViewModels.kt`](../android/app/src/main/java/com/adproject/candidate/feature/profile/ProfileViewModels.kt) `save(fullName, gender, headline, phone, birthplace)`：fullName 必填 ≤100、headline ≤200、phone 匹配同一 `PHONE_PATTERN`、birthplace ≤100；phone/birthplace 空白归 null。新增 `clearSaved()`。

### 1.4 Android — 独立 Resume 编辑页

- 复用真实 Resume API；`ResumeEditForm` 从旧内联表单移出，从 `state.data` 预填，处理 `notCreated`（version 0 创建）；经验增删、技能、版本冲突保持不变。

### 1.5 Android — 消息图片内联缩略图

- [`MessagesViewModel.kt`](../android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesViewModel.kt)：
  - `ChatUiState` 新增 `imageThumbnails: Map<String, ImagePreview>` 与 `loadingThumbnails: Set<String>`。
  - `ensureImageThumbnails` 仅对「PNG/JPEG 且未缓存、未在下载中」的消息发起缩略图下载，轮询不会重复下载已加载图片；`downloadThumbnail` 失败时清理 `loadingThumbnails`（回退为文件 chip，可再点重试）。
  - `openImage` 复用已缓存缩略图，否则走原 `download` 路径。
- [`MessagesScreens.kt`](../android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesScreens.kt)：
  - 新增 `InlineImageAttachment`：`produceState` + `Dispatchers.Default` 离主线程解码缩略图，成功后显示可点击 Image（点击放大），加载中显示 spinner，解码失败/无缩略图回退为附件 chip。
  - `ImagePreviewDialog` 大图解码移至 `produceState` 离主线程。
- 仅 PNG/JPEG 内联显示；PDF/Word/TXT 保持既有 `FileProvider + ACTION_VIEW` 下载/外部打开。

---

## 2. 具体修改文件

**Backend**
- [`backend/src/main/resources/db/migration/V15__add_candidate_profile_contact_and_gender.sql`](../backend/src/main/resources/db/migration/V15__add_candidate_profile_contact_and_gender.sql)（新增）
- [`backend/src/main/java/com/adproject/profile/domain/Gender.java`](../backend/src/main/java/com/adproject/profile/domain/Gender.java)（新增）
- [`backend/src/main/java/com/adproject/profile/infrastructure/CandidateProfileEntity.java`](../backend/src/main/java/com/adproject/profile/infrastructure/CandidateProfileEntity.java)
- [`backend/src/main/java/com/adproject/profile/api/ProfileDtos.java`](../backend/src/main/java/com/adproject/profile/api/ProfileDtos.java)
- [`backend/src/main/java/com/adproject/profile/application/CandidateProfileService.java`](../backend/src/main/java/com/adproject/profile/application/CandidateProfileService.java)
- [`backend/src/test/java/com/adproject/profile/CandidateProfileResumeIntegrationTest.java`](../backend/src/test/java/com/adproject/profile/CandidateProfileResumeIntegrationTest.java)（+4 用例，共 7）
- [`docs/openapi-v1.yaml`](../docs/openapi-v1.yaml)（candidate profile 段）

**Android**
- [`android/app/src/main/java/com/adproject/candidate/data/contract/ApiContract.kt`](../android/app/src/main/java/com/adproject/candidate/data/contract/ApiContract.kt)：`Gender` 枚举、`CandidateProfileDto` +3 可空字段、`UpdateProfileRequest`（移除 `location`，Moshi 会序列化 null 字段故完全不下发）。
- [`android/app/src/main/java/com/adproject/candidate/feature/profile/ProfileViewModels.kt`](../android/app/src/main/java/com/adproject/candidate/feature/profile/ProfileViewModels.kt)：`save` 新签名 + 校验 + `clearSaved()`。
- [`android/app/src/main/java/com/adproject/candidate/feature/profile/RealProfileScreens.kt`](../android/app/src/main/java/com/adproject/candidate/feature/profile/RealProfileScreens.kt)：5 项 Me 页 + 独立 Profile/Resume 编辑页；删除死 UI。
- [`android/app/src/main/java/com/adproject/candidate/feature/profile/ResumeEditScreen.kt`](../android/app/src/main/java/com/adproject/candidate/feature/profile/ResumeEditScreen.kt)（删除，无路由/无调用方）
- [`android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesViewModel.kt`](../android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesViewModel.kt)：缩略图状态 + `ensureImageThumbnails`/`downloadThumbnail`/`openImage`。
- [`android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesScreens.kt`](../android/app/src/main/java/com/adproject/candidate/feature/messages/MessagesScreens.kt)：`InlineImageAttachment` + 离主线程解码。
- [`android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt`](../android/app/src/main/java/com/adproject/candidate/AdCandidateApp.kt)：`ProfileEdit`/`ResumeEdit` 路由、ViewModel 提升到根、`saved` 触发返回、`onOpenImage` 接线。

**Android 测试**
- [`ProfileResumeViewModelTest.kt`](../android/app/src/test/java/com/adproject/candidate/ProfileResumeViewModelTest.kt)：`save` 新签名 + 3 个新用例（手机号/出生地校验、gender/phone/birthplace 下发、空白归 null）。
- [`ApplicationViewModelTest.kt`](../android/app/src/test/java/com/adproject/candidate/ApplicationViewModelTest.kt)：`profile()` helper 适配 3 个新可空字段。
- [`RepositoryIntegrationTest.kt`](../android/app/src/test/java/com/adproject/candidate/RepositoryIntegrationTest.kt)：`UpdateProfileRequest` 新签名 + 新增「gender/phone/birthplace 序列化且 `location` 缺失」用例。
- [`MessagesViewModelTest.kt`](../android/app/src/test/java/com/adproject/candidate/MessagesViewModelTest.kt)：新增缩略图去重 + `openImage` 复用缓存 + 失败回退用例。

**报告**
- `change_report/candidate-me-profile-and-message-images.md`

---

## 3. API / 数据库 / Flyway 变更

- **Flyway**：新增 V15（三列 + gender CHECK）。无改动既有 14 个迁移。
- **API**：`GET/PATCH /api/v1/candidate/profile` 的 candidate profile DTO 与更新请求新增可选 `gender`/`phone`/`birthplace`；`location` 保留兼容但不展示/编辑。
- **鉴权/所有权**：仍仅本人读写（`FORBIDDEN` 拦截非 CANDIDATE）。
- **消息图片**：复用现有带鉴权 + 所有权校验的附件下载接口，未暴露直连公开 URL，未把图片 Base64 写入消息 DTO/数据库。

---

## 4. 执行的命令与结果

| 层 | 命令 | 结果 |
|---|---|---|
| Backend | `mvn test`（JDK 21 `C:\Users\14188\.jdks\ms-21.0.8`） | ✅ **全绿**：24 个测试类 0 失败 0 错误；`CandidateProfileResumeIntegrationTest` 7 tests / 0 失败（`MySqlFlywayIntegrationTest` 因无真实 MySQL 跳过 6 个，为既有行为） |
| Android | `./gradlew testDebugUnitTest assembleDebug`（JDK 21） | ✅ **BUILD SUCCESSFUL**（44 tasks；`compileDebugKotlin` 仅一条与本次改动无关的既有 `LocalLifecycleOwner` 弃用警告） |

Android 编译期间修复了一处 `produceState` 委托属性智能转换失败（`Smart cast to 'ImageBitmap'/'Bitmap' is impossible, because 'bitmap' is a delegated property`），通过先取局部 `val decoded = bitmap` 解决。

---

## 5. 未验证项（未声称通过）

1. **未做 Android 模拟器/真机手动点击验收**：本环境无可用 AVD，Me 页五项导航、Profile/Resume 编辑表单交互、图片点击放大/关闭等手势均通过**代码级回归 + 单元测试**佐证，不标记为「人工点击通过」。
2. **未做消息图片真机解码手测**：真机/模拟器上真实 PNG/JPEG 附件的内联解码与显示未人工验证；已用 `runCatching` + 回退 chip + 失败清理保证不崩溃。
3. **未运行 Android instrumented/Compose UI 测试**：项目未配置 UI 测试基础设施。

---

## 6. 禁止范围确认（均未触碰）

- ✅ 未修改 Web / Admin / ML / Agent / Google OAuth & Meet / 其它数据库业务模块 / 认证方式 / 申请状态机。
- ✅ 未新增第三方依赖（图片内联仅用已有 `BitmapFactory`/`asImageBitmap`/`produceState`，未引入 Coil/Glide 等新库）。
- ✅ 未提交密钥 / `.env` / Token。
- ✅ 未清理或覆盖他人工作区改动（仅删除本包明确目标 `ResumeEditScreen.kt`；`web/` 与其它 backend 模块的既有未提交改动保持原样）。
- ✅ 未 commit / push / pull / merge / reset。
