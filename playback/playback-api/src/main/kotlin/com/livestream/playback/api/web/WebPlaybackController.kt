package com.livestream.playback.api.web

import kotlinx.coroutines.flow.StateFlow

sealed interface WebPlaybackState {
    data object Idle : WebPlaybackState
    data class Loading(val progress: Int) : WebPlaybackState
    data object Ready : WebPlaybackState
    data object Playing : WebPlaybackState
    data class Error(val code: Int, val description: String) : WebPlaybackState
    data object RendererCrashed : WebPlaybackState
    data object Destroyed : WebPlaybackState
}

/**
 * 官方合规网页播放控制器契约
 * 仅用于特定官方授权网页直播资源，具备独立的生命周期管理与崩溃恢复边界
 */
interface WebPlaybackController {

    val webState: StateFlow<WebPlaybackState>

    fun loadOfficialUrl(url: String, container: Any?)

    fun stopAndDestroy()

    fun reload()
}
