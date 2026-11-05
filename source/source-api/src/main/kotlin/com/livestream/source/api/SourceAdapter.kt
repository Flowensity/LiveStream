package com.livestream.source.api

import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceResolution

/**
 * 直播源适配器插件契约
 * 核心约束：
 * 1. 仅负责资源识别、可用性检测、元数据获取与合法公开资源的 SourceResolution 解析
 * 2. 严禁直接依赖 Media3、ExoPlayer 或操作 UI/WebView
 */
interface SourceAdapter {

    /**
     * 适配器唯一标识，对应 LiveSource.adapterProviderId
     */
    val providerId: String

    /**
     * 解析频道当前可播放资源（返回纯 Kotlin 模型 SourceResolution）
     */
    suspend fun resolve(
        channel: Channel,
        source: LiveSource
    ): SourceResolution

    /**
     * 独立检测资源可用性与连接延迟
     */
    suspend fun checkAvailability(
        channel: Channel,
        source: LiveSource
    ): AvailabilityResult
}
