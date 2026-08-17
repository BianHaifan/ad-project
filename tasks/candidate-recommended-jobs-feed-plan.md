# 求职者推荐职位流改造计划

## 目标

修复 Android 职位卡技能标签在长文本下溢出/变形的问题；将 `Recommended / Browse` 合并为单一的“Recommended for you”职位流；支持 `All / Full time / Internship / Part time` 类型筛选、标题搜索和按页懒加载。

## 已确认的现状

- Android `JobFeedViewModel` 在推荐模式调用 `GET /candidate/recommendations/jobs?limit=20`；该接口没有类型、搜索或分页参数，因而首次只能得到有限数量。
- 通用 `GET /jobs` 已支持 `q`、`employmentType`、`page`、`pageSize`，但切换到它会丢失推荐排序和理由。
- 推荐 Java 服务会在 Spring Boot 内部组织候选职位并调用既有 ML 服务；本计划不修改 `ml-service/`、模型输入输出、训练、Agent、数据库或 Flyway。
- 当前技能标签使用普通 `Row`，长技能文本和较多标签都会挤压/越界。

## API 契约（先更新 OpenAPI，再实现）

将既有接口扩展为：

`GET /api/v1/candidate/recommendations/jobs?q=&employmentType=&page=1&pageSize=10`

- `q`：可选标题关键词，服务端过滤后再推荐。
- `employmentType`：可选 `FULL_TIME`、`INTERNSHIP`、`PART_TIME`；为空即 All。
- `page`：从 1 开始，最小 1，默认 1。
- `pageSize`：1–20，默认 10。
- 响应继续保留现有推荐来源、模型版本和理由；`meta` 增加/复用分页字段 `page`、`pageSize`、`total`、`hasNext`。

服务端必须先基于 ACTIVE + PUBLIC 职位及上述筛选构造候选集，再使用已有推荐调用按排名取到 `offset + pageSize + 1` 条并切片返回。这保证同一筛选条件的连续分页仍按推荐排序，不把推荐页伪装成普通职位列表。

## 明确边界与风险

- 允许：`backend/.../recommendation` 的 Controller/DTO/Service/OpenAPI/测试，及 Android 的 API、Repository、ViewModel、Jobs UI、测试。
- 禁止：`ml-service/**`、模型训练与推理 HTTP 契约、`agent/**`、认证、数据库实体/迁移、Google Meet/OAuth、Admin、Web。
- 风险：推荐服务由其他同学负责。该改动不改变 Python ML 合约，但会改变其候选集大小；实施前应把本文件中的 API 和边界通知 ML 负责人，确认其服务可按现有 `limit` 返回所需数量。

## 分包实施

### Package 1：标签与 Android 列表状态

**范围**：仅 `android/`。

- 职位卡改用局部可换行的技能 chip 容器；每个 chip 单行省略、有限最大宽度，长文本不再撑破卡片。不要修改全局 `TagChip` 的既有调用行为。
- 删除 Recommended/Browse 切换；页面固定为推荐流。
- 保留标题搜索，但语义变为“在推荐候选集中搜索”；筛选为 `All / Full time / Internship / Part time`。
- UI state 增加当前页、`hasNext`、`loadingMore`、`loadMoreError`，并实现筛选/搜索/刷新时重置列表；请求返回后追加去重，不覆盖已有页。
- `LazyColumn` 滚到末尾前约 3 项时触发一次 `loadMore()`；加载中显示底部指示器；尾页显示 `You're all caught up`；加载失败只显示底部 Retry，不丢失已经加载的职位。
- 这一包先让 Repository API 接受分页参数；在后端契约未完成前，不把普通 `/jobs` 冒充为推荐结果。

**验收**：长技能、多个技能、四种类型筛选和搜索在窄屏无溢出；筛选和刷新不混页；重复滚动不会重复请求。

### Package 2：推荐 API 分页与筛选

**范围**：`backend/.../recommendation`、`docs/openapi-v1.yaml`、Android 网络 DTO/Repository；不进入 `ml-service/`。

- 先变更 OpenAPI 和 API 覆盖文档，再实现 Controller 参数校验、DTO 分页 meta 和服务端候选集过滤/切片。
- 保持既有 Candidate 鉴权、`RESUME_REQUIRED`、MODEL/FALLBACK 降级、ACTIVE/PUBLIC 可见性及安全错误格式。
- 更新 Android Retrofit 查询参数与 Repository 映射；首屏请求 `page=1&pageSize=10`，后续页按当前相同筛选加载。

**验收测试**：成功分页、类型筛选、搜索筛选、尾页 `hasNext=false`、无简历 422、未登录 401、错误角色 403、ML 失败 fallback；Android Repository URL/分页 meta 和 ViewModel 追加/重试/筛选重置测试。

### Package 3：联调与视觉验收

- 运行后端推荐相关集成测试、Android `lintDebug`、`testDebugUnitTest`、`assembleDebug`。
- 本地真实后端 + 模拟器验证：首屏约 10 条；三种类型能正确缩小结果；向下滚动加载下一页；尾页不重复请求；网络失败后 Retry 只补下一页；窄屏长标签不变形。
- 每包写入 `change_report/`，报告不记录密钥、不声称未手测为通过。

## 不做的事项

- 不新增新的职位类型、排序算法、收藏/社区功能或无限预取。
- 不把全部职位一次性拉到 Android 再在本地分页；分页和筛选以服务端推荐结果为准。
