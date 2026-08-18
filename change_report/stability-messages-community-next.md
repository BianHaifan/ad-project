# Candidate Messages 时间与 Community 交互优化报告

日期：2026-08-18

## 完成内容

- 修复 Candidate Messages 会话列表、消息气泡和面试卡片直接显示服务端 UTC/offset、未转换设备时区的问题。
- 统一采用“ISO-8601 offset → Instant → 设备 ZoneId → UI 格式”的转换链路。
- 时间解析失败时保留服务端原值，不使整个消息页面崩溃。
- Community 发帖 Category 从多个 FilterChip 改为单选下拉，默认 General，提交期间禁用。
- Community 详情将 Message author 与 Like/Unlike 合并到同一个可换行操作组。
- 新增 todo 逐项核查及下一阶段优化细则。

## 修改模块

- Android Candidate：Messages、Community UI 与相关测试。
- 文档：`tasks/stability-jobs-messaging-admin-company-next.md`。
- Backend、Web、OpenAPI、数据库均未修改。

## 测试

- 定向：`./gradlew testDebugUnitTest --tests 'com.adproject.candidate.feature.messages.MessageTimeFormattingTest' --tests 'com.adproject.candidate.feature.community.CommunityScreensUiTest' --console=plain`，通过。
- 全量相关验证：`./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain`，通过。
- 覆盖 UTC 到 Asia/Shanghai、非零 offset 到 America/New_York 的跨日转换、非法时间回退、Category 选择，以及 320dp 操作组。

## API 与数据库

- 无变化。后端 `Instant`、UTC 存储及 ISO-8601 响应是正确事实值，本次只修复 Android 展示层。

## 限制与下一步

- 尚未在真实设备切换系统时区后截图确认。
- 原 todo 要求的三角色双端人工端到端验收仍未完成。
- 下一步最小安全工作：使用一条已知 UTC 消息，在 Android 模拟器分别切换 Asia/Shanghai 和 America/New_York，核对列表、气泡和面试卡片并保存截图。
