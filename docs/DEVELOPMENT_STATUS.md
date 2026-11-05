# LiveStream 项目开发状态跟踪

本文档记录项目的整体开发进度、阶段交付物与决策日志。

## 阶段规划与进度

| 阶段 | 阶段名称 | 状态 | 交付目标 |
| :--- | :--- | :--- | :--- |
| **阶段一** | **项目初始化与架构设计** | **已完成 (Completed)** | 环境核验、技术选型、架构文档、基础工程初始化、空白应用编译验证 |
| **阶段二** | **基础数据与领域层** | **已完成 (Completed)** | 频道/播放源/EPG 纯领域模型、Room 数据库、DataStore 偏好存储、仓储实现、单元测试 |
| **阶段三** | **原生播放器核心** | **已完成 (Completed)** | AndroidX Media3 集成、Media3PlaybackEngine、PlaybackOrchestrator 状态机、多源回退与防重入切台 |
| **阶段四** | **频道和资源管理** | **已完成 (Completed)** | ChannelRepository、SourceAdapter 插件化体系、自定义直播源支持、回退策略 |
| 阶段五 | 核心 UI 开发 | 待开始 | 首页、频道列表、全屏播放器、OSD 交互面板、收藏与历史 |
| 阶段六 | 设备适配与交互 | 待开始 | Android TV D-pad 遥控器与焦点适配、手机竖屏/横屏手势、平板分栏布局 |
| 阶段七 | EPG 与 WebView 兼容 | 待开始 | EPG 节目单展示、官方合规网页独立播放容器、崩溃恢复 |
| 阶段八 | 性能优化与稳定性 | 待开始 | 首帧速度、切台耗时、内存监控、8小时持续播放与压力测试 |
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
