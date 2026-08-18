# 包 A 变更报告：密码重置与 Candidate Onboarding

## 完成内容与模块

- 后端 `auth`：一次性 6 位验证码、哈希存储、15 分钟过期、60 秒重发限制、5 次尝试上限、单次消费、SMTP port/adapter、统一不可枚举响应。
- 会话安全：JWT 加入 `authVersion`；重置成功后提升版本并撤销该用户全部 refresh token，使旧 access/refresh session 同时失效。
- 后端 `onboarding`：Candidate-only 原子提交 Profile、默认 Resume 和 Job Preferences；登录/注册响应新增可选 `onboardingRequired`。
- Recruiter Web 与 Candidate Android：密码重置两步流程、校验、倒计时、loading/error/success；Android 注册或再次登录后按服务端状态强制进入 onboarding。

## API 与数据库

- 新增 `POST /api/v1/auth/password-reset/request`、`POST /api/v1/auth/password-reset/confirm`、`PUT /api/v1/candidate/onboarding`。
- `V23__create_password_resets_and_auth_version.sql` 新增 `password_reset_codes`，并为 `users` 增加 `auth_version`。
- SMTP 凭据仅通过环境变量注入；`.env.example` 只记录变量名和非敏感示例。

## 测试

- 后端集成/单元测试覆盖：未知邮箱不可枚举、错误/过期/超限/已消费验证码、密码校验、并发单次消费、未配置邮件安全失败、旧 access/refresh 撤销，以及 onboarding 成功、持久化和角色拒绝。
- `mvn -q test` 全量 314 项通过（含 MySQL 8.4 Flyway）；Web 全量 232 项测试、lint 与生产构建通过；Android 138 项单测、lint、debug assemble 通过。

## 限制与下一步

- 本轮未使用真实 SMTP 账号发送邮件，生产部署前需在受控环境配置并验证投递、退信和限流监控。
- 未进行 Web/Android 到真实邮箱的跨端人工验收；下一步最小工作是在 staging 配置测试邮箱，分别完成一次重置并确认旧会话被拒绝。
