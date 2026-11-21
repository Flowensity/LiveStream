package com.livestream.source.custom

import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceResolution
import com.livestream.core.network.NetworkProbe
import com.livestream.core.network.ProbeResult
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.source.api.AvailabilityResult
import com.livestream.source.api.SourceAdapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomSourceAdapter @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val networkProbe: NetworkProbe
) : SourceAdapter {

    override val providerId: String = "custom_user_source"

    override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
        val streamUrl = source.url.ifBlank { channelRepository.getStreamUrl(source.id) }
        if (streamUrl.isNullOrBlank()) {
            return SourceResolution.Unavailable("未找到对应自定义直播源播放地址")
        }

        return SourceResolution.NativeMedia(
            uri = streamUrl,
            protocol = source.protocol.name,
            headersProfileId = source.headersProfileId,
            drmProfileId = source.drmProfileId,
            userAgent = source.userAgent,
            referer = source.referer
        )
    }

    override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
        val streamUrl = source.url.ifBlank { channelRepository.getStreamUrl(source.id) }
            ?.takeIf { it.isNotBlank() }
            ?: return AvailabilityResult.Unavailable("缺少播放地址")

        val probeResult = if (source.userAgent.isNullOrBlank() && source.referer.isNullOrBlank()) {
            networkProbe.probeUrl(streamUrl)
        } else {
            networkProbe.probeUrl(
                url = streamUrl,
                userAgent = source.userAgent,
                referer = source.referer
            )
        }
        return when (probeResult) {
            is ProbeResult.Success -> {
                if (probeResult.latencyMs > 3000) {
                    AvailabilityResult.Degraded(
                        latencyMs = probeResult.latencyMs,
                        reason = "网络响应延迟较高 (${probeResult.latencyMs}ms)",
                        videoWidth = probeResult.videoWidth,
                        videoHeight = probeResult.videoHeight
                    )
                } else {
                    AvailabilityResult.Available(
                        latencyMs = probeResult.latencyMs,
                        videoWidth = probeResult.videoWidth,
                        videoHeight = probeResult.videoHeight
                    )
                }
            }
            is ProbeResult.Failure -> {
                AvailabilityResult.Unavailable(probeResult.reason)
            }
        }
    }
}
