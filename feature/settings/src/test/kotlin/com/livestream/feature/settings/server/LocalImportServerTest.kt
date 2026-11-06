package com.livestream.feature.settings.server

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class LocalImportServerTest {

    private lateinit var server: LocalImportServer
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Before
    fun setup() {
        server = LocalImportServer()
    }

    @After
    fun teardown() {
        server.stop()
    }

    @Test
    fun testPushChineseContentSucceedsWithoutBlocking() = runBlocking {
        var receivedContent: String? = null
        val serverUrl = server.start { content ->
            receivedContent = content
            Result.success(10)
        }
        assertNotNull(serverUrl)

        val port = server.currentPort
        assertTrue("Port should be assigned", port != null && port > 0)

        // 中文字符串在 UTF-8 下每个中文占 3 字节，此请求体字符数远小于字节数
        val chinesePayload = """#EXTM3U
#EXTINF:-1 tvg-name="CCTV-1 综合" group-title="央视频道",CCTV-1 综合
http://example.com/cctv1.m3u8
#EXTINF:-1 tvg-name="湖南卫视 高清" group-title="地方卫视",湖南卫视 高清
http://example.com/hunan.m3u8"""

        val escapedPayload = chinesePayload
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        val jsonBody = """{"content":"$escapedPayload"}"""
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("http://127.0.0.1:$port/import")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        assertTrue(response.isSuccessful)
        val responseBody = response.body!!.string()
        assertTrue(responseBody.contains(""""success":true"""))
        assertTrue(responseBody.contains(""""count":10"""))
        assertEquals(chinesePayload, receivedContent)
    }

    @Test
    fun testOversizedContentLengthReturns413WithoutOom() = runBlocking {
        server.start { Result.success(1) }
        val port = server.currentPort!!

        java.net.Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 3000
            val out = socket.getOutputStream()
            val rawRequest = "POST /import HTTP/1.1\r\n" +
                "Host: 127.0.0.1:$port\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${LocalImportServer.MAX_BODY_BYTES + 1024}\r\n\r\n"
            out.write(rawRequest.toByteArray(Charsets.UTF_8))
            out.flush()

            val responseText = socket.getInputStream().bufferedReader().readLine() ?: ""
            assertTrue("Expected HTTP 413 response, got: $responseText", responseText.contains("413"))
        }
    }

    @Test
    fun testWebConsoleChannelAndRemoteEndpoints() = runBlocking {
        var playedChannelId: String? = null
        var remoteCmd: String? = null

        server.registerActions(object : WebConsoleActions {
            override suspend fun getChannels(): List<WebChannelInfo> {
                return listOf(
                    WebChannelInfo(
                        id = "cctv1",
                        name = "CCTV-1 综合",
                        channelNumber = 1,
                        categoryId = "cctv",
                        isFavorite = true,
                        isPlaying = true
                    )
                )
            }

            override suspend fun playChannel(channelId: String): Boolean {
                playedChannelId = channelId
                return true
            }

            override suspend fun toggleFavorite(channelId: String): Boolean = true

            override suspend fun executeRemoteCommand(command: String): Boolean {
                remoteCmd = command
                return true
            }
        })
        server.start { Result.success(1) }
        val port = server.currentPort!!

        // 1. GET /api/channels
        val listReq = Request.Builder().url("http://127.0.0.1:$port/api/channels").get().build()
        val listResp = okHttpClient.newCall(listReq).execute()
        assertTrue(listResp.isSuccessful)
        val listBody = listResp.body!!.string()
        assertTrue(listBody.contains("CCTV-1 综合"))

        // 2. POST /api/play
        val playBody = """{"channelId":"cctv1"}""".toRequestBody("application/json".toMediaType())
        val playReq = Request.Builder().url("http://127.0.0.1:$port/api/play").post(playBody).build()
        val playResp = okHttpClient.newCall(playReq).execute()
        assertTrue(playResp.isSuccessful)
        assertEquals("cctv1", playedChannelId)

        // 3. POST /api/remote
        val cmdBody = """{"command":"ASPECT_RATIO"}""".toRequestBody("application/json".toMediaType())
        val cmdReq = Request.Builder().url("http://127.0.0.1:$port/api/remote").post(cmdBody).build()
        val cmdResp = okHttpClient.newCall(cmdReq).execute()
        assertTrue(cmdResp.isSuccessful)
        assertEquals("ASPECT_RATIO", remoteCmd)
    }
}
