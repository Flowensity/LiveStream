# M3U 节目同名聚合与画质测速优选 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现第三方 M3U 播放列表中同名频道的线路聚合与标签识别，融入系统内置频道线路避免重复条目膨胀，并在播放时优选高分辨率与低时延线路，最后完成测试与 APK 构建。

**Architecture:** 
1. 在 `M3uPlaylistParser` 中实现单遍单 M3U 列表内同名频道的聚合、URL 去重、分辨率与网络运营商标签抽取、以及线路优先级赋分。
2. 在 `SettingsViewModel` 中根据方案 A 实现分流：匹配内置系统频道时仅追加线路并不新建多余频道，未匹配项正常建立聚合频道。
3. 在 `DefaultPlaybackOrchestrator` 中增强线路排序逻辑：综合健康状态、原生协议、画质分级与网络实测时延进行动态优选。

**Tech Stack:** Kotlin, Room, Coroutines, Media3 ExoPlayer, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-10-m3u-duplicate-aggregation-and-quality-priority-design.md`

## Global Constraints
- 保证 M3U 解析性能，避免多次冗余全量正则匹配。
- 保证对原有安全 URL 校验与 EPG URL 提取策略的向后兼容。
- 确保订阅源删除/刷新时能精准清理级联生成的 `channelIds` 与 `matchedSourceIds`。

## Review Focus
1. 包含多种噪音后缀的同名频道（如 `CCTV-1 综合 [4K 50fps]` 与 `CCTV-1 高清`）能正确聚合为同一个 `CCTV-1`。
2. 同一频道下完全相同的播放 URL 能够被自动去重，不生成重复线路。
3. 匹配到内置频道（如 `cctv_600001859`）后，`channelRepository.saveChannels` 中不再含有该自定义频道的重复记录。
4. 4K 高清线路在健康或未知状态下优先于 1080P/720P 启动；若 4K 出现严重降级（>3000ms 或不可用），自动回退至流畅优质线路。
5. 全模块单元测试通过且 `./gradlew assembleDebug` 成功产出 APK。

---

### Task 1: M3U 解析层同名聚合与画质标签提取 (`M3uPlaylistParser`)

**Files:**
- Modify: `source/source-custom/src/main/kotlin/com/livestream/source/custom/parser/M3uPlaylistParser.kt`
- Test: `source/source-custom/src/test/kotlin/com/livestream/source/custom/M3uPlaylistParserTest.kt`

**Interfaces:**
- Consumes: `ParsedPlaylist`, `Channel`, `LiveSource`, `ExtInfAttributes`
- Produces: `M3uPlaylistParser.parse(Reader): ParsedPlaylist` (输出频道已在组内同名聚合，单频道含多 LiveSource，按分辨率赋权优先级)

- [ ] **Step 1: Write the failing tests in `M3uPlaylistParserTest.kt`**
  - 测试用例 1：包含 3 条不同分辨率与不同运营商后缀的 `CCTV-1`，验证解析后只产出 1 个 `Channel`，3 条 `LiveSource`，且线路名称包含识别出的标签，分辨率高的线路 priority 更高。
  - 测试用例 2：包含重复 URL 的情况，验证自动去重。

- [ ] **Step 2: Run test to verify it fails**
  - Run: `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew :source:source-custom:testDebugUnitTest --tests com.livestream.source.custom.M3uPlaylistParserTest`
  - Expected: FAIL

- [x] **Step 3: Implement channel aggregation & tag extraction in `M3uPlaylistParser.kt`**
  - 清洗频道名称中的画质噪音（如 `[4K]`、`[1080P]` 等），避免标题冗余。
  - **杜绝基于文本命名给出虚假分辨率**：文本命名常含夸大与假清晰度，解析层不赋予虚假分辨率加权，仅提取运营商与传输标签（电信、联通、移动、IPv6 等）。
  - 真正分辨率由后续真实探测（HLS Manifest `RESOLUTION` 属性）与硬件解码（ExoPlayer `onVideoSizeChanged`）实时获取。
  - 维护同组内同名聚合映射字典，同名复用同一个 `Channel`，线路作为 `LiveSource` 挂载，URL 去重。

- [ ] **Step 4: Run test to verify it passes**
  - Run: `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew :source:source-custom:testDebugUnitTest --tests com.livestream.source.custom.M3uPlaylistParserTest`
  - Expected: PASS

- [ ] **Step 5: Commit**
  - `git add source/source-custom/`
  - `git commit -m "feat(source-custom): aggregate duplicate M3U channels into multi-line sources with quality tags"`

---

### Task 2: 方案 A 融入系统内置频道分流 (`SettingsViewModel`)

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/SettingsViewModel.kt`
- Test: `feature/settings/src/test/kotlin/com/livestream/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `ChannelMatchIndex`, `ParsedPlaylist`, `ChannelRepository`, `CategoryRepository`
- Produces: `SettingsViewModel.importM3uInternal` 分流保存逻辑（已匹配者仅增补线路，不保存自定义频道实体）

- [ ] **Step 1: Write/Update the test in `SettingsViewModelTest.kt`**
  - 验证当导入内容与已存在的频道同名时（例如 `CCTV-1`），不会保存新的自定义频道到 `channelRepository`，而只会将线路绑定至已存在的频道 ID。
  - 验证未匹配的频道正常保存自定义频道及对应线路。

- [ ] **Step 2: Run test to verify it fails**
  - Run: `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew :feature:settings:testDebugUnitTest --tests com.livestream.feature.settings.SettingsViewModelTest`
  - Expected: FAIL

- [ ] **Step 3: Implement Scheme A diversion in `SettingsViewModel.kt`**
  - 分离 `channelsToSave`（仅未匹配项）与 `matchedSources`。
  - 只对未匹配项执行 `channelRepository.saveChannels(channelsToSave)`。
  - `matchedSources` 继承高分辨率权重与内置频道优先级。

- [ ] **Step 4: Run test to verify it passes**
  - Run: `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew :feature:settings:testDebugUnitTest --tests com.livestream.feature.settings.SettingsViewModelTest`
  - Expected: PASS

- [ ] **Step 5: Commit**
  - `git add feature/settings/`
  - `git commit -m "feat(settings): merge matched M3U channels into built-in channel sources without duplicates"`

---

### Task 3: 播放时高分辨率与低时延优选排序 (`DefaultPlaybackOrchestrator`)

**Files:**
- Modify: `playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/DefaultPlaybackOrchestrator.kt`
- Test: `playback/playback-media3/src/test/kotlin/com/livestream/playback/media3/PlaybackOrchestratorTest.kt`

**Interfaces:**
- Consumes: `LiveSource.priority`, `SourceHealthStatus`, `sourceLatencyCache`
- Produces: `sortSourcesByHealthAndLatency` 高画质与低时延优选排序

- [ ] **Step 1: Write the failing tests in `PlaybackOrchestratorTest.kt`**
  - 测试用例 1：同为健康状态下，高分辨率线路（高 priority 权重）优先于低分辨率线路。
  - 测试用例 2：同分辨率下，已测速且低延迟线路（如 120ms）优先于高延迟线路（如 1800ms）。
  - 测试用例 3：高分辨率线路若处于 DEGRADED 状态，健康的高清/标清线路优先选播。

- [ ] **Step 2: Run test to verify it fails**
  - Run: `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew :playback:playback-media3:testDebugUnitTest --tests com.livestream.playback.media3.PlaybackOrchestratorTest`
  - Expected: FAIL

- [ ] **Step 3: Implement resolution & latency priority sorting in `DefaultPlaybackOrchestrator.kt`**
  - 优化 `sortSourcesByHealthAndLatency`，结合健康梯队、原生音视频流协议、画质分级与测速时延进行综合打分与排序。

- [ ] **Step 4: Run test to verify it passes**
  - Run: `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew :playback:playback-media3:testDebugUnitTest --tests com.livestream.playback.media3.PlaybackOrchestratorTest`
  - Expected: PASS

- [ ] **Step 5: Commit**
  - `git add playback/playback-media3/`
  - `git commit -m "feat(playback): prioritize high resolution and low latency sources in playback orchestrator"`

---

### Task 4: 全量测试验证与生成 APK

**Files:**
- 全模块自动化验证与打包构建

- [ ] **Step 1: Run full unit test suite**
  - Run: `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew testDebugUnitTest`
  - Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Build Debug APK**
  - Run: `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew assembleDebug`
  - Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify APK artifact**
  - 检查生成的 APK 路径（`app/build/outputs/apk/debug/app-debug.apk`）与文件信息。

- [ ] **Step 4: Commit and finalize**
  - `git commit -m "chore: complete M3U duplicate aggregation and quality priority build"`
