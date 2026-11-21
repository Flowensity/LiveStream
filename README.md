# LiveStream - Multi-Device Android Live TV App

<p align="left">
  <a href="README.md"><img src="https://img.shields.io/badge/Language-English-blue.svg" alt="English"></a>
  <a href="README.zh-CN.md"><img src="https://img.shields.io/badge/切换语言-简体中文-brightgreen.svg" alt="简体中文"></a>
</p>

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-8.2.2-green.svg)](https://developer.android.com/studio/releases/gradle-plugin)
[![Media3](https://img.shields.io/badge/Media3-1.2.1-red.svg)](https://developer.android.com/media/media3)
[![Compose](https://img.shields.io/badge/Compose-2024.02.00-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/minSdk-23-orange.svg)](https://developer.android.com/about/dashboards)
[![TargetSdk](https://img.shields.io/badge/targetSdk-34-purple.svg)](https://developer.android.com)

**English** | [简体中文](README.zh-CN.md)

`LiveStream` is a multi-device adaptive live TV streaming application built for **Android TV / Set-Top Boxes**, **Smartphones**, and **Tablets**, powered by **AndroidX Media3 (ExoPlayer)** and **Jetpack Compose**.

---

## ✨ Key Features

* **Adaptive Multi-Device UI**: Tailored layouts for Android TV (D-pad focus navigation), Tablet (dual-pane), Phone Portrait, and Phone Landscape.
* **Unified Playback Menu**: Quick source/line switching, aspect ratio modes (`Original`, `16:9`, `4:3`, `Crop Fullscreen`), and full-day EPG schedule — featuring a symmetric dual-column layout in mobile landscape mode.
* **M3U Multi-Source Aggregation & Smart Quality Priority**: Automatically aggregates duplicate M3U channel entries into multi-line sources, merges matching built-in channels, and prioritizes streams by decoded/probed resolution and low latency.
* **EPG Program Guide**: Supports CNTV & XMLTV EPG parsing and local caching, with live program tracking.
* **LAN Web Remote & QR Import (Android TV)**: Built-in local HTTP server for scanning a QR code from your phone to import M3U playlists or use a web-based remote control on Android TV.
* **Playback Reliability**: Integrated Playback Watchdog, IPv4-first DNS resolution, hardware/software decoder toggle, SurfaceView/TextureView switching, touch gestures (brightness, volume, double-tap source switch), and Picture-in-Picture (PiP) mode.

---

## 🛠️ Architecture & Tech Stack

```text
                      ┌───────────────────────┐
                      │      :app (Entry)     │
                      └───────────┬───────────┘
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      ▼                           ▼                           ▼
:feature:home              :feature:player            :feature:channel
(Device Layouts)           (OSD/Gestures/EPG)         (Drawer/Favorites)
      │                           │                           │
      └───────────────────────────┼───────────────────────────┘
                                  ▼
                            :core:ui (Theme/Device)
                                  │
      ┌───────────────────────────┴───────────────────────────┐
      ▼                                                       ▼
:domain:channel (Use Cases)                              :playback:playback-api
      │                                                       │
      ├───────────────────────────┬───────────────────────────┤
      ▼                           ▼                           ▼
:data:channel / :data:epg    :playback:playback-media3   :playback:playback-web
(Room/CNTV/XMLTV)            (ExoPlayer/Watchdog)        (Web Fallback)
      │                           │                           │
      └───────────────────────────┴───────────────────────────┘
                                  ▼
              :core:database / :core:network / :core:datastore / :core:model
```

* **UI & Rendering**: Jetpack Compose, Material 3, Custom One UI Theme, Compose for TV, Media3 UI
* **Playback Engine**: AndroidX Media3 (ExoPlayer 1.2.1), Playback Watchdog
* **Dependency Injection**: Dagger Hilt 2.50
* **Concurrency & State**: Kotlin Coroutines, StateFlow, SharedFlow
* **Persistence**: Room 2.6.1 (SQLite), Jetpack Preferences DataStore
* **Networking**: OkHttp 4.12, Retrofit 2.9, IPv4-first DNS, Lightweight Range-GET Probe

---

## 📦 Build & Run

### Prerequisites
* **OS**: Linux / macOS / Windows
* **JDK**: OpenJDK 17
* **Android SDK**: API 34 (Android 14)
* **Gradle**: 8.5 (AGP 8.2.2)

### Quick Build

```bash
# 1. Run unit tests
./gradlew testDebugUnitTest

# 2. Build Release APK (with R8 minification and resource shrinking)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# 3. Install onto a connected Android TV or phone
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📄 Disclaimer

* This project **does not provide, distribute, or host** any third-party IPTV streams, pirated media, or copyrighted content.
* Users are solely responsible for the legality and compliance of any M3U playlists, subscription URLs, or custom stream sources they import.
