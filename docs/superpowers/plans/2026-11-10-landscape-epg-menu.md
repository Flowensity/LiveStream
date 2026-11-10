# 手机横屏播放菜单双栏布局优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化手机端横屏状态下点击「播放菜单」时的界面布局，通过横向双栏结构（左侧控制，右侧节目单）解决信号源和画面比例胶囊对今日节目单严重挤压的体验问题。

**Architecture:** 
检测设备当前是否处于手机横屏状态（非 TV 且处于 Landscape 方向）。在横屏状态下，将模态抽屉宽度由原先固定的 `264.dp` 自适应扩展为宽屏双栏形态（最大不超过屏幕宽度的 58%，典型约 `440.dp`）；顶部保留全局标题栏与关闭胶囊，主体划分为左右两栏：左栏（约 `190.dp`）集中承载信号源切换与 2×2 网格化的画面比例胶囊，右栏独占垂直全高承载今日节目单 `LazyColumn`，实现节目单高度由不足 100dp 扩大到 260dp+，并在竖屏和 TV 端平滑维持原有单列形态。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, OneUI Theme Design System

**Spec:** [2026-11-10-landscape-epg-menu-design.md](file:///home/flowensity/LiveStream/docs/superpowers/specs/2026-11-10-landscape-epg-menu-design.md)

## Global Constraints

- JDK 17: `/home/flowensity/android-toolchain/jdk-17.0.21+9`
- Android SDK: `/home/flowensity/android-toolchain/sdk`
- 电视 TV 端（`DeviceType.TV`）交互逻辑与单列焦点导航严格保持原样，不产生回归。
- 手机竖屏（Portrait）下单列样式与逻辑严格保持原样。
- 严禁出现组件溢出、文字截断或横向滚动条。

## Review Focus

1. **窄屏横屏设备显示（如小屏手机或折叠屏外屏横屏）**：抽屉宽度不能超出屏幕可视范围，需通过 `minOf(440.dp, (screenWidthDp * 0.58f).dp)` 限制。
2. **画面比例胶囊文本可读性**：在左栏约 190dp 宽度内，4 个比例项采用 2 行 × 2 列网格排布，文本不截断且点击热区充足。
3. **正在播出节目自动定位**：双栏切换后，`LaunchedEffect` 对 `LazyColumn` 的 `scrollToItem` 依然有效。
4. **TV 初始焦点控制**：TV 端 `firstFocusRequester.requestFocus()` 行为不被横屏逻辑影响。
5. **单元测试与 APK 构建**：执行单元测试并执行 `./gradlew assembleDebug` 生成最终 APK 安装包。

---

### Task 1: 改造 `EpgScheduleDialog` 实现横屏自适应双栏布局

**Files:**
- Modify: `feature/player/src/main/kotlin/com/livestream/feature/player/ui/EpgScheduleDialog.kt:80-350`

**Interfaces:**
- Consumes:
  - `DeviceTypeDetector.kt:rememberDeviceType(): DeviceType`
  - `VideoAspectRatio` enum
  - `EpgProgram` data model
- Produces:
  - 响应式 `EpgScheduleDialog`，在横屏下自动展开双栏抽屉结构。

- [ ] **Step 1: 增加横屏状态判断与抽屉动态宽度计算**

在 `EpgScheduleDialog.kt` 中获取 `LocalConfiguration.current`，计算：
```kotlin
val configuration = LocalConfiguration.current
val deviceType = rememberDeviceType()
val isTv = deviceType == DeviceType.TV
val isLandscape = !isTv && (
    deviceType == DeviceType.PHONE_LANDSCAPE ||
    configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
)
val drawerWidth = if (isLandscape) {
    minOf(440.dp, (configuration.screenWidthDp * 0.58f).dp)
} else {
    OneUiDimensions.panelPlaylistWidth
}
```

- [ ] **Step 2: 抽屉主体结构分流（横屏双栏 vs 竖屏/TV单列）**

1. 将抽屉的 `.width(OneUiDimensions.panelPlaylistWidth)` 替换为 `.width(drawerWidth)`。
2. 保持顶部标题栏（频道名 + 关闭胶囊）贯穿全宽。
3. 抽屉内容主体在 `isLandscape` 时使用 `Row` 分为左右双栏：
   - **左栏 (`Modifier.width(190.dp).fillMaxHeight()`)**：
     - 放置「信号源与线路切换卡片」；
     - 放置「画面比例卡片」，卡片内 4 个比例胶囊拆分为 2 行 × 2 列的 `Row`，每行 2 个胶囊并均分宽度（`Modifier.weight(1f)`）；
   - **右栏 (`Modifier.weight(1f).fillMaxHeight()`)**：
     - 放置「今日节目单 ($todayStr) · 共 X 档」标题行；
     - 放置今日节目单 `LazyColumn`（占用剩余全部垂直高度，`Modifier.weight(1f).fillMaxWidth()`）。
4. 在 `!isLandscape`（竖屏与 TV）时，保持原有的单列 Column 垂直堆叠排版不变。

- [ ] **Step 3: 检查编译状态**

验证修改后的 Compose 代码没有语法错误或类型不匹配。

---

### Task 2: 运行全量单元测试并进行代码回归验证

**Files:**
- Test: 全工程单元测试

- [ ] **Step 1: 执行单元测试任务**

使用环境变量运行单元测试：
```bash
JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ANDROID_HOME=/home/flowensity/android-toolchain/sdk ./gradlew testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`，所有测试用例通过。

---

### Task 3: 构建 Debug APK 安装包并输出交付物

**Files:**
- Build output: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: 执行 assembleDebug**

```bash
JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ANDROID_HOME=/home/flowensity/android-toolchain/sdk ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 验证 APK 产物并记录信息**

检查 APK 文件的存在性、路径与大小：
```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```
Expected: 生成完整的 `.apk` 文件。
