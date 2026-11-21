package com.livestream.core.ui.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

val LocalDeviceType = staticCompositionLocalOf<DeviceType?> { null }

object DeviceDetector {

    @Volatile
    private var cachedIsTv: Boolean? = null

    internal fun clearCacheForTest() {
        cachedIsTv = null
    }

    /**
     * 判断当前设备是否为 Android TV / 电视盒子（结果在进程内缓存，避免高频 IPC 查询）
     */
    fun isTv(context: Context): Boolean {
        cachedIsTv?.let { return it }
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvDevice = if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            true
        } else {
            val packageManager = context.packageManager
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                packageManager.hasSystemFeature("android.hardware.type.television")
        }
        cachedIsTv = isTvDevice
        return isTvDevice
    }

    /**
     * 根据设备特征与当前屏幕宽高计算最佳适配形态
     */
    fun resolveDeviceType(
        context: Context,
        screenWidthDp: Int,
        screenHeightDp: Int,
        smallestScreenWidthDp: Int,
        orientation: Int
    ): DeviceType {
        if (isTv(context)) {
            return DeviceType.TV
        }

        // 平板大屏判定：最小宽度 sw >= 600dp，或者横屏可用宽度 >= 840dp
        val isTablet = smallestScreenWidthDp >= 600 ||
            (screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE)

        if (isTablet) {
            return DeviceType.TABLET
        }

        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            DeviceType.PHONE_LANDSCAPE
        } else {
            DeviceType.PHONE_PORTRAIT
        }
    }
}

/**
 * Compose 状态感知的设备形态记忆函数，在折叠屏展开或手机屏幕旋转时自动重组切换布局
 */
@Composable
fun rememberDeviceType(): DeviceType {
    LocalDeviceType.current?.let { return it }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
        configuration.orientation
    ) {
        DeviceDetector.resolveDeviceType(
            context = context,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            smallestScreenWidthDp = configuration.smallestScreenWidthDp,
            orientation = configuration.orientation
        )
    }
}
