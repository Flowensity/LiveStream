package com.livestream.playback.web.di

import com.livestream.playback.api.web.WebPlaybackController
import com.livestream.playback.web.DefaultWebPlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WebPlaybackModule {

    @Binds
    @Singleton
    abstract fun bindWebPlaybackController(
        impl: DefaultWebPlaybackController
    ): WebPlaybackController
}
