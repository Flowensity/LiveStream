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
    fun `probeUrl extracts real HLS resolution from Range GET body when present`() = runTest {
        val okHttpClient = mockk<OkHttpClient>()
        val headCall = mockk<Call>()
        val headResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/master.m3u8").build())
            .protocol(Protocol.HTTP_1_1)
            .code(405)
            .message("Method Not Allowed")
            .body("".toResponseBody())
            .build()

        val hlsBody = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080
            chunklist_1080p.m3u8
        """.trimIndent()

        val getCall = mockk<Call>()
        val getResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/master.m3u8").build())
            .protocol(Protocol.HTTP_1_1)
            .code(206)
            .message("Partial Content")
            .body(hlsBody.toResponseBody())
            .build()

        every { okHttpClient.newCall(match { it.method == "HEAD" }) } returns headCall
        every { headCall.execute() } returns headResponse

        every { okHttpClient.newCall(match { it.method == "GET" && it.header("Range") == "bytes=0-1024" }) } returns getCall
        every { getCall.execute() } returns getResponse

        val probe = NetworkProbe(okHttpClient)
        val result = probe.probeUrl("https://example.com/master.m3u8")

        assertTrue(result is ProbeResult.Success)
        val success = result as ProbeResult.Success
        assertEquals(1920, success.videoWidth)
        assertEquals(1080, success.videoHeight)
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

    @Test
    fun `Ipv4FirstDns prioritizes IPv4 addresses ahead of IPv6 and caches lookups`() {
        var lookupCount = 0
        val ipv6 = java.net.InetAddress.getByAddress("example.com", ByteArray(16) { 1 })
        val ipv4 = java.net.InetAddress.getByAddress("example.com", byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        val fakeDelegate = object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                lookupCount++
                return listOf(ipv6, ipv4)
            }
        }

        val dns = Ipv4FirstDns(delegate = fakeDelegate, cacheTtlMs = 60_000L)
        val firstResult = dns.lookup("example.com")
        val secondResult = dns.lookup("example.com")

        assertEquals(2, firstResult.size)
        assertTrue(firstResult[0] is java.net.Inet4Address)
        assertTrue(firstResult[1] is java.net.Inet6Address)
        assertEquals(firstResult.size, secondResult.size)
        assertEquals(1, lookupCount)
    }

    @Test
    fun `default OkHttpClient has unlimited callTimeout for long-lived streams and downloads`() {
        val client = com.livestream.core.network.di.NetworkModule.provideOkHttpClient()
        assertEquals(0, client.callTimeoutMillis)
        assertTrue(client.readTimeoutMillis >= 10_000)

        val probeClient = com.livestream.core.network.di.NetworkModule.provideProbeOkHttpClient(client)
        assertEquals(3_000, probeClient.connectTimeoutMillis)
        assertEquals(4_000, probeClient.readTimeoutMillis)
        assertEquals(8_000, probeClient.callTimeoutMillis)
        assertTrue(probeClient.connectionPool === client.connectionPool)
        assertTrue(probeClient.dns === client.dns)
    }
}
