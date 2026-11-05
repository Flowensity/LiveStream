package com.livestream.feature.player.gesture

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

class DeviceControlHelper(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

    fun getVolumePercentage(): Int {
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        return ((current.toFloat() / maxVolume.toFloat()) * 100).roundToInt().coerceIn(0, 100)
    }

    fun adjustVolumeByDelta(deltaY: Float): Int {
        // 向上滑为负 deltaY，增加音量
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val change = if (deltaY < -15f) 1 else if (deltaY > 15f) -1 else 0
        if (change != 0) {
            val target = (current + change).coerceIn(0, maxVolume)
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        return getVolumePercentage()
    }

    fun adjustBrightnessByDelta(activity: Activity?, deltaY: Float, currentBrightness: Float): Float {
        if (activity == null) return currentBrightness
        val window = activity.window
        val lp = window.attributes

        // 向上滑动增加亮度
        val step = -deltaY / 600f
        val newBrightness = (currentBrightness + step).coerceIn(0.05f, 1.0f)
        lp.screenBrightness = newBrightness
        window.attributes = lp
        return newBrightness
    }
}
