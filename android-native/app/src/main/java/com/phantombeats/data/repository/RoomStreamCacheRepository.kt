package com.phantombeats.data.repository

import com.phantombeats.data.local.dao.StreamCacheDao
import com.phantombeats.data.local.entity.StreamCacheEntity
import com.phantombeats.domain.model.StreamInfo
import com.phantombeats.domain.repository.StreamCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class RoomStreamCacheRepository @Inject constructor(
    private val streamCacheDao: StreamCacheDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : StreamCacheRepository {

    override suspend fun getCachedStream(songId: String): StreamInfo? {
        return withContext(Dispatchers.IO) {
            val entity = streamCacheDao.getStream(songId) ?: return@withContext null
            
            // Validación de caducidad
            if (entity.expiryTimeMs < System.currentTimeMillis()) {
                streamCacheDao.cleanExpiredStreams(System.currentTimeMillis())
                return@withContext null
            }

            StreamInfo(
                url = entity.url,
                format = entity.format,
                expiryTimeMs = entity.expiryTimeMs
            )
        }
    }

    override suspend fun saveStream(songId: String, streamInfo: StreamInfo) {
        withContext(Dispatchers.IO) {
            val entity = StreamCacheEntity(
                songId = songId,
                url = streamInfo.url,
                format = streamInfo.format,
                expiryTimeMs = streamInfo.expiryTimeMs
            )
            streamCacheDao.saveStream(entity)
            
            // Limpieza pasiva de enlaces caducados de otras canciones
            streamCacheDao.cleanExpiredStreams(System.currentTimeMillis())
        }
    }

    override suspend fun getLocalDownloadedFile(songId: String): String? {
        return withContext(Dispatchers.IO) {
            val outDir = File(context.filesDir, "offline_audio")
            val mp3File = File(outDir, "$songId.mp3")
            if (mp3File.exists()) return@withContext mp3File.absolutePath
            val m4aFile = File(outDir, "$songId.m4a")
            if (m4aFile.exists()) return@withContext m4aFile.absolutePath
            null 
        }
    }
}
