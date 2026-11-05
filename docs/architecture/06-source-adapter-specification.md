# 06 - 直播源适配器（Source Adapter）接入规范

## 1. 架构目标与插件化设计

为了支持不同来源（CCTV 官方源、卫视源、地方台源、用户自定义 M3U/TXT/JSON 格式直播源）的灵活接入与平滑演进，系统设计了统一的 `SourceAdapter` 插件化体系。

### 核心约束
1. **职责单一**：
   - Source Adapter **仅负责**：资源识别、可用性检测、元数据获取、返回统一的 `SourceResolution` 描述、配置必要请求头（如 Referer / User-Agent）。
   - Source Adapter **严禁负责**：媒体播放、初始化 ExoPlayer、持有 UI 组件、操作 WebView。
2. **纯 Kotlin 接口定义**：
   ```kotlin
   interface SourceAdapter {
       /**
        * 适配器唯一身份标识，与 LiveSource.adapterProviderId 对应
        */
       val providerId: String

       /**
        * 解析频道当前可播放资源
        */
       suspend fun resolve(
           channel: Channel,
           source: LiveSource
       ): SourceResolution

       /**
        * 独立检测资源健康状态与连接性
        */
       suspend fun checkAvailability(
           channel: Channel,
           source: LiveSource
       ): AvailabilityResult
   }
   ```

---

## 2. 合规与资源安全边界
- 严禁通过逆向、破解、绕过 DRM 鉴权获取受限流媒体。
- 绝不硬编码敏感认证 Token 或私钥。
- 用户自定义直播源在导入时必须执行强类型格式校验与合法 URI 方案验证（仅允许 http/https），防止恶意构造地址造成应用安全漏洞。
