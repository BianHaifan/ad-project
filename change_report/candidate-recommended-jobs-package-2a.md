# Package 2A — 推荐职位流分页与窄屏复核修复

**Date:** 2026-08-16
**Scope:** 修复两个已确认问题，不扩展产品范围：(1) Android 职位类型筛选在窄屏溢出；(2) 后端分页 offset 的 `int` 整数溢出。

---

## 1. 问题与修复

### 1.1 Android 职位类型筛选溢出

**问题：** `JobFeedScreen` 中 `All / Full time / Internship / Part time` 使用普通 `Row`（`Arrangement.spacedBy(14.dp)`），在较窄屏幕上四个文本项无法换行/滚动，可能截断或溢出。

**修复：** 将普通 `Row` 改为可水平滚动的局部 `LazyRow`（`androidx.compose.foundation.lazy.LazyRow`），每个选项用 `item`/`items` 作为独立可点击项。保留：
- `All` 默认（`employmentType == null` 选中）；
- 现有筛选行为（`onEmploymentType(null)` / `onEmploymentType(type)`）；
- 选中样式（选中 22.sp 加粗 + `AdText`，未选中 17.sp + `0xFF6E7781`）；
- 无障碍点击语义（沿用 `Modifier.clickable`，未改动）。

**未改动** 刚完成的 Job 技能标签修复（`JobSkillChip` / `TagChip` 均未触碰）。

### 1.2 后端分页整数溢出

**问题：** `offset = (page - 1) * pageSize` 使用 `int`。当 `page` 为超大合法值（如 `2147483647`）且 `pageSize=20` 时，`(page-1)*pageSize` 溢出为负数，导致后续 `ranked.get(负数)` 抛出 `IndexOutOfBoundsException` → 500。

**修复：** 改用 `long` 计算 offset，并在 offset 超出推荐上限/总数时稳定返回空页：

```java
int total = Math.min(filtered.size(), MAX_RANKED);
long offset = (long) (page - 1) * pageSize;
if (offset >= total) {
    return new RecommendedJobResponse(List.of(), new RecommendationMeta(
            "NONE", "none", "none", "NOT_APPLICABLE", 0, clock.instant(),
            page, pageSize, total, false));
}
int start = (int) offset;   // offset < total <= MAX_RANKED(100)，可安全窄化
```

后续 `requestLimit`、`toIndex`、循环下标、`hasNext` 均改用 `start`（保证在 `[0, 100]` 范围内，无溢出、无负索引）。超大 `page` 现在返回 **200 + 空 `data` + 正确 `page/pageSize/total` + `hasNext=false`**，不抛异常、不 500。

---

## 2. 修改文件清单

| 文件 | 变更 |
|---|---|
| `android/app/src/main/java/com/adproject/candidate/feature/jobs/MainScreens.kt` | 新增 `LazyRow` import；类型筛选 `Row` → `LazyRow`（保留 All 默认/样式/点击语义） |
| `backend/src/main/java/com/adproject/recommendation/application/CandidateRecommendationService.java` | `int offset` → `long offset` + 空页守卫 + `int start` 窄化 |
| `backend/src/test/java/com/adproject/recommendation/CandidateRecommendationIntegrationTest.java` | 新增 `hugePageDoesNotOverflowAndReturnsEmptyPage` 集成测试 |

---

## 3. 测试命令与真实结果

### Backend（JDK 21）

```
$env:JAVA_HOME = "C:\Users\14188\.jdks\ms-21.0.8"
mvn -q -Dtest='CandidateRecommendationIntegrationTest,MlRecommendationClientTest' test
```

**结果：9 tests run, 0 failures, 0 errors, 0 skipped。**
- `CandidateRecommendationIntegrationTest`：**8 tests**（含新增 huge-page 用例）全通过。
- `MlRecommendationClientTest`：**1 test** 通过。

新增用例断言（认证 Candidate + 有简历）：`GET /api/v1/candidate/recommendations/jobs?q=<token>&page=2147483647&pageSize=20` → 200、`meta.page=2147483647`、`meta.pageSize=20`、`meta.total=1`、`meta.hasNext=false`、`data` 为空。

### Android

```
$env:JAVA_HOME = "C:\Users\14188\.jdks\ms-21.0.8"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

**结果：BUILD SUCCESSFUL（1m 30s）。**
- `testDebugUnitTest`：**89 tests, 0 failures, 0 errors, 0 skipped**。
- `lintDebug`：通过。
- `assembleDebug`：通过。

---

## 4. 模拟器验证

**未进行模拟器验证。** 本次未启动模拟器，无已登录会话可复用；窄屏类型筛选不截断/溢出仅通过 **编译 + lint + 单元测试** 验证（LazyRow 由 Compose 保证横向滚动，不再受容器宽度约束），**未在真机/模拟器上手工目测窄屏布局**。此点如实记录，不伪称为已完成。

---

## 5. 明确未改动边界

- **API 契约未变**：未修改 `docs/openapi-v1.yaml`（本包不改变接口，`q/employmentType/page/pageSize` 及 `RecommendationMeta` 字段均沿用 Package 2）。
- **`ml-service/**`、`agent/**` 未改动**。
- **数据库、Flyway、认证、Google Meet/OAuth、Admin、Web 均未改动**。
- **推荐算法、模型输入输出、普通 `/api/v1/jobs` 接口未改动**。
- **未执行** commit / push / pull / merge / reset。
- **未输出** 任何密钥。
