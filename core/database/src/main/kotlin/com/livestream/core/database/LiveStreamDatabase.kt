package com.livestream.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.livestream.core.database.dao.CategoryDao
import com.livestream.core.database.dao.ChannelDao
import com.livestream.core.database.dao.EpgDao
import com.livestream.core.database.dao.LiveSourceDao
import com.livestream.core.database.dao.PlaybackHistoryDao
import com.livestream.core.database.entity.CategoryEntity
import com.livestream.core.database.entity.ChannelEntity
import com.livestream.core.database.entity.EpgProgramEntity
import com.livestream.core.database.entity.LiveSourceEntity
import com.livestream.core.database.entity.PlaybackHistoryEntity

@Database(
    entities = [
        CategoryEntity::class,
        ChannelEntity::class,
        LiveSourceEntity::class,
        EpgProgramEntity::class,
        PlaybackHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LiveStreamDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun liveSourceDao(): LiveSourceDao
    abstract fun epgDao(): EpgDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    companion object {
        const val DATABASE_NAME = "livestream.db"
    }
}
