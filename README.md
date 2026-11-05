# LiveStream - Android 多端电视直播 App

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.2.2-green.svg)](https://developer.android.com/studio/releases/gradle-plugin)
[![AndroidX Media3](https://img.shields.io/badge/Media3-1.2.1-red.svg)](https://developer.android.com/media/media3)
[![Jetpack Compose](https://img.shields.io/badge/Compose-2024.02.00-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/minSdk-23-orange.svg)](https://developer.android.com/about/dashboards)
[![TargetSdk](https://img.shields.io/badge/targetSdk-34-purple.svg)](https://developer.android.com)

`LiveStream` 是一款专为 **Android TV / 电视机顶盒**、**智能手机** 与 **平板电脑** 量身打造的多端自适应电视直播应用。基于 **AndroidX Media3 (ExoPlayer)** 原生内核与 **Jetpack Compose** 现代响应式 UI 构建。

---

## 🛠️ 技术栈与工程架构

```
                      ┌───────────────────────┐
                      │      :app (总装)       │
                      └───────────┬───────────┘
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      ▼                           ▼                           ▼
:feature:home              :feature:player            :feature:channel
(多端自适应布局)            (OSD/手势/手柄)            (分类/抽屉/收藏)
      │                           │                           │
      └───────────────────────────┼───────────────────────────┘
                                  ▼
                            :core:ui (主题/形态)
                                  │
      ┌───────────────────────────┴───────────────────────────┐
      ▼                                                       ▼
:domain:channel (业务用例)                               :playback:playback-api (调度契约)
      │                                                       │
      ├───────────────────────────┬───────────────────────────┤
      ▼                           ▼                           ▼
:data:channel / :data:epg    :playback:playback-media3   :playback:playback-web
(Room/CNTV/XMLTV)            (ExoPlayer/Watchdog)        (合规隔离容器)
      │                           │                           │
      └───────────────────────────┴───────────────────────────┘
                                  ▼
                  :core:database / :core:network / :core:model
```

* **界面与渲染**：Jetpack Compose、Material 3、One UI 自定义组件库、Media3 UI (SurfaceView)
* **核心播放引擎**：AndroidX Media3 (ExoPlayer 1.2.1)、Playback Watchdog 自愈监控
* **依赖注入**：Dagger Hilt 2.50
* **异步并发与数据流**：Kotlin Coroutines、StateFlow、SharedFlow
* **本地持久化**：Room 2.6 (SQLite)、Jetpack Preferences DataStore
* **网络与探测**：OkHttp 4.12、Retrofit 2.9、轻量 Range GET 网络探针

---

## 📦 编译与运行

### 环境要求
* **操作系统**：Linux / macOS / Windows
* **JDK**：OpenJDK 17
* **Android SDK**：API 34 (Android 14)
* **Gradle**：8.5 (AGP 8.2.2)

### 快速构建

```bash
# 1. 编译并打包 Release APK (已集成签名与 ProGuard 混淆优化)
./gradlew assembleRelease

# 产物输出路径: app/build/outputs/apk/release/app-release.apk

# 2. 安装至连接的 Android TV 或手机
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📄 开源与免责声明

* 本项目**不提供、不分发、不托管**任何非法的第三方 IPTV 直播源、盗版音视频内容或版权保护资源。
* 用户通过自定义 M3U、订阅链接或网络文本方式导入的直播流资源，由导入者自行承担来源的合法性与合规性责任。
