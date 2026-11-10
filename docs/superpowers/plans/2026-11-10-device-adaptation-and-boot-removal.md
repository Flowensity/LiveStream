# 终端形态遥控优化与移除开机自启 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在手机与平板端删除手机扫码遥控与选台功能并保留 Android TV 支持；在全端彻底删除开机自启动功能；完成单元测试并打包构建 APK。

**Architecture:** 基于已有的 `DeviceDetector.isTv` 与 Compose `rememberDeviceType() == DeviceType.TV` 机制，在 UI 层条件化渲染「扫码遥控」卡片与二维码，在 ViewModel 层按终端形态按需启停 `LocalImportServer`；同时在 Manifest、系统广播接收器、偏好设置 DataStore、Domain/Repository 接口、ViewModel 及 Compose UI 中完整剥离 `BootReceiver` 与开机自启配置。

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Android TV Leanback, AndroidX DataStore, Dagger Hilt, JUnit4, MockK, Android Gradle Plugin.

**Spec:** [2026-11-10-device-adaptation-and-boot-removal-design.md](file:///home/flowensity/LiveStream/docs/superpowers/specs/2026-11-10-device-adaptation-and-boot-removal-design.md)

## Global Constraints

- 电视 TV 端（`DeviceType.TV`）的扫码遥控、选台、推流及遥控器按键导航必须保持 100% 完整与正常。
- 手机竖屏、手机横屏与平板端不得显示扫码遥控二维码入口，且不得启动后台无用监听服务。
- 开机自启权限 `RECEIVE_BOOT_COMPLETED` 及 `BootReceiver` 必须从全局 Manifest 与代码库中彻底清除，不留残留配置或崩溃隐患。
- 所有单元测试必须通过，无编译警告与运行时回归。

## Review Focus

1. 电视端焦点链路：在 TV 端，首个可获焦项需正确聚焦在「手机扫码遥控」卡片上；在手机/平板端，移除该卡片后，焦点与触控流应无缝回退至「导入 M3U 播放列表」卡片。
2. 二维码弹窗：手机和平板打开 `M3uImportDialog` 时，不应出现空白二维码占位或已失效的放大按钮。
3. `SettingsViewModel` 构造注入与测试兼容：构造函数中注入 `Context` 或设备判断标志位时，需保持测试用例无 context 默认场景的平滑向下兼容。
4. Manifest 清单合规：生成的 APK 中不再包含 `RECEIVE_BOOT_COMPLETED` 权限和 `BootReceiver` 声明。

---

### Task 1: 全端彻底删除开机自启功能

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Delete: `app/src/main/kotlin/com/livestream/app/receiver/BootReceiver.kt`
- Modify: `core/model/src/main/kotlin/com/livestream/core/model/UserPreferences.kt`
- Modify: `core/datastore/src/main/kotlin/com/livestream/core/datastore/UserPreferencesDataStore.kt`
- Modify: `domain/channel/src/main/kotlin/com/livestream/domain/channel/repository/UserPreferencesRepository.kt`
- Modify: `data/channel/src/main/kotlin/com/livestream/data/channel/repository/UserPreferencesRepositoryImpl.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/SettingsUiState.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsComponents.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsDialog.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/HomeScreen.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhonePortraitLayout.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhoneLandscapeLayout.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/TabletDualPaneLayout.kt`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/TvLayout.kt`

**Interfaces:**
- Consumes: None
- Produces: 移除 `isBootStartupEnabled` 属性、`setBootStartupEnabled` 方法及 `onToggleBootStartup` 界面回调。

- [ ] **Step 1: 从 AndroidManifest.xml 移除权限与接收器声明并删除 BootReceiver.kt**
  - 移除 `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`
  - 移除 `<receiver android:name=".receiver.BootReceiver" ... />`
  - 删除 `app/src/main/kotlin/com/livestream/app/receiver/BootReceiver.kt`
- [ ] **Step 2: 从 Core/Domain/Data 层清理 UserPreferences 中的 bootStartup**
  - 在 `core/model/src/main/kotlin/com/livestream/core/model/UserPreferences.kt` 移除 `isBootStartupEnabled`
  - 在 `core/datastore/src/main/kotlin/com/livestream/core/datastore/UserPreferencesDataStore.kt` 移除 `BOOT_STARTUP_ENABLED`、`isBootStartupEnabled` 映射及 `setBootStartupEnabled`
  - 在 `domain/channel/src/main/kotlin/com/livestream/domain/channel/repository/UserPreferencesRepository.kt` 移除 `setBootStartupEnabled`
  - 在 `data/channel/src/main/kotlin/com/livestream/data/channel/repository/UserPreferencesRepositoryImpl.kt` 移除 `setBootStartupEnabled`
- [ ] **Step 3: 从 Feature Settings 及 Home UI 中移除开机自启动开关与回调链路**
  - 在 `feature/settings/src/main/kotlin/com/livestream/feature/settings/SettingsUiState.kt` 移除 `isBootStartupEnabled`
  - 在 `feature/settings/src/main/kotlin/com/livestream/feature/settings/SettingsViewModel.kt` 移除 `isBootStartupEnabled` 与 `setBootStartupEnabled`
  - 在 `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsComponents.kt` 移除「系统设置 - 开机自启动」卡片及 `onToggleBootStartup`
  - 在 `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsDialog.kt` 移除 `onToggleBootStartup`
  - 在 `feature/home/src/main/kotlin/com/livestream/feature/home/ui/` 各布局文件（`HomeScreen.kt`, `PhonePortraitLayout.kt`, `PhoneLandscapeLayout.kt`, `TabletDualPaneLayout.kt`, `TvLayout.kt`）移除 `onToggleBootStartup`
- [ ] **Step 4: 运行单元测试验证编译与测试通过**
  - 运行: `export JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9; export PATH=$JAVA_HOME/bin:$PATH; ./gradlew testDebugUnitTest`
  - 预期: BUILD SUCCESSFUL
- [ ] **Step 5: 提交改动**
  - `git commit -m "feat(settings): completely remove boot startup autostart feature from all platforms"`

---

### Task 2: 手机/平板端移除扫码遥控与选台功能（仅保留 TV 端）

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsComponents.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/M3uImportDialog.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/ui/SettingsDialog.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/SettingsViewModel.kt`
- Test: `feature/settings/src/test/kotlin/com/livestream/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `DeviceDetector.isTv(context: Context)` 与 `rememberDeviceType() == DeviceType.TV`
- Produces: `SettingsContent(..., isTv: Boolean)` 与 `M3uImportDialog(..., isTv: Boolean)`

- [ ] **Step 1: 在 SettingsViewModel 中适配终端形态按需启动 LocalImportServer**
  - 支持判断设备形态：注入 `@ApplicationContext context: Context?` 或提供 `isTvDevice: Boolean` 属性（默认通过 `DeviceDetector.isTv` 判断，若为 null 保持测试兼容）；
  - 当且仅当 `isTvDevice == true` 时在初始化/打开设置时调用 `ensureLocalServerStarted()`，非 TV 端不启动端口监听；
  - 编写并更新 `SettingsViewModelTest.kt`，验证 non-TV 模式下不启动 local server。
- [ ] **Step 2: 在 SettingsContent 与 SettingsDialog 中根据 isTv 隐藏手机扫码遥控卡片**
  - `SettingsContent` 接收 `isTv: Boolean`；
  - 当 `isTv == true` 时，展示置顶的「手机扫码遥控与选台」卡片并将首项焦点赋给它；
  - 当 `isTv == false` 时，不渲染该卡片，并将「导入 M3U 播放列表」副标题显示为「网络链接 / 文本导入」；首项焦点赋给首个可用项；
  - `SettingsDialog` 与 `SettingsDrawerPanel` 将 `isTv` 正确传递至 `SettingsContent`。
- [ ] **Step 3: 在 M3uImportDialog 中根据 isTv 隐藏二维码推流区域**
  - `M3uImportDialog` 增加 `isTv: Boolean` 参数；
  - 仅当 `isTv && uiState.qrBitmap != null && uiState.localServerUrl != null` 时渲染「局域网扫码推流」二维码区域及放大按钮；手机和平板下直接展示链接与文本输入框；
  - `SettingsDialog` 在调用 `M3uImportDialog` 时传入 `isTv = isTv`。
- [ ] **Step 4: 运行 settings 模块与全工程单元测试验证**
  - 运行: `export JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9; export PATH=$JAVA_HOME/bin:$PATH; ./gradlew :feature:settings:testDebugUnitTest testDebugUnitTest`
  - 预期: BUILD SUCCESSFUL
- [ ] **Step 5: 提交改动**
  - `git commit -m "feat(settings): restrict qr remote control and channel selection to android tv only"`

---

### Task 3: 整体回归测试与生成 APK

**Files:**
- Output: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: 运行全量单元测试**
  - 运行: `export JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9; export PATH=$JAVA_HOME/bin:$PATH; ./gradlew testDebugUnitTest`
  - 预期: BUILD SUCCESSFUL
- [ ] **Step 2: 运行 assembleDebug 构建 APK**
  - 运行: `export JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9; export PATH=$JAVA_HOME/bin:$PATH; ./gradlew assembleDebug`
  - 预期: BUILD SUCCESSFUL
- [ ] **Step 3: 验证 APK 产物与清单权限**
  - 检查产物文件是否存在：`ls -lh app/build/outputs/apk/debug/app-debug.apk`
  - 验证构建出的 APK 不包含 `RECEIVE_BOOT_COMPLETED` 权限声明。
