package com.phantombeats.data.repository

import com.phantombeats.domain.repository.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientStreamResolver @Inject constructor() : StreamResolver {

    override suspend fun getStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val youtubeService = ServiceList.YouTube
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor: StreamExtractor = youtubeService.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            val audioStreams: List<AudioStream> = extractor.audioStreams
            // Priorizamos OPUS/WebM de más alta calidad (~160kbps) vs M4A (~128kbps)
            val stream = audioStreams.find { it.format?.suffix == "webm" } 
                ?: audioStreams.find { it.format?.suffix == "m4a" }
                ?: audioStreams.maxByOrNull { it.averageBitrate } // O cualquier método disponible
                ?: audioStreams.firstOrNull()

            if (stream != null && stream.content.isNotBlank()) {
                Result.success(stream.content)
            } else {
                Result.failure(Exception("No se encontró stream de audio compatible"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resolveBySearch(query: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val youtubeService = ServiceList.YouTube
            val searchExtractor = youtubeService.getSearchExtractor(query)
            searchExtractor.fetchPage()

            val initialItems = searchExtractor.initialPage.items
            if (initialItems.isNotEmpty()) {
                // Get the first result's URL and extract stream
                val firstResultUrl = initialItems[0].url
                val streamExtractor = youtubeService.getStreamExtractor(firstResultUrl)
                streamExtractor.fetchPage()

                val audioStreams = streamExtractor.audioStreams
                val stream = audioStreams.find { it.format?.suffix == "webm" }
                    ?: audioStreams.find { it.format?.suffix == "m4a" }
                    ?: audioStreams.maxByOrNull { it.averageBitrate }
                    ?: audioStreams.firstOrNull()

                if (stream != null && stream.content.isNotBlank()) {
                    return@withContext Result.success(stream.content)
                }
            }
            Result.failure(Exception("No se encontraron resultados en YouTube o no hay audios compatibles para: $query"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}