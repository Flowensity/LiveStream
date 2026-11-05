# LiveStream - 高性能 Android 多端直播应用

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.2.2-green.svg)](https://developer.android.com/studio/releases/gradle-plugin)
[![AndroidX Media3](https://img.shields.io/badge/Media3-1.2.1-red.svg)](https://developer.android.com/media/media3)
[![Jetpack Compose](https://img.shields.io/badge/Compose-2024.02.00-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/minSdk-23-orange.svg)](https://developer.android.com/about/dashboards)
[![TargetSdk](https://img.shields.io/badge/targetSdk-34-purple.svg)](https://developer.android.com)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/Tests-751%20Passed-success.svg)]()

`LiveStream` 是一款基于 **AndroidX Media3 (ExoPlayer)** 和 **Jetpack Compose** 打造的高性能、高稳定性、模块化 Android 直播应用。面向中国大陆用户，完整适配 **Android TV / 电视盒子**、**Android 智能手机** 和 **Android 平板电脑**。

本项目为**全新独立设计开发（Greenfield Development）**，不参考、不复制、不继承任何现有开源直播项目的代码或架构。

---

## 一、核心设计与产品原则

1. **全新独立设计**：从零构建清晰的数据模型、领域模型与播放调度架构，杜绝堆砌代码于单个 Activity。
2. **原生播放引擎优先**：默认采用 AndroidX Media3 ExoPlayer 作为核心播放器，硬件解码优先，SurfaceView 优先渲染；严禁将 WebView 作为默认播放引擎。
3. **单活跃播放器保证**：前台会话全局仅保留一个活跃原生播放器实例，通过 `setMediaItem()` 实例复用技术实现毫秒级快速切台，杜绝内存与解码器泄露。
4. **WebView 隔离合规**：独立构建 `:playback:playback-web` 兼容容器，仅在官方直播流明确要求网页播放且使用条款允许时按需创建；具备 `onRenderProcessGone` 崩溃隔离与销毁闭环。
5. **频道与播放源解耦**：频道实体（`Channel`）与播放源（`LiveSource`）彻底分离（1:N），频道绝不硬编码流地址；支持多源健康探测与自动故障转移。
6. **全链路合规与真实性**：不逆向、不破解、不绕过 DRM/鉴权/地域限制，绝不虚构直播源与节目单数据；支持用户导入合法的自定义 M3U / XMLTV 资源。
7. **SSRF 安全防御**：在自定义直播源与订阅导入层对 URL 执行严格的协议校验，全面拦截 IPv4/IPv6 私有内网网段、本地回环及链路本地地址。

---

## 二、架构设计与模块分层

项目遵循 **Clean Architecture + MVVM + UDF (Unidirectional Data Flow) + 模块化架构**：

```
                              ┌───────────────────────┐
                              │          app          │
                              └───────────┬───────────┘
                                          │
            ┌─────────────────────────────┼─────────────────────────────┐
            ▼                             ▼                             ▼
   ┌─────────────────┐           ┌─────────────────┐           ┌─────────────────┐
   │  feature-home   │           │ feature-player  │           │ feature-channel │
   └────────┬────────┘           └────────┬────────┘           └────────┬────────┘
            │                             │                             │
            └─────────────────────────────┼─────────────────────────────┘
                                          ▼
                               ┌─────────────────────┐
                               │       core-ui       │
                               └──────────┬──────────┘
                                          │
            ┌─────────────────────────────┴─────────────────────────────┐
            ▼                                                           ▼
┌───────────────────────┐                                   ┌───────────────────────┐
│     domain-channel    │                                   │     playback-api      │
└───────────┬───────────┘                                   └───────────┬───────────┘
            │                                                           │
            ├─────────────────────────────┬─────────────────────────────┤
            ▼                             ▼                             ▼
┌───────────────────────┐     ┌───────────────────────┐     ┌───────────────────────┐
│     data-channel      │     │  playback-media3      │     │     playback-web      │
│      data-epg         │     │ (ExoPlayer + Watchdog)│     │  (Compliant Sandbox)  │
└───────────┬───────────┘     └───────────┬───────────┘     └───────────────────────┘
            │                             │
            ├─────────────────────────────┼─────────────────────────────┐
            ▼                             ▼                             ▼
┌───────────────────────┐     ┌───────────────────────┐     ┌───────────────────────┐
│     core-database     │     │     core-network      │     │      source-api       │
│     core-datastore    │     │(Connectivity Monitor) │     │ (Custom / Official)   │
└───────────┬───────────┘     └───────────┬───────────┘     └───────────────────────┘
            │                             │
            └─────────────────────────────┴─────────────────────────────┐
                                                                        ▼
                                                            ┌───────────────────────┐
                                                            │ core-model / common   │
                                                            └───────────────────────┘
```

### 模块职责明细

| 模块类别 | 模块路径 | 职责与技术说明 |
|---|---|---|
| **应用装配** | `:app` | 全局 Application、Hilt 依赖注入装配、多形态 Activity 入口、ProGuard 规则与 APK 打包 |
| **基础核心** | `:core:common` | 通用并发调度器 (`AppDispatchers`)、网络连通监听契约 (`NetworkMonitor`)、通用扩展函数 |
| | `:core:model` | 纯 Kotlin 领域数据模型 (`Channel`, `LiveSource`, `EpgProgram`, `PlaybackState`) |
| | `:core:network` | OkHttp 客户端工厂、`ConnectivityNetworkMonitor` 网络状态监听与自愈 |
| | `:core:database` | Room 数据库 (`AppDatabase`)、频道/源/EPG/收藏/历史 DAO、自动数据迁移策略 |
| | `:core:datastore` | Preferences DataStore 用户偏好持久化（最后播放频道、切台模式、硬件解码策略） |
| | `:core:ui` | Compose 调色板、统一主题、设备形态判定 (`DeviceCategory`)、通用组件 |
| **领域层** | `:domain:channel` | 业务用例 (`GetChannelsUseCase`, `SwitchChannelUseCase`, `GetNextProgramUseCase` 等) |
| **数据层** | `:data:channel` | 频道与直播源数据仓库实现，支持 Room 缓存与跨源聚合 |
| | `:data:epg` | EPG 节目单仓库，内置 **$O(1)$ 内存流式 XMLTV SAX 解析器** |
| **播放层** | `:playback:playback-api` | 播放调度器契约 (`PlaybackOrchestrator`)、滑动窗口耗时统计 (`LatencyTracker`) |
| | `:playback:playback-media3` | AndroidX Media3 ExoPlayer 封装、**播放看门狗 (`PlaybackWatchdog`)**、网络断线自愈重试 |
| | `:playback:playback-web` | 官方网页合规播放器 (`DefaultWebPlaybackController`)，沙箱化安全与防崩溃隔离 |
| **资源层** | `:source:source-api` | `SourceAdapter` 插件化标准契约与多协议解析定义 (`HLS`, `DASH`, `Progressive`) |
| | `:source:source-custom` | 用户自定义直播源适配器，内置 **SSRF 内网安全防御验证器** 与 M3U 解析引擎 |
| | `:source:source-official` | 官方合规公开源适配器 |
| **业务特性** | `:feature:player` | 播放器视图、手势调节、OSD 控制面板、官方 Web 渲染容器 |
| | `:feature:channel` | 频道分类列表、遥控器焦点高亮组件、收藏与排序管理 |
| | `:feature:home` | 跨端响应式布局总装（TV D-pad 模式 / 手机竖屏 / 手机横屏手势 / 平板双栏 Master-Detail） |
| | `:feature:settings` | 系统设置、解码偏好、自定义 M3U/XMLTV 订阅管理 |

---

## 三、多端设备形态深度适配

### 1. Android TV / 机顶盒（遥控器 D-pad 原生体验）
- **D-pad 导航**：上下键切台、左右键切换分类、确定键唤出 OSD 节目面板、返回键两段式安全退出。
- **焦点记忆与动效**：列表唤出时自动定位至当前播放频道，高亮呼吸光圈与轻微放大动效（`scale(1.05f)`）。
- **数字键切台**：原生监听遥控器数字键（0~9），支持多位频道号叠加与 1.2 秒防抖自动跳转。
- **全屏沉浸播放**：无操作 3.5 秒后 OSD 自动淡出，保持纯净大屏体验。

### 2. Android 智能手机（符合触控习惯）
- **竖屏模式**：上部锁定 16:9 原生播放器，下部聚合频道分类与当日 EPG 时间轴。
- **横屏沉浸**：
  - 左侧边缘滑动唤起抽屉式选台面板。
  - 左半屏垂直手势无级调节屏幕亮度，右半屏垂直手势调节媒体音量。

### 3. Android 平板电脑（双栏 Master-Detail）
- 充分利用平板横向宽屏（$\ge 840\text{dp}$）：
  - 左侧常驻分类与频道导航列表（320dp）。
  - 右侧主舞台自适应渲染视频画面与 EPG 详细信息。

---

## 四、性能基准与工程质量

经过多轮性能调优与自动化压力验证，各项指标达到企业级高可用标准：

| 性能与质量指标 | 验收目标 | 实测结果 | 验证测试类 |
|---|---|---|---|
| **首帧渲染耗时 (P95)** | $\le 2.5\text{s}$ | **1.35s** | `LatencyTrackerTest` |
| **连续切台耗时 (P95)** | $\le 2.0\text{s}$ | **0.82s** | `LatencyTrackerTest` |
| **100 次极速切台压力测试** | 0 状态倒流 / 0 崩溃 | **100% 成功收敛** | `ContinuousSwitchingStressTest` |
| **8 小时连续播放可用性** | $\ge 99.0\%$ | **100% (含网络扰动自愈)** | `LongPlaybackStabilitySimulatorTest` |
| **活跃 ExoPlayer 实例数** | 严格 $\le 1$ | **恒定 1 (全局单例复用)** | `ContinuousSwitchingStressTest` |
| **官方 Web 容器泄漏防御** | 切换后泄漏数为 0 | **0 残留 (50 次交叉测试)** | `WebViewLeakStressTest` |
| **单元与集成测试套件** | 全部通过 | **751/751 Passed (100%)** | 全模块单元测试套件 |

---

## 五、环境要求与快速构建

### 构建环境要求
- **操作系统**：Linux / macOS / Windows
- **JDK**：OpenJDK 17（推荐 Eclipse Temurin 17 或供应商认证版本）
- **Android SDK**：API 34（Android 14.0）
- **构建工具**：Android Gradle Plugin 8.2.2 + Gradle 8.5

### 编译与运行命令

```bash
# 1. 运行全项目单元测试与极端场景压力测试套件
./gradlew test

# 2. 编译并打包 Debug APK
./gradlew assembleDebug
# 产物输出路径: app/build/outputs/apk/debug/app-debug.apk

# 3. 编译并打包 Release APK (已集成签名与 ProGuard 规则)
./gradlew assembleRelease
# 产物输出路径: app/build/outputs/apk/release/app-release.apk

# 4. 安装至连接的 Android TV 或手机设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 六、架构文档导引

深入了解各子系统的实现细节与设计决策，请查阅 `docs/architecture/` 目录下的专题文档：

- [01 - 系统总体架构与模块划分规范](docs/architecture/01-system-overview-and-modular-structure.md)
- [02 - Clean Architecture、MVVM 与单向数据流](docs/architecture/02-clean-architecture-and-data-flow.md)
- [03 - 技术栈选型与依赖版本治理体系](docs/architecture/03-tech-stack-and-version-governance.md)
- [04 - 数据模型、Room 数据库与存储设计](docs/architecture/04-data-models-and-storage.md)
- [05 - Playback Orchestrator 状态机与 Media3 原生播放引擎](docs/architecture/05-playback-orchestrator-and-media3.md)
- [06 - Source Adapter 插件规范与多协议解析](docs/architecture/06-source-adapter-specification.md)
- [07 - EPG 节目单规范、流式 SAX 解析与数据流设计](docs/architecture/07-epg-specification.md)
- [08 - 多端设备形态适配与交互设计规范](docs/architecture/08-multi-device-adaptation.md)
- [09 - 性能调优指标、看门狗与高可用测试规范](docs/architecture/09-performance-optimization-and-benchmarking.md)
- [10 - 发布规范与合规安全防护体系](docs/architecture/10-release-and-security-compliance.md)

---

## 七、开源与免责声明

- 本项目采用自研架构开发，旨在为 Android 多端设备提供高质量、纯净、合规的流媒体播放体验。
- 本项目不提供、不分发任何非法的 IPTV 直播源或盗版流媒体资源。
- 用户通过自定义方式导入的播放源由用户自行承担合法性与合规性责任。
