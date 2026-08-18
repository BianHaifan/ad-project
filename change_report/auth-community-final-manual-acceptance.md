# 认证、Community、Android 与 HireX 品牌最终验收

日期：2026-08-18

## 结论

计划中的 8 项工作均已有实现与验证证据。本轮最终手工验收额外发现并修复了 Android Community 图片无法显示、发帖页缺少图片预览/删除两项 UI 缺口。

## 手工验收结果

- Recruiter Web：使用合成账号完成注册并进入工作台；页面标题与外壳显示 `HireX Recruiter`。
- Administrator Web：登录页显示 `Platform sign in`，存在 `Remember me`，品牌文案为 HireX/Platform Operations。
- Candidate Android：安装当前 debug APK，使用合成账号完成注册；注册成功后强制进入 `Build your HireX profile`，提交 Headline、Location、Age、Resume Summary、Skills、Target Role、Preferred Location、Work Style、Job Type 后进入职位页。
- Android Jobs：确认新搜索、筛选、刷新、收藏/底部导航图标与 empty 状态；长标题/薪酬排版由回归测试覆盖。
- Community Feed：Android 实机确认搜索、分类、刷新、FAB、空态/内容态和新的点赞图标。
- Community 图片：当前后端 multipart 发帖返回 HTTP 201，详情返回 1 个安全图片 URL；Android 详情最终实际渲染该 PNG。证据：`work/auth-community-android-image-final2.png`。
- Community 发帖图片草稿：Android 系统选择器选入合成 PNG 后显示 `Choose images (1/4)`、真实预览和 `Remove`；点击 Remove 后恢复 `Choose images (0/4)`。证据：`work/auth-community-android-create-preview.png`。
- Community 即时状态：Android 点赞详情后返回 Feed，合成帖计数立即从 0 更新为 1。
- 跨客户端：Recruiter Web 注册和 Community 独立发帖页已验证；应用内浏览器的文件上传桥在 `Publishing…` 阶段未把请求送到后端，因此图片发布改用同一 multipart 契约直接验证，再由 Android 读取与展示。该现象未在后端、Android 或普通 HTTP 客户端复现。
- 密码重置：本机未配置 SMTP，按设计安全返回邮件未配置错误；完整验证码、过期/次数限制、会话失效由 fake sender 集成测试覆盖。真实邮件投递留待 staging SMTP 账号验证。

## 本轮补充修复

- `android/app/build.gradle.kts`：加入 Coil 3 的 `coil-network-okhttp`，使 HTTP Community 图片和远程头像具备真实网络加载能力。
- `CommunityDetailScreen.kt`：为帖子图片提供 16:9 尺寸、圆角和裁切策略，避免无约束图片布局为 0 高。
- `CommunityScreen.kt`：在独立发帖页加入所选图片的方形预览与逐项删除。

## 自动化验证

- 后端完整测试：314 tests passed（含 MySQL 8.4 + Flyway V1–V25）。
- Web：232 tests passed；lint passed；production build passed。
- Android 既有完整单测：138 tests passed；lint 与 assemble passed。
- 本轮图片 UI 修复后：`CommunityTask5Test` passed；`lintDebug assembleDebug` passed（BUILD SUCCESSFUL）。
- 本轮曾启动一次 Android 全量单测，但测试执行器长时间无输出后手工中止；随后相关 Community 测试、最终 lint 与构建均独立通过，未把中止执行计为通过。

## API、数据库与配置

- API 已按 OpenAPI 增加密码重置、Candidate onboarding、Community 搜索/分类/图片和 Community 私信契约。
- 数据库通过 Flyway V23–V25 增加密码重置、Community 图片/分类与独立私信模型。
- 邮件部署参数与图标映射见 `change_report/auth-community-implementation-decisions.md`；仓库未加入任何 SMTP 密钥。

## 限制与最小下一步

- 真实 SMTP 投递尚未在 staging 验证；最小下一步是配置专用测试邮箱后执行 request → 收件 → confirm → 旧会话失效的端到端测试。
- 应用内浏览器文件上传桥不适合作为 Web multipart 的最终人工证据；应在普通 Chrome/CI Playwright 环境补一条真实文件选择与发布用例。
- 本轮没有修改或清理工作区中其他已有改动，也没有提交 Git commit。
