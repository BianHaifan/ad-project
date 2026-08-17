# 推荐职位流最终人工验收准备 — 环境与服务状态

**Date:** 2026-08-16
**性质:** 纯环境/服务状态记录。未修改任何源码、接口、配置、数据库、Flyway、ML、Agent、OAuth/Google Meet、Admin 或 Web；未执行 commit/push/pull/merge/reset；未删除 Docker 数据卷；未重置数据库；未打印任何密钥。

---

## 1. 服务状态

| 组件 | 状态 | 说明 |
|---|---|---|
| MySQL | ✅ 运行中（healthy） | 容器 `adproject-local-mysql`，端口 `13306->3306`，Up 3h+ |
| Spring Boot 后端 | ✅ 运行中 | 容器 `adproject-local-backend`，`mvn spring-boot:run`（bind-mount 当前源码），端口 `8081->8080` |
| Android 模拟器 | ✅ 运行中 | `emulator-5554`（AVD `Pixel_10_Pro`） |
| Android debug APK | ✅ 已安装最新版 | `app-debug.apk`（17:09 构建，含 Package 2 单流 + 2A LazyRow），`adb install -r` 保留会话 |
| ML 服务（Python） | ⛔ 未运行 | 推荐走确定性 fallback（`Rules fallback` / `modelStatus=DEGRADED`），符合预期，本包不涉及 ML |

**后端代码刷新说明（重要）：** 后端 dev 容器最初于 **13:46** 启动，而推荐模块源码的 Package 2 变更在 **16:18–17:06** 才落盘（`CandidateRecommendationController.java` 16:18、`RecommendationDtos.java` 16:18、`CandidateRecommendationService.java` 17:06、`MainScreens.kt` 17:06）。即原进程运行的是 **Package 2 之前**的旧代码，直接验收分页/筛选会失败。因此我执行了 `docker restart adproject-local-backend`（进程重启，复用现有容器配置；**未改动任何配置/DB/数据卷**），使其 `mvn spring-boot:run` 从当前 bind-mount 源码重新编译（174 个源文件），17:22 干净启动，MySQL 连接正常，13 个 Flyway 迁移校验通过，无报错。

---

## 2. 实际可验证项（我已执行的验证）

1. **后端推荐接口在线**：未登录 `GET /api/v1/candidate/recommendations/jobs` 返回 **HTTP 401**，响应体 `{"error":{"code":"UNAUTHORIZED",...}}` —— 即服务在线。（未记录任何 Token。）
2. **Android API 地址正确**：`build.gradle.kts` 中 debug 构建 `API_BASE_URL = http://10.0.2.2:8081/api/v1/`，与后端宿主机端口 8081 一致（`10.0.2.2` 为模拟器访问宿主机回环的别名）。
3. **模拟器会话保留**：`adb install -r` 覆盖安装后 `am force-stop` + 重启，App 重新落到 **Jobs 页**（非登录页），说明候选者会话（JWT）仍有效。
4. **新 App + 新后端端到端兼容**：重启后端后强制重启 App，Jobs 页成功渲染推荐职位卡片（Backend/Frontend/Mobile/DevOps Engineer 等，含 AI Match 分数），无反序列化/解析错误 —— 证明新 `RecommendationMeta`（page/pageSize/total/hasNext）与新后端响应结构匹配。
5. **UI 结构就位**：截图确认 Jobs 页包含「Recommended for you」标题、`All / Full time / Internship / Part time` 类型筛选行、搜索框 + Search 按钮、Refresh 按钮、职位卡片列表（含 `Rules fallback • model temporarily unavailable` 降级提示）。

---

## 3. 不可验证项与原因（未点击，不能声称已通过）

以下均为**交互式人工验收项**，需用户本人在模拟器上点击/滑动确认，我未代为执行，故不标记为通过：

1. **类型筛选可横向滑动且不溢出**：LazyRow 改为横向滚动布局，静态全宽截图无法证明“窄屏不溢出/可滑动”，需人工在窄屏或滑动验证。
2. **点击类型后列表刷新**：未点击任何类型选项。
3. **搜索按钮手动提交**：未触发搜索动作。
4. **向下滚动自动加载下一页**：未执行滚动到底手势；且**无法确认库中 ACTIVE+PUBLIC 职位是否 >10 条**（截图仅见 4+ 张卡片，总条数未核实）。若实际 ≤10 条，懒加载无从触发，属“无足够真实数据验证懒加载”，不造假数据。

---

## 4. 明确未触碰边界

- 未修改任何源码 / 接口 / 配置 / 数据库 / Flyway / ML / Agent / OAuth / Google Meet / Admin / Web。
- 未 commit / push / pull / merge / reset。
- 未删除 Docker 数据卷（`docker restart` 不影响 `mysql-data` 卷），未重置数据库。
- 未打印 DB_PASSWORD / JWT_SECRET / OAuth 密钥等任何秘密。
- ML Python 服务未启动、未改动（本包范围外）。
