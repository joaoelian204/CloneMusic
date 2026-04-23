package com.phantombeats.domain.repository

import com.phantombeats.domain.model.StreamInfo

interface StreamCacheRepository {
    suspend fun getCachedStream(songId: String): StreamInfo?
    suspend fun saveStream(songId: String, streamInfo: StreamInfo)
    suspend fun getLocalDownloadedFile(songId: String): String?
}
