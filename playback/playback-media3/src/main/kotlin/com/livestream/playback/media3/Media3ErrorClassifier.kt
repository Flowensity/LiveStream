package com.livestream.playback.media3

import androidx.media3.common.PlaybackException
import com.livestream.core.model.PlaybackErrorInfo
import com.livestream.core.model.PlaybackErrorType

/**
 * 将 Media3 底层异常分类映射为领域层错误模型
 */
object Media3ErrorClassifier {

    fun classify(exception: PlaybackException, retryCount: Int = 0): PlaybackErrorInfo {
        return classify(exception.errorCode, exception.errorCodeName, retryCount)
    }

    fun classify(errorCode: Int, errorCodeName: String = "", retryCount: Int = 0): PlaybackErrorInfo {
        val errorType = when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                PlaybackErrorType.NETWORK_TIMEOUT
            }
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                PlaybackErrorType.SOURCE_UNAVAILABLE
            }
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                PlaybackErrorType.DECODER_INIT_FAILED
            }
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> {
                PlaybackErrorType.DRM_AUTHORIZATION_FAILED
            }
            else -> {
                PlaybackErrorType.UNKNOWN
            }
        }

        val userMessage = when (errorType) {
            PlaybackErrorType.NETWORK_TIMEOUT -> "网络连接超时，请检查网络状态"
            PlaybackErrorType.NETWORK_DISCONNECTED -> "网络已断开"
            PlaybackErrorType.SOURCE_UNAVAILABLE -> "直播源暂时无法访问或格式异常"
            PlaybackErrorType.DECODER_INIT_FAILED -> "硬件解码器初始化异常，正在尝试适配"
            PlaybackErrorType.DRM_AUTHORIZATION_FAILED -> "数字版权鉴权未通过"
            PlaybackErrorType.RENDERER_CRASHED -> "渲染器异常"
            PlaybackErrorType.UNKNOWN -> "播放遇到未知异常 ($errorCodeName)"
        }

        val canRetry = retryCount < 3 && errorType != PlaybackErrorType.DRM_AUTHORIZATION_FAILED

        return PlaybackErrorInfo(
            errorType = errorType,
            message = userMessage,
            canRetry = canRetry,
            retryCount = retryCount
        )
    }
}
