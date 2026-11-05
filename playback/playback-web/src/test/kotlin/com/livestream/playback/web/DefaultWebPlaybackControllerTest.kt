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
}
