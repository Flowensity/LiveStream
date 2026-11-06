package com.livestream.feature.settings.server

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

data class WebChannelInfo(
    val id: String,
    val name: String,
    val channelNumber: Int,
    val categoryId: String,
    val isFavorite: Boolean,
    val isPlaying: Boolean
)

interface WebConsoleActions {
    suspend fun getChannels(): List<WebChannelInfo>
    suspend fun playChannel(channelId: String): Boolean
    suspend fun toggleFavorite(channelId: String): Boolean
    suspend fun executeRemoteCommand(command: String): Boolean
}

@Singleton
class LocalImportServer @Inject constructor() {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var consoleActions: WebConsoleActions? = null

    var currentPort: Int? = null
        private set

    var currentUrl: String? = null
        private set

    fun registerActions(actions: WebConsoleActions?) {
        this.consoleActions = actions
    }

    companion object {
        const val DEFAULT_PORT = 52026
        const val MAX_HEADER_BYTES = 8 * 1024
        const val MAX_BODY_BYTES = 10 * 1024 * 1024

        /**
         * 获取当前设备的局域网 IPv4 地址 (Wi-Fi 或 Ethernet)
         */
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                // 优先选取 wlan 或 eth
                val sortedInterfaces = interfaces.sortedByDescending {
                    when {
                        it.name.startsWith("wlan") -> 3
                        it.name.startsWith("eth") -> 2
                        else -> 1
                    }
                }
                for (intf in sortedInterfaces) {
                    if (intf.isLoopback || !intf.isUp) continue
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            if (!host.startsWith("127.")) {
                                return host
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        /**
         * 使用 ZXing 生成高清晰度二维码 Bitmap
         */
        fun generateQrCodeBitmap(text: String, size: Int = 512): Bitmap {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }

    /**
     * 启动嵌入式局域网推送与 Web 管理后台 HTTP 服务（若已在运行则直接复用，确保关闭二维码弹窗后手机仍可遥控）
     */
    fun start(onContentReceived: suspend (String) -> Result<Int>): String? {
        val existingSocket = serverSocket
        if (existingSocket != null && !existingSocket.isClosed && serverJob?.isActive == true) {
            val ip = getLocalIpAddress() ?: "127.0.0.1"
            val url = "http://$ip:${existingSocket.localPort}/"
            currentUrl = url
            return url
        }

        stop()

        val ss = try {
            ServerSocket(DEFAULT_PORT)
        } catch (_: Exception) {
            try {
                ServerSocket(0)
            } catch (e: Exception) {
                return null
            }
        }

        serverSocket = ss
        currentPort = ss.localPort

        val ip = getLocalIpAddress() ?: "127.0.0.1"
        val serverUrl = "http://$ip:${ss.localPort}/"
        currentUrl = serverUrl

        serverJob = scope.launch {
            while (isActive && !ss.isClosed) {
                try {
                    val socket = ss.accept()
                    launch {
                        handleClient(socket, onContentReceived)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }

        return serverUrl
    }

    /**
     * 关闭局域网服务
     */
    fun stop() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        currentPort = null
        currentUrl = null
    }

    private suspend fun handleClient(
        socket: Socket,
        onContentReceived: suspend (String) -> Result<Int>
    ) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                s.soTimeout = 10000
                val input = s.getInputStream()
                val output = s.getOutputStream()

                // 1. 读取 HTTP 头部字节序列（以 CRLFCRLF 或 LFLF 为界，限制最大 8KB 防止恶意长连接耗尽内存）
                val headerBytes = ByteArrayOutputStream()
                var b1: Int; var b2 = -1; var b3 = -1; var b4 = -1
                var headerDone = false

                while (!headerDone && headerBytes.size() < MAX_HEADER_BYTES) {
                    val b = input.read()
                    if (b == -1) break
                    headerBytes.write(b)
                    b1 = b2
                    b2 = b3
                    b3 = b4
                    b4 = b
                    if (b1 == '\r'.code && b2 == '\n'.code && b3 == '\r'.code && b4 == '\n'.code) {
                        headerDone = true
                    } else if (b3 == '\n'.code && b4 == '\n'.code) {
                        headerDone = true
                    }
                }
                if (!headerDone && headerBytes.size() >= MAX_HEADER_BYTES) {
                    val errJson = buildJsonResponse(success = false, message = "HTTP 请求头过大")
                    sendResponse(output, 431, "Request Header Fields Too Large", "application/json; charset=utf-8", errJson)
                    return@use
                }

                val headerText = headerBytes.toString(StandardCharsets.UTF_8.name())
                val lines = headerText.lines()
                if (lines.isEmpty()) return@use
                val requestLine = lines[0].trim()
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@use
                val method = parts[0].uppercase()
                val path = parts[1]

                var contentLength = 0
                for (line in lines) {
                    val colon = line.indexOf(':')
                    if (colon != -1) {
                        val headerName = line.substring(0, colon).trim()
                        val headerValue = line.substring(colon + 1).trim()
                        if (headerName.equals("Content-Length", ignoreCase = true)) {
                            contentLength = headerValue.toIntOrNull() ?: -1
                        }
                    }
                }

                fun readBody(): String {
                    if (contentLength <= 0) return ""
                    val bodyBytes = ByteArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = input.read(bodyBytes, readTotal, contentLength - readTotal)
                        if (r == -1) break
                        readTotal += r
                    }
                    return String(bodyBytes, 0, readTotal, StandardCharsets.UTF_8)
                }

                when {
                    method == "OPTIONS" -> {
                        sendResponse(output, 200, "OK", "text/plain", "")
                    }
                    method == "GET" && (path == "/" || path.startsWith("/index")) -> {
                        sendResponse(output, 200, "OK", "text/html; charset=utf-8", HTML_PAGE)
                    }
                    method == "GET" && path.startsWith("/api/channels") -> {
                        val list = consoleActions?.getChannels() ?: emptyList()
                        val jsonArr = list.joinToString(",") { ch ->
                            """{"id":"${escapeJson(ch.id)}","name":"${escapeJson(ch.name)}","number":${ch.channelNumber},"categoryId":"${escapeJson(ch.categoryId)}","favorite":${ch.isFavorite},"playing":${ch.isPlaying}}"""
                        }
                        val respJson = """{"success":true,"channels":[$jsonArr]}"""
                        sendResponse(output, 200, "OK", "application/json; charset=utf-8", respJson)
                    }
                    method == "POST" && path.startsWith("/api/play") -> {
                        if (contentLength < 0 || contentLength > MAX_HEADER_BYTES) {
                            sendResponse(output, 413, "Payload Too Large", "application/json; charset=utf-8", buildJsonResponse(false, "Payload Too Large"))
                            return@use
                        }
                        val channelId = extractJsonField(readBody(), "channelId").trim()
                        val ok = if (channelId.isNotEmpty()) consoleActions?.playChannel(channelId) ?: false else false
                        val msg = if (ok) "已在电视端切换频道" else "未找到指定频道"
                        sendResponse(output, 200, "OK", "application/json; charset=utf-8", buildJsonResponse(ok, msg))
                    }
                    method == "POST" && path.startsWith("/api/favorite") -> {
                        if (contentLength < 0 || contentLength > MAX_HEADER_BYTES) {
                            sendResponse(output, 413, "Payload Too Large", "application/json; charset=utf-8", buildJsonResponse(false, "Payload Too Large"))
                            return@use
                        }
                        val channelId = extractJsonField(readBody(), "channelId").trim()
                        val ok = if (channelId.isNotEmpty()) consoleActions?.toggleFavorite(channelId) ?: false else false
                        val msg = if (ok) "已更新收藏状态" else "更新收藏状态失败"
                        sendResponse(output, 200, "OK", "application/json; charset=utf-8", buildJsonResponse(ok, msg))
                    }
                    method == "POST" && path.startsWith("/api/remote") -> {
                        if (contentLength < 0 || contentLength > MAX_HEADER_BYTES) {
                            sendResponse(output, 413, "Payload Too Large", "application/json; charset=utf-8", buildJsonResponse(false, "Payload Too Large"))
                            return@use
                        }
                        val cmd = extractJsonField(readBody(), "command").trim()
                        val ok = if (cmd.isNotEmpty()) consoleActions?.executeRemoteCommand(cmd) ?: false else false
                        val msg = if (ok) "指令已执行" else "指令不支持"
                        sendResponse(output, 200, "OK", "application/json; charset=utf-8", buildJsonResponse(ok, msg))
                    }
                    method == "POST" && path.startsWith("/import") -> {
                        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                            val errJson = buildJsonResponse(success = false, message = "导入数据大小超出上限 (最大 10MB)")
                            sendResponse(output, 413, "Payload Too Large", "application/json; charset=utf-8", errJson)
                            return@use
                        }
                        val body = readBody()
                        val content = extractContentField(body).trim()

                        if (content.isEmpty()) {
                            val errJson = buildJsonResponse(success = false, message = "内容不能为空")
                            sendResponse(output, 400, "Bad Request", "application/json; charset=utf-8", errJson)
                        } else {
                            val result = onContentReceived(content)
                            if (result.isSuccess) {
                                val count = result.getOrNull() ?: 0
                                val succJson = buildJsonResponse(
                                    success = true,
                                    count = count,
                                    message = "成功导入 $count 个频道"
                                )
                                sendResponse(output, 200, "OK", "application/json; charset=utf-8", succJson)
                            } else {
                                val err = result.exceptionOrNull()?.message ?: "解析失败"
                                val errJson = buildJsonResponse(success = false, message = err)
                                sendResponse(output, 200, "OK", "application/json; charset=utf-8", errJson)
                            }
                        }
                    }
                    else -> {
                        sendResponse(output, 404, "Not Found", "text/plain", "Not Found")
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun escapeJson(str: String): String = buildString {
        for (ch in str) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    internal fun buildJsonResponse(success: Boolean, message: String, count: Int? = null): String {
        val escapedMsg = escapeJson(message)
        return if (count != null) {
            """{"success":$success,"count":$count,"message":"$escapedMsg"}"""
        } else {
            """{"success":$success,"message":"$escapedMsg"}"""
        }
    }

    internal fun extractContentField(body: String): String {
        return extractJsonField(body, "content", fallbackToRaw = true)
    }

    internal fun extractJsonField(body: String, fieldName: String, fallbackToRaw: Boolean = false): String {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return if (fallbackToRaw) body else ""
        return try {
            val json = JSONObject(trimmed)
            json.optString(fieldName, if (fallbackToRaw) body else "")
        } catch (_: Throwable) {
            val match = Regex(""""$fieldName"\s*:\s*"((?:\\.|[^"\\])*)"""").find(trimmed)
            if (match != null) {
                unescapeJsonString(match.groupValues[1])
            } else {
                if (fallbackToRaw) body else ""
            }
        }
    }

    private fun unescapeJsonString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val hex = raw.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                            } else {
                                sb.append(c)
                                i++
                            }
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }
}

private const val HTML_PAGE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>LiveStream · 智能电视遥控器</title>
  <style>
    :root {
      --bg: #101114;
      --panel: #171920;
      --card: #1C1D24;
      --card-sec: #252630;
      --pill: #2F313D;
      --text-pri: #F1F1F5;
      --text-sec: #A7A9B2;
      --text-muted: #7A7D87;
      --accent: #3898FF;
      --accent-soft: rgba(56, 152, 255, 0.18);
      --border: rgba(255, 255, 255, 0.10);
      --success-bg: rgba(30, 158, 82, 0.22);
      --success-fg: #6EE7B7;
      --danger-bg: rgba(255, 84, 73, 0.22);
      --danger-fg: #FCA5A5;
    }
    @media (prefers-color-scheme: light) {
      :root {
        --bg: #F5F6FA;
        --panel: #FFFFFF;
        --card: #FFFFFF;
        --card-sec: #EEF1F7;
        --pill: #E6EAF2;
        --text-pri: #191B20;
        --text-sec: #5C606A;
        --text-muted: #74777F;
        --accent: #0381FE;
        --accent-soft: rgba(3, 129, 254, 0.12);
        --border: rgba(25, 27, 32, 0.09);
        --success-bg: rgba(30, 158, 82, 0.14);
        --success-fg: #15803D;
        --danger-bg: rgba(219, 51, 42, 0.14);
        --danger-fg: #B91C1C;
      }
    }
    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Segoe UI", Roboto, sans-serif;
      background: var(--bg);
      color: var(--text-pri);
      padding: 14px;
      display: flex;
      flex-direction: column;
      align-items: center;
      min-height: 100vh;
    }
    .container { width: 100%; max-width: 500px; }
    .header-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 6px 4px 12px 4px;
    }
    .brand-title { font-size: 18px; font-weight: 800; letter-spacing: 0.2px; }
    .live-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--accent-soft);
      color: var(--accent);
      font-size: 12px;
      font-weight: 700;
      padding: 5px 12px;
      border-radius: 999px;
      max-width: 62%;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .live-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--accent);
      flex-shrink: 0;
    }
    .tabs {
      display: flex;
      background: var(--card-sec);
      border: 1px solid var(--border);
      border-radius: 999px;
      padding: 4px;
      margin-bottom: 14px;
    }
    .tab-btn {
      flex: 1;
      padding: 9px 0;
      border: none;
      background: transparent;
      color: var(--text-sec);
      font-size: 13.5px;
      font-weight: 700;
      border-radius: 999px;
      cursor: pointer;
      transition: all 0.18s;
    }
    .tab-btn.active {
      background: var(--accent);
      color: #FFFFFF;
      box-shadow: 0 2px 10px rgba(3, 129, 254, 0.30);
    }
    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 16px;
      margin-bottom: 12px;
    }
    .section-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 10px;
    }
    h2 { font-size: 15px; font-weight: 700; color: var(--text-pri); }
    .sub-hint { font-size: 11.5px; color: var(--text-sec); }
    .remote-rocker {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
      margin-bottom: 10px;
    }
    .btn-rocker {
      background: var(--accent-soft);
      border: 1px solid var(--accent);
      color: var(--accent);
      padding: 15px 12px;
      border-radius: 16px;
      font-size: 15px;
      font-weight: 800;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
    }
    .btn-rocker:active { transform: scale(0.97); background: var(--accent); color: #FFF; }
    .remote-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 8px;
    }
    button {
      border: none;
      border-radius: 999px;
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
      transition: transform 0.12s, opacity 0.15s;
    }
    button:active { transform: scale(0.96); }
    .btn-pill-action {
      background: var(--pill);
      color: var(--text-pri);
      padding: 11px 10px;
      border-radius: 14px;
    }
    .btn-primary {
      background: var(--accent);
      color: #FFFFFF;
      padding: 11px 16px;
      flex: 1;
    }
    .btn-secondary {
      background: var(--pill);
      color: var(--text-pri);
      padding: 11px 16px;
      flex: 1;
    }
    .cat-bar {
      display: flex;
      gap: 6px;
      overflow-x: auto;
      padding-bottom: 8px;
      margin-bottom: 8px;
      scrollbar-width: none;
    }
    .cat-bar::-webkit-scrollbar { display: none; }
    .cat-pill {
      padding: 6px 13px;
      border-radius: 999px;
      background: var(--pill);
      color: var(--text-sec);
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
      flex-shrink: 0;
    }
    .cat-pill.active {
      background: var(--accent);
      color: #FFFFFF;
      font-weight: 700;
    }
    textarea, input[type="text"] {
      width: 100%;
      background: var(--card-sec);
      border: 1px solid var(--border);
      border-radius: 14px;
      color: var(--text-pri);
      padding: 10px 14px;
      font-size: 13px;
      outline: none;
    }
    textarea { height: 150px; line-height: 1.45; resize: vertical; margin-top: 8px; }
    textarea:focus, input[type="text"]:focus { border-color: var(--accent); }
    .btn-group { display: flex; gap: 10px; margin-top: 12px; }
    .ch-list {
      margin-top: 10px;
      max-height: 400px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 7px;
    }
    .ch-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: var(--card-sec);
      border: 1.5px solid transparent;
      border-radius: 14px;
      padding: 9px 11px;
      cursor: pointer;
    }
    .ch-item.playing {
      border-color: var(--accent);
      background: var(--accent-soft);
    }
    .ch-left { display: flex; align-items: center; min-width: 0; flex: 1; margin-right: 8px; }
    .ch-num {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 30px;
      height: 22px;
      background: var(--accent-soft);
      color: var(--accent);
      font-size: 11.5px;
      font-weight: 800;
      padding: 0 6px;
      border-radius: 6px;
      margin-right: 9px;
      flex-shrink: 0;
    }
    .ch-title {
      font-size: 13.5px;
      font-weight: 650;
      color: var(--text-pri);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .ch-actions { display: flex; gap: 6px; flex-shrink: 0; }
    .btn-sm { padding: 6px 11px; font-size: 12px; border-radius: 999px; }
    .btn-fav { background: var(--pill); color: var(--text-sec); }
    .btn-fav.fav-on { color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent); }
    #status {
      position: fixed;
      bottom: 18px;
      left: 50%;
      transform: translateX(-50%);
      padding: 9px 18px;
      border-radius: 999px;
      font-size: 12.5px;
      font-weight: 700;
      text-align: center;
      display: none;
      z-index: 999;
      box-shadow: 0 6px 20px rgba(0,0,0,0.35);
    }
    .status-success { background: var(--success-bg); color: var(--success-fg); border: 1px solid var(--success-fg); display: block !important; }
    .status-error { background: var(--danger-bg); color: var(--danger-fg); border: 1px solid var(--danger-fg); display: block !important; }
    .status-loading { background: var(--card); color: var(--accent); border: 1px solid var(--accent); display: block !important; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header-bar">
      <div class="brand-title">LiveStream 遥控</div>
      <div class="live-badge" id="nowPlayingBadge">
        <span class="live-dot"></span>
        <span id="nowPlayingText">电视已连接</span>
      </div>
    </div>

    <div class="tabs">
      <button class="tab-btn active" id="tabRemoteBtn" onclick="switchTab('remote')">手机遥控与选台</button>
      <button class="tab-btn" id="tabImportBtn" onclick="switchTab('import')">M3U 源推送</button>
    </div>

    <div id="remotePane">
      <div class="card">
        <div class="section-head">
          <h2>电视快捷遥控</h2>
          <span class="sub-hint">关闭电视二维码后仍可随时控制</span>
        </div>
        <div class="remote-rocker">
          <button class="btn-rocker" onclick="sendRemote('PREV_CHANNEL')">▲ 上一频道</button>
          <button class="btn-rocker" onclick="sendRemote('NEXT_CHANNEL')">▼ 下一频道</button>
        </div>
        <div class="remote-grid">
          <button class="btn-pill-action" onclick="sendRemote('SWITCH_SOURCE')">⇄ 切换线路</button>
          <button class="btn-pill-action" onclick="sendRemote('ASPECT_RATIO')">◱ 画面比例</button>
          <button class="btn-pill-action" onclick="sendRemote('RETRY')">↻ 重试当前源</button>
          <button class="btn-pill-action" onclick="loadChannels()">⟳ 同步频道列表</button>
        </div>
      </div>

      <div class="card">
        <div class="section-head">
          <h2>频道即时点播</h2>
          <span class="sub-hint" id="chCountText">点击频道直接切换电视画面</span>
        </div>
        <div class="cat-bar" id="catBar">
          <button class="cat-pill active" onclick="selectCat('ALL')">全部频道</button>
          <button class="cat-pill" onclick="selectCat('FAV')">★ 收藏</button>
          <button class="cat-pill" onclick="selectCat('cctv')">央视</button>
          <button class="cat-pill" onclick="selectCat('satellite')">卫视</button>
          <button class="cat-pill" onclick="selectCat('custom')">自定义</button>
        </div>
        <input type="text" id="searchInput" placeholder="搜索频道名称或台号..." oninput="renderChannels()">
        <div class="ch-list" id="channelList"></div>
      </div>
    </div>

    <div id="importPane" style="display:none;">
      <div class="card">
        <div class="section-head">
          <h2>推送 M3U 播放列表</h2>
          <span class="sub-hint">自动识别 XMLTV 节目单</span>
        </div>
        <p class="sub-hint" style="margin-bottom:8px;">支持推送 M3U 订阅链接（http/https）或直接粘贴 #EXTM3U 文本内容。</p>
        <textarea id="content" placeholder="输入 M3U 订阅网址 (如 https://example.com/tv.m3u)&#10;或直接粘贴 #EXTM3U 文本内容..."></textarea>
        <div class="btn-group">
          <button class="btn-secondary" onclick="pasteContent()">粘贴剪贴板</button>
          <button class="btn-primary" id="submitBtn" onclick="sendToTv()">推送到电视</button>
        </div>
      </div>
    </div>

    <div id="status"></div>
  </div>
  <script>
    var allChannels = [];
    var currentCat = 'ALL';
    var toastTimer = null;

    function switchTab(tab) {
      document.getElementById('remotePane').style.display = tab === 'remote' ? 'block' : 'none';
      document.getElementById('importPane').style.display = tab === 'import' ? 'block' : 'none';
      document.getElementById('tabRemoteBtn').className = tab === 'remote' ? 'tab-btn active' : 'tab-btn';
      document.getElementById('tabImportBtn').className = tab === 'import' ? 'tab-btn active' : 'tab-btn';
      if (tab === 'remote') { loadChannels(); }
    }
    function selectCat(cat) {
      currentCat = cat;
      var btns = document.getElementById('catBar').children;
      var cats = ['ALL', 'FAV', 'cctv', 'satellite', 'custom'];
      for (var i = 0; i < btns.length; i++) {
        btns[i].className = (cats[i] === cat) ? 'cat-pill active' : 'cat-pill';
      }
      renderChannels();
    }
    function showToast(msg, ok) {
      var status = document.getElementById('status');
      status.className = ok ? 'status-success' : 'status-error';
      status.textContent = msg;
      if (toastTimer) clearTimeout(toastTimer);
      toastTimer = setTimeout(function() { status.style.display = 'none'; status.className = ''; }, 2400);
    }
    async function pasteContent() {
      try {
        var text = await navigator.clipboard.readText();
        if (text) { document.getElementById('content').value = text; }
      } catch(e) {
        alert('请长按输入框进行粘贴');
      }
    }
    async function sendToTv() {
      var content = document.getElementById('content').value.trim();
      var status = document.getElementById('status');
      var btn = document.getElementById('submitBtn');
      if (!content) {
        showToast('请输入 M3U 链接或播放列表内容', false);
        return;
      }
      btn.disabled = true;
      status.className = 'status-loading';
      status.textContent = '正在推送并解析频道列表，请稍候...';
      try {
        var resp = await fetch('/import', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ content: content })
        });
        var data = await resp.json();
        showToast(data.message || (data.success ? '推送成功' : '推送失败'), data.success);
        if (data.success) { loadChannels(); }
      } catch (e) {
        showToast('网络请求失败，请确认手机与电视处于同一局域网', false);
      } finally {
        btn.disabled = false;
      }
    }
    async function sendRemote(cmd) {
      try {
        var resp = await fetch('/api/remote', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ command: cmd })
        });
        var data = await resp.json();
        showToast(data.message, data.success);
        setTimeout(loadChannels, 300);
      } catch (e) {
        showToast('发送遥控指令失败', false);
      }
    }
    async function loadChannels() {
      try {
        var resp = await fetch('/api/channels');
        var data = await resp.json();
        if (data.success) {
          allChannels = data.channels || [];
          var playingCh = allChannels.find(function(c) { return c.playing; });
          var badgeText = document.getElementById('nowPlayingText');
          if (playingCh) {
            badgeText.textContent = '正在播放: ' + playingCh.name;
          } else {
            badgeText.textContent = '共 ' + allChannels.length + ' 个频道';
          }
          renderChannels();
        }
      } catch (e) {}
    }
    function renderChannels() {
      var q = (document.getElementById('searchInput').value || '').trim().toLowerCase();
      var container = document.getElementById('channelList');
      container.innerHTML = '';
      var filtered = allChannels.filter(function(c) {
        var matchCat = true;
        if (currentCat === 'FAV') matchCat = !!c.favorite;
        else if (currentCat === 'cctv') matchCat = c.categoryId === 'cctv';
        else if (currentCat === 'satellite') matchCat = c.categoryId === 'satellite';
        else if (currentCat === 'custom') matchCat = c.categoryId !== 'cctv' && c.categoryId !== 'satellite';
        var matchQuery = !q || c.name.toLowerCase().indexOf(q) !== -1 || String(c.number).indexOf(q) !== -1;
        return matchCat && matchQuery;
      });
      document.getElementById('chCountText').textContent = '共 ' + filtered.length + ' 个频道 · 点击即可切台';
      filtered.forEach(function(c) {
        var row = document.createElement('div');
        row.className = 'ch-item' + (c.playing ? ' playing' : '');
        var left = document.createElement('div');
        left.className = 'ch-left';
        left.onclick = function() { playOnTv(c.id); };
        var numSpan = document.createElement('span');
        numSpan.className = 'ch-num';
        numSpan.textContent = String(c.number).padStart(2, '0');
        var titleSpan = document.createElement('span');
        titleSpan.className = 'ch-title';
        titleSpan.textContent = c.name + (c.playing ? ' · 播放中' : '');
        left.appendChild(numSpan);
        left.appendChild(titleSpan);

        var actions = document.createElement('div');
        actions.className = 'ch-actions';
        var favBtn = document.createElement('button');
        favBtn.className = 'btn-sm btn-fav' + (c.favorite ? ' fav-on' : '');
        favBtn.textContent = c.favorite ? '★ 已藏' : '☆ 收藏';
        favBtn.onclick = function(ev) { ev.stopPropagation(); toggleFav(c.id); };

        var playBtn = document.createElement('button');
        playBtn.className = 'btn-sm btn-primary';
        playBtn.textContent = c.playing ? '当前台' : '切台';
        playBtn.onclick = function(ev) { ev.stopPropagation(); playOnTv(c.id); };

        actions.appendChild(favBtn);
        actions.appendChild(playBtn);
        row.appendChild(left);
        row.appendChild(actions);
        container.appendChild(row);
      });
    }
    async function playOnTv(channelId) {
      try {
        var resp = await fetch('/api/play', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ channelId: channelId })
        });
        var data = await resp.json();
        showToast(data.message, data.success);
        loadChannels();
      } catch (e) {}
    }
    async function toggleFav(channelId) {
      try {
        var resp = await fetch('/api/favorite', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ channelId: channelId })
        });
        var data = await resp.json();
        loadChannels();
      } catch (e) {}
    }
    loadChannels();
  </script>
</body>
</html>
"""
