package com.livestream.source.official.di

import com.livestream.source.api.SourceAdapter
import com.livestream.source.official.OfficialSourceAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class OfficialSourceModule {

    @Binds
    @IntoSet
    abstract fun bindOfficialSourceAdapter(
        impl: OfficialSourceAdapter
    ): SourceAdapter
}
