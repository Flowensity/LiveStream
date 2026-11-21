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
}
