# 包 C 变更报告：双端 Community 第二版

## 完成内容与模块

- 后端 Community Feed 支持正文搜索、五类分类筛选和稳定分页；发帖支持分类及最多 4 张 PNG/JPEG/WebP，校验数量、5 MB 单图上限、MIME 与文件签名。
- 图片以独立二进制记录保存，响应只返回附件元数据和下载 URL，不在帖子正文或 JSON 中嵌入 Base64。
- 新增与 Application Conversation 隔离的 Community Direct Message，会话按参与双方复用，读取/发送仅双方可见。
- Recruiter Web 与 Candidate Android 均新增搜索/筛选、独立发帖页、图片选择/预览、详情图像、Message author 和社区私信页面。

## API 与数据库

- 扩展 `GET/POST /api/v1/community/posts`，新增图片下载、启动/读取社区会话、读取/发送社区消息端点；契约记录在 `docs/openapi-v1.yaml`。
- `V24__extend_community_posts_with_categories_and_images.sql` 增加分类和图片表。
- `V25__create_community_direct_conversations.sql` 增加隔离的社区会话与消息表，不修改现有 application conversations。

## 测试

- 后端 V2 集成测试覆盖跨角色发图、关键词/分类查找、公开图片读取、会话发送与第三方所有权拒绝；既有 Community 测试继续覆盖认证、角色、404、文本边界、点赞和评论。
- `mvn -q test` 全量 314 项通过，包含 MySQL 8.4 上 V24/V25 迁移验证；Web 全量 232 项测试、lint/构建及 Android 138 项单测、lint/assemble 通过。

## 限制与下一步

- 按范围未实现审核、举报、反垃圾、编辑/删除、推荐流和通知。
- 图片当前由业务数据库保存，适合演示但不适合大规模生产；下一步最小工作是定义兼容现有 URL 契约的对象存储 adapter。
- 未完成“Android 发图 → Web 搜索可见 → 双端私信”的真机/浏览器跨端人工验收；应在 staging 作为下一项验证。
