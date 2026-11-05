# LiveStream 项目开发状态跟踪

本文档记录项目的整体开发进度、阶段交付物与决策日志。

## 阶段规划与进度

| 阶段 | 阶段名称 | 状态 | 交付目标 |
| :--- | :--- | :--- | :--- |
| **阶段一** | **项目初始化与架构设计** | **已完成 (Completed)** | 环境核验、技术选型、架构文档、基础工程初始化、空白应用编译验证 |
| **阶段二** | **基础数据与领域层** | **已完成 (Completed)** | 频道/播放源/EPG 纯领域模型、Room 数据库、DataStore 偏好存储、仓储实现、单元测试 |
| **阶段三** | **原生播放器核心** | **已完成 (Completed)** | AndroidX Media3 集成、Media3PlaybackEngine、PlaybackOrchestrator 状态机、多源回退与防重入切台 |
| **阶段四** | **频道和资源管理** | **已完成 (Completed)** | ChannelRepository、SourceAdapter 插件化体系、自定义直播源支持、回退策略 |
| **阶段五** | **核心 UI 开发** | **已完成 (Completed)** | 首页、频道列表、全屏播放器、OSD 交互面板、收藏与设置 |
| **阶段六** | **设备适配与交互** | **已完成 (Completed)** | Android TV D-pad 遥控器与焦点适配、手机竖屏/横屏手势、平板分栏布局 |
| **阶段七** | **EPG 与 WebView 兼容** | **已完成 (Completed)** | XMLTV 流式节目单解析与持久化、EPG 节目用例、官方合规网页独立播放容器、崩溃恢复 |
| **阶段八** | **性能优化与稳定性** | **已完成 (Completed)** | 首帧速度 P95 统计、切台分段度量（P95 ≤ 2.0s）、网络自愈、卡顿看门狗、100次切台压测与8小时播放模拟 |
| 阶段九 | 发布准备与交付物整理 | 待开始 | 启动页、图标、权限说明、Release 构建、交付文档汇总 |

---

## 阶段三详细执行日志

- [x] **建立播放抽象模块 (`:playback:playback-api`)**：
  - 纯 Kotlin 领域契约：`PlaybackEngine`、`PlaybackOrchestrator`、`EngineState`、`PlaybackMediaItem`、`PlaybackMetrics`。
  - 调度器聚合状态 `OrchestratorState`（维护 `PlaybackStatus` 10 大核心状态、当前活跃频道、当前活跃源、错误详情与实时指标）。
- [x] **实现 AndroidX Media3 原生引擎 (`:playback:playback-media3`)**：
  - 硬件解码优先（`MediaCodecSelector.DEFAULT` + `EXTENSION_RENDERER_MODE_OFF`，禁止默认软解）。
  - 低延迟直播专用缓冲策略（`DefaultLoadControl`：最小 1.5s，最大 5s，起播 0.5s，重缓冲 1.0s；`LiveConfiguration` 目标延迟 3s）。
  - 播放器单实例复用机制（通过 `setMediaItem()` 换台，杜绝反复销毁重建开销）。
  - 支持绑定/解绑 SurfaceView 与 TextureView 硬件渲染图层。
  - 实现首帧耗时（`onRenderedFirstFrame`）、切台时长、实时码率与缓冲水位统计。
  - `Media3ErrorClassifier`：将 Media3 底层网络超时、源不可用、解码器失败、DRM 鉴权等错误精准分类为领域错误模型。
- [x] **实现统一播放调度器 (`DefaultPlaybackOrchestrator`)**：
  - 异步切台请求取消（在途协程任务协同取消）。
  - 递增 Sequence Token（`activeSessionToken`）隔离机制，杜绝旧任务回调污染新频道状态。
  - 同频道多源自动无缝回退（主线路异常时自动顺延切换至备用线路）。
  - 有限重试机制（指数退避，上限 3 次，杜绝死循环）。
- [x] **依赖注入与测试验证**：
  - 在 `:app` 中注入 `PlaybackOrchestrator` 验证整图装配。
  - 单元测试套件：`Media3ErrorClassifierTest`、`PlaybackOrchestratorTest`。
  - `./gradlew assembleDebug`：成功生成最新集成 APK。
  - `./gradlew test`：全工程 354 个 Action（8 个模块单元测试 100% 通过）。

---

## 阶段四详细执行日志

- [x] **建立通用网络与探测模块 (`:core:network`)**：
  - 基于 OkHttp 4.12.0 构建轻量流式网络探测器 `NetworkProbe`。
  - 支持 HEAD/GET 状态码探测、响应时间测量与安全超时保护。
  - Hilt 单例网络依赖配置 `NetworkModule`。
- [x] **建立统一源适配器插件化标准 (`:source:source-api`)**：
  - `SourceAdapter` 核心抽象：`providerId`、`resolve(channel, source)` 解析、`checkAvailability(source)` 可用性检测。
  - `AvailabilityResult` 严格状态定义（`Available` 带延迟测量、`NetworkError`、`HttpError`、`Forbidden`、`InvalidUri`）。
  - `SourceAdapterRegistry` 与 `DefaultSourceAdapterRegistry`：注册、反注册、按 ProviderId 查找适配器、线程安全并发保护。
- [x] **实现自定义源适配器与安全 M3U 解析器 (`:source:source-custom`)**：
  - `M3uPlaylistParser`：严格解析 `#EXTM3U`、`#EXTINF`（tvg-id, tvg-name, tvg-logo, group-title）、URL 提取与清洗。
  - 防 SSRF 与内网穿透安全过滤（拦截 IPv4 私有网段 `10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`、`127.0.0.0/8`、`169.254.0.0/16`，以及 IPv6 本地回环与链路本地地址）。
  - 协议协议过滤：仅放行 `http://`、`https://` 与合规流协议，拦截恶意 `file://`、`content://` 等。
  - `CustomSourceAdapter`：解析自定义 NativeMedia，支持 HLS/DASH/Progressive 协议自动探测与可用性校验。
- [x] **实现官方源适配器 (`:source:source-official`)**：
  - `OfficialSourceAdapter`：支持官方网页授权播放协议分发（解析出 `SourceResolution.OfficialWeb` 或原生合规流）。
- [x] **源适配器体系与播放调度器无缝连接**：
  - 升级 `DefaultPlaybackOrchestrator`：在准备播放源阶段接入 `SourceAdapterRegistry`，动态调度对应适配器进行合法性解析与鉴权配置组装，无法解析时优雅平滑回退至下一源。
  - 仓储层 `ChannelRepository` 增加根据 `sourceId` 查询流地址能力并与 Room 严格持久化同步。
  - Hilt Multibindings（`@IntoSet`）实现适配器解耦自动注入，并在 `SourceRegistryModule` 自动组装进入单例注册表。
- [x] **全面单元测试与构建验证**：
  - 覆盖 `SourceAdapterRegistryTest`、`M3uPlaylistParserTest`、`CustomSourceAdapterTest`、`OfficialSourceAdapterTest`。
  - `./gradlew test`：全工程 498 个 Action 全部通过（100% 成功）。
  - `./gradlew assembleDebug`：成功编译输出包含 Phase 4 的完整调试 APK。

---

## 阶段五详细执行日志

- [x] **建立核心 TV/移动通用 UI 基础组件 (`:core:ui`)**：
  - `TvFocusableBox`：专为 Android TV 遥控器与触摸端优化的焦点容器（遥控器获焦时触发 1.04x 微放大动效、`FocusHighlight` 高对比度边框与背景色提升，确保远距离观看可见性）。
  - `StreamLoadingIndicator`：暗色主题动态脉冲三点缓冲组件与状态提示文字。
- [x] **实现原生播放器视图与 OSD 交互面板 (`:feature:player`)**：
  - `LiveVideoSurface`：基于 Compose `AndroidView` 封装底层原生 `SurfaceView`（默认优先启用独立硬件叠加层），自动生命周期绑定/解绑 `PlaybackOrchestrator`。
  - `PlayerOverlay`：全屏 OSD 叠加面板，集成顶部频道徽标（频道号、频道名、多源线路切换信息）、底部实时技术指标（分辨率、帧率、码率、缓冲水位、切台耗时）、中间缓冲状态与错误重试卡片。
  - `PlayerViewModel`：UDF 单向数据流架构，自动响应 `OrchestratorState`，实现 5 秒无操作 OSD 自动淡出淡入倒计时与遥控器数字键切台防抖收集。
- [x] **实现频道侧边栏与分类列表 (`:feature:channel`)**：
  - `CategoryTabRow`：横向平滑滚动与 D-pad 左右焦点穿透的分类导航栏（支持虚拟"全部频道"、"我的收藏"及数据库分类）。
  - `ChannelListItem`：高可见度频道项组件（台号、频道名、正在播放绿点指示器、星标收藏一键切换）。
  - `ChannelDrawer`：左侧半透明毛玻璃抽屉面板，集成即时搜索栏、分类筛选、频道统计与空状态占位。
  - `ChannelViewModel`：状态容器管理，支持关键词即时模糊搜索与分类切换，配合 Turbine 单元测试验证。
- [x] **实现设置与自定义直播源导入 (`:feature:settings`)**：
  - `SettingsDialog`：模态暗色交互面板，支持硬件解码优先开关、SurfaceView/TextureView 渲染图层切换、换台超时设置。
  - `M3uImportDialog`：直接输入/粘贴 M3U 播放列表，结合 `M3uPlaylistParser` 安全策略解析并写入 Room 数据库，展示实时导入成功计数或错误原因。
  - `SettingsViewModel`：持久化与 DataStore 偏好设置及 Room 频道仓储联动。
- [x] **实现主界面统一协调容器与按键调度 (`:feature:home`)**：
  - `HomeScreen`：全层级叠加容器（底层视频、中层 OSD、顶层抽屉与模态设置），支持 Back 键逐级消费。
  - Android TV 遥控器按键全量劫持（`Key.DirectionUp/Down` 快速切台、`Key.DirectionLeft` 切换直播源线路、`Key.DirectionCenter/Enter` 调出频道抽屉、`Key.Menu` 打开设置、数字键 `0-9` 直接选台）。
  - `HomeViewModel`：开机自动首播（记忆最后播放频道，无记录时默认起播首个频道）。
- [x] **全面单元测试与构建验证**：
  - 覆盖 `PlayerViewModelTest`、`ChannelViewModelTest`、`SettingsViewModelTest`、`HomeViewModelTest`。
  - `./gradlew test`：全工程 694 个 Action 全部通过（100% 成功）。
  - `./gradlew assembleDebug`：成功构建包含 Phase 5 完整 UI 的调试 APK（`app-debug.apk`）。

---

## 阶段六详细执行日志

- [x] **建立设备形态动态探测引擎 (`:core:ui`)**：
  - `DeviceType` 四大交互形态枚举：`TV`、`PHONE_PORTRAIT`、`PHONE_LANDSCAPE`、`TABLET`。
  - `DeviceDetector`：结合 `UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION`、`PackageManager.FEATURE_LEANBACK` 系统特征及屏幕尺寸分级（Window Size Class、`smallestScreenWidthDp >= 600dp`、`screenWidthDp >= 840dp`），精准划分宿主形态。
  - `rememberDeviceType()`：Compose 响应式状态函数，在手机旋转、折叠屏展开或多窗口模式下自适应动态重组。
- [x] **实现手机专属形态 (`PhonePortraitLayout` & `PhoneLandscapeLayout`)**：
  - **手机竖屏模式**：上半屏 16:9 播放窗口（带全屏进入按钮与设置入口），下半屏自适应频道分类导航栏、搜索框与频道列表流，符合单手滑动触控习惯。
  - **手机横屏全屏模式**：沉浸式视频渲染，引入 `playerTouchGestures` 手势控制：
    - 屏幕左半屏纵向滑动：线性调节系统屏幕亮度（带 `GestureHud` 亮度百分比反馈）。
    - 屏幕右半屏纵向滑动：平滑步进调节媒体系统音量（带 `GestureHud` 音量百分比反馈）。
    - 双击画面：快速切线/换源。
    - 顶部快捷栏提供一键"退出全屏"（自动旋转恢复竖屏）。
- [x] **实现平板电脑分栏响应式布局 (`TabletDualPaneLayout`)**：
  - 左侧常驻导航面板（宽度 360dp）：集成标题、搜索框、分类标签、频道列表与设置入口。
  - 右侧全尺寸播放区域：自适应填充剩余大屏空间，展示高画质视频图层与指标。
- [x] **增强 Android TV 遥控器与交互布局 (`TvLayout`)**：
  - 沉浸式大屏全屏播放，顶层快捷键无缝消费。
  - 遥控器 D-pad 方向键全键位拦截映射（上下切台、左右切线/OSD、OK 键唤出频道抽屉、MENU 打开设置、数字键 `0-9` 直接选台）。
  - 全界面交互元素继承 `TvFocusableBox`，获焦触发微放大动效与高对比度高亮外框。
- [x] **多端统一入口路由与返回键消费 (`HomeScreen`)**：
  - `HomeScreen` 统一监听 `rememberDeviceType()` 并无缝路由至对应的宿主布局。
  - 配合 `BackHandler` 实现优雅的返回键逐层退出（导入弹窗 -> 设置弹窗 -> 频道抽屉 -> OSD 隐藏 -> 系统返回）。
- [x] **单元测试与构建验证**：
  - 覆盖 `DeviceDetectorTest`（TV、平板、手机横屏/竖屏判断）与 `DeviceControlHelperTest`（音量百分比换算）。
  - `./gradlew test`：全工程 700 个 Action 全部通过（100% 成功）。
  - `./gradlew assembleDebug`：成功构建多端自适应调试 APK。

---

## 阶段七详细执行日志

- [x] **实现 XMLTV 流式节目单解析器 (`:data:epg`)**：
  - `XmltvParser`：采用标准 SAX 流式事件驱动解析（`SAXParserFactory`），实现内存占用恒定 O(1) 的大容量 XML 节目单流式导入。
  - 严谨处理时区偏移格式（`yyyyMMddHHmmss Z` 与 `yyyyMMddHHmmss`）与确定性唯一主键设计（`${channelId}_${startTimestamp}`），杜绝重复与数据污染。
  - 单元测试：`XmltvParserTest` 覆盖标准 XMLTV、缺失时区、格式容错与空字段健壮性验证。
- [x] **EPG 领域用例与数据层装配 (`:domain:channel` & `:data:epg`)**：
  - `GetCurrentProgramUseCase`：按当前时标动态流式观察当前频道的正在播出节目。
  - `GetNextProgramUseCase`：流式观察下一档即将播出的节目预告。
  - `GetProgramsForChannelUseCase`：观察指定时间区间内的全量节目单。
  - 在 `EpgDataModule` 中声明并由 Hilt 注入各 UseCase。
- [x] **官方合规网页独立播放容器 (`:playback:playback-api` & `:playback:playback-web`)**：
  - `WebPlaybackController`：纯领域播放契约，独立生命周期与状态机（`Idle`、`Loading`、`Ready`、`Playing`、`Error`、`RendererCrashed`、`Destroyed`）。
  - `DefaultWebPlaybackController`：独立沙箱生命周期管理，按需激活。
    - 严格 Web 安全沙箱：禁止明文混合内容（`MIXED_CONTENT_NEVER_ALLOW`）。
    - 崩溃隔离与恢复（`onRenderProcessGone`）：防止因 Web 进程内存超限崩溃导致整个宿主应用闪退，优雅捕获崩溃并流转至 `RendererCrashed` 触发自动回退或重启。
    - 页面加载 15 秒超时保护（`PAGE_LOAD_TIMEOUT_MS`）。
    - 干净退出销毁（`stopLoading`、`clearHistory`、`about:blank`、从父布局容器解绑、`destroy()` 并置空引用，杜绝内存泄漏）。
- [x] **统一播放调度层整合与状态流转 (`DefaultPlaybackOrchestrator`)**：
  - 接入 `WebPlaybackController`，当适配器解析出 `SourceResolution.OfficialWeb` 时：
    - 自动停止原生 Media3 引擎（释放硬件解码通道）。
    - 将调度器状态标记为 `isOfficialWeb = true` 与 `officialWebUrl`。
  - 当解析或回退为 `SourceResolution.NativeMedia` 时，自动调用 `webPlaybackController.stopAndDestroy()`。
  - 在 `stop()` 与 `release()` 周期中自动联动销毁 Web 播放容器。
  - 监听 `webState` 驱动调度器的 `BUFFERING`、`PLAYING` 与 `ERROR` 状态。
- [x] **UI 呈现与多端合规渲染 (`:feature:player` & `:feature:home`)**：
  - `WebPlaybackContainer`：基于 Compose `AndroidView` 实现的官方网页容器，自动托管加载与生命周期解绑。
  - `PlayerOverlay`：在 OSD 顶部面板动态呈现当前播出节目（如 `正在播出: 新闻联播 (19:00 - 19:30)`）与即将播出预告，展示"官方合规网页"状态角标。
  - `ChannelListItem`：频道列表项增加当前播出节目副标题，丰富选台信息。
  - 多端布局（`TvLayout`、`PhonePortraitLayout`、`PhoneLandscapeLayout`、`TabletDualPaneLayout`）全量支持根据流类型自适应切换 `LiveVideoSurface` 与 `WebPlaybackContainer`。
- [x] **单元测试与构建验证**：
  - 覆盖 `XmltvParserTest`、`DefaultWebPlaybackControllerTest`、`PlaybackOrchestratorTest`（官方网页状态流转测试）、`PlayerViewModelTest`（EPG 订阅与官方网页状态测试）。
  - `./gradlew test`：全工程 749 个 Action 全部通过（100% 成功）。
  - `./gradlew assembleDebug`：成功构建集成 Phase 7 的完整多端调试 APK。

---

## 阶段八详细执行日志

- [x] **精细化时延拆解与统计基准追踪 (`:playback:playback-api`)**：
  - `PlaybackMetrics`：扩展分段时延统计字段（`networkResolutionMs` 网络解析耗时、`enginePrepareMs` 引擎初始化耗时、`decodeToFirstFrameMs` 硬件解码首帧呈现耗时、`stallCount` 卡顿计数）。
  - `LatencyTracker` & `DefaultLatencyTracker`：滑动窗口记录切台时延样本，采用 Nearest-Rank 分位数算法计算 P50 / P90 / P95 时延，判定是否达成验收指标（P95 ≤ 2.0s，成功率 ≥ 99%）。
  - 单元测试：`LatencyTrackerTest` 验证 100 样本下的 P50/P90/P95 精确值、成功率计算、滑动窗口滚动淘汰与重置功能。
- [x] **系统网络状态感知与断网无缝自愈 (`:core:common` & `:core:network`)**：
  - `NetworkMonitor`：基于 Clean Architecture 定义纯 Kotlin 网络状态观察契约。
  - `ConnectivityNetworkMonitor`：基于 Android `ConnectivityManager.NetworkCallback` 实时捕获 Wi-Fi / 移动蜂窝网络连接与断开。
  - `DefaultPlaybackOrchestrator`：集成网络自愈监听，当由断网恢复连网（如进出电梯或网络切换）且当前播放器处于 `NETWORK_TIMEOUT` 或 `NETWORK_DISCONNECTED` 时，自动触发 `retry()` 恢复拉流，用户无需手动干预。
- [x] **播放卡死看门狗机制 (`:playback:playback-media3`)**：
  - `PlaybackWatchdog`：监控播放器 `BUFFERING` 状态，若缓冲卡死持续超过超时阈值（默认 10 秒无响应），自动报警并触发平滑切线或安全自愈，杜绝黑屏死锁。
  - 单元测试：`PlaybackWatchdogTest` 验证超时触发、正常起播前取消定时器及主动停止保护。
- [x] **单例播放器复用与 WebView 内存泄漏追踪 (`:playback:playback-media3` & `:playback:playback-web`)**：
  - `Media3PlaybackEngine`：保持单一前台 `ExoPlayer` 实例复用（通过 `setMediaItem()` 增量换台），绝不重复创建。
  - `DefaultWebPlaybackController`：加入 `activeWebViewCount` 实例追踪状态，并在 `stopAndDestroy` 时执行全量释放（`stopLoading`、`clearHistory`、`about:blank`、移除父容器、`destroy`），确保活跃数严格 $\le 1$ 且退出后为 0。
  - 压测测试：`WebViewLeakStressTest` 验证连续 50 次切换后 WebView 活跃数恒定归零，无孤儿对象。
- [x] **高频连续切台防竞争与 8 小时播放稳定性压测**：
  - `ContinuousSwitchingStressTest`：模拟 TV 遥控器极端高频狂按方向键连续切台 100 次，验证递增 Sequence Token（`activeSessionToken`）彻底隔离废弃请求，播放器最终严格收敛至第 100 个频道，无并发串流与状态撕裂。
  - `LongPlaybackStabilitySimulatorTest`：模拟连续 8 小时（按 1 小时步进推进）长时间运行，模拟偶发网络轻微重缓冲自愈，验证 8 小时全周期保持 `PLAYING` 状态、零崩溃、无持续性内存泄露、看门狗指标正常。
- [x] **全工程集成测试与构建验证**：
  - `./gradlew test`：全工程 751 个 Action 全部通过（100% 成功）。
  - `./gradlew assembleDebug`：成功构建包含 Phase 8 性能与稳定性全套能力的调试 APK。
