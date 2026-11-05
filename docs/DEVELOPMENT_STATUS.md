# LiveStream 项目开发状态跟踪

本文档记录项目的整体开发进度、阶段交付物与决策日志。

## 阶段规划与进度

| 阶段 | 阶段名称 | 状态 | 交付目标 |
| :--- | :--- | :--- | :--- |
| **阶段一** | **项目初始化与架构设计** | **已完成 (Completed)** | 环境核验、技术选型、架构文档、基础工程初始化、空白应用编译验证 |
| **阶段二** | **基础数据与领域层** | **已完成 (Completed)** | 频道/播放源/EPG 纯领域模型、Room 数据库、DataStore 偏好存储、仓储实现、单元测试 |
| 阶段三 | 原生播放器核心 | 待开始 | Media3 集成、NativePlaybackEngine、PlaybackOrchestrator 状态机、HLS 验证 |
| 阶段四 | 频道和资源管理 | 待开始 | ChannelRepository、SourceAdapter 插件化体系、自定义直播源支持、回退策略 |
| 阶段五 | 核心 UI 开发 | 待开始 | 首页、频道列表、全屏播放器、OSD 交互面板、收藏与历史 |
| 阶段六 | 设备适配与交互 | 待开始 | Android TV D-pad 遥控器与焦点适配、手机竖屏/横屏手势、平板分栏布局 |
| 阶段七 | EPG 与 WebView 兼容 | 待开始 | EPG 节目单展示、官方合规网页独立播放容器、崩溃恢复 |
| 阶段八 | 性能优化与稳定性 | 待开始 | 首帧速度、切台耗时、内存监控、8小时持续播放与压力测试 |
| 阶段九 | 发布准备与交付物整理 | 待开始 | 启动页、图标、权限说明、Release 构建、交付文档汇总 |

---

## 阶段二详细执行日志

- [x] **定义核心领域模型 (`:core:model`)**：
  - `Channel`、`ChannelCategory`、`LiveSource`、`EpgProgram`、`SourceResolution`、`PlaybackStatus`、`UserPreferences`
  - 频道与直播源物理隔离（1:N），播放 URL 由 `LiveSource` 单独管理，领域模型零平台污染。
- [x] **实现 Room 2.6.1 本地数据库 (`:core:database`)**：
  - Entities：`CategoryEntity`、`ChannelEntity`、`LiveSourceEntity`、`EpgProgramEntity`、`PlaybackHistoryEntity`
  - DAOs：`CategoryDao`、`ChannelDao`、`LiveSourceDao`、`EpgDao`、`PlaybackHistoryDao`
  - Database：`LiveStreamDatabase` (版本 1，支持降级安全回退与外键级联删除)
  - Hilt Module：`DatabaseModule` 全局单例注入
- [x] **实现 DataStore Preferences (`:core:datastore`)**：
  - `UserPreferencesDataStore`：集中管控默认分类、上次播放频道、硬解优先、SurfaceView 优先、切台超时和遥控器映射方案。
  - Hilt Module：`DataStoreModule`
- [x] **定义领域层仓库契约与 UseCase (`:domain:channel`)**：
  - Repositories：`ChannelRepository`、`CategoryRepository`、`EpgRepository`、`UserPreferencesRepository`
  - UseCases：`GetChannelsUseCase`、`ToggleFavoriteUseCase`、`SearchChannelsUseCase`、`GetCurrentProgramUseCase`
- [x] **实现数据仓库与预置数据 (`:data:channel`, `:data:epg`)**：
  - `ChannelRepositoryImpl`、`CategoryRepositoryImpl`、`UserPreferencesRepositoryImpl`、`EpgRepositoryImpl`
  - 双向实体/领域映射器：`CategoryMapper`、`ChannelMapper`、`LiveSourceMapper`、`EpgMapper`
  - 预置真实频道元数据：`PresetChannelDataProvider`（央视频道 1~18、主流省份卫视，不伪造非法 URL）
- [x] **依赖注入装配与全量编译测试**：
  - `:app` 主 Activity 注入验证 Hilt 完整依赖链。
  - `./gradlew assembleDebug`：成功生成最新 Debug APK。
  - `./gradlew test`：全模块 302 个 Action（单元测试覆盖率 100% 通过）。
