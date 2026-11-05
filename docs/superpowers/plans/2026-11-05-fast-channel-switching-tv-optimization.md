# Fast Channel Switching Optimization on Android TV Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the ~10-second channel switching lag on Android TV (MT9679) by optimizing watchdog stall timeouts, HTTP connect/read timeouts, initial buffer playback thresholds, and fallback switching delays in the Media3 playback engine.

**Architecture:** Refine playback timing controls across the `playback-media3` layer: update `PlaybackWatchdog` default timeout to 2500ms with runtime mutation support, tune `Media3PlaybackEngine`'s `DefaultLoadControl` (250ms buffer for playback) and `DefaultHttpDataSource` (3s connect, 4s read timeouts), and decrease source fallback delay in `DefaultPlaybackOrchestrator` to 100ms.

**Tech Stack:** Kotlin, AndroidX Media3 (ExoPlayer 1.2.1), Kotlin Coroutines, Flow, JUnit4, MockK.

**Spec:** [docs/superpowers/specs/2026-11-05-fast-channel-switching-tv-optimization-design.md](file:///home/flowensity/LiveStream/docs/superpowers/specs/2026-11-05-fast-channel-switching-tv-optimization-design.md)

## Global Constraints
- Target platform: Android TV / Mobile / Tablet (minSdk 23, targetSdk 34).
- Language: Kotlin 1.9.22, JVM 17.
- Threading: Main thread for Player state interactions; AppDispatchers (default/io) for background delays and watchdog timers.
- Preserve backward compatibility and stability across all existing unit tests in `:playback:playback-media3`.

## Review Focus
1. Watchdog timeout update while a timer is actively running: verify updating timeout reschedules or takes effect safely without leaks.
2. Rapid channel switching (channel flipping): verify shorter timeouts do not cause premature source switching on high-latency but functional streams.
3. Stream reconnection after network drop: verify self-healing continues to work with reduced timeouts.
4. Error classification: verify HTTP 3s/4s timeout errors continue to map cleanly to `NETWORK_TIMEOUT` via `Media3ErrorClassifier`.
5. Null or 0ms custom timeout input: verify coerced bounds (minimum 1000ms) prevent busy-spin watchdog loops.

---

### Task 1: Optimize PlaybackWatchdog Timeout and Add Dynamic Configuration

**Files:**
- Modify: `playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/watchdog/PlaybackWatchdog.kt`
- Test: `playback/playback-media3/src/test/kotlin/com/livestream/playback/media3/watchdog/PlaybackWatchdogTest.kt`

**Interfaces:**
- Produces: `PlaybackWatchdog.updateStallTimeout(timeoutMs: Long)`
- Default constant: `DEFAULT_STALL_TIMEOUT_MS = 2500L`

- [ ] **Step 1: Write the failing tests for dynamic timeout and new default timeout**

In `PlaybackWatchdogTest.kt`, add:
```kotlin
@Test
fun `default stall timeout constant is 2500ms`() {
    assertEquals(2500L, PlaybackWatchdog.DEFAULT_STALL_TIMEOUT_MS)
}

@Test
fun `updateStallTimeout changes timeout for subsequent buffering events`() = runTest(testDispatcher) {
    val dynamicWatchdog = PlaybackWatchdog(
        dispatchers = testDispatchers,
        stallTimeoutMs = 5000L,
        onStallTimeout = { stallTriggerCount++ }
    )
    dynamicWatchdog.start(statusFlow)
    dynamicWatchdog.updateStallTimeout(1500L)
    statusFlow.value = PlaybackStatus.BUFFERING
    testScheduler.runCurrent()

    advanceTimeBy(1000L)
    assertEquals(0, stallTriggerCount)

    advanceTimeBy(600L)
    assertEquals(1, stallTriggerCount)
    dynamicWatchdog.stop()
}

@Test
fun `updateStallTimeout enforces minimum threshold of 1000ms`() {
    val dynamicWatchdog = PlaybackWatchdog(
        dispatchers = testDispatchers,
        stallTimeoutMs = 5000L,
        onStallTimeout = {}
    )
    dynamicWatchdog.updateStallTimeout(200L)
    assertEquals(1000L, dynamicWatchdog.currentStallTimeoutMs)
}
```

- [ ] **Step 2: Run test to verify it fails**

Verify test fails because `DEFAULT_STALL_TIMEOUT_MS` is currently `10000L` and `updateStallTimeout` does not yet exist.

- [ ] **Step 3: Implement dynamic timeout in `PlaybackWatchdog.kt`**

- Update `DEFAULT_STALL_TIMEOUT_MS = 2500L`.
- Add `private var _stallTimeoutMs: Long` with getter `val currentStallTimeoutMs: Long get() = _stallTimeoutMs`.
- Add `fun updateStallTimeout(timeoutMs: Long)` coercing minimum to `1000L`.
- If currently buffering when timeout is updated, restart `stallTimerJob` with the remaining or updated duration.

- [ ] **Step 4: Run test to verify it passes**

Confirm all tests in `PlaybackWatchdogTest` pass.

- [ ] **Step 5: Commit**

```bash
git add playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/watchdog/PlaybackWatchdog.kt playback/playback-media3/src/test/kotlin/com/livestream/playback/media3/watchdog/PlaybackWatchdogTest.kt
git commit -m "perf: reduce default watchdog timeout to 2500ms and add dynamic timeout update"
```

---

### Task 2: Tune Media3PlaybackEngine Buffers and Network Timeouts

**Files:**
- Modify: `playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/Media3PlaybackEngine.kt:60-77`

**Interfaces:**
- Consumes: `DefaultLoadControl.Builder`, `DefaultHttpDataSource.Factory`
- Produces: Low-latency buffering configuration (`bufferForPlaybackMs = 250`) and accelerated network timeout parameters (`connectTimeoutMs = 3000`, `readTimeoutMs = 4000`).

- [ ] **Step 1: Check existing Media3PlaybackEngine load control and data source initialization**

Examine lines 60-77 in `Media3PlaybackEngine.kt`.

- [ ] **Step 2: Update `Media3PlaybackEngine.kt`**

Update `createExoPlayer()`:
```kotlin
// 2. 结合低延迟直播特点的精细化缓冲控制（首帧极速起播优化）
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 1500,
        /* maxBufferMs = */ 5000,
        /* bufferForPlaybackMs = */ 250, // 调优：从 500ms 降至 250ms，显著加快首帧出图速度
        /* bufferForPlaybackAfterRebufferMs = */ 1000
    )
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()

// 3. 定制 HTTP 数据源工厂（缩短握手超时，从 8s 降至 3s，快速剔除死链与避免 IPv6 假死挂起）
val httpDataSourceFactory = DefaultHttpDataSource.Factory()
    .setConnectTimeoutMs(3000)
    .setReadTimeoutMs(4000)
    .setAllowCrossProtocolRedirects(true)
```

- [ ] **Step 3: Commit**

```bash
git add playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/Media3PlaybackEngine.kt
git commit -m "perf: tune playback load control to 250ms and http timeouts to 3000ms"
```

---

### Task 3: Optimize DefaultPlaybackOrchestrator Fallback Delay and Dynamic Watchdog Config

**Files:**
- Modify: `playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/DefaultPlaybackOrchestrator.kt`
- Test: `playback/playback-media3/src/test/kotlin/com/livestream/playback/media3/PlaybackOrchestratorTest.kt`

**Interfaces:**
- Consumes: `watchdog.updateStallTimeout(Long)`
- Modifies: `handlePlaybackFailure` delay (from 300ms to 100ms)

- [ ] **Step 1: Write test for rapid fallback source switching**

In `PlaybackOrchestratorTest.kt`, verify that fallback to backup source occurs smoothly within the reduced delay window:
```kotlin
@Test
fun `test fallback to backup source uses 100ms rapid retry delay`() = runTest(testDispatcher) {
    coEvery { channelRepository.getSourcesForChannelSync("cctv1") } returns listOf(sampleSource1, sampleSource2)

    val orchestrator = DefaultPlaybackOrchestrator(engine, channelRepository, sourceAdapterRegistry, webPlaybackController, networkMonitor, latencyTracker, testDispatchers)
    orchestrator.playChannel(sampleChannel1)
    advanceUntilIdle()

    // 模拟首选源发生错误
    engineStateFlow.value = EngineState.Error(PlaybackErrorInfo(PlaybackErrorType.NETWORK_TIMEOUT, "超时", true))
    testScheduler.runCurrent()

    // 推进 100ms 快速切至备用源
    advanceTimeBy(150L)
    advanceUntilIdle()

    assertEquals("src_2", orchestrator.orchestratorState.value.currentSource?.id)
}
```

- [ ] **Step 2: Update `DefaultPlaybackOrchestrator.kt`**

1. In `handlePlaybackFailure`:
   Change:
   ```kotlin
   delay(300) // 平滑延时避免突发震荡
   ```
   To:
   ```kotlin
   delay(100) // 调优：由 300ms 降至 100ms 快速换线切换备用源
   ```
2. Expose `fun updateStallTimeout(timeoutMs: Long)` on `DefaultPlaybackOrchestrator` delegating to `watchdog.updateStallTimeout(timeoutMs)`.

- [ ] **Step 3: Run tests in `PlaybackOrchestratorTest` to verify PASS**

- [ ] **Step 4: Commit**

```bash
git add playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/DefaultPlaybackOrchestrator.kt playback/playback-media3/src/test/kotlin/com/livestream/playback/media3/PlaybackOrchestratorTest.kt
git commit -m "perf: reduce fallback retry delay to 100ms and support dynamic watchdog timeout"
```

---

### Task 4: Comprehensive Review and Final Verification

**Files:**
- Review: `playback/playback-media3/src/main/kotlin/com/livestream/playback/media3/**`
- Verify: git diff cleanliness, adherence to architectural contracts, absence of regressions.

- [ ] **Step 1: Review git diff across all modified files**

Run `git diff` and verify that only intended performance tuning changes are present.

- [ ] **Step 2: Verify all unit tests compile and logic is sound**

Verify Kotlin code syntax and ensure all edge cases are handled.

- [ ] **Step 3: Final Commit & Tagging if needed**

```bash
git status
```
