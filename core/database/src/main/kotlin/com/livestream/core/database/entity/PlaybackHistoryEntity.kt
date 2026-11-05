package com.livestream.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["last_played_timestamp"])
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "channel_id")
    val channelId: String,
    @ColumnInfo(name = "last_played_timestamp")
    val lastPlayedTimestamp: Long,
    @ColumnInfo(name = "last_source_id")
    val lastSourceId: String? = null,
    @ColumnInfo(name = "duration_watched_ms")
    val durationWatchedMs: Long = 0L
)
