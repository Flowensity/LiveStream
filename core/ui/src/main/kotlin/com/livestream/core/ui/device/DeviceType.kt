package com.livestream.core.ui.device

/**
 * 宿主设备形态与屏幕朝向枚举
 */
enum class DeviceType {
    /** Android TV 智能电视或机顶盒（遥控器主控，全屏横屏） */
    TV,

    /** 智能手机竖屏模式（上半区播放器，下半区频道分类与节目流） */
    PHONE_PORTRAIT,

    /** 智能手机横屏全屏模式（触控手势切台、亮度与音量控制） */
    PHONE_LANDSCAPE,

    /** 平板电脑大屏模式（左右分栏响应式布局，左侧导航右侧播放） */
    TABLET
}
