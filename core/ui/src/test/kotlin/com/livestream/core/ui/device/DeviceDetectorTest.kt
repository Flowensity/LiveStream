package com.livestream.core.ui.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceDetectorTest {

    @Before
    fun setUp() {
        DeviceDetector.clearCacheForTest()
    }

    @After
    fun tearDown() {
        DeviceDetector.clearCacheForTest()
    }

    @Test
    fun `isTv caches result and avoids repeated IPC system feature queries`() {
        val packageManager = mockk<PackageManager>()
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns true
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false

        val uiModeManager = mockk<UiModeManager>()
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL

        val context = mockk<Context>()
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.packageManager } returns packageManager

        assertTrue(DeviceDetector.isTv(context))
        assertTrue(DeviceDetector.isTv(context))
        assertTrue(DeviceDetector.isTv(context))

        // Only 1 actual query even after 3 calls
        verify(exactly = 1) { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

        DeviceDetector.clearCacheForTest()
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
        assertFalse(DeviceDetector.isTv(context))
    }
}
