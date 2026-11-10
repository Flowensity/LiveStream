package com.livestream.data.epg.parser

import com.livestream.core.model.EpgProgram
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

object XmltvParser {

    private val DATE_FORMAT_WITH_TZ = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    }
    private val DATE_FORMAT_WITHOUT_TZ = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    }
    private val saxParserFactory: SAXParserFactory by lazy {
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
    }

    /**
     * 流式解析 XMLTV 格式节目单文本
     */
    fun parse(xmlContent: String): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        parseStreaming(InputSource(StringReader(xmlContent)), batchSize = 500) { batch ->
            programs.addAll(batch)
        }
        return programs
    }

    /**
     * 流式解析 XMLTV 输入流
     */
    fun parse(inputStream: InputStream): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        parseStreaming(InputSource(inputStream), batchSize = 500) { batch ->
            programs.addAll(batch)
        }
        return programs
    }

    /**
     * 分块流式解析 XMLTV 输入流（每满 batchSize 条回调一次，避免大文件一次性驻留内存引发 OOM）
     */
    fun parseStreaming(
        inputStream: InputStream,
        batchSize: Int = 500,
        onBatch: (List<EpgProgram>) -> Unit
    ) {
        parseStreaming(InputSource(inputStream), batchSize.coerceAtLeast(1), onBatch)
    }

    fun parseStreaming(
        xmlContent: String,
        batchSize: Int = 500,
        onBatch: (List<EpgProgram>) -> Unit
    ) {
        parseStreaming(InputSource(StringReader(xmlContent)), batchSize.coerceAtLeast(1), onBatch)
    }

    private fun parseStreaming(
        inputSource: InputSource,
        batchSize: Int,
        onBatch: (List<EpgProgram>) -> Unit
    ) {
        val currentBatch = ArrayList<EpgProgram>(batchSize)
        val parser = saxParserFactory.newSAXParser()

        val handler = object : DefaultHandler() {
            private var currentChannel: String? = null
            private var currentStart: Long = 0L
            private var currentStop: Long = 0L
            private var currentTitle: StringBuilder? = null
            private var currentDesc: StringBuilder? = null
            private var insideTitle = false
            private var insideDesc = false

            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes?
            ) {
                val tag = (qName ?: localName)?.lowercase(Locale.ROOT)
                when (tag) {
                    "programme" -> {
                        currentChannel = attributes?.getValue("channel")
                        val startStr = attributes?.getValue("start")
                        val stopStr = attributes?.getValue("stop")
                        currentStart = parseTimestamp(startStr)
                        currentStop = parseTimestamp(stopStr)
                        currentTitle = null
                        currentDesc = null
                    }
                    "title" -> {
                        insideTitle = true
                        currentTitle = StringBuilder()
                    }
                    "desc" -> {
                        insideDesc = true
                        currentDesc = StringBuilder()
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (insideTitle) {
                    currentTitle?.append(ch, start, length)
                } else if (insideDesc) {
                    currentDesc?.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = (qName ?: localName)?.lowercase(Locale.ROOT)
                when (tag) {
                    "title" -> insideTitle = false
                    "desc" -> insideDesc = false
                    "programme" -> {
                        val title = currentTitle?.toString()?.trim()
                        val channel = currentChannel
                        if (!channel.isNullOrEmpty() && !title.isNullOrEmpty()) {
                            val programId = "${channel}_$currentStart"
                            currentBatch.add(
                                EpgProgram(
                                    id = programId,
                                    channelId = channel,
                                    title = title,
                                    description = currentDesc?.toString()?.trim(),
                                    startTimestamp = currentStart,
                                    endTimestamp = currentStop
                                )
                            )
                            if (currentBatch.size >= batchSize) {
                                onBatch(currentBatch.toList())
                                currentBatch.clear()
                            }
                        }
                    }
                }
            }
        }

        parser.parse(inputSource, handler)
        if (currentBatch.isNotEmpty()) {
            onBatch(currentBatch.toList())
            currentBatch.clear()
        }
    }

    private fun parseTimestamp(timeString: String?): Long {
        return parseTimestampFast(timeString)
    }

    /**
     * 高性能零对象分配算术时间戳解析器：
     * 直接对标准 XMLTV 时间戳 "yyyyMMddHHmmss [±HHmm]" 进行字符位运算与公历儒略日转换，
     * 避免高频调用 SimpleDateFormat.parse 产生的百万级临时 Calendar/Date 对象分配。
     */
    internal fun parseTimestampFast(timeString: String?): Long {
        if (timeString.isNullOrBlank()) return 0L
        val s = timeString.trim()
        if (s.length >= 14 && isDigits(s, 0, 14)) {
            val year = parseDigits(s, 0, 4)
            val month = parseDigits(s, 4, 6)
            val day = parseDigits(s, 6, 8)
            val hour = parseDigits(s, 8, 10)
            val minute = parseDigits(s, 10, 12)
            val second = parseDigits(s, 12, 14)

            if (month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59 && second in 0..60) {
                var idx = 14
                while (idx < s.length && s[idx] == ' ') idx++
                // 若存在标准 ±HHmm 时区偏移，直接算术换算为 UTC 毫秒
                if (idx + 5 <= s.length && (s[idx] == '+' || s[idx] == '-') && isDigits(s, idx + 1, idx + 5)) {
                    val sign = if (s[idx] == '+') 1 else -1
                    val tzHours = parseDigits(s, idx + 1, idx + 3)
                    val tzMinutes = parseDigits(s, idx + 3, idx + 5)
                    val tzOffsetMillis = sign * ((tzHours * 3600L + tzMinutes * 60L) * 1000L)
                    val epochDays = daysFromCivil(year, month, day)
                    val utcMillis = ((epochDays * 86400L) + hour * 3600L + minute * 60L + second) * 1000L
                    return utcMillis - tzOffsetMillis
                }
            }
        }

        // 非标准格式回退至 SimpleDateFormat
        return try {
            if (s.contains(" ") || s.contains("+") || s.contains("-")) {
                DATE_FORMAT_WITH_TZ.get()?.parse(s)?.time ?: 0L
            } else {
                DATE_FORMAT_WITHOUT_TZ.get()?.parse(s)?.time ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun isDigits(s: String, start: Int, end: Int): Boolean {
        for (i in start until end) {
            val c = s[i]
            if (c < '0' || c > '9') return false
        }
        return true
    }

    private fun parseDigits(s: String, start: Int, end: Int): Int {
        var value = 0
        for (i in start until end) {
            value = value * 10 + (s[i] - '0')
        }
        return value
    }

    /**
     * Howard Hinnant 公历日期转 Unix 纪元天数 (1970-01-01 为 0) 常数时间纯整数算法
     */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val m = month.toLong()
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
}
