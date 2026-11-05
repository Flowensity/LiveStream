package com.livestream.app.di

import com.livestream.source.api.DefaultSourceAdapterRegistry
import com.livestream.source.api.SourceAdapter
import com.livestream.source.api.SourceAdapterRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceRegistryModule {

    @Provides
    @Singleton
    fun provideSourceAdapterRegistry(
        adapters: Set<@JvmSuppressWildcards SourceAdapter>
    ): SourceAdapterRegistry {
        val registry = DefaultSourceAdapterRegistry()
        adapters.forEach { adapter ->
            registry.register(adapter)
        }
        return registry
    }
}
