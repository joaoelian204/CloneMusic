package com.phantombeats.data.repository

import android.content.Context
import com.maxrave.kotlinyoutubeextractor.State
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import com.phantombeats.domain.repository.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientStreamResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : StreamResolver {

    override suspend fun getStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val yt = YTExtractor(con = context, CACHING = false, LOGGING = true)
            yt.extract(videoId)
            if (yt.state == State.SUCCESS) {
                // Formato 140 es M4A audio preferido, o 251 para WebM
                val streamInfo = yt.getYTFiles()?.get(140) ?: yt.getYTFiles()?.get(251)
                
                val url = streamInfo?.url
                if (!url.isNullOrBlank()) {
                    Result.success(url)
                } else {
                    Result.failure(Exception("No compatible audio stream found for video: $videoId"))
                }
            } else {
                Result.failure(Exception("Failed to extract youtube stream for video: $videoId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}