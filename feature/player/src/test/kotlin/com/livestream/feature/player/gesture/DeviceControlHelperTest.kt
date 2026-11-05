package com.livestream.feature.player.gesture

import android.content.Context
import android.media.AudioManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DeviceControlHelperTest {

    private val context = mockk<Context>(relaxed = true)
    private val audioManager = mockk<AudioManager>(relaxed = true)
    private lateinit var helper: DeviceControlHelper

    @Before
    fun setUp() {
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 15
        every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 9

        helper = DeviceControlHelper(context)
    }

    @Test
    fun `getVolumePercentage returns correct ratio`() {
        // 9 / 15 * 100 = 60%
        val percentage = helper.getVolumePercentage()
        assertEquals(60, percentage)
    }
}
