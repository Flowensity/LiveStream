package com.livestream.core.database.di

import android.content.Context
import androidx.room.Room
import com.livestream.core.database.LiveStreamDatabase
import com.livestream.core.database.dao.CategoryDao
import com.livestream.core.database.dao.ChannelDao
import com.livestream.core.database.dao.EpgDao
import com.livestream.core.database.dao.LiveSourceDao
import com.livestream.core.database.dao.PlaybackHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLiveStreamDatabase(
        @ApplicationContext context: Context
    ): LiveStreamDatabase {
        return Room.databaseBuilder(
            context,
            LiveStreamDatabase::class.java,
            LiveStreamDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideCategoryDao(database: LiveStreamDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideChannelDao(database: LiveStreamDatabase): ChannelDao = database.channelDao()

    @Provides
    fun provideLiveSourceDao(database: LiveStreamDatabase): LiveSourceDao = database.liveSourceDao()

    @Provides
    fun provideEpgDao(database: LiveStreamDatabase): EpgDao = database.epgDao()

    @Provides
    fun providePlaybackHistoryDao(database: LiveStreamDatabase): PlaybackHistoryDao = database.playbackHistoryDao()
}
