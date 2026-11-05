# 07 - EPG 节目单规范与数据流设计

## 1. EPG 与频道的解耦设计

- **独立标识符**：频道（`Channel`）通过 `epgSourceId` 与 EPG 数据源关联，EPG 本身完全独立于频道生命周期。
- **真实性原则**：严禁伪造、虚构节目单数据；如无可用 EPG 数据，统一显示“暂无节目信息”。
- **时间范围语义**：
  ```kotlin
  fun EpgProgram.isCurrentlyPlaying(currentTimestamp: Long): Boolean =
      currentTimestamp in startTimestamp until endTimestamp
  ```

---

## 2. 缓存与失效机制
- **内存一级缓存**：针对当前活跃频道的当日节目单，在 ViewModel 内缓存 StateFlow，提升快速切台时的 OSD 响应速度。
- **Room 二级持久化缓存**：存储全量或近 3 日内的节目单数据，支持离线或弱网下秒开加载。
- **定时更新与清理**：
  - 过期节目（超过 24 小时且非回看支持范围）在后台维护任务中自动淘汰清理。
  - 每日凌晨或初次启动时，根据最后更新时间戳比对拉取最新 XMLTV / JSON 节目单。
