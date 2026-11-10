# 终端形态遥控优化与移除开机自启 设计方案

## 需求背景与目标
1. **终端形态优化**：如果是手机或平板设备，删除手机扫码遥控与选台功能；仅在 Android TV 智能电视端保留该功能。
2. **移除开机自启动**：彻底删除所有端的开机自启功能及相关代码、权限与广播接收器。
3. **验证与交付**：完成上述功能优化后进行单元测试，并构建生成 APK 安装包。

## 详细设计

### 1. 手机与平板端删除扫码遥控与选台功能（仅 TV 保留）
- **设备类型检测**：
  - 复用现有 `DeviceDetector.isTv` 与 Compose `rememberDeviceType() == DeviceType.TV` 机制。
- **UI 呈现控制**：
  - `SettingsContent`：增加 `isTv: Boolean` 参数。当 `isTv == false`（手机竖屏、手机横屏、平板）时，完全隐藏「手机扫码遥控」卡片；将「导入 M3U 播放列表」卡片副标题从“局域网扫码推送 / 网络链接”调整为“网络链接 / 文本导入”。
  - `M3uImportDialog`：增加 `isTv: Boolean` 参数。当 `isTv == false` 时，不展示顶部的局域网二维码推流区域及放大按钮，仅提供直观的链接/文本输入与确认操作。
- **后台常驻服务优化**：
  - `SettingsViewModel` 初始化及调用时，检测当前设备是否为 Android TV；
  - 非 TV 端（手机/平板）不拉起 `LocalImportServer` 端口监听，避免移动端多余的电量消耗及网络端口暴露；TV 端保持启动供手机扫码遥控与推流。

### 2. 全端删除开机自启动功能
- **清单与权限**：
  - 从 `app/src/main/AndroidManifest.xml` 中移除 `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`。
  - 从 `AndroidManifest.xml` 中移除 `<receiver android:name=".receiver.BootReceiver" ... />`。
- **广播组件清理**：
  - 删除文件 `app/src/main/kotlin/com/livestream/app/receiver/BootReceiver.kt`。
- **UI 与偏好设置清理**：
  - 从 `SettingsComponents.kt` 中删除「系统设置 - 开机自启动」切换卡片；
  - 清理 `SettingsContent`、`SettingsDialog`、`SettingsDrawerPanel`、`HomeScreen`、`PhonePortraitLayout`、`PhoneLandscapeLayout`、`TabletDualPaneLayout`、`TvLayout` 中传递的 `onToggleBootStartup` 回调。
- **数据与领域模型清理**：
  - 从 `UserPreferences` 中删除 `isBootStartupEnabled` 字段；
  - 从 `UserPreferencesDataStore`、`UserPreferencesRepository`、`UserPreferencesRepositoryImpl`、`SettingsUiState`、`SettingsViewModel` 中移除所有 `bootStartup` 相关的存储、流读取和设置方法。

### 3. 测试与构建
- 运行 `./gradlew testDebugUnitTest` 验证所有模块单元测试通过；
- 运行 `./gradlew assembleDebug` 构建 APK，输出位于 `app/build/outputs/apk/debug/`。
