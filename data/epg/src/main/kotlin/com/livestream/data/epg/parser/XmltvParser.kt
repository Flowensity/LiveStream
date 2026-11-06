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
        return parse(InputSource(StringReader(xmlContent)))
    }

    /**
     * 流式解析 XMLTV 输入流（适合大文件流式导入，避免一次性全部加载至内存）
     */
    fun parse(inputStream: InputStream): List<EpgProgram> {
        return parse(InputSource(inputStream))
    }

    private fun parse(inputSource: InputSource): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
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
                            // 使用确定性唯一 ID: channelId_startTimestamp
                            val programId = "${channel}_$currentStart"
                            programs.add(
                                EpgProgram(
                                    id = programId,
                                    channelId = channel,
                                    title = title,
                                    description = currentDesc?.toString()?.trim(),
                                    startTimestamp = currentStart,
                                    endTimestamp = currentStop
                                )
                            )
                        }
                    }
                }
            }
        }

        parser.parse(inputSource, handler)
        return programs
    }

    private fun parseTimestamp(timeString: String?): Long {
        if (timeString.isNullOrBlank()) return 0L
        val trimmed = timeString.trim()
        return try {
            if (trimmed.contains(" ") || trimmed.contains("+") || trimmed.contains("-")) {
                DATE_FORMAT_WITH_TZ.get()?.parse(trimmed)?.time ?: 0L
            } else {
                DATE_FORMAT_WITHOUT_TZ.get()?.parse(trimmed)?.time ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}
