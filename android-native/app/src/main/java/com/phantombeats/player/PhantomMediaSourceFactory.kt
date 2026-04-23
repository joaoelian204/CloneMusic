package com.phantombeats.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.datasource.cache.CacheDataSource
import com.phantombeats.data.repository.StreamOrchestrator
import kotlinx.coroutines.runBlocking

class PhantomMediaSourceFactory(
    private val context: Context,
    private val streamOrchestrator: StreamOrchestrator,
    private val defaultMediaSourceFactory: DefaultMediaSourceFactory,
    // Ahora inyectamos la fábrica de Caché de ExoPlayer (SimpleCache)
    private val cacheDataSourceFactory: CacheDataSource.Factory? = null 
) : MediaSource.Factory {

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        // En Media3, si quieres resolver lazily la URI real,
        // pasas una ResolvingDataSource a la Factory de orígenes principal:
        
        val songId = mediaItem.mediaId

        val defaultDataSourceFactory = cacheDataSourceFactory ?: DataSource.Factory { 
            // fallback al Default si no hay cache
            androidx.media3.datasource.DefaultDataSource.Factory(context).createDataSource() 
        }

        // ResolvingDataSource.Resolver bloquea hasta conseguir la DataSpec final
        val resolvingDataSourceFactory = DataSource.Factory {
            ResolvingDataSource(
                defaultDataSourceFactory.createDataSource(),
                object : ResolvingDataSource.Resolver {
                    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                        val url = runBlocking { streamOrchestrator.getAudioSource(songId) }
                        return dataSpec.buildUpon().setUri(Uri.parse(url)).build()
                    }
                }
            )
        }

        // Devolvemos el origen modificado inyectando el resolver en medio
        return defaultMediaSourceFactory
            .setDataSourceFactory(resolvingDataSourceFactory)
            .createMediaSource(mediaItem)
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider): MediaSource.Factory {
        defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy): MediaSource.Factory {
        defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray {
        return defaultMediaSourceFactory.supportedTypes
    }
}
