# Profile / Resume / Media 最终验收与收尾

**Date:** 2026-08-16
**性质:** 对已完成的头像上传、消息图片预览、Web Profile 集成、Android Resume Hub 的最终回归与收尾。仅修复明确的问题（头像选择器有界读取 + 删除无路由的旧 Resume UI 残留），未新增功能、未引入依赖、未重构无关模块。

---

## 1. 本次修复（2 项）

### 1.1 Android 头像选择器有界读取（修复）

**问题：** 头像选择器使用无界 `readBytes()`，大文件会被完整读入内存且阻塞。

**修复内容：**
- 新增 [`AvatarBytes.kt`](../android/app/src/main/java/com/adproject/candidate/feature/profile/AvatarBytes.kt)：`readAvatarBytes(input, maxBytes)` 最多读取 `maxBytes + 1` 字节（`ByteArray(maxBytes + 1)` 缓冲），超过上限立即返回 `AvatarReadResult.TooLarge`，否则返回 `Ok(copyOf(total))`。**不再使用无界 `readBytes()`。**
- [`RealProfileScreens.kt:231-258`](../android/app/src/main/java/com/adproject/candidate/feature/profile/RealProfileScreens.kt#L231-L258) 的 `rememberAvatarPicker`：
  - 在 `Dispatchers.IO` 后台线程读取（`scope.launch(Dispatchers.IO)`），不阻塞 UI。
  - 调用 `readAvatarBytes(it, MAX_AVATAR_BYTES)`，`MAX_AVATAR_BYTES = 5 * 1024 * 1024`（5 MiB）。
  - 超限立即停止并回调 `onTooLarge()` → `CandidateProfileViewModel.rejectAvatarTooLarge()` → 复用既有安全文案 `"This image is larger than 5 MB."`。
- [`ProfileViewModels.kt`](../android/app/src/main/java/com/adproject/candidate/feature/profile/ProfileViewModels.kt)：新增 `rejectAvatarTooLarge()` 与常量 `MAX_AVATAR_BYTES` / `AVATAR_TOO_LARGE_MESSAGE`；`uploadAvatar()` 的超限分支复用同一文案（删除内联字面量）。
- 新增测试 [`AvatarBytesTest.kt`](../android/app/src/test/java/com/adproject/candidate/AvatarBytesTest.kt)（5 个用例，含 `overLimitDoesNotReadBeyondCap` 用 `CountingInputStream` 断言超限最多只多读 1 字节）。

### 1.2 删除无路由的旧 Resume UI 残留（清理）

- 删除 `android/app/src/main/java/com/adproject/candidate/feature/profile/ResumeEditScreen.kt`。
- 删除前已确认：该文件**无路由**（`Route` 对象中不存在对应 route）、**无调用方**（仅自身定义），且**删除后无任何源码引用残留**（仅 Gradle `executionHistory.bin` 构建缓存二进制含历史字符串，非源码）。
- 未触碰其他无关旧文件（`CandidateApi`、`ResumeData`、`FakeCandidateRepository` 等遗留层仍被 `LearningScreen.getLearning()` 使用，保持原样）。

---

## 2. 测试结果

| 层 | 命令 | 结果 |
|---|---|---|
| Android | `./gradlew testDebugUnitTest assembleDebug`（JDK 21） | ✅ BUILD SUCCESSFUL（44 tasks，含新 `AvatarBytesTest` + `ProfileResumeViewModelTest`） |
| Backend | `mvn -Dtest=AvatarIntegrationTest,ConversationIntegrationTest test`（JDK 21 + Docker Testcontainers） | ✅ BUILD SUCCESS，**29 tests / 0 失败**（`AvatarIntegrationTest` 14 + `ConversationIntegrationTest` 15） |
| Web typecheck | `npm run typecheck` | ✅ 通过 |
| Web test | `npm test` | ✅ 通过（23 文件 / 201 tests；仅一条 jsdom `Not implemented: navigation` 无害告警） |
| Web build | `npm run build` | ✅ 通过（129 modules，`tsc -b && vite build`） |

Backend 测试使用 H2 测试库 + Flyway 14 个迁移全部通过；Docker server 29.5.2 可用。

---

## 3. 回归确认（代码级）

### Web（recruiter）
- **头像上传/替换/删除**：`ProfilePage.tsx` `AvatarSection` — 上传（`useUploadAvatar` → POST form）、删除（`useDeleteAvatar` → DELETE）、`validateAvatarFile`（仅 PNG/JPEG + ≤5MB）、`presentAvatarError`（`FILE_TOO_LARGE` / `VALIDATION_ERROR` / `NETWORK_ERROR`）。`avatarHttpClient.ts` 提供 upload/delete。
- **Profile Google Calendar 卡片**：`GoogleConnectionSection.tsx` — `CONNECTED` / `REVOKED` / 未连接 三态，connect/disconnect 完整。
- **图片消息预览**：`MessagesPage.tsx` `MessageImageAttachment` — PNG/JPEG 走 blob object URL 预览，失败回退到附件按钮。
- **非图片附件下载**：`onDownload` — blob → 临时 `<a download>` 触发浏览器下载。

### Android（candidate）
- **头像上传/替换/删除**：`CandidateProfileViewModel` `uploadAvatar`/`deleteAvatar` + 有界选择器（见 §1.1）。
- **Resume Hub 创建/编辑/经验增删**：`RealProfileScreens.kt` `ResumeHubCard` / `ResumeForm` — `+ Add experience`（`experiences.add`）、`onRemove`（`removeAt`）、`onUpdate`（`experiences[index] = updated`）；`CandidateResumeViewModel` `edit`/`cancelEdit`/`save` 完整。
- **图片消息预览**：`MessagesScreens.kt` `ImagePreviewDialog` — `BitmapFactory.decodeByteArray` → `asImageBitmap`。
- **非图片外部打开**：`openDownloadedAttachment` — 写缓存 + `Intent.ACTION_VIEW`（`FileProvider` + `FLAG_GRANT_READ_URI_PERMISSION`）。
- **Profile 与 Resume 保存仍是两次独立 API 调用**：`CandidateProfileViewModel`（`candidateProfileRepository`）与 `CandidateResumeViewModel`（`candidateResumeRepository`）独立 factory、独立 repository、独立 save，未合并。

---

## 4. API / DB 变更

**无。** 未新增/修改任何 Flyway 迁移、OpenAPI 定义、后端业务契约、数据库结构。

---

## 5. 未验证项（未声称通过）

1. **未做 Android 模拟器手动点击验收**：本环境无可用 AVD/模拟器实例，无法执行头像选择、Resume 编辑、图片预览等交互手势；上述行为均通过**代码级回归确认 + 单元/集成测试**佐证，不标记为「人工点击通过」。
2. **未做 Web 浏览器手动验收**：需要运行中的后端 + 浏览器自动化，本环境未提供；头像上传、Google Calendar 卡片、附件下载等交互未人工点击。
3. **消息附件读取仍为无界 `readBytes()`**：`MessagesScreens.kt:542` `readPendingAttachment` 仍用 `readBytes()`。此为**头像选择器之外**的既有行为，不在本包要求范围（本包仅要求修复「头像选择器」有界读取），按「不重构无关模块」约束未改动；如后续需要可单独立项。

---

## 6. 禁止范围确认

- ✅ 未触碰 ML / Agent / Admin / Google OAuth 配置 / 数据库 / Flyway / OpenAPI / 后端业务契约。
- ✅ 未引入新功能、新依赖；未重构无关模块。
- ✅ 未清理或覆盖他人工作区改动（仅删除本包明确目标 `ResumeEditScreen.kt`）。
- ✅ 未 commit / push / pull / merge / reset。
