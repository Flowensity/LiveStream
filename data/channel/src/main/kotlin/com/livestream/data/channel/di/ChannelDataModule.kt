package com.livestream.data.channel.di

import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.common.dispatcher.DefaultAppDispatchers
import com.livestream.data.channel.repository.CategoryRepositoryImpl
import com.livestream.data.channel.repository.ChannelRepositoryImpl
import com.livestream.data.channel.repository.UserPreferencesRepositoryImpl
import com.livestream.domain.channel.repository.CategoryRepository
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.domain.channel.usecase.GetChannelsUseCase
import com.livestream.domain.channel.usecase.SearchChannelsUseCase
import com.livestream.domain.channel.usecase.ToggleFavoriteUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChannelDataModule {

    @Binds
    @Singleton
    abstract fun bindChannelRepository(
        impl: ChannelRepositoryImpl
    ): ChannelRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository

    companion object {
        @Provides
        @Singleton
        fun provideAppDispatchers(): AppDispatchers = DefaultAppDispatchers()

        @Provides
        @Singleton
        fun provideGetChannelsUseCase(
            channelRepository: ChannelRepository
        ): GetChannelsUseCase = GetChannelsUseCase(channelRepository)

        @Provides
        @Singleton
        fun provideSearchChannelsUseCase(
            channelRepository: ChannelRepository
        ): SearchChannelsUseCase = SearchChannelsUseCase(channelRepository)

        @Provides
        @Singleton
        fun provideToggleFavoriteUseCase(
            channelRepository: ChannelRepository
        ): ToggleFavoriteUseCase = ToggleFavoriteUseCase(channelRepository)
    }
}
