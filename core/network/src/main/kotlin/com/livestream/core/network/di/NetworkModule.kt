package com.livestream.core.network.di

import com.livestream.core.common.network.NetworkMonitor
import com.livestream.core.network.ConnectivityNetworkMonitor
import com.livestream.core.network.Ipv4FirstDns
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProbeOkHttpClient

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
                .dns(Ipv4FirstDns())
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = if (original.header("User-Agent") == null) {
                        original.newBuilder()
                            .header("User-Agent", "LiveStream-Android/1.0 (Linux; Android)")
                            .build()
                    } else {
                        original
                    }
                    chain.proceed(request)
                }
                .build()
        }

        @Provides
        @Singleton
        @ProbeOkHttpClient
        fun provideProbeOkHttpClient(baseClient: OkHttpClient): OkHttpClient {
            return baseClient.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
        }
    }
}
