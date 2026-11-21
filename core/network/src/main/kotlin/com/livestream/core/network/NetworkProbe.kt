package com.livestream.core.network

import com.livestream.core.network.di.ProbeOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProbeResult {
    data class Success(
        val latencyMs: Long,
        val statusCode: Int,
        val videoWidth: Int? = null,
        val videoHeight: Int? = null
    ) : ProbeResult
    data class Failure(val reason: String, val throwable: Throwable? = null) : ProbeResult
}

@Singleton
class NetworkProbe @Inject constructor(
    @ProbeOkHttpClient private val okHttpClient: OkHttpClient
) {
    companion object {
        private val HLS_RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
    }

    suspend fun probeUrl(
        url: String,
        userAgent: String? = null,
        referer: String? = null
    ): ProbeResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val headRequest = Request.Builder()
            .url(url)
            .head()
            .applyProbeHeaders(userAgent, referer)
            .build()

        try {
            val headResponse = okHttpClient.newCall(headRequest).execute()
            val latency = System.currentTimeMillis() - start
            val code = headResponse.code
            val isSuccess = headResponse.isSuccessful || headResponse.isRedirect
            headResponse.close()

            if (isSuccess) {
                return@withContext ProbeResult.Success(
                    latencyMs = latency,
                    statusCode = code
                )
            }

            // 针对禁用 HEAD 请求（返回 405 Method Not Allowed 或 403 Forbidden）的流媒体服务器，使用轻量 Range GET 兜底探测
            if (code == 405 || code == 403 || code == 400) {
                val getStart = System.currentTimeMillis()
                val getRequest = Request.Builder()
                    .url(url)
                    .applyProbeHeaders(userAgent, referer)
                    .header("Range", "bytes=0-1024")
                    .build()

                okHttpClient.newCall(getRequest).execute().use { getResponse ->
                    val getLatency = System.currentTimeMillis() - getStart
                    val getCode = getResponse.code
                    if (getResponse.isSuccessful || getResponse.isRedirect || getCode == 206) {
                        val bodyText = getResponse.body?.string().orEmpty()
                        val resMatch = HLS_RESOLUTION_REGEX.find(bodyText)
                        val realW = resMatch?.groupValues?.get(1)?.toIntOrNull()
                        val realH = resMatch?.groupValues?.get(2)?.toIntOrNull()
                        return@withContext ProbeResult.Success(
                            latencyMs = getLatency,
                            statusCode = getCode,
                            videoWidth = realW,
                            videoHeight = realH
                        )
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

    private fun Request.Builder.applyProbeHeaders(userAgent: String?, referer: String?): Request.Builder {
        header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: "LiveStream-Android/1.0")
        if (!referer.isNullOrBlank()) {
            header("Referer", referer)
        }
        return this
    }
}
