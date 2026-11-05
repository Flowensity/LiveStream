package com.livestream.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "live_sources",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["channel_id"]),
        Index(value = ["priority"]),
        Index(value = ["health_status"])
    ]
)
data class LiveSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "channel_id")
    val channelId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "protocol")
    val protocol: String, // "HLS", "DASH", "PROGRESSIVE_HTTP", "OFFICIAL_WEB"
    @ColumnInfo(name = "adapter_provider_id")
    val adapterProviderId: String,
    @ColumnInfo(name = "priority")
    val priority: Int = 0,
    @ColumnInfo(name = "health_status")
    val healthStatus: String = "UNKNOWN",
    @ColumnInfo(name = "last_checked_timestamp")
    val lastCheckedTimestamp: Long = 0L,
    @ColumnInfo(name = "headers_profile_id")
    val headersProfileId: String? = null,
    @ColumnInfo(name = "drm_profile_id")
    val drmProfileId: String? = null,
    @ColumnInfo(name = "user_agent")
    val userAgent: String? = null,
    @ColumnInfo(name = "referer")
    val referer: String? = null
)
