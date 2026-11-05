# Samsung One UI 深度优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 针对用户反馈全面优化 LiveStream UI：移除所有图标与 Emoji、缩减弹出菜单尺寸至精致紧凑、优化 EPG 播出与下期超长标题截断与排版、“各省卫视”更名为“地方卫视”、实现手机端透明沉浸式状态栏。

**Architecture:** 
1. 通过 `WindowCompat.setDecorFitsSystemWindows(window, false)` 与透明系统栏实现全屏沉浸，配合 WindowInsets 适配手机状态栏与导航手势条；
2. 全局清理字符图标/Emoji，采用 Samsung One UI 纯文字、字重与圆角胶囊组合；
3. 将 `SettingsDialog` 尺寸自适应收敛至紧凑卡片 (`maxWidth = 380.dp`) 并精简内边距与字体层级；
4. 重构 `PlayerOverlay` EPG 显示为双行带角标约束单行省略格式；
5. 修改预设分类名称为“地方卫视”并更新启动同步逻辑。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Navigation Insets, Room Database, Media3.

## Global Constraints
- 禁止在界面中保留任何 Emoji 图标字符（如 📺, 📅, ⚙, ⛶, 🔍, ✕, ☀️, 🌙, 🔴, 🕒 等）。
- 保持现有播放业务逻辑、遥控器 D-pad 控制及 M3U/CNTV 解析功能不变。
- 保证明暗主题与跟随系统模式无缝切换。

---

### Task 1: 分类更名与启动数据库同步 (“各省卫视” -> “地方卫视”)
**Files:**
- Modify: `data/channel/src/main/kotlin/com/livestream/data/channel/preset/PresetChannelDataProvider.kt`
- Modify: `data/channel/src/main/kotlin/com/livestream/data/channel/repository/ChannelRepositoryImpl.kt`

- [ ] 1.1 修改 `PresetChannelDataProvider.kt` 中 `satellite` 分类名称为“地方卫视”。
- [ ] 1.2 在 `ChannelRepositoryImpl.kt` 的 `checkAndSeedDefaultChannels()` 中增加兼容检查：若数据库中存在名为“各省卫视”的分类，则自动将其名称更新为“地方卫视”。
- [ ] 1.3 验证预置数据加载逻辑。

---

### Task 2: 手机端全屏沉浸式状态栏适配
**Files:**
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/kotlin/com/livestream/app/MainActivity.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhonePortraitLayout.kt`

- [ ] 2.1 修改 `themes.xml`，将 `statusBarColor` 与 `navigationBarColor` 设为透明 `@android:color/transparent`，启用 `windowDrawsSystemBarBackgrounds`。
- [ ] 2.2 在 `MainActivity.kt` 的 `onCreate` 中调用 `WindowCompat.setDecorFitsSystemWindows(window, false)`，并设置透明系统栏及对比度控制。
- [ ] 2.3 在 `PhonePortraitLayout.kt` 中合理处理状态栏与安全区域：确保播放器位于顶部沉浸区域，视频画面自然铺展至状态栏背景下，同时操作控件避免与状态栏重叠。

---

### Task 3: 移除全应用所有图标与 Emoji
**Files:**
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhonePortraitLayout.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhoneLandscapeLayout.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/TabletDualPaneLayout.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/TvLayout.kt`
- Modify: `feature/channel/src/main/kotlin/com/livestream/feature/channel/ui/ChannelDrawer.kt`
- Modify: `feature/channel/src/main/kotlin/com/livestream/feature/channel/ui/ChannelListItem.kt`
- Modify: `feature/player/src/main/kotlin/com/livestream/feature/player/ui/PlayerOverlay.kt`
- Modify: `feature/player/src/main/kotlin/com/livestream/feature/player/ui/GestureHud.kt`
- Modify: `feature/player/src/main/kotlin/com/livestream/feature/player/ui/EpgScheduleDialog.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsDialog.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/ExitConfirmDialog.kt`

- [ ] 3.1 清理 `PhonePortraitLayout` 中的图标：全屏按钮由“⛶ 全屏”改为“全屏”，操作胶囊改为“节目单”、“设置”，搜索栏移除 🔍 图标与 ✕，底部导航栏改为纯文字 One UI 胶囊标签。
- [ ] 3.2 清理 `PhoneLandscapeLayout`, `TabletDualPaneLayout`, `TvLayout` 中的图标。
- [ ] 3.3 清理 `ChannelDrawer` 与 `ChannelListItem` 中的图标（移除收藏星号等，统一为纯文字或极简高亮点）。
- [ ] 3.4 清理 `PlayerOverlay`、`GestureHud`、`EpgScheduleDialog`、`SettingsDialog` 中的所有 Emoji 字符。

---

### Task 4: 弹窗菜单精致轻量化改造 (`SettingsDialog` 与 `ChannelDrawer`)
**Files:**
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsDialog.kt`
- Modify: `feature/channel/src/main/kotlin/com/livestream/feature/channel/ui/ChannelDrawer.kt`

- [ ] 4.1 改造 `SettingsDialog`：
  - 容器尺寸由固定 `width(500.dp)` 调整为响应式 `fillMaxWidth(0.92f).widthIn(max = 380.dp)`；
  - 面板内边距由 `24.dp` 缩减为 `16.dp`；
  - 标题字号从 `22.sp` 精简为 `17.sp`，去除冗余的副标题介绍；
  - 减小分组卡片的 padding (`12.dp`) 和项与项间距，精简 Switch 与三态主题选择器，视觉效果更加紧凑、高级。
- [ ] 4.2 优化 `ChannelDrawer`：侧边抽屉宽度从 `370.dp` 收敛至 `290.dp`，提升画面透气度。

---

### Task 5: 播放器 OSD 节目单“播出”与“下期”长标题排版优化
**Files:**
- Modify: `feature/player/src/main/kotlin/com/livestream/feature/player/ui/PlayerOverlay.kt`
- Modify: `feature/player/src/main/kotlin/com/livestream/feature/player/ui/EpgScheduleDialog.kt`

- [ ] 5.1 在 `PlayerOverlay.kt` 中将“播出”与“下期”重构为清晰规整的上下两行布局：
  - 第一行：`播出` 圆角微型胶囊徽章 + 当前节目名称（限制 `maxLines = 1, overflow = TextOverflow.Ellipsis`）+ 时间段；
  - 第二行（若有）：`下期` 圆角次级徽章 + 下期节目名称（限制 `maxLines = 1, overflow = TextOverflow.Ellipsis`）+ 开始时间；
  - 即使节目名称达数十字，也能优雅单行截断，与右侧/右下侧的控制胶囊互不挤压。
- [ ] 5.2 检查并优化 `EpgScheduleDialog.kt` 中节目列表项，为节目标题增加 `maxLines = 1` 与 `TextOverflow.Ellipsis`，防止任何长文本溢出。

---

### Task 6: 编译、单元测试与 APK 打包验证
**Files:**
- Verification: `./gradlew test`
- Verification: `./gradlew assembleDebug assembleRelease`
- Verification: `git status`, `git commit`

- [ ] 6.1 运行 `./gradlew test`，确保所有单元测试全部通过。
- [ ] 6.2 运行 `./gradlew assembleDebug assembleRelease`，验证 Debug 与 Release APK 成功打包。
- [ ] 6.3 检查 git diff，提交代码更新，输出最终交付报告。
