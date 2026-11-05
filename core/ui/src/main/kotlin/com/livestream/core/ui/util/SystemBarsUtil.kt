package com.livestream.core.ui.util

import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 系统状态栏与导航栏沉浸式全屏控制工具
 */
object SystemBarsUtil {

    fun setImmersiveFullscreen(activity: Activity?, isFullscreen: Boolean) {
        setImmersiveFullscreen(activity?.window, isFullscreen)
    }

    fun setImmersiveFullscreen(window: Window?, isFullscreen: Boolean) {
        if (window == null) return
        val decorView = window.decorView
        val insetsController = WindowCompat.getInsetsController(window, decorView)

        if (isFullscreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.hide(WindowInsetsCompat.Type.navigationBars())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val params = window.attributes
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = params
            }
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
