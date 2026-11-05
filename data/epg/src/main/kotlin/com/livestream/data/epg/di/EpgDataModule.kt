package com.livestream.data.epg.di

import com.livestream.data.epg.repository.EpgRepositoryImpl
import com.livestream.domain.channel.repository.EpgRepository
import dagger.Binds
import dagger.Module
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
}
