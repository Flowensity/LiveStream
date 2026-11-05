# 01 - 系统总体架构与模块划分设计

## 1. 项目定位与核心设计目标

LiveStream 是一款专为中国大陆市场设计的、多端合一（Android TV、Android 手机、Android 平板）的高性能、高稳定性、现代化直播应用。

### 核心设计原则
1. **原创自主设计（Greenfield）**：不基于任何现有开源直播项目（如 WebViewTvLive 等）改写，从零独立设计 Clean Architecture 架构。
2. **原生播放优先**：以 AndroidX Media3 (ExoPlayer) 为核心播放引擎，严格采用 SurfaceView 优先与硬件解码优先策略；WebView 仅作为官方合规网页的边缘兼容方案，严禁作为默认引擎。
3. **严格分层与单向依赖（UDF）**：UI → ViewModel → UseCase → Repository Interface。UI 绝不直接依赖播放器引擎、Room 数据库或网络层；Source Adapter 绝不直接依赖播放器实现。
4. **单实例生命周期管控**：前台仅允许存在唯一活跃的原生播放器会话，杜绝实例泄露、音频冲突和后台持续解码导致的资源浪费。

---

## 2. 模块依赖架构（Modular Architecture）

系统按照关注点分离原则，划分为七大逻辑层次：

```
                      +---------------------------------------+
                      |                 :app                  |
                      +---------------------------------------+
                                          |
                +-------------------------+-------------------------+
                |                                                   |
                v                                                   v
      +-------------------+                               +-------------------+
      |     :feature:*    |                               | :playback:engine  |
      +-------------------+                               +-------------------+
                |                                                   |
                v                                                   v
      +-------------------+                               +-------------------+
      |     :domain:*     | <---------------------------- |  :playback:api    |
      +-------------------+                               +-------------------+
                ^
                |
      +-------------------+
      |      :data:*      |
      +-------------------+
         |             |
         v             v
  +-------------+  +---------------+
  |  :source:*  |  | :core:database|
  +-------------+  | :core:network |
                   +---------------+
                           |
                           v
                   +---------------+
                   |  :core:model  |
                   |  :core:common |
                   +---------------+
```

### 模块详细清单与职责

| 模块层级 | 模块路径 | 职责范围 | 依赖约束 |
| :--- | :--- | :--- | :--- |
| **App** | `:app` | Application 入口、全局 Hilt 依赖注入容器装配、导航图集成 | 依赖所有 feature 与核心基础设施模块 |
| **Core** | `:core:common` | 通用调度器接口、Result 封装、常量、Base 工具 | 纯 Kotlin 模块，零业务依赖 |
| | `:core:model` | 领域公共数据模型（Channel, Source, EPG, Quality, SourceResolution 等） | 纯 Kotlin 模块，不依赖任何 AndroidX / Media3 |
| | `:core:database` | Room 数据库抽象、Entity、DAO、Migration 策略 | 依赖 `:core:model` |
| | `:core:network` | OkHttp、Retrofit、统一重试与超时控制、请求头拦截器 | 依赖 `:core:model` |
| | `:core:ui` | 共享 Compose 主题、配色设计系统、TV/Mobile 自适应扩展修饰符 | 依赖 `:core:common`, Compose 基础库 |
| **Domain** | `:domain:channel` | 频道列表、分类、收藏、排序、搜索业务 UseCases | 依赖 `:core:model`, `:core:common` |
| | `:domain:playback` | 播放调度策略、切台编排接口、播放状态定义 | 依赖 `:core:model` |
| **Data** | `:data:channel` | 频道与直播源数据仓库实现，管理本地缓存与远程拉取同步 | 依赖 `:core:database`, `:core:network`, `:domain:channel` |
| | `:data:epg` | EPG 节目单仓库实现、XMLTV 解析、缓存更新与失效控制 | 依赖 `:core:database`, `:core:network` |
| **Playback**| `:playback:playback-api` | 播放器抽象接口（PlaybackEngine, OrchestratorContract） | 依赖 `:core:model`，不依赖 ExoPlayer |
| | `:playback:playback-media3`| AndroidX Media3 ExoPlayer 原生引擎实现、硬解配置、SurfaceView 管理 | 依赖 `:playback:playback-api`, Media3 核心依赖 |
| | `:playback:playback-web` | 官方合规网页独立播放容器、WebView 崩溃恢复与隔离 | 依赖 `:playback:playback-api` |
| **Source** | `:source:source-api` | SourceAdapter 统一契约、资源解析协议、状态检测器 | 依赖 `:core:model` |
| | `:source:source-custom` | 用户自定义 M3U/JSON 直播源解析适配器 | 依赖 `:source:source-api` |
| **Feature**| `:feature:player` | 视频播放主界面、切台遮罩、OSD 控制面板、TV/Mobile 双形态适配 | 依赖 `:playback:playback-api`, `:domain:*`, `:core:ui` |
| | `:feature:channel` | 频道列表、分类侧边栏、EPG 浮层、收藏夹 | 依赖 `:domain:channel`, `:core:ui` |

---

## 3. 渐进式工程模块创建策略

为避免前期过度抽象与编译开销膨胀，工程采用**渐进式演进路径**：
1. **阶段一（当前）**：建立根工程、统一 Version Catalog、基础 App 容器与最底层的核心实体模块，打通 Gradle 编译构建与单元测试通道。
2. **阶段二**：接入 `:core:database`、`:core:network` 与 `:data:channel`，实现实体落地与本地持久化测试。
3. **阶段三**：接入 `:playback:playback-api` 与 `:playback:playback-media3`，实现 Playback Orchestrator 与 Media3 原生播放验证。
4. **后续阶段**：依序扩展 Feature 业务模块与设备形态自适应。
