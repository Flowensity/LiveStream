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
        val request = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "LiveStream-Android/1.0")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (response.isSuccessful || response.isRedirect) {
                    ProbeResult.Success(latencyMs = latency, statusCode = response.code)
                } else {
                    ProbeResult.Failure("HTTP状态码异常: ${response.code}")
                }
            }
        } catch (e: IOException) {
            ProbeResult.Failure("网络连接失败: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            ProbeResult.Failure("非法URL: ${e.message}", e)
        }
    }
}
