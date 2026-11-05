package com.livestream.data.epg.service

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.EpgProgram
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 央视官方开放节目单 (CNTV EPG) 服务：
 * 从国家级权威公开开放接口获取 CCTV 与各省卫视的全天真实节目排期。
 */
@Singleton
class CntvEpgService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers
) {

    /**
     * 根据频道名称或原始 ID 解析 CNTV 官方 EPG 频源码
     */
    fun resolveCntvChannelCode(channelName: String): String? {
        val cleanName = channelName.replace(" ", "").replace("-", "").uppercase(Locale.getDefault())

        return when {
            // CCTV 央视频道
            cleanName.contains("CCTV1综合") || cleanName == "CCTV1" -> "cctv1"
            cleanName.contains("CCTV2财经") || cleanName == "CCTV2" -> "cctv2"
            cleanName.contains("CCTV3综艺") || cleanName == "CCTV3" -> "cctv3"
            cleanName.contains("CCTV4") || cleanName.contains("中文国际") -> "cctv4"
            cleanName.contains("CCTV5+") || cleanName.contains("体育赛事") -> "cctv5plus"
            cleanName.contains("CCTV5体育") || cleanName == "CCTV5" -> "cctv5"
            cleanName.contains("CCTV6电影") || cleanName == "CCTV6" -> "cctv6"
            cleanName.contains("CCTV7国防") || cleanName.contains("军事") || cleanName == "CCTV7" -> "cctv7"
            cleanName.contains("CCTV8电视剧") || cleanName == "CCTV8" -> "cctv8"
            cleanName.contains("CCTV9纪录") || cleanName.contains("纪录") || cleanName == "CCTV9" -> "cctvjilu"
            cleanName.contains("CCTV10科教") || cleanName == "CCTV10" -> "cctv10"
            cleanName.contains("CCTV11戏曲") || cleanName == "CCTV11" -> "cctv11"
            cleanName.contains("CCTV12社会") || cleanName.contains("法") || cleanName == "CCTV12" -> "cctv12"
            cleanName.contains("CCTV13新闻") || cleanName == "CCTV13" -> "cctv13"
            cleanName.contains("CCTV14少儿") || cleanName.contains("少儿") || cleanName == "CCTV14" -> "cctvchild"
            cleanName.contains("CCTV15音乐") || cleanName == "CCTV15" -> "cctv15"
            cleanName.contains("CCTV16奥林匹克") || cleanName == "CCTV16" -> "cctv16"
            cleanName.contains("CCTV17农业") || cleanName.contains("农村") || cleanName == "CCTV17" -> "cctv17"

            // 各省主要卫视
            cleanName.contains("北京卫视") -> "btv1"
            cleanName.contains("江苏卫视") -> "jiangsu"
            cleanName.contains("东方卫视") -> "dongfang"
            cleanName.contains("浙江卫视") -> "zhejiang"
            cleanName.contains("湖南卫视") -> "hunan"
            cleanName.contains("湖北卫视") -> "hubei"
            cleanName.contains("广东卫视") -> "guangdong"
            cleanName.contains("广西卫视") -> "guangxi"
            cleanName.contains("黑龙江卫视") -> "heilongjiang"
            cleanName.contains("海南卫视") -> "hainan"
            cleanName.contains("重庆卫视") -> "chongqing"
            cleanName.contains("深圳卫视") -> "shenzhen"
            cleanName.contains("四川卫视") -> "sichuan"
            cleanName.contains("河南卫视") -> "henan"
            cleanName.contains("福建") || cleanName.contains("东南卫视") -> "dongnan"
            cleanName.contains("贵州卫视") -> "guizhou"
            cleanName.contains("江西卫视") -> "jiangxi"
            cleanName.contains("辽宁卫视") -> "liaoning"
            cleanName.contains("安徽卫视") -> "anhui"
            cleanName.contains("河北卫视") -> "hebei"
            cleanName.contains("山东卫视") -> "shandong"
            cleanName.contains("天津卫视") -> "tianjin"

            else -> null
        }
    }

    /**
     * 从 CNTV 接口抓取当天的全量真实节目排期
     */
    suspend fun fetchDayPrograms(
        channelId: String,
        channelName: String,
        date: Date = Date()
    ): List<EpgProgram> = withContext(dispatchers.io) {
        val code = resolveCntvChannelCode(channelName) ?: return@withContext emptyList()
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
        val dateStr = dateFormat.format(date)

        val url = "http://api.cntv.cn/epg/getEpgInfoByChannelNew?c=$code&serviceId=tvcctv&d=$dateStr"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) LiveStream/1.0")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val dataObj = json.optJSONObject("data") ?: return@withContext emptyList()
                val channelObj = dataObj.optJSONObject(code) ?: return@withContext emptyList()
                val listArray = channelObj.optJSONArray("list") ?: return@withContext emptyList()

                val result = mutableListOf<EpgProgram>()
                for (i in 0 until listArray.length()) {
                    val item = listArray.getJSONObject(i)
                    val title = item.optString("title").trim()
                    val startSec = item.optLong("startTime")
                    val endSec = item.optLong("endTime")

                    if (title.isNotEmpty() && startSec > 0 && endSec > startSec) {
                        result.add(
                            EpgProgram(
                                id = "${channelId}_$startSec",
                                channelId = channelId,
                                title = title,
                                startTimestamp = startSec * 1000L,
                                endTimestamp = endSec * 1000L,
                                description = item.optString("showTime")
                            )
                        )
                    }
                }
                result
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
