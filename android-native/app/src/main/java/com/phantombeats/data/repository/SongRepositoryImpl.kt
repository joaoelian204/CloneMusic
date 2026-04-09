package com.phantombeats.data.repository

import com.phantombeats.data.local.dao.SearchHistoryDao
import com.phantombeats.data.local.dao.SongDao
import com.phantombeats.data.local.dao.FavoriteDao
import com.phantombeats.data.download.SongDownloadWorker
import com.phantombeats.data.local.entity.FavoriteEntity
import com.phantombeats.data.local.entity.SearchHistoryEntity
import com.phantombeats.data.local.entity.SongEntity
import com.phantombeats.data.mapper.toDomain
import com.phantombeats.domain.model.Song
import com.phantombeats.domain.repository.SongRepository
import com.phantombeats.domain.repository.StreamResolver
import java.io.IOException
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
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
    private val innerTubeRepository: InnerTubeRepositoryImpl,
    private val itunesApi: com.phantombeats.data.remote.api.ItunesApi,
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

    override suspend fun searchArtists(query: String, limit: Int): Result<List<com.phantombeats.domain.model.Artist>> {
        return try {
            // Using 'song' entity is a trick to get artworkUrl100 which represents the artist's top album/song cover
            val response = itunesApi.searchSongs(term = query, limit = limit * 2, entity = "song")
            
            val artists = mutableListOf<com.phantombeats.domain.model.Artist>()
            val seenArtistIds = mutableSetOf<Long>()
            
            for (track in response.results) {
                if (track.artistId != null && !seenArtistIds.contains(track.artistId)) {
                    val artwork = track.artworkUrl100?.replace("100x100bb", "600x600bb") ?: ""
                    artists.add(
                        com.phantombeats.domain.model.Artist(
                            id = track.artistId.toString(),
                            name = track.artistName ?: "Unknown Artist",
                            imageUrl = artwork
                        )
                    )
                    seenArtistIds.add(track.artistId)
                    if (artists.size >= limit) break
                }
            }
            Result.success(artists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchAlbums(query: String, limit: Int): Result<List<com.phantombeats.domain.model.Album>> {
        return try {
            val response = itunesApi.searchAlbums(term = query, limit = limit)
            val albums = response.results.map { track ->
                val artwork = track.artworkUrl100?.replace("100x100bb", "600x600bb") ?: ""
                com.phantombeats.domain.model.Album(
                    id = track.collectionId?.toString() ?: "",
                    title = track.collectionName ?: "Unknown Album",
                    artistName = track.artistName ?: "Unknown Artist",
                    artistId = track.artistId?.toString() ?: "",
                    coverUrl = artwork,
                    releaseYear = track.releaseDate?.take(4) ?: ""
                )
            }.filter { it.id.isNotEmpty() }
            Result.success(albums)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchSongsPaged(query: String, limit: Int, offset: Int, mode: String): Result<List<Song>> {
        return try {
            // Guardamos la query en el historial antes que todo
            if (offset == 0) {
                searchHistoryDao.insertSearchQuery(SearchHistoryEntity(query = query))
            }

            // 1. Reemplazamos Proxy de GO por Scraper Nativo de YouTube Music (InnerTube API)
            val resultInnerTube = innerTubeRepository.searchTrack(query)
            
            if (resultInnerTube.isFailure) {
                return Result.failure(Exception("Error extrayendo datos de YouTube Music"))
            }
            val domainSongs = resultInnerTube.getOrNull() ?: emptyList()

            // Transición a Entity local
            val entities = domainSongs.map { domainSong ->
                val existing = songDao.getSongById(domainSong.id)
                SongEntity(
                    id = domainSong.id,
                    title = domainSong.title,
                    artist = domainSong.artist,
                    duration = domainSong.duration,
                    coverUrl = domainSong.coverUrl,
                    provider = domainSong.provider,
                    localPath = existing?.localPath,
                    playCount = existing?.playCount ?: 0,
                    skipCount = existing?.skipCount ?: 0,
                    lastPlayed = existing?.lastPlayed ?: 0L,
                    isFavorite = existing?.isFavorite ?: false,
                    addedAt = existing?.addedAt ?: System.currentTimeMillis()
                )
            }
            songDao.insertSongs(entities)

            // Retornamos mapeado al Domain desde las Entities (o directamente domainSongs)
            Result.success(domainSongs)

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

            // Enqueuemos el Worker usando solo el ID de la canción para evitar
            // bloquear el hilo actual o tener URLs expiradas en el worker.
            songDao.updateLocalPath(song.id, PENDING_DOWNLOAD_PATH)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SongDownloadWorker>()
                .setInputData(
                    workDataOf(
                        SongDownloadWorker.KEY_SONG_ID to song.id
                    )
                )
                .setConstraints(constraints)
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
