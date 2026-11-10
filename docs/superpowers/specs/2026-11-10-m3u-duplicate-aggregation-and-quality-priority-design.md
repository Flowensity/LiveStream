# M3U 节目同名聚合与画质测速优选 设计方案

## 需求背景与目标
1. **M3U 同名节目聚合**：导入第三方 M3U 播放列表时，同名节目在播放列表内聚合为单一频道的不同播放线路（`LiveSource`），彻底解决导入数千个重复频道导致抽屉列表爆炸的问题。
2. **方案 A 智能融合系统内置频道**：
   - 若导入频道与系统内置预置频道（如央视、卫视等）名称匹配，不创建重复的自定义频道实体，直接将其解析出的线路作为备用线路绑定至对应的内置系统频道。
   - 仅对系统内置不存在的新频道（如特色小众台、海外频道、轮播频道）在自定义分类下新建频道实体（且同一 M3U 内同名依然聚合为单频道多线路）。
3. **播放时优选高分辨率与高加载速度线路**：
   - 线路解析阶段提取分辨率与线路标签（4K/8K、1080P/FHD、720P/HD 等），为高分辨率线路赋予更高的权重与优先级。
   - 播放编排器（`DefaultPlaybackOrchestrator`）在线路选择与探测重排阶段，综合**可用状态 > 原生流协议 > 分辨率梯队 > 测速网络时延**进行优选排序，确保首播和切台优先进入高画质且秒开流畅的优质线路。
4. **自动化测试与构建发布**：编写与更新单元测试验证聚合与排序逻辑，并通过 `./gradlew assembleDebug` 构建 APK。

---

## 详细设计

### 1. M3U 解析层同名聚合与网络标签提取 (`M3uPlaylistParser.kt`)
- **同名聚合维度**：
  - 提取 `#EXTINF` 原始属性：`tvg-id`、`tvg-name`、`tvg-logo`、`group-title`、`channelName` 与播放链接 `url`。
  - 规范化频道名称清洗（去除 `[4K]`, `[1080P]`, `(电信)`, `_超清`, `高清`, `标清`, `频道` 等噪音词），提取基础频道名（例如从 `CCTV-1 综合 [4K 50fps]` 提炼出 `CCTV-1 综合`）。
  - **杜绝基于文本标题命名判定画质**：
    - 第三方源常存在“假 4K”、“假 1080P”的标题虚标欺骗，解析层严禁直接根据标题字符串赋予分辨率权重或虚假标签；
    - 解析层仅提取网络运营商与协议客观标签（检测 `电信`、`联通`、`移动`、`IPv6`、`专线`、`备用` 等）；
    - 线路统一初始权重（基础值 10，匹配内置频道为 50），不因假标题产生虚高优先级。
  - **聚合规则**：
    - 维护聚合索引 `groupTitle + "_" + normalizedChannelName`（或非空 `tvg-id`）。
    - 同一聚合分组只生成 **1 个 `Channel`**，其所有有效播放 URL 生成各自的 `LiveSource`。
    - URL 去重：若同一个频道包含完全相同的 URL，忽略重复项。
    - 线路命名规则：`线路 1 (电信)`、`线路 2 (IPv6)`、`线路 3` 等。

### 2. 真实物理分辨率的获取与测速机制
- **流媒体探测层 (`NetworkProbe` / `CustomSourceAdapter`)**：
  - 针对 HLS 流媒体（`.m3u8`），探测时检查 Master Playlist 中的 `#EXT-X-STREAM-INF:RESOLUTION=(\d+)x(\d+)`，从技术协议层提取服务端实际编码物理分辨率（如 1920x1080、3840x2160）；
- **解码播放层 (`Media3PlaybackEngine` / `DefaultPlaybackOrchestrator`)**：
  - 在 ExoPlayer 开始解码渲染后，通过 `onVideoSizeChanged` 监听精准获取硬件解码器输出的真实视频像素尺寸（`videoWidth` 与 `videoHeight`）；
  - `DefaultPlaybackOrchestrator` 将真实物理分辨率记录在 `sourceRealResolutionCache` 中。

### 3. 播放优选高真实分辨率与高加载速度线路 (`DefaultPlaybackOrchestrator.kt`)
- **多维度排序算法 (`sortSourcesByHealthAndLatency`)**：
  - 排序维度层次如下：
    1. **健康状态**：`HEALTHY` / `UNKNOWN` (2) > `DEGRADED` (1) > `UNAVAILABLE` (0)。
    2. **播放协议**：原生音视频流协议（HLS / DASH / HTTP，1）> 网页容器（`OFFICIAL_WEB`，0）。
    3. **真实画质与速度综合打分**：
       - 基于获取到的真实物理尺寸：
         - 真实 4K / 8K（宽 >= 3840 或 高 >= 2160）：基础加权 +400；
         - 真实 1080P（宽 >= 1920 或 高 >= 1080）：基础加权 +300；
         - 真实 720P（宽 >= 1280 或 高 >= 720）：基础加权 +200；
         - 真实 标清/未达标（宽 > 0 或 高 > 0）：基础加权 +100；
         - 尚未探测到真实尺寸：基础加权 0，同未测速源保持平等基线。
       - 结合测速时延（`sourceLatencyCache`）：
         - 低延迟（<500ms）不降权；
         - 高延迟（>1500ms）适当扣分，避免卡顿；
         - **彻底击碎假分辨率欺骗**：标题声称“4K”但真实解码为 576P 的线路，其实际评分为 100 分，将被真实 1080P（300 分）绝对超越！
    4. **测速时延**：`sourceLatencyCache[it.id]` 升序排列（毫秒延迟越低越优先）。
    5. **优先级兜底**：`it.priority` 降序。
- **后台测速联动**：
  - 频道切入后启动 `probeCandidateSources` 异步并发测速；
  - 测速完成后回调刷新 `availableSources` 并按上述规则动态重排，当前若发生卡死或切线时无缝切换至最快的高画质备用线路。

---

## 验证与交付
1. **单元测试验证**：
   - `M3uPlaylistParserTest`：测试同名频道合并为单频道多线路、URL 重复去重、分辨率/运营商标签提取与优先级赋予。
   - `SettingsViewModelTest`：测试方案 A 匹配内置频道时线路正确追加、未生成多余自定义频道实体、未匹配频道正确保留。
   - `PlaybackOrchestratorTest`：测试多线路排序时优先高分辨率及低延迟线路。
2. **全工程测试**：
   - 执行 `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew testDebugUnitTest` 验证所有测试通过。
3. **构建 APK**：
   - 执行 `JAVA_HOME=/home/flowensity/android-toolchain/jdk-17.0.21+9 ./gradlew assembleDebug`，输出最终 APK 文件。
