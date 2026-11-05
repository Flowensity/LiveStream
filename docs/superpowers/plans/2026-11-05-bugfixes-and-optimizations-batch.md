# Comprehensive Bugfixes and Optimizations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix confirmed critical/major bugs and implement performance/architecture optimizations across `LocalImportServer`, channel selection concurrency, TV remote number key sync, EPG query streaming, database seeding overhead, and settings wiring.

**Architecture:** 
- Fix `LocalImportServer` byte-length reading in socket server.
- Streamline channel selection flow in UI so only `HomeViewModel` initiates playback and persists state.
- Coordinate `PlayerViewModel` number key input with `HomeViewModel`.
- Introduce a periodic ticker in `GetCurrentProgramUseCase` to drive time-dependent EPG queries.
- Restrict `checkAndSeedDefaultChannels` in `ChannelRepositoryImpl` to one-time initialization.
- Broaden `CntvEpgService` regex channel matching to handle HD/4K suffixes.
- Clean up `matchedSources` upon deleting custom playlist sources.
- Wire `channelSwitchTimeoutMs` from `UserPreferences` into `PlaybackOrchestrator` / `PlaybackWatchdog`.
- Reset phone orientation to `SCREEN_ORIENTATION_UNSPECIFIED` upon exiting fullscreen.
- Make `XmltvParser` thread-safe.

**Tech Stack:** Kotlin, Coroutines, Flow, Room, Jetpack Compose, OkHttp, AndroidX Media3.

## Global Constraints
- Target platform: Android TV / Mobile / Tablet (minSdk 23, targetSdk 34).
- Language: Kotlin 1.9.22, JVM 17.
- Threading: Main thread for Player state interactions; AppDispatchers (default/io) for background delays and watchdog timers.
- Clean Architecture principles: Domain layer remains pure Kotlin without Android dependencies.

## Review Focus
1. `LocalImportServer`: Chinese UTF-8 multi-byte characters must be fully received without blocking socket timeouts.
2. Channel switching: No duplicate `playChannel` calls on click or number key press; `activeSessionToken` increments exactly once.
3. TV Remote: Pressing number key '1' then DPAD Down switches to channel '2', not an arbitrary previous channel.
4. EPG Program: As time advances past a program's end time, the current program updates to the next without requiring channel switching.
5. Custom Playlist deletion: Deleting a custom playlist purges both custom channels and any injected `matchedSources` in preset channels.

---

### Task 1 (Batch 1, P0): Fix `LocalImportServer` Multi-Byte Character Read Timeout

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/server/LocalImportServer.kt:182-194`
- Test: `feature/settings/src/test/kotlin/com/livestream/feature/settings/server/LocalImportServerTest.kt` (or create test)

**Interfaces:**
- Consumes: `Socket.getInputStream()`, HTTP `Content-Length` (in bytes)
- Produces: Correct UTF-8 string payload regardless of Chinese/multi-byte characters

- [ ] **Step 1: Write unit test reproducing the multi-byte UTF-8 read truncation/timeout**
- [ ] **Step 2: Implement byte-based reading in `LocalImportServer.kt`**
  Instead of `BufferedReader.read(CharArray(contentLength))`, read exactly `contentLength` bytes from `InputStream`, then decode with `StandardCharsets.UTF_8`.
- [ ] **Step 3: Run test to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add feature/settings/src/main/kotlin/com/livestream/feature/settings/server/LocalImportServer.kt
  git commit -m "fix(settings): fix socket body read blocking on multi-byte utf8 characters in LocalImportServer"
  ```

---

### Task 2 (Batch 1, P0): Eliminate Redundant Concurrent Channel Selection Calls

**Files:**
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/HomeScreen.kt:87-90,127-130,165-168,218-221`
- Modify: `feature/channel/src/main/kotlin/com/livestream/feature/channel/ChannelViewModel.kt:98-103`
- Test: `feature/channel/src/test/kotlin/com/livestream/feature/channel/ChannelViewModelTest.kt`

**Interfaces:**
- `ChannelViewModel.selectChannel(channel: Channel)`: only closes the drawer without triggering `playbackOrchestrator.playChannel(channel)`
- `HomeScreen.kt`: `onChannelSelected = { homeViewModel.playChannel(it); channelViewModel.closeDrawer() }`

- [ ] **Step 1: Update `ChannelViewModelTest` to assert `selectChannel` closes drawer without orchestrator race**
- [ ] **Step 2: Update `ChannelViewModel.kt` and `HomeScreen.kt`**
  Remove redundant `playbackOrchestrator.playChannel(channel)` in `ChannelViewModel.selectChannel(channel)` or streamline `selectChannel` to only manage drawer state.
- [ ] **Step 3: Run test to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add feature/channel/src/main/kotlin/com/livestream/feature/channel/ChannelViewModel.kt feature/home/src/main/kotlin/com/livestream/feature/home/ui/HomeScreen.kt
  git commit -m "fix(home): eliminate duplicate concurrent playChannel invocations on channel selection"
  ```

---

### Task 3 (Batch 2, P1): Fix TV Remote Number Key Selection Sync & Persistence

**Files:**
- Modify: `feature/player/src/main/kotlin/com/livestream/feature/player/PlayerViewModel.kt:190-207`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/HomeViewModel.kt:84-126`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/HomeScreen.kt:214`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/TvLayout.kt:75`
- Test: `feature/player/src/test/kotlin/com/livestream/feature/player/PlayerViewModelTest.kt`
- Test: `feature/home/src/test/kotlin/com/livestream/feature/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `onChannelSelected: (Channel) -> Unit` in `PlayerViewModel` or callback from number input to `HomeViewModel.playChannel(channel)`
- Produces: Correct `targetChannelIndex` in `HomeViewModel` and persistence to `UserPreferencesRepository`

- [ ] **Step 1: Write test in `HomeViewModelTest` for number key channel switch synchronizing index and preference**
- [ ] **Step 2: Update `PlayerViewModel` and `HomeScreen`**
  Pass `onChannelSelected = { homeViewModel.playChannel(it) }` to `PlayerViewModel.onNumberKeyInput` or route channel confirmed event to `homeViewModel.playChannel`.
- [ ] **Step 3: Run test to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add feature/player/src/main/kotlin/com/livestream/feature/player/PlayerViewModel.kt feature/home/src/main/kotlin/com/livestream/feature/home/HomeViewModel.kt feature/home/src/main/kotlin/com/livestream/feature/home/ui/HomeScreen.kt
  git commit -m "fix(tv): synchronize targetChannelIndex and save preference on number key channel switch"
  ```

---

### Task 4 (Batch 2, P1): Fix Stale EPG Query by Adding Periodic Time Flow

**Files:**
- Modify: `domain/channel/src/main/kotlin/com/livestream/domain/channel/usecase/GetCurrentProgramUseCase.kt`
- Modify: `domain/channel/src/main/kotlin/com/livestream/domain/channel/usecase/GetNextProgramUseCase.kt`
- Test: `domain/channel/src/test/kotlin/com/livestream/domain/channel/usecase/GetCurrentProgramUseCaseTest.kt`

**Interfaces:**
- Consumes: `EpgRepository.observeCurrentProgram(channelId, currentTime)`
- Produces: Flow that emits periodically (e.g., every 30-60s) or on minute boundary with updated timestamp so Room re-queries current program as time elapses

- [ ] **Step 1: Write test verifying that `GetCurrentProgramUseCase` refreshes when time progresses past program end**
- [ ] **Step 2: Implement time ticker flow in `GetCurrentProgramUseCase` and `GetNextProgramUseCase`**
- [ ] **Step 3: Run test to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add domain/channel/src/main/kotlin/com/livestream/domain/channel/usecase/
  git commit -m "fix(epg): introduce periodic time updates in GetCurrentProgramUseCase to refresh ongoing programs"
  ```

---

### Task 5 (Batch 2, P1): Eliminate Redundant Database Seeding on Category Flow Collection

**Files:**
- Modify: `data/channel/src/main/kotlin/com/livestream/data/channel/repository/ChannelRepositoryImpl.kt:30-41,162-185`
- Test: `data/channel/src/test/kotlin/com/livestream/data/channel/ChannelRepositoryImplTest.kt`

**Interfaces:**
- `checkAndSeedDefaultChannels()`: checks if database is already seeded (`channelDao.getChannelCount() > 0`) before executing full batch inserts.
- Removes `.onStart { checkAndSeedDefaultChannels() }` from category observation flow to prevent disk I/O on every UI tab click.

- [ ] **Step 1: Write test verifying `observeChannelsByCategory` does not trigger redundant insert when channels exist**
- [ ] **Step 2: Update `ChannelRepositoryImpl.kt`**
  Guards `checkAndSeedDefaultChannels()` with atomic / count check, remove `.onStart { checkAndSeedDefaultChannels() }` from `observeChannelsByCategory`.
- [ ] **Step 3: Run test to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add data/channel/src/main/kotlin/com/livestream/data/channel/repository/ChannelRepositoryImpl.kt
  git commit -m "perf(database): eliminate redundant database seeding on category flow collection"
  ```

---

### Task 6 (Batch 2, P1): Broaden CNTV Channel Code Matching for HD / 4K Suffixes

**Files:**
- Modify: `data/epg/src/main/kotlin/com/livestream/data/epg/service/CntvEpgService.kt:28-77`
- Test: `data/epg/src/test/kotlin/com/livestream/data/epg/service/CntvEpgServiceTest.kt`

**Interfaces:**
- `resolveCntvChannelCode(channelName: String): String?`
- Supports `CCTV-1 高清`, `CCTV-2 4K超高清`, `CCTV-16 4K`, `湖南卫视 高清`, etc.

- [ ] **Step 1: Write unit tests in `CntvEpgServiceTest` covering `CCTV-1 高清`, `CCTV-2 标清`, `CCTV-16 4K超高清`, etc.**
- [ ] **Step 2: Implement fuzzy/suffix-stripping regex in `resolveCntvChannelCode`**
- [ ] **Step 3: Run test to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add data/epg/src/main/kotlin/com/livestream/data/epg/service/CntvEpgService.kt
  git commit -m "feat(epg): enhance CNTV channel code resolver to match HD, 4K and UHD suffixes"
  ```

---

### Task 7 (Batch 2, P1): Fix Orphaned Matched Sources on Custom Playlist Deletion

**Files:**
- Modify: `core/database/src/main/kotlin/com/livestream/core/database/dao/LiveSourceDao.kt`
- Modify: `data/channel/src/main/kotlin/com/livestream/data/channel/repository/ChannelRepositoryImpl.kt`
- Modify: `feature/settings/src/main/kotlin/com/livestream/feature/settings/SettingsViewModel.kt:270-277`
- Test: `feature/settings/src/test/kotlin/com/livestream/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- `LiveSourceDao.deleteSourcesByIds(ids: List<String>)` or `LiveSourceDao.deleteSourcesByPrefix(prefix: String)`
- When deleting `CustomPlaylistSource`, purge all `matchedSources` related to this import session.

- [ ] **Step 1: Write test for deleting custom playlist source also purging matched sources from preset channels**
- [ ] **Step 2: Implement matched source tracking & deletion**
- [ ] **Step 3: Run test to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add core/database/src/main/kotlin/com/livestream/core/database/dao/LiveSourceDao.kt feature/settings/src/main/kotlin/com/livestream/feature/settings/SettingsViewModel.kt
  git commit -m "fix(source): clean up matched backup sources when custom playlist source is deleted"
  ```

---

### Task 8 (Batch 3, P2): Connect Settings Channel Switch Timeout to Playback Watchdog

**Files:**
- Modify: `playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/DefaultPlaybackOrchestrator.kt`
- Modify: `playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/di/PlaybackModule.kt`
- Test: `playback/playback-media3/src/test/kotlin/com/livestream/playback/media3/PlaybackOrchestratorTest.kt`

**Interfaces:**
- `DefaultPlaybackOrchestrator`: injects `UserPreferencesRepository`, observes `userPreferences.channelSwitchTimeoutMs`, and invokes `watchdog.updateStallTimeout(timeoutMs)`.

- [ ] **Step 1: Write test asserting orchestrator watchdog timeout updates when user preference changes**
- [ ] **Step 2: Inject `UserPreferencesRepository` into `DefaultPlaybackOrchestrator` and observe timeout**
- [ ] **Step 3: Run test to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/DefaultPlaybackOrchestrator.kt
  git commit -m "feat(playback): observe and synchronize channel switch timeout from user preferences"
  ```

---

### Task 9 (Batch 3, P2): Fix Phone Orientation Lockout upon Exiting Fullscreen

**Files:**
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhoneLandscapeLayout.kt:197-200`
- Modify: `feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhonePortraitLayout.kt:180-185`

**Interfaces:**
- Replaces `ActivityInfo.SCREEN_ORIENTATION_PORTRAIT` with `ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED` (or `SCREEN_ORIENTATION_USER`) when exiting fullscreen so system sensor orientation is restored.

- [ ] **Step 1: Update `PhoneLandscapeLayout.kt` and `PhonePortraitLayout.kt`**
- [ ] **Step 2: Commit**
  ```bash
  git add feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhoneLandscapeLayout.kt feature/home/src/main/kotlin/com/livestream/feature/home/ui/PhonePortraitLayout.kt
  git commit -m "fix(ui): restore SCREEN_ORIENTATION_UNSPECIFIED when exiting phone fullscreen"
  ```

---

### Task 10 (Batch 3, P2): Make `XmltvParser` Thread-Safe and Allow LAN IPTV Streams

**Files:**
- Modify: `data/epg/src/main/kotlin/com/livestream/data/epg/parser/XmltvParser.kt`
- Modify: `source/source-custom/src/main/kotlin/com/livestream/source/custom/parser/M3uPlaylistParser.kt`
- Modify: `core/network/src/main/kotlin/com/livestream/core/network/NetworkProbe.kt`
- Test: `data/epg/src/test/kotlin/com/livestream/data/epg/parser/XmltvParserTest.kt`
- Test: `source/source-custom/src/test/kotlin/com/livestream/source/custom/M3uPlaylistParserTest.kt`

**Interfaces:**
- `XmltvParser`: uses thread-safe timestamp parsing.
- `M3uPlaylistParser`: permits local LAN addresses (`192.168.x.x`, `10.x.x.x`, `172.16-31.x.x`) for IPTV home proxies while continuing to block dangerous schemes (`file://`, `content://`, `javascript://`) and `localhost`/`127.0.0.1`.
- `NetworkProbe`: falls back to `GET` with `Range: bytes=0-0` when `HEAD` returns 405 or 403.

- [ ] **Step 1: Write test in `M3uPlaylistParserTest` validating private LAN IPTV stream URLs are accepted**
- [ ] **Step 2: Update `XmltvParser.kt`, `M3uPlaylistParser.kt`, and `NetworkProbe.kt`**
- [ ] **Step 3: Run tests to verify PASS**
- [ ] **Step 4: Commit**
  ```bash
  git add data/epg/src/main/kotlin/com/livestream/data/epg/parser/XmltvParser.kt source/source-custom/src/main/kotlin/com/livestream/source/custom/parser/M3uPlaylistParser.kt core/network/src/main/kotlin/com/livestream/core/network/NetworkProbe.kt
  git commit -m "fix(source): support private LAN IPTV streams, thread-safe XMLTV dates, and probe GET fallback"
  ```

---

### Task 11: Final Integration Verification

**Files:**
- All modified modules

- [ ] **Step 1: Check `git status` and `git diff` for cleanliness**
- [ ] **Step 2: Verify all module contracts and interfaces compile**
- [ ] **Step 3: Final check of task tracking and summary**
