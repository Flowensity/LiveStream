# Fast Channel Switching Optimization on Android TV Design

## 1. Intent & Context
On Android TV devices powered by MediaTek MT9679 with 4GB RAM, channel switching currently experiences an abnormal ~10-second delay before rendering the first video frame, whereas OnePlus 8 and Waydroid switch channels quickly.
Investigation revealed that when a primary stream source fails, experiences slow DNS resolution, or experiences routing stalls (such as IPv6 SYN timeouts common on TV platforms), the player stalls for 10 seconds due to hardcoded timeout thresholds before falling back or recovering:
- `PlaybackWatchdog` had `DEFAULT_STALL_TIMEOUT_MS = 10000L`.
- `Media3PlaybackEngine` configured `DefaultHttpDataSource` with 8000ms connect and 8000ms read timeouts.
- `DefaultPlaybackOrchestrator` statically initialized `PlaybackWatchdog` without utilizing the `channelSwitchTimeoutMs` user preference, and used a 300ms delay between source switch fallbacks.
- `bufferForPlaybackMs` was 500ms, delaying first frame presentation unnecessarily.

## 2. Goals & Non-Goals
### Goals
- Cut channel switching stall / fallback latency from 10s down to < 2.5s.
- Accelerate HTTP connect / read failure detection to 3000ms / 4000ms so dead sources are dropped immediately.
- Lower initial playback buffer (`bufferForPlaybackMs`) to 250ms for faster first frame render while keeping `minBufferMs = 1500ms` for stream stability.
- Ensure `PlaybackWatchdog` default timeout is 2500ms and supports runtime dynamic timeout configuration.
- Reduce fallback delay between multi-source switching from 300ms to 100ms.
- Ensure all unit tests in `:playback:playback-media3` remain fully green and accurately reflect new timeout behaviors.

### Non-Goals
- Modifying UI components or TV D-pad key navigation.
- Overhauling WebView playback container (this optimization focuses on native ExoPlayer live streams).

## 3. Approaches Considered
- **Approach A (Constants-only change)**: Simply modify the default constant in `PlaybackWatchdog` from 10s to 3s and HTTP timeouts from 8s to 3s. Rejected because it does not allow dynamic timeout adjustment and leaves buffer tuning unaddressed.
- **Approach B (Full-stack native switching optimization - Chosen)**:
  1. Optimize `PlaybackWatchdog` default to 2500ms with a dynamic `updateStallTimeoutMs(Long)` method.
  2. Optimize `Media3PlaybackEngine` connect timeout (3000ms), read timeout (4000ms), and `bufferForPlaybackMs` (250ms).
  3. Optimize `DefaultPlaybackOrchestrator` source fallback delay (100ms) and dynamic timeout propagation.
  4. Update unit tests in `PlaybackWatchdogTest` and `PlaybackOrchestratorTest`.

## 4. Detailed Component Changes
### 4.1 `PlaybackWatchdog.kt`
- Change `DEFAULT_STALL_TIMEOUT_MS` from `10000L` to `2500L`.
- Add `@Volatile var stallTimeoutMs: Long` or setter function `updateStallTimeout(timeoutMs: Long)` so callers can adjust it dynamically.

### 4.2 `Media3PlaybackEngine.kt`
- Update `DefaultLoadControl.Builder`:
  - `minBufferMs = 1500`
  - `maxBufferMs = 5000`
  - `bufferForPlaybackMs = 250` (lowered from 500)
  - `bufferForPlaybackAfterRebufferMs = 1000`
- Update `DefaultHttpDataSource.Factory`:
  - `setConnectTimeoutMs(3000)` (lowered from 8000)
  - `setReadTimeoutMs(4000)` (lowered from 8000)
  - `setAllowCrossProtocolRedirects(true)`

### 4.3 `DefaultPlaybackOrchestrator.kt`
- In `handlePlaybackFailure`:
  - Change `delay(300)` to `delay(100)` when switching to the next backup source.
- Provide capability to update watchdog timeout when switching channels or configuring orchestrator.

### 4.4 Unit Tests
- `PlaybackWatchdogTest`: ensure unit test coverage validates default 2500ms behavior and `updateStallTimeout` functionality.
