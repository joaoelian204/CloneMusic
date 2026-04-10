package com.phantombeats.data.repository

import com.phantombeats.domain.repository.StreamCacheRepository
import com.phantombeats.domain.repository.StreamExtractor
import javax.inject.Inject

class StreamOrchestrator @Inject constructor(
    private val localCache: StreamCacheRepository,
    private val extractors: Set<@JvmSuppressWildcards StreamExtractor> // Conjunto inyectado por Hilt
) {
    suspend fun getAudioSource(songId: String): String {
        // 1. ESTRATEGIA OFFLINE-FIRST REAL
        val localFile = localCache.getLocalDownloadedFile(songId)
        if (localFile != null) return localFile

        // 2. CACHE INTELIGENTE DE STREAMS
        val cachedStream = localCache.getCachedStream(songId)
        if (cachedStream != null && cachedStream.expiryTimeMs > System.currentTimeMillis()) {
            return cachedStream.url
        }

        // 3. FALLBACK ENTRE MÚLTIPLES EXTRACTORES
        for (extractor in extractors.filter { it.isStable }) {
            val result = extractor.getStreamUrl(songId)
            if (result.isSuccess) {
                val stream = result.getOrNull()!!
                localCache.saveStream(songId, stream)
                return stream.url
            }
        }
        
        throw IllegalStateException("Ningún extractor disponible o sin red. No se reprodujo contenido.")
    }
}
