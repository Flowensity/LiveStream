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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalImportServer @Inject constructor() {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var currentPort: Int? = null
        private set

    companion object {
        const val DEFAULT_PORT = 52026

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
     * 启动嵌入式局域网推送 HTTP 服务
     */
    fun start(onContentReceived: suspend (String) -> Result<Int>): String? {
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
    }

    private suspend fun handleClient(
        socket: Socket,
        onContentReceived: suspend (String) -> Result<Int>
    ) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                s.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val output = s.getOutputStream()

                val requestLine = reader.readLine() ?: return@use
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@use
                val method = parts[0].uppercase()
                val path = parts[1]

                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrBlank()) break
                    val header = line!!
                    val colon = header.indexOf(':')
                    if (colon != -1) {
                        val headerName = header.substring(0, colon).trim()
                        val headerValue = header.substring(colon + 1).trim()
                        if (headerName.equals("Content-Length", ignoreCase = true)) {
                            contentLength = headerValue.toIntOrNull() ?: 0
                        }
                    }
                }

                when {
                    method == "OPTIONS" -> {
                        sendResponse(output, 200, "OK", "text/plain", "")
                    }
                    method == "GET" && (path == "/" || path.startsWith("/index")) -> {
                        sendResponse(output, 200, "OK", "text/html; charset=utf-8", HTML_PAGE)
                    }
                    method == "POST" && path.startsWith("/import") -> {
                        val body = if (contentLength > 0) {
                            val buffer = CharArray(contentLength)
                            var readTotal = 0
                            while (readTotal < contentLength) {
                                val r = reader.read(buffer, readTotal, contentLength - readTotal)
                                if (r == -1) break
                                readTotal += r
                            }
                            String(buffer, 0, readTotal)
                        } else {
                            ""
                        }

                        val content = try {
                            val json = JSONObject(body)
                            json.optString("content", body)
                        } catch (_: Exception) {
                            body
                        }.trim()

                        if (content.isEmpty()) {
                            val errJson = JSONObject().put("success", false).put("message", "内容不能为空").toString()
                            sendResponse(output, 400, "Bad Request", "application/json; charset=utf-8", errJson)
                        } else {
                            val result = onContentReceived(content)
                            if (result.isSuccess) {
                                val count = result.getOrNull() ?: 0
                                val succJson = JSONObject()
                                    .put("success", true)
                                    .put("count", count)
                                    .put("message", "成功导入 $count 个频道")
                                    .toString()
                                sendResponse(output, 200, "OK", "application/json; charset=utf-8", succJson)
                            } else {
                                val err = result.exceptionOrNull()?.message ?: "解析失败"
                                val errJson = JSONObject()
                                    .put("success", false)
                                    .put("message", err)
                                    .toString()
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
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
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
  <title>LiveStream - 电视 M3U 导入推送</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: #121212;
      color: #e0e0e0;
      padding: 24px;
      display: flex;
      flex-direction: column;
      align-items: center;
      min-height: 100vh;
    }
    .card {
      background: #1e1e1e;
      border-radius: 16px;
      padding: 24px;
      width: 100%;
      max-width: 480px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.5);
    }
    h1 { font-size: 20px; font-weight: 700; color: #ffffff; margin-bottom: 8px; }
    p.desc { font-size: 13px; color: #9e9e9e; margin-bottom: 20px; line-height: 1.5; }
    label { font-size: 14px; font-weight: 600; color: #ffd200; display: block; margin-bottom: 8px; }
    textarea {
      width: 100%;
      height: 180px;
      background: #2a2a2a;
      border: 1px solid #444;
      border-radius: 10px;
      color: #fff;
      padding: 12px;
      font-size: 13px;
      line-height: 1.4;
      resize: vertical;
      outline: none;
    }
    textarea:focus { border-color: #ffd200; }
    .btn-group { display: flex; gap: 12px; margin-top: 16px; }
    button {
      flex: 1;
      padding: 14px 0;
      border: none;
      border-radius: 10px;
      font-size: 15px;
      font-weight: 700;
      cursor: pointer;
      transition: opacity 0.2s;
    }
    .btn-primary { background: #ffd200; color: #000; }
    .btn-secondary { background: #333; color: #e0e0e0; }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    #status {
      margin-top: 16px;
      padding: 12px;
      border-radius: 8px;
      font-size: 13px;
      text-align: center;
      display: none;
    }
    .status-success { background: #1b5e20; color: #a5d6a7; display: block !important; }
    .status-error { background: #b71c1c; color: #ef9a9a; display: block !important; }
    .status-loading { background: #333; color: #ffd200; display: block !important; }
  </style>
</head>
<body>
  <div class="card">
    <h1>📺 LiveStream 电视推送</h1>
    <p class="desc">在同一 Wi-Fi 局域网内，直接粘贴 M3U 订阅 URL 或播放列表内容推送到电视。</p>
    <label for="content">M3U 订阅链接 (URL) 或 文本内容：</label>
    <textarea id="content" placeholder="输入 M3U 订阅网址 (如 https://example.com/tv.m3u)&#10;或直接粘贴 #EXTM3U 文本内容..."></textarea>
    <div class="btn-group">
      <button class="btn-secondary" onclick="pasteContent()">📋 粘贴剪贴板</button>
      <button class="btn-primary" id="submitBtn" onclick="sendToTv()">🚀 推送到电视</button>
    </div>
    <div id="status"></div>
  </div>
  <script>
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
        status.className = 'status-error';
        status.textContent = '请输入 M3U 链接或播放列表内容';
        return;
      }
      btn.disabled = true;
      status.className = 'status-loading';
      status.textContent = '正在推送到电视并校验解析中...';
      try {
        var res = await fetch('/import', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ content: content })
        });
        var data = await res.json();
        if (data.success) {
          status.className = 'status-success';
          status.textContent = '🎉 ' + (data.message || '成功推送到电视！');
        } else {
          status.className = 'status-error';
          status.textContent = '❌ ' + (data.message || '导入失败');
        }
      } catch(e) {
        status.className = 'status-error';
        status.textContent = '❌ 连接失败：' + e.message;
      } finally {
        btn.disabled = false;
      }
    }
  </script>
</body>
</html>"""
