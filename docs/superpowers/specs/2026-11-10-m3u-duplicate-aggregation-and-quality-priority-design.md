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

### 1. M3U 解析层同名聚合与线路标签提取 (`M3uPlaylistParser.kt`)
- **同名聚合维度**：
  - 提取 `#EXTINF` 原始属性：`tvg-id`、`tvg-name`、`tvg-logo`、`group-title`、`channelName` 与播放链接 `url`。
  - 规范化频道名称清洗（去除 `[4K]`, `[1080P]`, `(电信)`, `_超清`, `高清`, `标清`, `频道` 等噪音词），提取基础频道名（例如从 `CCTV-1 综合 [4K 50fps]` 提炼出 `CCTV-1`）。
  - 分辨率与线路标签检测：
    - **4K / 8K / 2160P / 超高清**：分辨率权重 +40，线路标签标为 `4K`。
    - **1080P / FHD / 全高清**：分辨率权重 +30，线路标签标为 `1080P`。
    - **720P / HD / 高清**：分辨率权重 +20，线路标签标为 `720P`。
    - **480P / 576P / SD / 标清**：分辨率权重 +10，线路标签标为 `标清`。
    - 网络/运营商标签：检测 `电信`、`联通`、`移动`、`IPv6`、`专线`、`备用` 等。
  - **聚合规则**：
    - 维护聚合索引 `groupTitle + "_" + normalizedChannelName`（或非空 `tvg-id`）。
    - 同一聚合分组只生成 **1 个 `Channel`**，其所有有效播放 URL 生成各自的 `LiveSource`。
    - URL 去重：若同一个频道包含完全相同的 URL，忽略重复项。
    - 线路命名规则：`线路 1 (4K 电信)`、`线路 2 (1080P)`、`线路 3` 等。
    - 初始优先级：基础值 10 + 分辨率权重（0 ~ 40）。

### 2. 方案 A 融入系统内置频道 (`SettingsViewModel.kt`)
- **匹配与分流**：
  - 调用 `ChannelMatchIndex` 对解析出的 `parsed.channels` 进行精准/模糊比对。
  - **已匹配内置频道的项**：
    - 不加入 `channelsToSave`（彻底阻止生成多余的自定义频道）。
    - 其挂载的线路转换为针对内置频道的 `matchedSources`，`channelId` 指向对应内置频道的 ID，优先级赋予更高基础分（50 + 分辨率权重）。
    - 记录到 `CustomPlaylistSource.matchedSourceIds`，以便未来更新或删除订阅时精准级联清理。
  - **未匹配内置频道的项**：
    - 加入 `channelsToSave`，进入自定义频道库。
    - 其挂载的线路加入 `sourcesToSave`。
    - 记录到 `CustomPlaylistSource.channelIds`。
- **存储与元数据同步**：
  - `channelRepository.saveChannels(channelsToSave)`：仅保存真正的新频道。
  - `channelRepository.saveSources(sourcesToSave + matchedSources)`：全量保存优化后的线路。
  - `CustomPlaylistSource.channelCount` 记录实际新增频道数与补充线路数。

### 3. 播放优选高分辨率与高加载速度线路 (`DefaultPlaybackOrchestrator.kt`)
- **多维度排序算法 (`sortSourcesByHealthAndLatency`)**：
  - 排序维度层次如下：
    1. **健康状态**：`HEALTHY` / `UNKNOWN` (2) > `DEGRADED` (1) > `UNAVAILABLE` (0)。
    2. **播放协议**：原生音视频流协议（HLS / DASH / HTTP，1）> 网页容器（`OFFICIAL_WEB`，0）。
    3. **画质与速度综合梯队**：
       - 若测速已完成（`sourceLatencyCache` 包含测速时延）：
         - 优秀 4K / 8K（延迟 < 1500ms）：梯队分最高。
         - 优质 1080P（延迟 < 1000ms）：梯队分次高。
         - 高延迟 4K（延迟 >= 1500ms）与优质 720P。
         - 延迟测速较低者在同画质梯队内部绝对优先。
       - 若尚未完成测速（切台初次加载）：
         - 直接按 `priority`（包含分辨率权重 4K > 1080P > 720P > SD）降序排列，确保首播默认命中最高清晰度线路。
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
