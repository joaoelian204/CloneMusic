package com.phantombeats.data.repository

import com.phantombeats.data.local.dao.SearchHistoryDao
import com.phantombeats.data.local.dao.SongDao
import com.phantombeats.data.local.dao.FavoriteDao
import com.phantombeats.data.download.SongDownloadWorker
import com.phantombeats.data.local.entity.FavoriteEntity
import com.phantombeats.data.local.entity.SearchHistoryEntity
import com.phantombeats.data.mapper.toDomain
import com.phantombeats.data.remote.api.PhantomApi
import com.phantombeats.domain.model.Song
import com.phantombeats.domain.repository.SongRepository
import com.phantombeats.domain.repository.StreamResolver
import java.io.IOException
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SongRepositoryImpl @Inject constructor(
    private val api: PhantomApi,
    private val songDao: SongDao,
    private val favoriteDao: FavoriteDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val workManager: WorkManager,
    private val streamResolver: StreamResolver
) : SongRepository {

    companion object {
        private const val PENDING_DOWNLOAD_PATH = "__PENDING__"
        private const val DEFAULT_SEARCH_PAGE_SIZE = 25
        private const val STREAM_TIMEOUT_MS = 20_000L
    }

    private fun mapStreamError(e: Exception): Exception {
        val message = when (e) {
            is HttpException -> when (e.code()) {
                429 -> "El proveedor de audio está limitado temporalmente (429). Intenta de nuevo en unos segundos."
                503 -> "El stream no está disponible temporalmente para esta canción. Prueba con otra opción."
                404 -> "No se encontró stream para esta canción."
                else -> "Error del servidor al obtener el stream (${e.code()})."
            }
            is SocketTimeoutException -> "El stream tardó demasiado en responder."
            is TimeoutCancellationException -> "El stream tardó demasiado en responder."
            is UnknownHostException -> "No se pudo resolver el servidor de streaming."
            is SSLException -> "No se pudo establecer una conexión segura para reproducir."
            is IOException -> "No hay conexión para iniciar la reproducción."
            else -> e.localizedMessage ?: "No se pudo iniciar la reproducción."
        }
        return Exception(message, e)
    }

    override suspend fun searchSongs(query: String, mode: String): Result<List<Song>> {
        return searchSongsPaged(query, DEFAULT_SEARCH_PAGE_SIZE, 0, mode)
    }

    override suspend fun searchSongsPaged(query: String, limit: Int, offset: Int, mode: String): Result<List<Song>> {
        return try {
            // Guardamos la query en el historial antes que todo
            if (offset == 0) {
                searchHistoryDao.insertSearchQuery(SearchHistoryEntity(query = query))
            }

            // 1. Intentamos obtener de la red (Proxy en Go)
            val remoteSongs = api.searchSongs(query, limit = limit, offset = offset, mode = mode)
            
            // 2. Persistir localmente en Room (Como caché inicial)
            // Aquí ignoramos si choca con canciones descargadas localmente (ON CONFLICT REPLACE)
            val entities = remoteSongs.map { dto ->
                val existing = songDao.getSongById(dto.id)
                dto.toEntity().copy(
                    localPath = existing?.localPath,
                    playCount = existing?.playCount ?: 0,
                    skipCount = existing?.skipCount ?: 0,
                    lastPlayed = existing?.lastPlayed ?: 0L,
                    isFavorite = existing?.isFavorite ?: false,
                    addedAt = existing?.addedAt ?: System.currentTimeMillis()
                )
            }
            songDao.insertSongs(entities)

            // Retornamos mapeado al Domain
            Result.success(entities.map { it.toDomain() })

        } catch (e: Exception) {
            // 3. ✨ OFFLINE-FIRST / FALBACK: Si hay un error de red (Ej. modo avión)
            try {
                // Buscamos con algoritmo Fuzzy en Room local base
                val localData = songDao.searchSongsOffline(query)
                if (localData.isNotEmpty()) {
                    val pagedLocal = localData.drop(offset).take(limit)
                    Result.success(pagedLocal.map { it.toDomain() })
                } else {
                    val message = when (e) {
                        is HttpException -> {
                            if (e.code() == 404) {
                                "No se encontraron resultados para \"$query\"."
                            } else {
                                "Error del servidor (${e.code()})."
                            }
                        }
                        is SocketTimeoutException -> "El servidor tardó demasiado en responder. Intenta de nuevo en unos segundos."
                        is UnknownHostException -> "No se pudo resolver el servidor. Verifica tu conexión a internet."
                        is SSLException -> "No se pudo establecer una conexión segura con el servidor."
                        is IOException -> "No hay conexión y no existen resultados guardados sin conexión."
                        else -> e.localizedMessage ?: "No se pudo completar la búsqueda."
                    }
                    Result.failure(Exception(message, e))
                }
            } catch (localError: Exception) {
                Result.failure(localError)
            }
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> {
        // 1. Si la canción ya existe en disco porque está descargada, retornamos de inmediato
        if (song.isDownloaded && song.localPath != null) {
            return Result.success("file://${song.localPath}")
        }

        // 2. Extraemos el audio crudo en el cliente con StreamResolver
        return try {
            val streamResult = withTimeout(STREAM_TIMEOUT_MS) {
                streamResolver.getStreamUrl(song.id)
            }
            streamResult
        } catch (e: Exception) {
            Result.failure(mapStreamError(e))
        }
    }

    override fun getAllCachedSongs(): Flow<List<Song>> {
        // Observamos Room. Cualquier cambio de DB local actualizará automáticamente la UI en Compose
        return songDao.getAllSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun markAsPlayed(songId: String) {
        // Incrementa la métrica local para propósitos de recomendación/relevancia
        songDao.incrementPlayCount(songId)
    }

    override suspend fun setFavorite(songId: String, isFavorite: Boolean): Result<Unit> {
        return try {
            if (isFavorite) {
                favoriteDao.insertFavorite(FavoriteEntity(songId = songId))
            } else {
                favoriteDao.deleteFavorite(songId)
            }
            songDao.updateFavoriteStatus(songId, isFavorite)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadSong(song: Song): Result<Unit> {
        return try {
            if (song.localPath != null && !song.localPath.startsWith(PENDING_DOWNLOAD_PATH)) {
                return Result.success(Unit)
            }

            val streamResult = withTimeout(STREAM_TIMEOUT_MS) {
                streamResolver.getStreamUrl(song.id)
            }
            
            val streamUrl = streamResult.getOrThrow()
            
            songDao.updateLocalPath(song.id, PENDING_DOWNLOAD_PATH)
            val request = OneTimeWorkRequestBuilder<SongDownloadWorker>()
                .setInputData(
                    workDataOf(
                        SongDownloadWorker.KEY_SONG_ID to song.id,
                        SongDownloadWorker.KEY_STREAM_URL to streamUrl
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                "download_${song.id}",
                ExistingWorkPolicy.KEEP,
                request
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapStreamError(e))
        }
    }
}
