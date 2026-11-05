package com.livestream.data.epg.di

import com.livestream.data.epg.repository.EpgRepositoryImpl
import com.livestream.domain.channel.repository.EpgRepository
import com.livestream.domain.channel.usecase.GetCurrentProgramUseCase
import com.livestream.domain.channel.usecase.GetNextProgramUseCase
import com.livestream.domain.channel.usecase.GetProgramsForChannelUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EpgDataModule {

    @Binds
    @Singleton
    abstract fun bindEpgRepository(
        impl: EpgRepositoryImpl
    ): EpgRepository

    companion object {
        @Provides
        @Singleton
        fun provideGetCurrentProgramUseCase(
            epgRepository: EpgRepository
        ): GetCurrentProgramUseCase = GetCurrentProgramUseCase(epgRepository)

        @Provides
        @Singleton
        fun provideGetNextProgramUseCase(
            epgRepository: EpgRepository
        ): GetNextProgramUseCase = GetNextProgramUseCase(epgRepository)

        @Provides
        @Singleton
        fun provideGetProgramsForChannelUseCase(
            epgRepository: EpgRepository
        ): GetProgramsForChannelUseCase = GetProgramsForChannelUseCase(epgRepository)
    }
}
