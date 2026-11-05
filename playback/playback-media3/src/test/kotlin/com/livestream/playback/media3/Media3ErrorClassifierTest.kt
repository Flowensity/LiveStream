package com.livestream.playback.media3

import androidx.media3.common.PlaybackException
import com.livestream.core.model.PlaybackErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ErrorClassifierTest {

    @Test
    fun `test network timeout error classification`() {
        val info = Media3ErrorClassifier.classify(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
            retryCount = 0
        )

        assertEquals(PlaybackErrorType.NETWORK_TIMEOUT, info.errorType)
        assertTrue(info.canRetry)
        assertEquals(0, info.retryCount)
    }

    @Test
    fun `test drm failure is non-retryable`() {
        val info = Media3ErrorClassifier.classify(
            errorCode = PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            errorCodeName = "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED",
            retryCount = 0
        )

        assertEquals(PlaybackErrorType.DRM_AUTHORIZATION_FAILED, info.errorType)
        assertFalse(info.canRetry)
    }

    @Test
    fun `test retry count limit enforcement`() {
        val info = Media3ErrorClassifier.classify(
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            errorCodeName = "ERROR_CODE_IO_BAD_HTTP_STATUS",
            retryCount = 3
        )

        assertEquals(PlaybackErrorType.SOURCE_UNAVAILABLE, info.errorType)
        assertFalse(info.canRetry)
    }
}
