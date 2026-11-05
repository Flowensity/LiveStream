package com.livestream.source.custom.di

import com.livestream.source.api.SourceAdapter
import com.livestream.source.custom.CustomSourceAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class CustomSourceModule {

    @Binds
    @IntoSet
    abstract fun bindCustomSourceAdapter(
        impl: CustomSourceAdapter
    ): SourceAdapter
}
