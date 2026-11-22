package com.livestream.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSanityTest {

    @Test
    fun `sanity check that unit test suite runs`() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun `MainActivity overrides onDestroy to release playback resources`() {
        val hasOnDestroy = MainActivity::class.java.declaredMethods.any { it.name == "onDestroy" }
        assertTrue("MainActivity must override onDestroy to release PlaybackOrchestrator", hasOnDestroy)
    }

    @Test
    fun `shouldEnterPip returns false by default when PiP setting is disabled or app is exiting`() {
        org.junit.Assert.assertFalse(
            MainActivity.shouldEnterPip(
                isPipEnabled = false,
                isExitingApp = false,
                isFinishing = false,
                isDestroyed = false,
                status = com.livestream.core.model.PlaybackStatus.PLAYING
            )
        )
        org.junit.Assert.assertFalse(
            MainActivity.shouldEnterPip(
                isPipEnabled = true,
                isExitingApp = true,
                isFinishing = false,
                isDestroyed = false,
                status = com.livestream.core.model.PlaybackStatus.PLAYING
            )
        )
        assertTrue(
            MainActivity.shouldEnterPip(
                isPipEnabled = true,
                isExitingApp = false,
                isFinishing = false,
                isDestroyed = false,
                status = com.livestream.core.model.PlaybackStatus.PLAYING
            )
        )
    }
}
