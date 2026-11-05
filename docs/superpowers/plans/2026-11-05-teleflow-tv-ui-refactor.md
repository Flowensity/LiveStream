# LiveStream UI 深度重构实现计划 (参考 TeleflowTv 项目规范)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 
1. 修复更名为“地方卫视”后分类下频道数据为空的严重 Bug（重构 `checkAndSeedDefaultChannels` 确保预置数据热同步与全量更新）；
2. 深度参考 `/home/flowensity/TeleflowTv` 项目的 UI 规范、组件度量与布局体系，重构 LiveStream 的 Design Tokens、ChannelBarView 浮动底部 OSD、PlaylistView 抽屉/列表、设置面板与退出弹窗。

**Architecture:**
- **Design Tokens**: 严格对齐 TeleflowTv 调色板：Samsung Blue `#0381FE` (Light) / `#3898FF` (Dark)、面板色 `#FAF5F6FA` / `#F5101114`、卡片色 `#FFFFFF` / `#1C1D22`、文字高对比度 `#191B20` / `#F1F1F5`、次级文字 `#5C606A` / `#A7A9B2`、细边框 `#1A191B20` / `#26FFFFFF`。
- **ChannelBarView 底部 HUD**: 采用 TeleflowTv 专属 440dp 浮动药丸底栏，展示频道大标题、线路胶囊徽章（`线路 1/1 · 央视频官方`）、正在播出与下期预告单行信息。
- **Playlist 频道面板**: 采用 TeleflowTv 316dp 浮动卡片面板，集成分组切换器（`‹` / 分类 / `›`）、药丸搜索框、48dp 紧凑频道行（Squircle 编号 + 粗体频道名）。
- **数据同步修复**: 重写 `checkAndSeedDefaultChannels()`，在任何启动场景下全量比对并补全预置分类、33 个地方卫视频道与官方源，保留已有收藏标记。

## Global Constraints
- 禁止任何冗余 Emoji 字符堆叠，保持 TeleflowTv 纯净现代 UI 质感。
- 保留完整的电视遥控器 D-Pad 支持、手机手势操作及 ExoPlayer/WebView 双内核播放架构。

---

### Task 1: 修复地方卫视分类频道为空 Bug
**Files:**
- Modify: `data/channel/src/main/kotlin/com/livestream/data/channel/repository/ChannelRepositoryImpl.kt`
- Test: `data/channel/src/test/kotlin/com/livestream/data/channel/ChannelRepositoryImplTest.kt`

- [x] 1.1 重构 `ChannelRepositoryImpl.checkAndSeedDefaultChannels()`：
  - 无条件更新预置分类列表（确保“地方卫视”分类 ID 与名称准确同步）；
  - 读取现有频道列表，保留用户的收藏状态（`isFavorite`）；
  - 将所有预置的 40 个 CCTV 频道与 33 个地方卫视频道（共 73 个）增量 upsert 进 `channels` 表，确保 `category_id = "satellite"` 的地方卫视频道完整入库；
  - 同步插入所有官方直播源实体。
- [x] 1.2 编写针对热升级/增量数据补全的单元测试并执行验证。

---

### Task 2: 对齐 TeleflowTv 全局 Design Tokens
**Files:**
- Modify: `core/ui/src/main/kotlin/com/livestream/core/ui/theme/Color.kt`
- Modify: `core/ui/src/main/kotlin/com/livestream/core/ui/theme/Theme.kt`

- [x] 2.1 更新 Color.kt 与 Theme.kt，精准匹配 TeleflowTv 的 `ThemePalette` 规范：
  - 强调色调整为精准 Samsung Blue `#0381FE`（亮色）与 `#3898FF`（暗色）；
  - 文字颜色升级为 `#191B20` / `#5C606A`（亮色）与 `#F1F1F5` / `#A7A9B2`（暗色）；
  - 引入 `panelBackground`、`surfacePill`、`borderSubtle` 等 TeleflowTv 专属 Token。
- [x] 2.2 增加 TeleflowTv 标准度量常量：`panelPlaylistWidth = 316.dp`、`hudChannelBarWidth = 440.dp`、`rowHeightChannel = 48.dp`。

---

### Task 3: 重构播放器 OSD 状态栏 (对齐 TeleflowTv ChannelBarView)
**Files:**
- Modify: `feature/player/src/main/kotlin/com/livestream/feature/player/ui/PlayerOverlay.kt`

- [x] 3.1 将底部浮动 OSD 重构为 TeleflowTv 风格的 `ChannelBarView` 浮动胶囊面板：
  - 宽度限制为 `440.dp`（手机端 `fillMaxWidth(0.92f)`），底部居中悬浮；
  - 第 1 行：频道名称（16sp，Bold，`textPrimary`）+ 线路/比例胶囊标签（`accentPrimary` 底衬，12sp）；
  - 第 2 行：源详情（11sp，`textSecondary`，如 `线路 1/1 · 央视频官方`）；
  - 第 3 行（EPG Now）：`正在播出  12:00-13:00  新闻联播`（12sp，单行截断省略）；
  - 第 4 行（EPG Next）：`下期预告  13:00  今日说法`（11sp，单行截断省略）。
- [x] 3.2 移除原有零散杂乱的浮动元素，换台时以优雅的淡入淡出动画展示。

---

### Task 4: 重构频道抽屉与列表项 (对齐 TeleflowTv PlaylistView & item_channel)
**Files:**
- Modify: `feature/channel/src/main/kotlin/com/livestream/feature/channel/ui/ChannelListItem.kt`
- Modify: `feature/channel/src/main/kotlin/com/livestream/feature/channel/ui/ChannelDrawer.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhonePortraitLayout.kt`

- [x] 4.1 重构 `ChannelListItem.kt`：
  - 行高严格采用 TeleflowTv 规范（`48.dp`）；
  - 左侧 34dp 宽度频道编号（`01`, `02`, Bold），中间频道标题（15sp，Bold），右侧简洁状态指示与轻量收藏切换；
  - 聚焦与选中态使用 TeleflowTv 专属柔和蓝底衬（`accentSoft`）与蓝色文字。
- [x] 4.2 重构 `ChannelDrawer.kt`：
  - 面板宽度调整为 TeleflowTv 标准 `316.dp`；
  - 顶部增加 TeleflowTv 经典的 One UI 分组切换条（`‹` / 分类名 / `›`）及流式分类药丸；
  - 搜索栏采用简洁的无边框/柔和底框，输入清爽。
- [x] 4.3 优化 `PhonePortraitLayout.kt` 中频道流与单手交互体验。

---

### Task 5: 重构设置面板与退出弹窗 (对齐 TeleflowTv AppSettingsView & ExitConfirmView)
**Files:**
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsDialog.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/ExitConfirmDialog.kt`

- [x] 5.1 重构 `SettingsDialog.kt`：
  - 对齐 TeleflowTv 的 `AppSettingsView`：居中浮动卡片模态（`440.dp` 宽，手机端 `360.dp`），半透明遮罩背景；
  - 项目高度 `52.dp`，采用 TeleflowTv 的分段选择器与简洁开关；
  - M3U 导入与扫码推送整合进精简卡片，去繁就简。
- [x] 5.2 重构 `ExitConfirmDialog.kt`：
  - 对齐 TeleflowTv 的 `ExitConfirmView`：`284.dp` 精致宽度，显示 App 版本信息与硬解状态；
  - 按钮采用 TeleflowTv 样式的次要按钮与危险警示按钮（`#DB332A` 退出）。

---

### Task 6: 编译、单元测试与 APK 验证
**Files:**
- Verification: `./gradlew test`
- Verification: `./gradlew assembleDebug assembleRelease`

- [x] 6.1 运行 `./gradlew test`，验证所有测试通过。
- [x] 6.2 运行 `./gradlew assembleDebug assembleRelease`，生成最新 APK。
- [x] 6.3 验证产物并提交代码。
