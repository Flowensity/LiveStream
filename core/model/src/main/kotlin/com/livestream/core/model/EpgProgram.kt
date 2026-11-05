package com.livestream.core.model

import kotlinx.serialization.Serializable

/**
 * EPG 节目单实体（与频道分离）
 */
@Serializable
data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTimestamp: Long,
    val endTimestamp: Long
) {
    fun isCurrentlyPlaying(currentTimestamp: Long): Boolean {
        return currentTimestamp in startTimestamp until endTimestamp
    }
}
