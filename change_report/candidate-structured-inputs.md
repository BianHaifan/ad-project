# Candidate 结构化输入改造包

## 目标

统一 Candidate Android 端 Profile / Resume / Job preferences 三个编辑页的输入体验：把可枚举数据从平铺标签或自由文本改为选择器（单选、多选、数值滚轮），保留真正需要自由表达的文本输入。纯 Android UI 与本地 UI 状态改动，不动后端、OpenAPI、数据库迁移、ML、Agent、认证、消息或 Web。

## 完成内容

### 1. 复用式选择器组件（新增 `StructuredSelectors.kt`）

在 `feature/profile` 内抽取了以下组件，全部使用现有 Ad 设计系统颜色/间距，无新第三方依赖：

| 组件 | 作用 | 被谁使用 |
|---|---|---|
| `SelectorField` | 只读、可点击的表单行（label + 已选摘要/占位符 + 箭头 + 错误态） | 三页所有结构化字段入口 |
| `SingleSelectSheet<T>` | 单选面板（含“Not specified”清除项），本地副本、确认才提交 | Gender |
| `LocationSelectSheet` | 地点单选面板（目录 + 面板内自定义添加；已有目录外地点会保留为选中项） | Profile Location |
| `SearchableMultiSelectSheet` | 可搜索多选面板（目录 + 面板内自定义添加；已有自定义项可搜索/删除） | Skills、Desired titles、Preferred locations |
| `EnumMultiSelectSheet<T>` | 枚举多选勾选列表（无搜索/自定义，列表封闭且小） | Workplace / Employment type |
| `NumberWheel` | 带吸附的纵向数字滚轮（`rememberSnapFlingBehavior` 吸附到顶部高亮项） | Age、Minimum salary |
| `NumberWheelSheet` | 数值滚轮面板（顶部“Not specified”清除行 + 滚轮 + 确认/取消） | Age、Minimum salary |

通用约束全部满足：每个面板都有 **Cancel / Confirm**、**已选数量或摘要**、**错误态**；所有面板在本地副本上工作，取消/点外部/返回键均丢弃副本，不污染外层表单状态；确认后才写回。

### 2. Profile 编辑页（`RealProfileScreens.kt`）

- Gender：从平铺 `FilterChip` 改为 `SingleSelectSheet` 单选（含 “Not specified”）。
- Age：从 `OutlinedTextField` 改为 `NumberWheelSheet`（范围 16–80，`AGE_OPTIONS`），可“Not specified”。
- Location：从文本框改为 `LocationSelectSheet`（`COMMON_LOCATIONS` 目录 + 面板内自定义地点）。
- Full name / Headline / Phone / Birthplace 保持自由文本；头像（选图/上传/删除/5MB 限制）逻辑未改动。

### 3. Resume 编辑页（`RealProfileScreens.kt`）

- Skills：从主页面平铺全部标签改为单个 “Select skills” 入口，点击打开 `SearchableMultiSelectSheet`（搜索 + 多选 + 面板内 “Add a skill”）；目录外自定义技能打开后保留且可删除。
- Summary / Title / Company / Description 保持自由文本；经历月份选择器与 “Present” 逻辑保留。

### 4. Job preferences 页（`JobPreferencesScreen.kt`）

- Desired titles / Preferred locations：改为 `SearchableMultiSelectSheet`，保留面板内自定义项。
- Workplace type / Employment type：从平铺 Chip 改为 `EnumMultiSelectSheet` 多选列表。
- Minimum monthly salary：从薪资档位 Chip 改为 `NumberWheelSheet`（“Not specified” + `SALARY_OPTIONS` 滚轮），仍按 SGD/月保存。
- 保存请求、版本冲突处理（`expectedVersion`）、推荐刷新提示均未改动。

### 5. ViewModel 与测试

- `CandidateProfileViewModel.save` 的 `age` 参数由 `ageText: String` 改为 `age: Int?`（`UpdateProfileRequest.age` 本就是 `Int?`，请求格式不变）。滚轮只产生 16–80 或 null，仍保留 16–100 的校验兜底。
- Resume / Job preferences 的 ViewModel `save` 签名不变（skills 仍是 `List<String>`，薪资仍是 `Long?`）。
- `ProfileResumeViewModelTest.kt`：把 7 处 `save(..., ageText)` 调用改为 `Int?` 参数；现有 3 条 job-preference 用例与其余用例语义不变。

## 改动文件

- 新增 `android/app/src/main/java/com/adproject/candidate/feature/profile/StructuredSelectors.kt`
- 修改 `android/app/src/main/java/com/adproject/candidate/feature/profile/RealProfileScreens.kt`
- 修改 `android/app/src/main/java/com/adproject/candidate/feature/profile/ProfileViewModels.kt`
- 修改 `android/app/src/main/java/com/adproject/candidate/feature/profile/JobPreferencesScreen.kt`
- 修改 `android/app/src/test/java/com/adproject/candidate/ProfileResumeViewModelTest.kt`

`JobPreferenceCatalog.kt` / `SkillCatalog.kt`（目录）与 `AdCandidateApp.kt`（`onSave = ...::save` 方法引用）无需改动，签名自动适配。

## API / 数据库

- **无任何 API、OpenAPI、数据库迁移变更**。`UpdateProfileRequest`、`SaveResumeRequest`、`SaveJobPreferenceRequest` 等 data class 与后端契约均未变；本包只改 UI 与本地状态。

## 测试结果

命令（JDK 21）：

```
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

结果：**BUILD SUCCESSFUL**（1m 18s）。

`ProfileResumeViewModelTest` 25 个用例全部通过，全量单元测试（14 个 test suite，共约 119 个用例）`failures=0 errors=0`。`assembleDebug` 产出 APK 成功。

唯一告警为 `AdCandidateApp.kt:97` 的既有 `LocalLifecycleOwner` 弃用提示（非本包改动引入）。

## 已知限制

- 数值滚轮采用「吸附到顶部高亮项」的纵向列表实现（`LazyColumn` + `rememberSnapFlingBehavior`），选中项为视口顶部的吸附项；这是稳定的基础实现，视觉上与 iOS 式“中间高亮”略有差异，但吸附/选中语义明确。
- 选择面板统一使用 Material3 `AlertDialog`（而非 `ModalBottomSheet`），便于搜索框与键盘共存、避免底部面板的键盘遮挡不确定性。
- 本包未做模拟器截图/视觉回归（遵循此前“尽量少做手工验证”的约定），仅完成编译与单元测试验证。

## 建议手动验证步骤

1. 安装 debug APK，打开 Me → Edit profile：Gender 单选、Age 滚轮（含 Not specified）、Location 面板（选目录项 + 添加自定义地点）；保存后重开确认回显（含自定义地点）。
2. Me → Resume：点 “Select skills” 搜索并多选、添加自定义技能、取消一次确认不污染、再确认；重开确认目录外技能保留且可删。
3. Me → Job preferences：titles/locations 搜索多选 + 自定义、workplace/employment 多选、salary 滚轮选档位与 Not specified；保存后重开确认回显，并观察推荐刷新提示。
