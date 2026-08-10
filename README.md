# AD Project

面向求职者、招聘者和管理员的智能招聘平台毕业设计。

## 技术基线

- 求职者客户端：Kotlin + Jetpack Compose（Android）
- 招聘者与管理员端：React + TypeScript
- 核心后端：Java + Spring Boot
- 数据库：MySQL
- 机器学习：团队自行训练职位推荐模型，由独立 Python 服务负责训练与推理
- AI Agent：通过 Spring Boot 的受控工具执行用户本人有权完成的操作

## 当前目标

先完成一条可真实运行的 MVP 垂直流程：

```text
招聘者发布职位
→ Android 展示职位
→ 求职者查看职位详情并投递
→ 招聘者查看申请并修改状态
→ 求职者查看申请进度
```

ML 和 AI Agent 是毕业设计亮点，但不得成为登录、浏览、投递等核心业务的前置依赖。

## 文档入口

- [产品需求](docs/product-requirements.md)
- [用户流程](docs/user-flows.md)
- [系统架构](docs/architecture.md)
- [数据库设计](docs/database-design.md)
- [API 设计](docs/api-design.md)
- [权限规范](docs/permissions.md)
- [测试计划](docs/testing-plan.md)
- [开发计划](docs/development-plan.md)
- [Figma MVP 设计审查](docs/figma-mvp-audit.md)
- [论文提纲](docs/graduation-thesis-outline.md)

设计参考： [AD project Copy](https://www.figma.com/design/ellcZx2GjomKwCQNxuryri/AD_project--Copy-?node-id=0-1)

## 后端启动与测试

后端要求 Java 21、Maven、MySQL 8。复制 `.env.example` 中的变量到自己的本地环境配置（不要提交真实值），然后执行：

```bash
cd backend
mvn spring-boot:run
```

服务默认监听 `http://localhost:8080`，API 前缀为 `/api/v1`。启动时 Flyway 自动迁移数据库，Hibernate 只校验结构，不创建表。

运行全部测试和打包：

```bash
cd backend
mvn test
mvn package
```

测试套件始终使用隔离的 H2 MySQL 兼容模式执行 Auth HTTP 集成测试；Docker 可用时还会通过 Testcontainers MySQL 8.4 验证空库迁移。
