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

    companion object {
        private val REGEX_CCTV_4K = Regex("""CCTV[-_ ]*4K""", RegexOption.IGNORE_CASE)
        private val REGEX_CCTV_8K = Regex("""CCTV[-_ ]*8K""", RegexOption.IGNORE_CASE)
        private val REGEX_CCTV_NUMBERED = Regex("""CCTV[-_ ]*(1[0-7]|[1-9])(?![0-9K])""", RegexOption.IGNORE_CASE)
        private val REGEX_CCTV_5_PLUS = Regex("""CCTV[-_ ]*5\+|CCTV[-_ ]*5PLUS|体育赛事""", RegexOption.IGNORE_CASE)
        private val REGEX_CCTV_NUM_EXTRACT = Regex("""(?:CCTV|中央)[-_ ]*(1[0-7]|[1-9])(?![0-9K])""", RegexOption.IGNORE_CASE)
        private val REGEX_CCTV_ZH_NUM = Regex("""中央([一二三四五六七八九十]+)台?""")
    }

    /**
     * 根据频道名称或原始 ID 解析 CNTV 官方 EPG 频源码
     */
    fun resolveCntvChannelCode(channelName: String): String? {
        if (channelName.isBlank()) return null

        // 1. 检查独立的 4K / 8K 频道 (如 CCTV-4K, CCTV-8K)
        val isCctv4k = REGEX_CCTV_4K.containsMatchIn(channelName) &&
            !REGEX_CCTV_NUMBERED.containsMatchIn(channelName)
        if (isCctv4k) return "cctv4k"

        val isCctv8k = REGEX_CCTV_8K.containsMatchIn(channelName) &&
            !REGEX_CCTV_NUMBERED.containsMatchIn(channelName)
        if (isCctv8k) return "cctv8k"

        // 2. 检查 CCTV-5+ 体育赛事
        if (REGEX_CCTV_5_PLUS.containsMatchIn(channelName)) {
            return "cctv5plus"
        }

        // 3. 检查 CCTV 1-17 号数字频道 (支持 CCTV-1, CCTV1, CCTV-16 4K, CCTV1 高清 等各种格式)
        val cctvNumMatch = REGEX_CCTV_NUM_EXTRACT.find(channelName)
        if (cctvNumMatch != null) {
            val num = cctvNumMatch.groupValues[1].toInt()
            return when (num) {
                1 -> "cctv1"
                2 -> "cctv2"
                3 -> "cctv3"
                4 -> "cctv4"
                5 -> "cctv5"
                6 -> "cctv6"
                7 -> "cctv7"
                8 -> "cctv8"
                9 -> "cctvjilu"
                10 -> "cctv10"
                11 -> "cctv11"
                12 -> "cctv12"
                13 -> "cctv13"
                14 -> "cctvchild"
                15 -> "cctv15"
                16 -> "cctv16"
                17 -> "cctv17"
                else -> null
            }
        }

        // 4. 检查中央中文数字别名 (如 中央一台, 中央十六台)
        val zhMatch = REGEX_CCTV_ZH_NUM.find(channelName)
        if (zhMatch != null) {
            val zhNum = zhMatch.groupValues[1]
            when (zhNum) {
                "一" -> return "cctv1"
                "二" -> return "cctv2"
                "三" -> return "cctv3"
                "四" -> return "cctv4"
                "五" -> return "cctv5"
                "六" -> return "cctv6"
                "七" -> return "cctv7"
                "八" -> return "cctv8"
                "九" -> return "cctvjilu"
                "十" -> return "cctv10"
                "十一" -> return "cctv11"
                "十二" -> return "cctv12"
                "十三" -> return "cctv13"
                "十四" -> return "cctvchild"
                "十五" -> return "cctv15"
                "十六" -> return "cctv16"
                "十七" -> return "cctv17"
            }
        }

        // 5. 检查带有中央/CCTV前缀的频道内容别名
        if (channelName.contains("CCTV", ignoreCase = true) || channelName.contains("中央")) {
            when {
                channelName.contains("中文国际") -> return "cctv4"
                channelName.contains("国防") || channelName.contains("军事") -> return "cctv7"
                channelName.contains("纪录") -> return "cctvjilu"
                channelName.contains("新闻") -> return "cctv13"
                channelName.contains("少儿") -> return "cctvchild"
                channelName.contains("奥林匹克") -> return "cctv16"
                channelName.contains("农业") || channelName.contains("农村") -> return "cctv17"
            }
        }

        // 6. 各省主要卫视 (自动匹配 高清, 4K, 1080P, HD 等后缀)
        val cleanName = channelName.replace(" ", "").replace("-", "").uppercase(Locale.getDefault())
        return when {
            cleanName.contains("北京卫视") || cleanName.contains("BTV北京卫视") -> "btv1"
            cleanName.contains("江苏卫视") -> "jiangsu"
            cleanName.contains("东方卫视") || cleanName.contains("上海卫视") -> "dongfang"
            cleanName.contains("浙江卫视") -> "zhejiang"
            cleanName.contains("湖南卫视") -> "hunan"
            cleanName.contains("湖北卫视") -> "hubei"
            cleanName.contains("广东卫视") -> "guangdong"
            cleanName.contains("广西卫视") -> "guangxi"
            cleanName.contains("黑龙江卫视") -> "heilongjiang"
            cleanName.contains("海南卫视") || cleanName.contains("旅游卫视") -> "hainan"
            cleanName.contains("重庆卫视") -> "chongqing"
            cleanName.contains("深圳卫视") -> "shenzhen"
            cleanName.contains("四川卫视") -> "sichuan"
            cleanName.contains("河南卫视") -> "henan"
            cleanName.contains("东南卫视") || cleanName.contains("福建东南") || cleanName.contains("福建卫视") -> "dongnan"
            cleanName.contains("贵州卫视") -> "guizhou"
            cleanName.contains("江西卫视") -> "jiangxi"
            cleanName.contains("辽宁卫视") -> "liaoning"
            cleanName.contains("安徽卫视") -> "anhui"
            cleanName.contains("河北卫视") -> "hebei"
            cleanName.contains("山东卫视") -> "shandong"
            cleanName.contains("天津卫视") -> "tianjin"
            cleanName.contains("陕西卫视") -> "shanxi1"
            cleanName.contains("吉林卫视") -> "jilin"
            cleanName.contains("内蒙古卫视") -> "neimenggu"
            cleanName.contains("新疆卫视") -> "xinjiang"
            cleanName.contains("云南卫视") -> "yunnan"
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

        val url = "https://api.cntv.cn/epg/getEpgInfoByChannelNew?c=$code&serviceId=tvcctv&d=$dateStr"

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
