package com.livestream.playback.web

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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultWebPlaybackControllerTest {

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
    fun `initial state is Idle`() = runTest(testDispatcher) {
        assertEquals(WebPlaybackState.Idle, controller.webState.value)
    }

    @Test
    fun `stopAndDestroy resets state to Destroyed`() = runTest(testDispatcher) {
        controller.stopAndDestroy()
        assertEquals(WebPlaybackState.Destroyed, controller.webState.value)
    }

    @Test
    fun `shouldReusePooledWebView returns true when pooled instance is active for different official URL`() = runTest(testDispatcher) {
        org.junit.Assert.assertFalse(controller.canReusePooledInstance())
        controller.markPooledInstanceReadyForTest("https://tv.cctv.com/live/cctv1")
        org.junit.Assert.assertTrue(controller.canReusePooledInstance())

        controller.stopAndDestroy()
        org.junit.Assert.assertFalse(controller.canReusePooledInstance())
    }

    @Test
    fun `shouldBlockWebResource blocks images fonts and trackers while allowing stream and core scripts`() {
        // 应拦截的冗余图片、字体与统计脚本
        org.junit.Assert.assertTrue(
            controller.shouldBlockWebResource("https://resources.yangshipin.cn/assets/oms/image/202306/d57905b.png?imageMogr2/format/webp")
        )
        org.junit.Assert.assertTrue(
            controller.shouldBlockWebResource("https://www.yangshipin.cn/static/fonts/ custom.woff2")
        )
        org.junit.Assert.assertTrue(
            controller.shouldBlockWebResource("https://hm.baidu.com/hm.js?123456")
        )
        org.junit.Assert.assertTrue(
            controller.shouldBlockWebResource("https://otheve.beacon.qq.com/analytics/v2_upload")
        )

        // 必须放行的核心页面、JS 与流媒体分片/索引
        org.junit.Assert.assertFalse(
            controller.shouldBlockWebResource("https://www.yangshipin.cn/tv/home?pid=600001859")
        )
        org.junit.Assert.assertFalse(
            controller.shouldBlockWebResource("https://www.yangshipin.cn/static/js/app.12345.js")
        )
        org.junit.Assert.assertFalse(
            controller.shouldBlockWebResource("https://live-play.yangshipin.cn/live/cctv1.m3u8?txSecret=abc")
        )
        org.junit.Assert.assertFalse(
            controller.shouldBlockWebResource("https://live-play.yangshipin.cn/live/cctv1_001.ts")
        )
    }
}
