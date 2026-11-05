package com.livestream.core.ui.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDetectorTest {

    private val context = mockk<Context>(relaxed = true)
    private val uiModeManager = mockk<UiModeManager>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)

    @Test
    fun `isTv returns true when uiModeManager returns television`() {
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION

        assertTrue(DeviceDetector.isTv(context))
    }

    @Test
    fun `isTv returns true when packageManager has leanback feature`() {
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns true

        assertTrue(DeviceDetector.isTv(context))
    }

    @Test
    fun `isTv returns false on standard phone`() {
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false

        assertFalse(DeviceDetector.isTv(context))
    }

    @Test
    fun `resolveDeviceType detects TV when isTv is true`() {
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION

        val type = DeviceDetector.resolveDeviceType(
            context = context,
            screenWidthDp = 1920,
            screenHeightDp = 1080,
            smallestScreenWidthDp = 1080,
            orientation = Configuration.ORIENTATION_LANDSCAPE
        )

        assertEquals(DeviceType.TV, type)
    }

    @Test
    fun `resolveDeviceType detects TABLET on large screens`() {
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false

        val type = DeviceDetector.resolveDeviceType(
            context = context,
            screenWidthDp = 1200,
            screenHeightDp = 800,
            smallestScreenWidthDp = 720,
            orientation = Configuration.ORIENTATION_LANDSCAPE
        )

        assertEquals(DeviceType.TABLET, type)
    }

    @Test
    fun `resolveDeviceType detects PHONE_LANDSCAPE on phone in landscape`() {
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false

        val type = DeviceDetector.resolveDeviceType(
            context = context,
            screenWidthDp = 780,
            screenHeightDp = 390,
            smallestScreenWidthDp = 390,
            orientation = Configuration.ORIENTATION_LANDSCAPE
        )

        assertEquals(DeviceType.PHONE_LANDSCAPE, type)
    }

    @Test
    fun `resolveDeviceType detects PHONE_PORTRAIT on phone in portrait`() {
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false

        val type = DeviceDetector.resolveDeviceType(
            context = context,
            screenWidthDp = 390,
            screenHeightDp = 780,
            smallestScreenWidthDp = 390,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )

        assertEquals(DeviceType.PHONE_PORTRAIT, type)
    }
}
