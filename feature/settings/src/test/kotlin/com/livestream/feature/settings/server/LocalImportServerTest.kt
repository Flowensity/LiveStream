package com.livestream.feature.settings.server

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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

        val port = server.currentPort
        assertTrue("Port should be assigned", port != null && port > 0)

        // 中文字符串在 UTF-8 下每个中文占 3 字节，此请求体字符数远小于字节数
        val chinesePayload = """#EXTM3U
#EXTINF:-1 tvg-name="CCTV-1 综合" group-title="央视频道",CCTV-1 综合
http://example.com/cctv1.m3u8
#EXTINF:-1 tvg-name="湖南卫视 高清" group-title="地方卫视",湖南卫视 高清
http://example.com/hunan.m3u8"""

        val jsonBody = JSONObject().put("content", chinesePayload).toString()
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("http://127.0.0.1:$port/import")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        assertTrue(response.isSuccessful)
        val responseJson = JSONObject(response.body!!.string())
        assertTrue(responseJson.getBoolean("success"))
        assertEquals(10, responseJson.getInt("count"))
        assertEquals(chinesePayload, receivedContent)
    }
}
