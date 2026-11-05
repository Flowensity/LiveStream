package com.livestream.core.network.di

import com.livestream.core.common.network.NetworkMonitor
import com.livestream.core.network.ConnectivityNetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(
        impl: ConnectivityNetworkMonitor
    ): NetworkMonitor

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("User-Agent", "LiveStream-Android/1.0 (Linux; Android)")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }
    }
}
