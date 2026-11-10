package com.livestream.playback.media3.datasource

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * 基于全局共享 [OkHttpClient] 的 Media3 数据源实现：
 * 使 ExoPlayer 播放流能够直接复用 [com.livestream.core.network.NetworkProbe] 探测阶段已建立的
 * TCP/TLS Keep-Alive 连接池、HTTP/2 多路复用通道以及 [com.livestream.core.network.Ipv4FirstDns] 缓存。
 */
@OptIn(UnstableApi::class)
class SharedOkHttpDataSource(
    private val callFactory: Call.Factory,
    private val defaultRequestProperties: HttpDataSource.RequestProperties
) : BaseDataSource(/* isNetwork = */ true), HttpDataSource {

    class Factory(
        private val callFactory: Call.Factory
    ) : HttpDataSource.Factory {
        private val defaultRequestProperties = HttpDataSource.RequestProperties()

        override fun createDataSource(): HttpDataSource {
            return SharedOkHttpDataSource(callFactory, defaultRequestProperties)
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties)
            return this
        }
    }

    private val requestProperties = HttpDataSource.RequestProperties()
    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseByteStream: InputStream? = null
    private var opened = false
    private var bytesToRead: Long = 0L
    private var bytesRead: Long = 0L

    override fun getUri(): Uri? = response?.request?.url?.toString()?.let { Uri.parse(it) } ?: dataSpec?.uri

    override fun getResponseCode(): Int = response?.code ?: -1

    override fun getResponseHeaders(): Map<String, List<String>> = response?.headers?.toMultimap() ?: emptyMap()

    override fun setRequestProperty(name: String, value: String) {
        requestProperties.set(name, value)
    }

    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        bytesRead = 0L
        bytesToRead = 0L
        transferInitializing(dataSpec)

        val requestBuilder = Request.Builder().url(dataSpec.uri.toString())

        val headers = HashMap<String, String>()
        headers.putAll(defaultRequestProperties.snapshot)
        headers.putAll(requestProperties.snapshot)
        headers.putAll(dataSpec.httpRequestHeaders)
        for ((k, v) in headers) {
            requestBuilder.header(k, v)
        }

        if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            val rangeEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                (dataSpec.position + dataSpec.length - 1).toString()
            } else {
                ""
            }
            requestBuilder.addHeader("Range", "bytes=${dataSpec.position}-$rangeEnd")
        }

        val resp = try {
            callFactory.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                dataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN
            )
        }

        this.response = resp
        val body = resp.body
        this.responseByteStream = body?.byteStream()

        val responseCode = resp.code
        if (!resp.isSuccessful) {
            val errorBytes = try {
                body?.bytes() ?: ByteArray(0)
            } catch (_: IOException) {
                ByteArray(0)
            }
            closeConnectionQuietly()
            throw HttpDataSource.InvalidResponseCodeException(
                responseCode,
                resp.message,
                null,
                resp.headers.toMultimap(),
                dataSpec,
                errorBytes
            )
        }

        bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            val contentLength = body?.contentLength() ?: -1L
            if (contentLength != -1L) contentLength else C.LENGTH_UNSET.toLong()
        }

        opened = true
        transferStarted(dataSpec)
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val toRead = if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val remaining = bytesToRead - bytesRead
            if (remaining == 0L) return C.RESULT_END_OF_INPUT
            min(length.toLong(), remaining).toInt()
        } else {
            length
        }

        val stream = responseByteStream ?: return C.RESULT_END_OF_INPUT
        val read = try {
            stream.read(buffer, offset, toRead)
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                dataSpec!!,
                HttpDataSource.HttpDataSourceException.TYPE_READ
            )
        }

        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }

        bytesRead += read
        bytesTransferred(read)
        return read
    }

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        closeConnectionQuietly()
    }

    private fun closeConnectionQuietly() {
        try {
            responseByteStream?.close()
        } catch (_: IOException) {}
        responseByteStream = null
        response?.close()
        response = null
    }
}
