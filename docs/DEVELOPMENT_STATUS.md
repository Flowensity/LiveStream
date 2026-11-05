# LiveStream 项目开发状态跟踪

本文档记录项目的整体开发进度、阶段交付物与决策日志。

## 阶段规划与进度

| 阶段 | 阶段名称 | 状态 | 交付目标 |
| :--- | :--- | :--- | :--- |
| **阶段一** | **项目初始化与架构设计** | **已完成 (Completed)** | 环境核验、技术选型、架构文档、基础工程初始化、空白应用编译验证 |
| **阶段二** | **基础数据与领域层** | **已完成 (Completed)** | 频道/播放源/EPG 纯领域模型、Room 数据库、DataStore 偏好存储、仓储实现、单元测试 |
| **阶段三** | **原生播放器核心** | **已完成 (Completed)** | AndroidX Media3 集成、Media3PlaybackEngine、PlaybackOrchestrator 状态机、多源回退与防重入切台 |
| 阶段四 | 频道和资源管理 | 待开始 | ChannelRepository、SourceAdapter 插件化体系、自定义直播源支持、回退策略 |
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
