package com.livestream.feature.player

import com.livestream.core.model.Channel
import com.livestream.core.model.EpgProgram
import com.livestream.core.model.LiveSource
import com.livestream.core.model.PlaybackErrorInfo
import com.livestream.core.model.PlaybackStatus
import com.livestream.playback.api.model.PlaybackMetrics

data class PlayerUiState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val activeChannel: Channel? = null,
    val activeSource: LiveSource? = null,
    val activeSourceIndex: Int = 0,
    val totalSources: Int = 1,
    val metrics: PlaybackMetrics = PlaybackMetrics(),
    val error: PlaybackErrorInfo? = null,
    val isOsdVisible: Boolean = true,
    val quickChannelNumber: String = "",
    val isOfficialWeb: Boolean = false,
    val officialWebUrl: String? = null,
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null
)
