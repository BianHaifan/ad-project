# 会话初始化交接提示词

> 把下面整段粘贴到新会话作为起始提示词。不绑定具体任务，下一部分工作由你在新会话中指定。

---

【角色与流程】你是「Claude 实现 / Codex 复核」流程中的实现者，工作在 `c:\Users\14188\Desktop\ad-project`（Java 21 + Spring Boot 后端；Kotlin Android 候选人端；React + TypeScript Web 招聘者端）。你负责实现、测试、写文档，但**绝不 commit / push**，全部等待 Codex 复核。当前分支 `codex/recruiter-candidate-improvements`，工作区有大量未提交/未跟踪文件，不要提交、不要推送。

【全局安全边界（始终生效）】
- 不 commit、不 push。
- 不使用真实 Google 凭据 / 网络 / 密钥 / token / Meet 链接。
- 不读取真实 `.env`。
- 不记录真实邮箱、token、密钥、Meet 链接或 Google 响应体。
- 每项任务另有各自的禁止清单（Android / Messages / ML / Agent / Admin / Google OAuth / 数据库迁移等，视任务而定），必须逐条遵守。

【当前工作区状态（多任务并行，均未提交）】
1. `interview-mode-simplification`（面试排期模式简化）——已完成并修复审查意见，等待 Codex 再次复核。报告 `change_report/interview-mode-simplification.md`。除非 Codex 返回新意见，否则不要改动。
2. `recruiter-profile` Package 1——已完成（后端 `GET/PATCH /api/v1/recruiter/profile`、`V11__create_recruiter_profiles.sql`、Web `/recruiter/profile` 页），等待复核。报告 `change_report/recruiter-profile-package-1.md`。**注意：后端 Maven 测试尚未成功执行，需补跑**。下一包为 Candidate 公开 Recruiter/Company Profile。
3. `admin-mvp`——仅完成包 0（OpenAPI / API_COVERAGE 草案 + 风险清单），**三项开工前置条件未获书面确认**，不能写业务代码。报告 `change_report/admin-mvp-package-0.md`。Admin 审计迁移编号需用 `V12`（`V11` 已被 profile 占用）。
4. `community-demo`——计划与待办已存在（`tasks/community-demo-implementation-plan.md` / `-todo.md`），尚未启动；Task 0 需负责人确认「社区为 P2 演示增强、允许复用现有视觉规范、Candidate 与 Recruiter 均可发帖」。
5. Google Meet / OAuth 链路——一系列报告已完成（foundation → oauth → calendar → reschedule/cancel sync 等），详见 `change_report/google-*.md`。

【环境 / 工具事实（已验证）】
- 仓库**无 Maven Wrapper**（根目录与 `backend/` 均无 `mvnw` / `mvnw.cmd`），`mvn` 未必在 PATH。Maven 3.9.16 位于 `~/.m2/wrapper/dists/...` 缓存目录，用 `Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter 'mvn.cmd'` 查找。
- 默认 Java 为 25，后端需 JDK 21：先 `$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'`。
- 后端测试（离线）：`& $mvn -o -Dtest=<TestClass> test` 或 `& $mvn -o test`。
- Web（在 `web/` 下）：`npm run lint` / `npm run typecheck` / `npm run test` / `npm run build`。
- Docker MySQL 容器 `adproject-local-mysql`（mysql:8.4，host 端口 13306，库 `adproject`）。**绝不操作 `adproject` 库**；验证时新建随机后缀临时库并用完 `DROP`。
- Flyway：已提交最高版本 `V10`；本分支未提交 `V11__create_recruiter_profiles.sql`；新增迁移前先确认编号未被占用。

【开始动作】
1. 先运行 `git status --short` 与 `git log --oneline -10` 确认当前工作区与分支。
2. 询问用户「下一部分工作要做哪一块」，由用户指定具体任务后，再阅读对应 `tasks/*.md` 计划并开始；不要自行假设是 Admin、社区或其他任务。
3. 若任务计划含「开工前置条件」，先向用户确认是否已获书面批准；未批准只做读代码 / OpenAPI 草案 / 风险清单，不写业务代码。
