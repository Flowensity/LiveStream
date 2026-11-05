package com.livestream.playback.web

import android.widget.FrameLayout
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.playback.api.web.WebPlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 阶段八性能与稳定性压测：官方网页播放器实例泄漏与销毁回收验证
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebViewLeakStressTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    private lateinit var controller: DefaultWebPlaybackController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        controller = DefaultWebPlaybackController(dispatchers)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial active webview count is strictly 0`() = runTest(testDispatcher) {
        assertEquals(0, controller.activeWebViewCount.value)
    }

    @Test
    fun `repeated stopAndDestroy keeps active webview count at 0 and transitions to Destroyed`() = runTest(testDispatcher) {
        for (i in 1..50) {
            controller.stopAndDestroy()
            assertEquals(0, controller.activeWebViewCount.value)
            assertEquals(WebPlaybackState.Destroyed, controller.webState.value)
        }
    }
}
