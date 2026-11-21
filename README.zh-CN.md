# LiveStream - Android 多端电视直播 App

<p align="left">
  <a href="README.md"><img src="https://img.shields.io/badge/Language-English-lightgrey.svg" alt="English"></a>
  <a href="README.zh-CN.md"><img src="https://img.shields.io/badge/当前语言-简体中文-blue.svg" alt="简体中文"></a>
</p>

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-8.2.2-green.svg)](https://developer.android.com/studio/releases/gradle-plugin)
[![Media3](https://img.shields.io/badge/Media3-1.2.1-red.svg)](https://developer.android.com/media/media3)
[![Compose](https://img.shields.io/badge/Compose-2024.02.00-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/minSdk-23-orange.svg)](https://developer.android.com/about/dashboards)
[![TargetSdk](https://img.shields.io/badge/targetSdk-34-purple.svg)](https://developer.android.com)

[English](README.md) | **简体中文**

`LiveStream` 是一款专为 **Android TV / 电视机顶盒**、**智能手机** 与 **平板电脑** 量身打造的多端自适应电视直播应用，基于 **AndroidX Media3 (ExoPlayer)** 原生内核与 **Jetpack Compose** 现代响应式 UI 构建。

> **说明：** 本项目全程由 AI 编写。

---

## ✨ 核心特性

* **多端自适应界面**：针对 Android TV（遥控器 D-pad 焦点导航）、平板（双栏分屏）、手机竖屏与手机横屏分别定制交互布局。
* **综合播放菜单**：集成信号源/线路切换、画面比例调节（原始比例 / 16:9 / 4:3 / 裁剪全屏）与今日完整节目单；手机横屏下采用左右对称双栏布局，兼顾操控与浏览体验。
* **M3U 多源聚合与智能画质优选**：自动聚合 M3U 中的同名频道为多线路源，支持匹配并入内置频道；依据实测解码/探测分辨率与网络延迟自动优选最佳线路。
* **EPG 电子节目单**：支持 CNTV 与 XMLTV 格式节目单解析与本地缓存，实时高亮当前正在播出的节目。
* **局域网扫码导入与遥控器（Android TV 专属）**：内置本地 HTTP 服务，支持手机扫码向电视导入 M3U 播放列表或进行网页端遥控操作。
* **高可用播放保障**：集成 Playback Watchdog 自愈监控、IPv4 优先 DNS 解析、软硬解切换、SurfaceView/TextureView 切换、手势调节（亮度/音量/双击切源）与画中画（PiP）模式。

---

## 🛠️ 技术栈与工程架构

```text
                      ┌───────────────────────┐
                      │      :app (总装)       │
                      └───────────┬───────────┘
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      ▼                           ▼                           ▼
:feature:home              :feature:player            :feature:channel
(多端自适应布局)            (OSD/手势/节目单)          (分类/抽屉/收藏)
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
(Room/CNTV/XMLTV)            (ExoPlayer/Watchdog)        (合规网页容器)
      │                           │                           │
      └───────────────────────────┴───────────────────────────┘
                                  ▼
              :core:database / :core:network / :core:datastore / :core:model
```

* **界面与渲染**：Jetpack Compose、Material 3、One UI 自定义主题、Compose for TV、Media3 UI
* **核心播放引擎**：AndroidX Media3 (ExoPlayer 1.2.1)、Playback Watchdog 自愈监控
* **依赖注入**：Dagger Hilt 2.50
* **异步并发与数据流**：Kotlin Coroutines、StateFlow、SharedFlow
* **本地持久化**：Room 2.6.1 (SQLite)、Jetpack Preferences DataStore
* **网络与探测**：OkHttp 4.12、Retrofit 2.9、IPv4 优先 DNS、轻量 Range GET 网络探针

---

## 📦 编译与运行

### 环境要求
* **操作系统**：Linux / macOS / Windows
* **JDK**：OpenJDK 17
* **Android SDK**：API 34 (Android 14)
* **Gradle**：8.5 (AGP 8.2.2)

### 快速构建

```bash
# 1. 运行单元测试
./gradlew testDebugUnitTest

# 2. 编译并打包 Release APK (已集成 R8 混淆与资源压缩)
./gradlew assembleRelease
# 产物输出路径: app/build/outputs/apk/release/app-release.apk

# 3. 安装至连接的 Android TV 或手机
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📄 开源与免责声明

* 本项目**不提供、不分发、不托管**任何非法的第三方 IPTV 直播源、盗版音视频内容或版权保护资源。
* 用户通过自定义 M3U、订阅链接或网络文本方式导入的直播流资源，由导入者自行承担来源的合法性与合规性责任。
