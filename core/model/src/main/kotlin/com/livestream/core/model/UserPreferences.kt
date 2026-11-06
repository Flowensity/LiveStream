package com.livestream.core.model

import kotlinx.serialization.Serializable

/**
 * 界面主题显示模式
 */
@Serializable
enum class ThemeMode {
    SYSTEM, // 跟随系统
    DARK,   // 深色模式
    LIGHT   // 浅色模式
}

/**
 * 电视遥控器按键映射策略
 */
@Serializable
enum class TvKeyMappingScheme {
    CLASSIC, // 经典数字键/方向键切台
    MINIMAL  // 极简遥控器确认键唤出频道菜单
}

/**
 * 播放器画面比例模式
 */
@Serializable
enum class VideoAspectRatio(val label: String) {
    ORIGINAL("原始比例"),
    SIXTEEN_NINE("16:9 标准"),
    FOUR_THREE("4:3 经典"),
    ZOOM_FILL("裁剪全屏");

    fun next(): VideoAspectRatio {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * 用户个性化配置偏好
 */
@Serializable
data class UserPreferences(
    val defaultCategoryId: String = "cctv",
    val lastPlayedChannelId: String? = null,
    val isHardwareDecodePreferred: Boolean = true,
    val isSurfaceViewPreferred: Boolean = true,
    val channelSwitchTimeoutMs: Long = 5000L,
    val tvKeyMappingScheme: TvKeyMappingScheme = TvKeyMappingScheme.CLASSIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isBootStartupEnabled: Boolean = false,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL
)
