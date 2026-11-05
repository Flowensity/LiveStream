package com.livestream.playback.media3.di

import com.livestream.playback.api.PlaybackEngine
import com.livestream.playback.api.PlaybackOrchestrator
import com.livestream.playback.media3.DefaultPlaybackOrchestrator
import com.livestream.playback.media3.Media3PlaybackEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackEngine(
        impl: Media3PlaybackEngine
    ): PlaybackEngine

    @Binds
    @Singleton
    abstract fun bindPlaybackOrchestrator(
        impl: DefaultPlaybackOrchestrator
    ): PlaybackOrchestrator
}
