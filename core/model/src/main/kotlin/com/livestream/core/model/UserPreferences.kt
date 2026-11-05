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
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
