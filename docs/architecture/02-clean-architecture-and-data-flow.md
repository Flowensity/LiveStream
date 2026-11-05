# 02 - Clean Architecture 分层与单向数据流 (UDF) 设计

## 1. Clean Architecture 分层边界

为了确保系统高度可测试、易维护并且支持未来的长期演进，系统严格划分为四层：

```
+--------------------------------------------------------------------------+
|                          Presentation Layer (UI)                         |
|   Compose Screen / TV OSD / ViewModels / UiState / UiEvent (UDF)         |
+--------------------------------------------------------------------------+
                                     |  depends on
                                     v
+--------------------------------------------------------------------------+
|                           Domain Layer (Business)                        |
|   UseCases / Repository Interfaces / Pure Models / SourceResolution      |
+--------------------------------------------------------------------------+
                                     ^  implemented by
                                     |
+--------------------------------------------------------------------------+
|                           Data Layer (Persistence)                       |
|   Room Entities / DAOs / DataStore / OkHttp / Remote APIs / Mappers      |
+--------------------------------------------------------------------------+
                                     |  interacts with
                                     v
+--------------------------------------------------------------------------+
|                      Playback & Source Engine Layer                      |
|   PlaybackOrchestrator / NativePlaybackEngine (Media3) / SourceAdapters  |
+--------------------------------------------------------------------------+
```

### 关键架构约束
1. **纯粹的领域模型**：
   - `core:model` 中严禁引入 Android Framework（如 `android.content.Context`）及第三方媒体库（如 `androidx.media3.*`）。
   - 直播源解析结果必须使用纯 Kotlin sealed interface：
     ```kotlin
     sealed interface SourceResolution {
         data class NativeMedia(
             val uri: String,
             val protocol: String,
             val headersProfileId: String? = null,
             val drmProfileId: String? = null
         ) : SourceResolution

         data class OfficialWeb(
             val uri: String
         ) : SourceResolution

         data class Unavailable(
             val reason: String
         ) : SourceResolution
     }
     ```
   - 媒体库依赖只在具体的引擎实现模块（`:playback:playback-media3`）中持有，并在该模块内将 `NativeMedia` 转换为 Media3 `MediaItem`。

2. **单向数据流 (Unidirectional Data Flow - UDF)**：
   - 视图状态由 ViewModel 统一通过只读 `StateFlow<UiState>` 暴露。
   - 用户操作（如切换频道、遥控器方向键事件、刷新收藏）转换为显式 `UiEvent` 发送至 ViewModel 处理。
   - 严禁 UI 层越过 ViewModel 直接调用 Repository、播放器或数据库。

---

## 2. 播放调度器（Playback Orchestrator）架构

播放调度器是整个播放核心的大脑，彻底解除 UI 与具体播放器实现的直接耦合。

### 状态机规范
```
    +------+
    | IDLE | <--------------------+
    +------+                      |
       |                          |
       v                          |
+--------------+                  |
|  RESOLVING   |                  |
+--------------+                  |
       |                          |
       v                          |
+--------------+                  |
|  PREPARING   |                  |
+--------------+                  |
       |                          |
       v                          |
+--------------+                  |
|  BUFFERING   |                  |
+--------------+                  |
       |                          |
       v                          |
+--------------+                  |
|    READY     |                  |
+--------------+                  |
       |                          |
       v                          |
+--------------+                  |
|   PLAYING    |                  |
+--------------+                  |
   |        |                     |
   |        v                     |
   |   +----------+               |
   |   |  PAUSED  |               |
   |   +----------+               |
   v                              |
+--------------+                  |
|  SWITCHING   | -----------------+
+--------------+                  |
       |                          |
       v                          |
+--------------+                  |
|    ERROR     | -----------------+
+--------------+
```

### 异步切台竞争与防重入机制
- **Job 取消机制**：调度器在收到新的切台请求时，必须立刻通过 Coroutine Job 协同取消正在进行的旧解析任务与旧播放准备任务。
- **请求序列递增令牌**：每次播放请求携带递增的 `requestId`，在播放器任何状态回调时校验是否匹配当前活跃请求，杜绝旧频道异步回调污染新频道状态。
- **资源重试与回退策略**：若某直播源解析失败或连接超时，调度器自动在同一频道的后备源列表中轮换下一个可用源，重试上限严格限制为 3 次，防止无休止重试导致内存暴涨。
