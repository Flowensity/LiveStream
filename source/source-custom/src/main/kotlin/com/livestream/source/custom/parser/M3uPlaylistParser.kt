package com.livestream.source.custom.parser

import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ChannelCategoryGroup
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceHealthStatus
import com.livestream.core.model.StreamProtocol
import java.net.URI
import java.util.UUID

data class ParsedPlaylist(
    val categories: List<ChannelCategory>,
    val channels: List<Channel>,
    val sources: List<LiveSource>,
    val epgUrl: String? = null
)

object M3uPlaylistParser {

    private val GROUP_TITLE_REGEX = Regex("""group-title="([^"]*)"""")
    private val TVG_ID_REGEX = Regex("""tvg-id="([^"]*)"""")
    private val TVG_LOGO_REGEX = Regex("""tvg-logo="([^"]*)"""")
    private val TVG_NAME_REGEX = Regex("""tvg-name="([^"]*)"""")
    private val EPG_URL_REGEX = Regex("""(?:x-tvg-url|url-tvg)="([^"]+)"""", RegexOption.IGNORE_CASE)

    /**
     * 解析用户导入的 M3U 播放列表内容
     * 具备严格的安全审计规则：拒绝本地私有地址、内网回环与危险 URI 协议
     */
    fun parse(content: String): ParsedPlaylist {
        val lines = content.lines()
        val channels = mutableListOf<Channel>()
        val sources = mutableListOf<LiveSource>()
        val categoriesMap = mutableMapOf<String, ChannelCategory>()
        var detectedEpgUrl: String? = null

        var currentExtInf: String? = null
        var channelIndex = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTM3U", ignoreCase = true) && detectedEpgUrl == null) {
                val rawUrls = EPG_URL_REGEX.find(trimmed)?.groupValues?.get(1)
                if (!rawUrls.isNullOrBlank()) {
                    detectedEpgUrl = rawUrls.split(",")
                        .map { it.trim() }
                        .firstOrNull { isSafeAndValidStreamUrl(it) }
                }
            } else if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                currentExtInf = trimmed
            } else if (!trimmed.startsWith("#") && currentExtInf != null) {
                val streamUrl = trimmed
                if (isSafeAndValidStreamUrl(streamUrl)) {
                    val groupTitle = GROUP_TITLE_REGEX.find(currentExtInf)?.groupValues?.get(1)?.trim()
                        ?.ifEmpty { "自定义" } ?: "自定义"
                    val tvgId = TVG_ID_REGEX.find(currentExtInf)?.groupValues?.get(1)?.trim()
                    val tvgLogo = TVG_LOGO_REGEX.find(currentExtInf)?.groupValues?.get(1)?.trim()
                    val tvgName = TVG_NAME_REGEX.find(currentExtInf)?.groupValues?.get(1)?.trim()

                    val commaIndex = currentExtInf.lastIndexOf(',')
                    val channelName = if (commaIndex != -1 && commaIndex < currentExtInf.length - 1) {
                        currentExtInf.substring(commaIndex + 1).trim()
                    } else {
                        tvgName ?: "自定义频道 $channelIndex"
                    }

                    val categoryId = "custom_${groupTitle.hashCode()}"
                    categoriesMap.getOrPut(categoryId) {
                        ChannelCategory(
                            id = categoryId,
                            name = groupTitle,
                            group = ChannelCategoryGroup.CUSTOM,
                            orderIndex = 1000 + categoriesMap.size
                        )
                    }

                    val channelId = "user_ch_${UUID.nameUUIDFromBytes("${channelName}_$channelIndex".toByteArray())}"
                    val channel = Channel(
                        id = channelId,
                        name = channelName,
                        channelNumber = 800 + channelIndex,
                        categoryId = categoryId,
                        logoUrl = tvgLogo,
                        orderIndex = channelIndex,
                        epgSourceId = tvgId
                    )

                    val protocol = inferProtocol(streamUrl)
                    val source = LiveSource(
                        id = "user_src_${UUID.nameUUIDFromBytes(streamUrl.toByteArray())}",
                        channelId = channelId,
                        name = "网络线路",
                        protocol = protocol,
                        adapterProviderId = "custom_user_source",
                        priority = 10,
                        healthStatus = SourceHealthStatus.UNKNOWN,
                        url = streamUrl
                    )

                    channels.add(channel)
                    sources.add(source)
                    channelIndex++
                }
                currentExtInf = null
            }
        }

        return ParsedPlaylist(
            categories = categoriesMap.values.toList(),
            channels = channels,
            sources = sources,
            epgUrl = detectedEpgUrl
        )
    }

    /**
     * URL 安全检查（阻止本地回环攻击与危险 URI 协议如 file://, content://, javascript://；
     * 允许公网 HTTP/HTTPS 以及家庭局域网 IPTV/UDPXY 代理流如 192.168.x.x, 10.x.x.x）
     */
    fun isSafeAndValidStreamUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return false
            }

            val host = uri.host?.lowercase() ?: return false
            // 禁止本机回环地址，防止 SSRF 探测本机应用服务
            if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0.0.0.0" ||
                host == "ip6-localhost" || host == "ip6-loopback") {
                return false
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun inferProtocol(url: String): StreamProtocol {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") || lower.contains("/hls/") -> StreamProtocol.HLS
            lower.contains(".mpd") || lower.contains("/dash/") -> StreamProtocol.DASH
            else -> StreamProtocol.PROGRESSIVE_HTTP
        }
    }
}
