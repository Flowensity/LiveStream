package com.livestream.core.network

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkProbeTest {

    @Test
    fun `probeUrl returns success when HEAD request succeeds`() = runTest {
        val okHttpClient = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com/live.m3u8").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()

        every { okHttpClient.newCall(match { it.method == "HEAD" }) } returns call
        every { call.execute() } returns response

        val probe = NetworkProbe(okHttpClient)
        val result = probe.probeUrl("https://example.com/live.m3u8")

        assertTrue(result is ProbeResult.Success)
        assertEquals(200, (result as ProbeResult.Success).statusCode)
    }

    @Test
    fun `probeUrl falls back to Range GET when HEAD returns 405 Method Not Allowed`() = runTest {
        val okHttpClient = mockk<OkHttpClient>()
        val headCall = mockk<Call>()
        val headResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/live.m3u8").build())
            .protocol(Protocol.HTTP_1_1)
            .code(405)
            .message("Method Not Allowed")
            .body("".toResponseBody())
            .build()

        val getCall = mockk<Call>()
        val getResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/live.m3u8").build())
            .protocol(Protocol.HTTP_1_1)
            .code(206)
            .message("Partial Content")
            .body("partial-bytes".toResponseBody())
            .build()

        every { okHttpClient.newCall(match { it.method == "HEAD" }) } returns headCall
        every { headCall.execute() } returns headResponse

        every { okHttpClient.newCall(match { it.method == "GET" && it.header("Range") == "bytes=0-1024" }) } returns getCall
        every { getCall.execute() } returns getResponse

        val probe = NetworkProbe(okHttpClient)
        val result = probe.probeUrl("https://example.com/live.m3u8")

        assertTrue(result is ProbeResult.Success)
        assertEquals(206, (result as ProbeResult.Success).statusCode)
    }

    @Test
    fun `probeUrl returns failure when both HEAD and Range GET fail`() = runTest {
        val okHttpClient = mockk<OkHttpClient>()
        val headCall = mockk<Call>()
        val headResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/live.m3u8").build())
            .protocol(Protocol.HTTP_1_1)
            .code(405)
            .message("Method Not Allowed")
            .body("".toResponseBody())
            .build()

        val getCall = mockk<Call>()
        val getResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/live.m3u8").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body("error".toResponseBody())
            .build()

        every { okHttpClient.newCall(match { it.method == "HEAD" }) } returns headCall
        every { headCall.execute() } returns headResponse

        every { okHttpClient.newCall(match { it.method == "GET" }) } returns getCall
        every { getCall.execute() } returns getResponse

        val probe = NetworkProbe(okHttpClient)
        val result = probe.probeUrl("https://example.com/live.m3u8")

        assertTrue(result is ProbeResult.Failure)
        assertTrue((result as ProbeResult.Failure).reason.contains("500"))
    }
}
