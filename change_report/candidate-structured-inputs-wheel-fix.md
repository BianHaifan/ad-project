# NumberWheel 空值回传修复

## 问题

`NumberWheelSheet`（用于 Age 与 Minimum salary）在 `initialValue == null` 时，打开面板并直接点 Confirm，会把列表第一项（年龄 16 / 薪资 S$3,000）自动当成选择结果保存，破坏「Not specified」语义。

根因在 `StructuredSelectors.kt` 的 `NumberWheel`：

```kotlin
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .collect { index -> if (index in values.indices) onValueChange(values[index]) }
}
```

`snapshotFlow` 在首次组合时立即发射当前 `firstVisibleItemIndex`（= 0），于是 `onValueChange(values[0])` 无条件被调用；而 `NumberWheelSheet` 里的 `onValueChange = { selected = it }` 就把 null 覆盖成了第一项。同时 `initialIndex` 在 `initialValue == null` 时默认回落到 0。

## 修复点（仅 `StructuredSelectors.kt`）

1. **初始索引改用 `-1` 哨兵**：`initialIndex` 在 `initialValue == null` 时为 `-1`（表示「未选择」），`selectedIndex` 初始化为 `-1`，不再默认落在第一项。

2. **只在真实滚动后才回传数值**：把原先观察 `firstVisibleItemIndex` 的 `snapshotFlow` 改为观察 `listState.isScrollInProgress`，并用局部 `hasScrolled` 标志门控——只有用户真正滚动（`isScrollInProgress` 由 `false→true→false`）并吸附稳定后，才读取 `firstVisibleItemIndex` 并 `onValueChange`。首次组合的初始 `false` 被 `hasScrolled == false` 拦截，不再产生自动选择。

3. **补充「点击数字」直接选择**：给每个滚轮项加上 `clickable`，点击某数字立即 `selectedIndex = index`、`onValueChange(value)` 并 `scrollToItem(index, 0)` 吸附到顶部。这样「滚动」或「点击数字」都能产生选择，与需求一致。

4. **“Not specified” 重置不再被覆盖**：新增 `LaunchedEffect(initialValue)`，当外层 sheet 把值清为 `null`（点击 “Not specified”）时，重置 `selectedIndex = -1` 并滚回顶部。由于 `scrollToItem` 是瞬时跳转、不会触发 `isScrollInProgress`，该重置不会再次回传数值，确认后保存 null 不再被滚轮覆盖。

5. **non-null 初始值定位不变**：`initialValue != null` 时仍通过 `LaunchedEffect(values) { scrollToItem(initialIndex, 0) }` 定位并高亮已有值，行为与修复前一致。

## 语义保持

- `NumberWheelSheet` 的 Confirm / Cancel / 点外部 / 返回键丢弃语义不变；`NumberWheel` 的签名（`values / initialValue / labelOf / onValueChange / modifier`）不变。
- 未改动 API / ViewModel / 后端 / 数据库 / ML / Agent / 其他 UI 页面；仅 `StructuredSelectors.kt` 内 `NumberWheel` 的滚轮状态管理与渲染。

## 未运行测试 / 构建

按要求**未运行** `testDebugUnitTest` / `assembleDebug`。本次改动为纯 Compose 本地 UI 状态逻辑，未触碰任何 ViewModel / Repository / 契约类型；建议后续需要时再跑一次完整单测与构建确认。

## 编译修复：点击回调中的挂起函数

`NumberWheel` 内项目点击的 `clickable` 回调里直接调用了 `listState.scrollToItem(index, 0)`，而 `LazyListState.scrollToItem` 是 `suspend` 函数，普通 `clickable` lambda 无法直接调用，导致无法编译。

修复（仅 `StructuredSelectors.kt`）：

1. 在 `NumberWheel` 中新增 `val scope = rememberCoroutineScope()`，并补充 `androidx.compose.runtime.rememberCoroutineScope` 与 `kotlinx.coroutines.launch` 两个 import。
2. 点击数字时保留原逻辑不变（先更新 `selectedIndex`、再 `onValueChange(value)`），仅把吸附动作改为 `scope.launch { listState.scrollToItem(index, 0) }`，在协程中执行挂起跳转。
3. 空值修复、`LaunchedEffect` 内的 `scrollToItem` 调用、Confirm/Cancel 语义、API / ViewModel / 后端 / ML / 其他文件均未改动。

该编译修复同样**未运行测试 / 构建**。
