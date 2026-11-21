package com.livestream.source.custom.parser

import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ChannelCategoryGroup
import com.livestream.core.model.LiveSource
import com.livestream.core.model.SourceHealthStatus
import com.livestream.core.model.StreamProtocol
import java.io.BufferedReader
import java.io.Reader
import java.io.StringReader
import java.net.URI
import java.util.Locale
import java.util.UUID

data class ParsedPlaylist(
    val categories: List<ChannelCategory>,
    val channels: List<Channel>,
    val sources: List<LiveSource>,
    val epgUrl: String? = null
)

data class ExtInfAttributes(
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val groupTitle: String?,
    val channelName: String?
)

object M3uPlaylistParser {

    private val EPG_URL_REGEX = Regex("""(?:x-tvg-url|url-tvg)="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val CCTV_NUM_REGEX = Regex("""CCTV(\d+\+?)""")
    private val NOISE_REGEX = Regex("""(?:FHD|HD|SD|4K|8K|2160P|1080P|1080I|720P|超高清|全高清|超清|高清|标清|频道|[\s\-_])+""")

    // 括号及噪音词清洗正则
    private val BRACKETED_NOISE_REGEX = Regex(
        """\[[^\]]*(?:4K|8K|2160|1080|720|480|576|FHD|HD|SD|fps|电信|联通|移动|IPv6|专线|超清|高清|标清)[^\]]*\]|\([^\)]*(?:4K|8K|2160|1080|720|480|576|FHD|HD|SD|fps|电信|联通|移动|IPv6|专线|超清|高清|标清)[^\)]*\)""",
        RegexOption.IGNORE_CASE
    )
    private val QUALITY_TOKEN_REGEX = Regex(
        """\b(?:4K|8K|2160P|1080P|1080I|720P|576P|480P|FHD|HD|SD)\b|超高清|全高清|超清|高清|标清""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 解析用户导入的 M3U 播放列表内容
     */
    fun parse(content: String, startingChannelNumber: Int = 801): ParsedPlaylist {
        return parse(StringReader(content), startingChannelNumber)
    }

    /**
     * 流式解析 M3U 字符输入流：
     * 1. 采用 BufferedReader.lineSequence() 惰性流式消费；
     * 2. 采用单遍零正则字符扫描器提取属性；
     * 3. 在同一分组内对同名频道进行聚合为单 Channel + 多 LiveSource；
     * 4. 提取分辨率与运营商标签，为高画质源赋予更高 priority；
     * 5. 相同播放 URL 自动去重。
     */
    fun parse(reader: Reader, startingChannelNumber: Int = 801): ParsedPlaylist {
        val channelsMap = LinkedHashMap<String, Channel>()
        val sourcesList = mutableListOf<LiveSource>()
        val channelUrlsMap = mutableMapOf<String, MutableSet<String>>()
        val channelSourceIndexMap = mutableMapOf<String, Int>()
        val categoriesMap = LinkedHashMap<String, ChannelCategory>()
        var detectedEpgUrl: String? = null

        var currentExtInf: String? = null
        var globalChannelSequence = 1
        val baseChannelNumber = startingChannelNumber.coerceAtLeast(1)

        val buffered = if (reader is BufferedReader) reader else BufferedReader(reader)
        buffered.useLines { lineSeq ->
            for (line in lineSeq) {
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
                    val streamUrl = stripPipeHeaderSuffix(trimmed)
                    val extInf = currentExtInf!!
                    if (isSafeAndValidStreamUrl(streamUrl)) {
                        val attrs = extractExtInfAttributes(extInf)
                        val groupTitle = attrs.groupTitle?.ifEmpty { "自定义" } ?: "自定义"
                        val rawChannelName = attrs.channelName?.ifEmpty { null }
                            ?: attrs.tvgName?.ifEmpty { null }
                            ?: "自定义频道 $globalChannelSequence"

                        val categoryId = "custom_${groupTitle.hashCode()}"
                        categoriesMap.getOrPut(categoryId) {
                            ChannelCategory(
                                id = categoryId,
                                name = groupTitle,
                                group = ChannelCategoryGroup.CUSTOM,
                                orderIndex = 1000 + categoriesMap.size
                            )
                        }

                        // 仅提取客观网络运营商/协议标签，严禁轻信文本声称的假分辨率
                        val networkTag = extractNetworkTag(rawChannelName, streamUrl)
                        val cleanedChannelName = cleanChannelTitle(rawChannelName)

                        // 计算组内聚合 Key
                        val aggKey = computeAggregationKey(
                            categoryId = categoryId,
                            tvgId = attrs.tvgId,
                            tvgName = attrs.tvgName,
                            cleanedName = cleanedChannelName
                        )

                        var channel = channelsMap[aggKey]
                        if (channel == null) {
                            val channelId = "user_ch_${UUID.nameUUIDFromBytes("${categoryId}_$aggKey".toByteArray())}"
                            channel = Channel(
                                id = channelId,
                                name = cleanedChannelName,
                                channelNumber = baseChannelNumber + (globalChannelSequence - 1),
                                categoryId = categoryId,
                                logoUrl = attrs.tvgLogo,
                                orderIndex = globalChannelSequence,
                                epgSourceId = attrs.tvgId
                            )
                            channelsMap[aggKey] = channel
                            globalChannelSequence++
                        } else {
                            // 补充缺失的元数据（Logo 或 epgId）
                            var updatedChannel = channel
                            if (updatedChannel.logoUrl.isNullOrEmpty() && !attrs.tvgLogo.isNullOrEmpty()) {
                                updatedChannel = updatedChannel.copy(logoUrl = attrs.tvgLogo)
                            }
                            if (updatedChannel.epgSourceId.isNullOrEmpty() && !attrs.tvgId.isNullOrEmpty()) {
                                updatedChannel = updatedChannel.copy(epgSourceId = attrs.tvgId)
                            }
                            if (updatedChannel !== channel) {
                                channelsMap[aggKey] = updatedChannel
                                channel = updatedChannel
                            }
                        }

                        // URL 去重与线路装配
                        val urls = channelUrlsMap.getOrPut(channel.id) { mutableSetOf() }
                        if (urls.add(streamUrl)) {
                            val lineIndex = (channelSourceIndexMap[channel.id] ?: 0) + 1
                            channelSourceIndexMap[channel.id] = lineIndex

                            val lineName = if (networkTag != null) "线路 $lineIndex ($networkTag)" else "线路 $lineIndex"
                            // 统一保持客观基准优先级 10，真实画质由底层播放探针与解码器硬件确认
                            val priority = 10

                            val protocol = inferProtocol(streamUrl)
                            val source = LiveSource(
                                id = "user_src_${UUID.nameUUIDFromBytes("${channel.id}_$streamUrl".toByteArray())}",
                                channelId = channel.id,
                                name = lineName,
                                protocol = protocol,
                                adapterProviderId = "custom_user_source",
                                priority = priority,
                                healthStatus = SourceHealthStatus.UNKNOWN,
                                url = streamUrl
                            )
                            sourcesList.add(source)
                        }
                    }
                    currentExtInf = null
                }
            }
        }

        return ParsedPlaylist(
            categories = categoriesMap.values.toList(),
            channels = channelsMap.values.toList(),
            sources = sourcesList,
            epgUrl = detectedEpgUrl
        )
    }

    /**
     * 单遍零正则字符扫描器：一次线性遍历同时提取 tvg-id、tvg-name、tvg-logo、group-title 与频道标题
     */
    fun extractExtInfAttributes(extInfLine: String): ExtInfAttributes {
        var tvgId: String? = null
        var tvgName: String? = null
        var tvgLogo: String? = null
        var groupTitle: String? = null
        var unquotedCommaIndex = -1

        val len = extInfLine.length
        var i = if (extInfLine.startsWith("#EXTINF:", ignoreCase = true)) 8 else 0
        var inQuotes = false

        while (i < len) {
            val c = extInfLine[i]
            if (c == '"') {
                inQuotes = !inQuotes
                i++
                continue
            }
            if (!inQuotes && c == ',') {
                unquotedCommaIndex = i
                break
            }
            if (!inQuotes && c == '=') {
                var keyEnd = i
                while (keyEnd > 0 && extInfLine[keyEnd - 1] == ' ') keyEnd--
                var keyStart = keyEnd
                while (keyStart > 0 && extInfLine[keyStart - 1] != ' ' && extInfLine[keyStart - 1] != ':') {
                    keyStart--
                }
                var valStart = i + 1
                while (valStart < len && extInfLine[valStart] == ' ') valStart++
                if (valStart < len && extInfLine[valStart] == '"') {
                    val contentStart = valStart + 1
                    val contentEnd = extInfLine.indexOf('"', contentStart)
                    if (contentEnd != -1) {
                        val keyLen = keyEnd - keyStart
                        val value = extInfLine.substring(contentStart, contentEnd).trim()
                        when {
                            keyLen == 6 && extInfLine.regionMatches(keyStart, "tvg-id", 0, 6, ignoreCase = true) -> {
                                tvgId = value
                            }
                            keyLen == 8 && extInfLine.regionMatches(keyStart, "tvg-name", 0, 8, ignoreCase = true) -> {
                                tvgName = value
                            }
                            keyLen == 8 && extInfLine.regionMatches(keyStart, "tvg-logo", 0, 8, ignoreCase = true) -> {
                                tvgLogo = value
                            }
                            keyLen == 11 && extInfLine.regionMatches(keyStart, "group-title", 0, 11, ignoreCase = true) -> {
                                groupTitle = value
                            }
                        }
                        i = contentEnd + 1
                        continue
                    }
                }
            }
            i++
        }

        val channelName = if (unquotedCommaIndex != -1 && unquotedCommaIndex < len - 1) {
            extInfLine.substring(unquotedCommaIndex + 1).trim()
        } else {
            null
        }

        return ExtInfAttributes(
            tvgId = tvgId,
            tvgName = tvgName,
            tvgLogo = tvgLogo,
            groupTitle = groupTitle,
            channelName = channelName
        )
    }

    /**
     * 仅提取网络运营商与协议客观标签（不以假画质文本作为依据）
     */
    fun extractNetworkTag(rawName: String, url: String): String? {
        val combined = "$rawName $url"
        val lower = combined.lowercase(Locale.ROOT)

        return when {
            combined.contains("电信") -> "电信"
            combined.contains("联通") -> "联通"
            combined.contains("移动") -> "移动"
            lower.contains("ipv6") -> "IPv6"
            combined.contains("BGP", ignoreCase = true) -> "BGP"
            combined.contains("专线") -> "专线"
            combined.contains("备用") -> "备用"
            else -> null
        }
    }

    /**
     * 清洗频道名称中的画质噪音词及后缀
     */
    fun cleanChannelTitle(rawName: String): String {
        var cleaned = BRACKETED_NOISE_REGEX.replace(rawName, "").trim()
        cleaned = QUALITY_TOKEN_REGEX.replace(cleaned, "").trim()
        cleaned = cleaned.trimEnd('-', '_', ' ')
        return if (cleaned.isNotBlank()) cleaned else rawName.trim()
    }

    /**
     * 计算组内同名聚合 Key
     */
    private fun computeAggregationKey(
        categoryId: String,
        tvgId: String?,
        tvgName: String?,
        cleanedName: String
    ): String {
        val tid = tvgId?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        if (tid != null) {
            return "$categoryId:id:$tid"
        }

        val normName = normalizeChannelKey(tvgName?.takeIf { it.isNotBlank() } ?: cleanedName)
        return "$categoryId:norm:$normName"
    }

    private fun normalizeChannelKey(raw: String): String {
        val upper = raw.uppercase(Locale.ROOT)
        val noNoise = NOISE_REGEX.replace(upper, "").trim()
        val cctvMatch = CCTV_NUM_REGEX.find(noNoise)
        if (cctvMatch != null) {
            return "CCTV" + cctvMatch.groupValues[1]
        }
        return noNoise.ifEmpty { raw.trim().uppercase(Locale.ROOT) }
    }

    private fun stripPipeHeaderSuffix(url: String): String {
        return url.trim().substringBefore('|').trim()
    }

    /**
     * URL 安全检查（阻止本地回环攻击与危险 URI 协议如 file://, content://, javascript://；
     * 允许公网 HTTP/HTTPS 以及家庭局域网 IPTV/UDPXY 代理流如 192.168.x.x, 10.x.x.x）
     */
    fun isSafeAndValidStreamUrl(url: String): Boolean {
        return try {
            val cleanUrl = stripPipeHeaderSuffix(url)
            val uri = URI(cleanUrl)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") {
                return false
            }

            val host = uri.host?.lowercase(Locale.ROOT) ?: return false
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
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.contains(".m3u8") || lower.contains("/hls/") -> StreamProtocol.HLS
            lower.contains(".mpd") || lower.contains("/dash/") -> StreamProtocol.DASH
            else -> StreamProtocol.PROGRESSIVE_HTTP
        }
    }
}
