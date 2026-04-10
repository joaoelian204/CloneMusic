package com.phantombeats.di

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.phantombeats.domain.repository.StreamResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton

    fun provideDataSourceFactory(
        @ApplicationContext context: Context,
        cache: SimpleCache,
        streamResolver: StreamResolver
    ): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)

        // Usamos DefaultDataSource.Factory para soportar archivos locales
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // Wrapper de ResolvingDataSource para resolver YouTube Stream URL on-the-fly de manera Client-Side
        val resolvingFactory = ResolvingDataSource.Factory(defaultDataSourceFactory) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "phantom-yt") {
                val videoId = uri.schemeSpecificPart
                val streamUrl = runBlocking {
                    streamResolver.getStreamUrl(videoId).getOrElse { "" }
                }
                if (streamUrl.isNotBlank()) {
                    return@Factory dataSpec.withUri(Uri.parse(streamUrl))
                } else {
                    throw java.io.IOException("No se pudo resolver el stream para: $videoId")
                }
            } else if (uri.scheme == "phantom-search") {
                val query = uri.schemeSpecificPart
                val streamUrl = runBlocking {
                    streamResolver.resolveBySearch(query).getOrElse { "" }
                }
                if (streamUrl.isNotBlank()) {
                    return@Factory dataSpec.withUri(Uri.parse(streamUrl))
                } else {
                    throw java.io.IOException("No se pudo resolver el stream para la búsqueda: $query")
                }
            }
            dataSpec
        }

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        dataSourceFactory: DataSource.Factory
    ): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)
            .build()
    }
}