# 包 B 变更报告：Android 视觉与刷新体验

## 完成内容与模块

- 将提供的 16 个 SVG 复制为 ASCII 命名的 `res/raw/hirex_*` 资源，替换底部导航、搜索、筛选、刷新、收藏、点赞和发帖入口；修复 Me 未选中态图标映射。
- Job 卡片改为标题独占行、工资下一行右对齐；SGD 展示为 `S$`，周期展示为 `· monthly` 等可读形式。
- Community 使用 app 级共享 ViewModel；详情页返回时立即把服务端点赞/评论状态合并回 Feed。
- 新增独立发帖 FAB，并保留刷新禁用/进度、empty/error/loading 等状态。

## API 与数据库

- 本包自身无新增 API 或数据库迁移；Community 使用包 C 的统一契约。

## 测试

- Android 回归测试新增工资格式与详情返回 Feed 后 liked/计数同步断言。
- `./gradlew testDebugUnitTest lintDebug assembleDebug` 通过（138 项单测，0 失败）。

## 限制与下一步

- 未在多种实体屏幕尺寸上做截图像素对比；下一步最小工作是在窄屏和常规屏各验证一次长职位标题、长工资、四个底部导航状态。
