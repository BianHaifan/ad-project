# 修改报告：关键业务流程中文注释

## 完成内容

为后端核心业务入口补充中文注释，说明业务意图、权限边界、幂等性、事务一致性和状态机约束。注释覆盖：

- 认证：公开注册角色限制、招聘者注册后的待审核公司、抗邮箱枚举登录、刷新令牌轮换、候选人资料引导条件；
- 职位：公司审核前置条件、草稿编辑乐观锁、发布截止日期、职位状态审计；
- 投递与申请：简历快照、首条状态历史、会话创建、公司数据边界、申请状态迁移、匹配分快照失效规则；
- 站内消息：候选人/招聘者的资源归属、发送幂等、Google 集成与站内消息的解耦、面试通知去重。

## 修改模块

- `backend/src/main/java/com/adproject/auth/application/AuthService.java`
- `backend/src/main/java/com/adproject/job/application/JobService.java`
- `backend/src/main/java/com/adproject/application/application/CandidateApplicationService.java`
- `backend/src/main/java/com/adproject/application/application/RecruiterApplicationService.java`
- `backend/src/main/java/com/adproject/conversation/application/ConversationService.java`

## API / 数据库变化

- API：无变化。
- 数据库与 Flyway：无变化。
- 业务逻辑、权限判断、状态机、ML/Agent：无变化。

## 验证

- `git diff --check`：通过。
- 后端 Maven 测试：未运行；当前终端未安装或未配置 `mvn` 命令。此次为仅注释改动，未改变可执行代码。

## 限制与下一步

- 本包不触碰由其他成员负责的 ML、Agent、Admin 或 Google OAuth 模块。
- 若团队希望继续提高可读性，应按功能包逐步补充 Controller/API DTO 的中文业务注释，避免一次性改动大量文件造成冲突。
