package com.livestream.source.official

import com.livestream.core.model.Channel
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceResolution
import com.livestream.core.model.StreamProtocol
import com.livestream.core.network.NetworkProbe
import com.livestream.core.network.ProbeResult
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.source.api.AvailabilityResult
import com.livestream.source.api.SourceAdapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfficialSourceAdapter @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val networkProbe: NetworkProbe
) : SourceAdapter {

    override val providerId: String = "official_source_provider"

    override suspend fun resolve(channel: Channel, source: LiveSource): SourceResolution {
        val streamUrl = channelRepository.getStreamUrl(source.id)
        if (streamUrl.isNullOrBlank()) {
            return SourceResolution.Unavailable("官方公开资源地址未配置")
        }

        return if (source.protocol == StreamProtocol.OFFICIAL_WEB) {
            SourceResolution.OfficialWeb(uri = streamUrl)
        } else {
            SourceResolution.NativeMedia(
                uri = streamUrl,
                protocol = source.protocol.name,
                headersProfileId = source.headersProfileId,
                drmProfileId = source.drmProfileId
            )
        }
    }

    override suspend fun checkAvailability(channel: Channel, source: LiveSource): AvailabilityResult {
        val streamUrl = channelRepository.getStreamUrl(source.id)
            ?: return AvailabilityResult.Unavailable("缺少官方资源配置")

        return when (val probeResult = networkProbe.probeUrl(streamUrl)) {
            is ProbeResult.Success -> {
                if (probeResult.latencyMs > 2500) {
                    AvailabilityResult.Degraded(probeResult.latencyMs, "网络连通但延迟较高 (${probeResult.latencyMs}ms)")
                } else {
                    AvailabilityResult.Available(probeResult.latencyMs)
                }
            }
            is ProbeResult.Failure -> {
                AvailabilityResult.Unavailable(probeResult.reason)
            }
        }
    }
}
