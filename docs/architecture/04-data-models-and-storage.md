# 04 - 数据模型与本地存储架构设计

## 1. 实体分离原则

根据系统开发原则，**频道实体**与**播放源实体**必须完全解耦：
1. 一个频道（`Channel`）可以绑定零个、一个或多个播放源（`LiveSource`）。
2. 一个播放源（`LiveSource`）具备独立的健康状态、延迟检测结果、协议类型与优先级。
3. 频道实体只保留展示与业务元数据（频道名称、频道号、分类、台标、收藏状态、排序权重），绝不硬编码任何播放 URL。

```
+------------------------------------+
|              Channel               |
+------------------------------------+
| id: String                         |
| name: String                       |
| channelNumber: Int                 |
| categoryId: String                 |
| isFavorite: Boolean                |
| orderIndex: Int                    |
+------------------------------------+
                  |
                  | 1 : N
                  v
+------------------------------------+
|             LiveSource             |
+------------------------------------+
| id: String                         |
| channelId: String                  |
| name: String (如 "4K主线路", "备用") |
| protocol: StreamProtocol           |
| adapterProviderId: String          |
| priority: Int                      |
| healthStatus: SourceHealthStatus   |
+------------------------------------+
```

---

## 2. 本地持久化设计（Room + DataStore）

### Room 关系型存储
负责结构化且需要复杂查询、排序和关联的数据：
- `ChannelEntity`（频道表）
- `LiveSourceEntity`（直播源表，外键关联 `channel_id`）
- `CategoryEntity`（分类表）
- `EpgProgramEntity`（EPG 节目单表，支持按时间范围与频道索引快速查询）
- `PlaybackHistoryEntity`（历史播放记录表）

### DataStore Preferences 偏好存储
负责轻量、单例、无需复杂关联的键值对偏好配置：
- `KEY_DEFAULT_CATEGORY`: 启动默认定位分类
- `KEY_LAST_PLAYED_CHANNEL_ID`: 上次观看频道 ID
- `KEY_HARDWARE_DECODE_PREFERRED`: 是否优先硬件解码（默认 true）
- `KEY_SURFACE_VIEW_PREFERRED`: 是否优先 SurfaceView（默认 true）
- `KEY_SWITCH_CHANNEL_TIMEOUT_MS`: 切台超时判定阈值（毫秒）
- `KEY_TV_KEY_MAPPING_SCHEME`: 电视遥控器按键映射方案（经典模式 / 极简模式）
