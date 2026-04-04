package com.phantombeats.data.repository

import com.phantombeats.data.remote.api.PhantomApi
import com.maxrave.kotlinyoutubeextractor.State
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import com.phantombeats.domain.repository.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientStreamResolver @Inject constructor(
    private val phantomApi: PhantomApi
) : StreamResolver {

    override suspend fun getStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val streamUrlDto = phantomApi.getStreamUrl(videoId)
            if (streamUrlDto.url.isNotBlank()) {
                Result.success(streamUrlDto.url)
            } else {
                Result.failure(Exception("Stream URL is empty"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}