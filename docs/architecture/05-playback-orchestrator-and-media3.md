# 05 - 播放器架构与 Media3 深度治理

## 1. 原生播放器技术栈与约束

- **核心库**：AndroidX Media3 (ExoPlayer)。
- **硬件解码优先**：
  - 默认使用系统硬解管道（`MediaCodecSelector.DEFAULT`）。
  - 禁止无理由全局降级软解（如 FFMpeg 扩展），软解仅作为个别破损或特殊格式的容错手段。
- **渲染表面（SurfaceView 优先）**：
  - SurfaceView 拥有系统 WindowManager 独立的 Surface 图层，直接与硬件合成器（SurfaceFlinger）交互，零额外显存拷贝，抖动与能耗显著低于 TextureView。
- **单例与复用原则**：
  - 正常前台直播只保留一个活跃原生播放器实例。
  - 切台时通过 `player.setMediaItem()`、`player.prepare()` 复用既有实例与解码器通道，避免反复 `release()` 与重新创建导致的黑屏和卡顿。

---

## 2. 播放调度器（Playback Orchestrator）治理机制

### 异步切台竞争与状态安全
在频繁连续按上下键快速换台场景下：
1. **取消在途 Coroutine Job**：每次切台发起时，先 `currentJob?.cancel()`，立即中止正在执行的网络解析、鉴权与前置探测。
2. **递增 Sequence 令牌**：
   ```kotlin
   class PlaybackOrchestrator {
       private var activeSessionId = 0L

       fun playChannel(channel: Channel) {
           val currentSession = ++activeSessionId
           scope.launch {
               val resolution = resolveSource(channel)
               if (currentSession != activeSessionId) return@launch // 过期请求丢弃
               applyResolution(resolution)
           }
       }
   }
   ```
3. **播放器回调隔离**：播放器事件监听器（`Player.Listener`）在触发 `onPlaybackStateChanged`、`onPlayerError` 时，必须核验当前事件关联的会话 ID 是否与活跃会话一致，杜绝旧频道异步错误覆盖新频道的缓冲或就绪状态。

---

## 3. WebView 兼容播放机制边界
WebView 绝非默认播放器，仅在同时满足以下条件时启用：
1. 官方特定资源要求在合规网页容器内鉴权或交互。
2. 网站服务条款明确允许。

### 生命周期隔离与回收
- 独立于原生播放器容器，独立生命周期。
- 退出或切换至原生频道时，必须从父 ViewGroup 移除，显式执行 `stopLoading()`、`loadUrl("about:blank")`、`clearHistory()`、`removeAllViews()` 及 `destroy()`，防止内存泄露与后台音视频静默盗跑。
