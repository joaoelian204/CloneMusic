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

    private fun AudioStream.normalizedBitrateKbps(): Int {
        val raw = averageBitrate
        if (raw <= 0) return Int.MAX_VALUE
        return if (raw > 1024) raw / 1000 else raw
    }

    private fun AudioStream.containerPreference(): Int {
        val suffix = format?.suffix?.lowercase().orEmpty()
        return when (suffix) {
            "webm" -> 3
            "m4a" -> 2
            else -> 1
        }
    }

    private fun AudioStream.lowBitratePenalty(): Int {
        val bitrate = normalizedBitrateKbps()
        if (bitrate in 48..64) return 0
        if (bitrate == Int.MAX_VALUE) return 9999
        return kotlin.math.abs(bitrate - 56) + 100
    }

    private fun pickPreferredStream(audioStreams: List<AudioStream>): AudioStream? {
        return audioStreams
            .sortedWith(
                compareBy<AudioStream> { it.lowBitratePenalty() }
                    .thenByDescending { it.containerPreference() }
                    .thenBy { it.normalizedBitrateKbps() }
            )
            .firstOrNull()
    }

    override suspend fun getStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val youtubeService = ServiceList.YouTube
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor: StreamExtractor = youtubeService.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            val audioStreams: List<AudioStream> = extractor.audioStreams
            val stream = pickPreferredStream(audioStreams)

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
                val stream = pickPreferredStream(audioStreams)

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