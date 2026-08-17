# 求职者 Me 重构与消息图片显示计划

## 范围

将 Android 求职者端的 Me 改为五个明确入口：Profile、My applications、Resume、Job preferences、Sign out；同时让聊天记录中的 PNG/JPEG 显示缩略图并可点开查看。

## 关键决定

- Profile 页新增性别、手机号、出生地的真实字段；这需要 Profile 模块的受控 Flyway 迁移、OpenAPI 契约与 Spring Boot 校验，不能用 Android 本地 mock 代替。
- Me 的申请摘要使用既有三组统计：进行中（APPLIED + IN_REVIEW）、面试（INTERVIEW）、已归档（REJECTED + WITHDRAWN）。拒绝状态存在，详情页仍显示 Rejected。
- Resume 改为独立编辑页；Me 只展示入口和简历摘要/完成状态。
- 消息图片在气泡中加载受鉴权缩略图，点击查看大图；非图片继续走既有外部打开流程。

## 实施顺序

1. 审计迁移编号与 Profile 契约，新增可选的 gender、phone、birthplace 字段，并测试认证、所有权、校验和版本冲突。
2. 更新 Android Profile Repository / ViewModel，并新增独立 Profile 编辑路由与 Resume 编辑路由。
3. 重构 Me 为五个入口；应用摘要复用既有 `/candidate/applications` 的三组 `counts`，不新增申请 API。
4. 将 Android 图片消息从“附件点击后尝试对话框预览”改为“气泡内缩略图 + 点击大图”，复用既有认证下载接口并避免轮询重复下载。
5. 运行后端、Android 测试和 debug 构建，并在模拟器完成手动验收。

## 风险

- Profile 新字段会触及共享后端 Profile 契约，因此先审计 Flyway 最新编号；若发现同事未提交迁移冲突，停止而不猜测编号。
- 手机号和性别属于敏感个人信息：仅当前求职者本人可读写，不放入公开招聘者资料、职位详情或消息 DTO。
- 图片缩略图必须维持会话鉴权，不能拼接匿名附件 URL。
