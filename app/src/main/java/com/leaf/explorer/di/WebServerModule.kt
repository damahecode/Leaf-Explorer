package com.leaf.explorer.di

import android.content.Context
import com.yanzhenjie.andserver.AndServer
import com.yanzhenjie.andserver.Server
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.leaf.explorer.config.AppConfig
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebServerModule {
    @Provides
    @Singleton
    @WebShareServer
    fun providesWebServer(@ApplicationContext context: Context): Server {
        return AndServer.webServer(context)
            .port(AppConfig.SERVER_PORT_WEBSHARE)
            .timeout(10, TimeUnit.SECONDS)
            .build()
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebShareServer