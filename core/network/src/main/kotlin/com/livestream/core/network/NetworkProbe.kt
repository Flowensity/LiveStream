package com.livestream.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProbeResult {
    data class Success(val latencyMs: Long, val statusCode: Int) : ProbeResult
    data class Failure(val reason: String, val throwable: Throwable? = null) : ProbeResult
}

@Singleton
class NetworkProbe @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun probeUrl(url: String): ProbeResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val headRequest = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "LiveStream-Android/1.0")
            .build()

        try {
            val headResponse = okHttpClient.newCall(headRequest).execute()
            val latency = System.currentTimeMillis() - start
            val code = headResponse.code
            val isSuccess = headResponse.isSuccessful || headResponse.isRedirect

            if (isSuccess) {
                headResponse.close()
                return@withContext ProbeResult.Success(latencyMs = latency, statusCode = code)
            }
            headResponse.close()

            // 针对禁用 HEAD 请求（返回 405 Method Not Allowed 或 403 Forbidden）的流媒体服务器，使用轻量 Range GET 兜底探测
            if (code == 405 || code == 403 || code == 400) {
                val getStart = System.currentTimeMillis()
                val getRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LiveStream-Android/1.0")
                    .header("Range", "bytes=0-1024")
                    .build()

                okHttpClient.newCall(getRequest).execute().use { getResponse ->
                    val getLatency = System.currentTimeMillis() - getStart
                    val getCode = getResponse.code
                    if (getResponse.isSuccessful || getResponse.isRedirect || getCode == 206) {
                        return@withContext ProbeResult.Success(latencyMs = getLatency, statusCode = getCode)
                    } else {
                        return@withContext ProbeResult.Failure("HTTP状态码异常: $getCode")
                    }
                }
            }

            ProbeResult.Failure("HTTP状态码异常: $code")
        } catch (e: IOException) {
            ProbeResult.Failure("网络连接失败: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            ProbeResult.Failure("非法URL: ${e.message}", e)
        }
    }
}
