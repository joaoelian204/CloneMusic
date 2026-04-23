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
import java.io.File
import java.io.IOException
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.concurrent.TimeUnit
import java.util.UUID
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
        private const val STREAM_TIMEOUT_MS = 12_000L
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

    private fun normalizeText(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanAlbumTitle(raw: String): String {
        return raw
            .replace(Regex("(?i)\\s*[-–]\\s*(single|ep)\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isLikelyAlbumRelease(trackCount: Int?): Boolean {
        val count = trackCount ?: return true
        return count >= 3
    }

    private fun canonicalAlbumKey(title: String, artist: String): String {
        return "${normalizeText(cleanAlbumTitle(title))}|${normalizeText(artist)}"
    }

    private fun canonicalSongTitle(value: String): String {
        return value
            .lowercase()
            .replace(Regex("\\((feat|ft|featuring)[^)]+\\)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\[(feat|ft|featuring)[^]]+\\]", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?i)\\b(feat|ft|featuring)\\.?\\s+[^-()\\[]+"), " ")
            .replace(Regex("(?i)\\b(live|acoustic|remix|remaster(ed)?|version|radio edit|karaoke)\\b"), " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun canonicalSongKey(title: String, artist: String): String {
        return "${canonicalSongTitle(title)}|${normalizeText(artist)}"
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
            val response = itunesApi.searchAlbums(term = query, limit = limit * 4)
            val normalizedQuery = normalizeText(query)

            val albums = response.results
                .asSequence()
                .filter { track ->
                    val wrapper = normalizeText(track.wrapperType ?: "")
                    val collectionType = normalizeText(track.collectionType ?: "")
                    val artist = normalizeText(track.artistName ?: "")

                    wrapper == "collection" &&
                        collectionType == "album" &&
                        track.collectionId != null &&
                        isLikelyAlbumRelease(track.trackCount) &&
                        (normalizedQuery.isBlank() || artist.contains(normalizedQuery))
                }
                .map { track ->
                val artwork = track.artworkUrl100?.replace("100x100bb", "600x600bb") ?: ""
                com.phantombeats.domain.model.Album(
                    id = track.collectionId?.toString() ?: "",
                    title = cleanAlbumTitle(track.collectionName ?: "Unknown Album"),
                    artistName = track.artistName ?: "Unknown Artist",
                    artistId = track.artistId?.toString() ?: "",
                    coverUrl = artwork,
                    releaseYear = track.releaseDate?.take(4) ?: ""
                )
            }
                .distinctBy { canonicalAlbumKey(it.title, it.artistName) }
                .sortedByDescending { it.releaseYear.toIntOrNull() ?: 0 }
                .take(limit)
                .toList()

            Result.success(albums)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchAllSongsByArtist(
        artistName: String,
        maxSongs: Int
    ): Result<List<Song>> {
        return try {
            val normalizedArtist = normalizeText(artistName)
            val pageSize = 200
            var offset = 0
            val collected = mutableListOf<Song>()

            while (collected.size < maxSongs) {
                val response = itunesApi.searchSongs(
                    term = artistName,
                    entity = "song",
                    limit = pageSize,
                    offset = offset,
                    attribute = "artistTerm"
                )

                val pageSongs = response.results
                    .asSequence()
                    .filter { track ->
                        val trackArtist = normalizeText(track.artistName ?: "")
                        val trackName = track.trackName?.trim().orEmpty()
                        trackName.isNotBlank() &&
                            (trackArtist == normalizedArtist || trackArtist.contains(normalizedArtist))
                    }
                    .map { track ->
                        val cover = track.artworkUrl100?.replace("100x100bb", "1000x1000bb") ?: ""
                        val stableId = track.trackId?.toString() ?: UUID.nameUUIDFromBytes(
                            "${track.trackName}-${track.artistName}-${track.collectionName}".toByteArray()
                        ).toString()
                        Song(
                            id = "itunes-$stableId",
                            title = track.trackName ?: "Unknown Track",
                            artist = track.artistName ?: artistName,
                            duration = 0,
                            coverUrl = cover,
                            provider = "iTunes"
                        )
                    }
                    .toList()

                if (pageSongs.isEmpty()) break

                collected.addAll(pageSongs)
                if (response.results.size < pageSize) break
                offset += pageSize
            }

            val uniqueSongs = collected
                .distinctBy { canonicalSongKey(it.title, it.artist) }
                .take(maxSongs)

            if (uniqueSongs.isEmpty()) {
                Result.failure(Exception("No se encontraron canciones para este artista."))
            } else {
                Result.success(uniqueSongs)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchAllAlbumsByArtist(
        artistName: String,
        maxAlbums: Int
    ): Result<List<com.phantombeats.domain.model.Album>> {
        return try {
            val normalizedArtist = normalizeText(artistName)
            val pageSize = 200
            var offset = 0
            val collected = mutableListOf<com.phantombeats.domain.model.Album>()

            while (collected.size < maxAlbums) {
                val response = itunesApi.searchAlbums(
                    term = artistName,
                    entity = "album",
                    attribute = "artistTerm",
                    limit = pageSize,
                    offset = offset
                )

                val pageAlbums = response.results
                    .asSequence()
                    .filter { track ->
                        val wrapper = normalizeText(track.wrapperType ?: "")
                        val collectionType = normalizeText(track.collectionType ?: "")
                        val trackArtist = normalizeText(track.artistName ?: "")

                        wrapper == "collection" &&
                            collectionType == "album" &&
                            track.collectionId != null &&
                            isLikelyAlbumRelease(track.trackCount) &&
                            (trackArtist == normalizedArtist || trackArtist.contains(normalizedArtist))
                    }
                    .map { track ->
                        val artwork = track.artworkUrl100?.replace("100x100bb", "600x600bb") ?: ""
                        com.phantombeats.domain.model.Album(
                            id = track.collectionId?.toString() ?: "",
                            title = cleanAlbumTitle(track.collectionName ?: "Unknown Album"),
                            artistName = track.artistName ?: artistName,
                            artistId = track.artistId?.toString() ?: "",
                            coverUrl = artwork,
                            releaseYear = track.releaseDate?.take(4) ?: ""
                        )
                    }
                    .filter { it.id.isNotBlank() }
                    .toList()

                if (pageAlbums.isEmpty()) break

                collected.addAll(pageAlbums)
                if (response.results.size < pageSize) break
                offset += pageSize
            }

            val uniqueAlbums = collected
                .distinctBy { canonicalAlbumKey(it.title, it.artistName) }
                .sortedByDescending { it.releaseYear.toIntOrNull() ?: 0 }
                .take(maxAlbums)

            if (uniqueAlbums.isEmpty()) {
                Result.failure(Exception("No se encontraron albumes para este artista."))
            } else {
                Result.success(uniqueAlbums)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAlbumTracks(
        albumId: String,
        albumTitle: String,
        artistName: String,
        limit: Int
    ): Result<List<Song>> {
        return try {
            val desiredAlbum = normalizeText(albumTitle)
            val desiredArtist = normalizeText(artistName)
            val desiredAlbumId = albumId.trim()
            val query = "$artistName $albumTitle"

            val fromLookup = if (desiredAlbumId.isNotBlank()) {
                itunesApi.lookupAlbumTracks(id = desiredAlbumId).results
                    .asSequence()
                    .filter { track ->
                        val sameAlbumId = track.collectionId?.toString() == desiredAlbumId
                        val trackArtist = normalizeText(track.artistName ?: "")
                        val isSong = normalizeText(track.kind ?: "") == "song" || normalizeText(track.wrapperType ?: "") == "track"
                        sameAlbumId &&
                            isSong &&
                            track.trackName?.isNotBlank() == true &&
                            (trackArtist == desiredArtist || trackArtist.contains(desiredArtist))
                    }
                    .map { track ->
                        val cover = track.artworkUrl100?.replace("100x100bb", "1000x1000bb") ?: ""
                        val stableId = track.trackId?.toString() ?: UUID.nameUUIDFromBytes(
                            "${track.trackName}-${track.artistName}-${track.collectionName}".toByteArray()
                        ).toString()
                        Song(
                            id = "itunes-$stableId",
                            title = track.trackName ?: "Unknown Track",
                            artist = track.artistName ?: artistName,
                            duration = 0,
                            coverUrl = cover,
                            provider = "iTunes"
                        )
                    }
                    .distinctBy { it.id }
                    .toList()
            } else {
                emptyList()
            }

            if (fromLookup.isNotEmpty()) {
                return Result.success(fromLookup)
            }

            val response = itunesApi.searchSongs(
                term = query,
                entity = "song",
                limit = limit.coerceAtLeast(60)
            )

            val strictMatches = response.results.filter { track ->
                val trackAlbum = normalizeText(track.collectionName ?: "")
                val trackArtist = normalizeText(track.artistName ?: "")
                val sameAlbumId = desiredAlbumId.isNotBlank() && track.collectionId?.toString() == desiredAlbumId
                track.trackName?.isNotBlank() == true &&
                    (sameAlbumId || trackAlbum == desiredAlbum) &&
                    (trackArtist == desiredArtist || trackArtist.contains(desiredArtist))
            }

            val fallbackMatches = if (strictMatches.isEmpty()) {
                response.results.filter { track ->
                    val trackAlbum = normalizeText(track.collectionName ?: "")
                    val trackArtist = normalizeText(track.artistName ?: "")
                    track.trackName?.isNotBlank() == true &&
                        trackAlbum.contains(desiredAlbum) &&
                        (trackArtist == desiredArtist || trackArtist.contains(desiredArtist))
                }
            } else {
                strictMatches
            }

            val songs = fallbackMatches.map { track ->
                val cover = track.artworkUrl100?.replace("100x100bb", "1000x1000bb") ?: ""
                val stableId = track.trackId?.toString() ?: UUID.nameUUIDFromBytes(
                    "${track.trackName}-${track.artistName}-${track.collectionName}".toByteArray()
                ).toString()

                Song(
                    id = "itunes-$stableId",
                    title = track.trackName ?: "Unknown Track",
                    artist = track.artistName ?: artistName,
                    duration = 0,
                    coverUrl = cover,
                    provider = "iTunes"
                )
            }.distinctBy { it.id }

            if (songs.isEmpty()) {
                Result.failure(Exception("No se encontraron canciones para este album."))
            } else {
                Result.success(songs)
            }
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
            val domainSongs = (resultInnerTube.getOrNull() ?: emptyList())
                .distinctBy { canonicalSongKey(it.title, it.artist) }

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
            val localPath = song.localPath
            val validPath = if (localPath.endsWith(".pba", ignoreCase = true)) {
                val target = File(localPath)
                if (!target.exists()) {
                    return Result.failure(Exception("El archivo offline no existe."))
                }
                "encfile://$localPath"
            } else if (localPath.startsWith("content://")) {
                localPath
            } else if (localPath.startsWith("file://")) {
                val normalized = localPath.removePrefix("file://")
                val target = File(normalized)
                if (!target.exists()) {
                    return Result.failure(Exception("El archivo local no existe."))
                }
                localPath
            } else {
                val target = File(localPath)
                if (!target.exists()) {
                    return Result.failure(Exception("El archivo local no existe."))
                }
                "file://$localPath"
            }
            return Result.success(validPath)
        }

        // 2. Extraemos el audio crudo en el cliente con StreamResolver
        return try {
            val streamResult = withTimeout(STREAM_TIMEOUT_MS) {
                if (song.provider.equals("YouTube", ignoreCase = true)) {
                    streamResolver.getStreamUrl(song.id)
                } else {
                    val query = "${song.title} - ${song.artist}".trim()
                    streamResolver.resolveBySearch(query)
                }
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
            val existingPath = song.localPath
            if (!existingPath.isNullOrBlank() && !existingPath.startsWith(PENDING_DOWNLOAD_PATH)) {
                val normalizedExistingPath = if (existingPath.startsWith("file://")) {
                    existingPath.removePrefix("file://")
                } else {
                    existingPath
                }

                if (File(normalizedExistingPath).exists()) {
                    return Result.success(Unit)
                }

                songDao.updateLocalPath(song.id, null)
            }

            // Enqueuemos el Worker usando solo el ID de la canción para evitar
            // bloquear el hilo actual o tener URLs expiradas en el worker.
            songDao.updateLocalPath(song.id, PENDING_DOWNLOAD_PATH)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<SongDownloadWorker>()
                .setInputData(
                    workDataOf(
                        SongDownloadWorker.KEY_SONG_ID to song.id
                    )
                )
                .addTag("song_download")
                .addTag("download_song_${song.id}")
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                "download_${song.id}",
                ExistingWorkPolicy.REPLACE,
                request
            )

            val finalInfo = workManager
                .getWorkInfoByIdFlow(request.id)
                .filterNotNull()
                .first { it.state.isFinished }

            if (finalInfo.state == WorkInfo.State.SUCCEEDED) {
                Result.success(Unit)
            } else {
                val latest = songDao.getSongById(song.id)
                if (latest?.localPath?.startsWith(PENDING_DOWNLOAD_PATH) == true) {
                    songDao.updateLocalPath(song.id, null)
                }
                Result.failure(Exception("No se pudo completar la descarga"))
            }
        } catch (e: Exception) {
            Result.failure(mapStreamError(e))
        }
    }

    override suspend fun cancelSongDownload(songId: String): Result<Unit> {
        return try {
            workManager.cancelUniqueWork("download_$songId")

            val song = songDao.getSongById(songId)
            if (song?.localPath?.startsWith(PENDING_DOWNLOAD_PATH) == true) {
                songDao.updateLocalPath(songId, null)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeDownloadedSong(songId: String): Result<Unit> {
        return try {
            workManager.cancelUniqueWork("download_$songId")

            val song = songDao.getSongById(songId)
            val localPath = song?.localPath

            if (!localPath.isNullOrBlank() && !localPath.startsWith(PENDING_DOWNLOAD_PATH)) {
                val filePath = if (localPath.startsWith("file://")) {
                    localPath.removePrefix("file://")
                } else {
                    localPath
                }

                val target = File(filePath)
                if (target.exists()) {
                    target.delete()
                }
            }

            songDao.updateLocalPath(songId, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getRecentSearchQueries(limit: Int): Flow<List<String>> {
        return searchHistoryDao.getRecentSearches(limit).map { items ->
            items.map { it.query.trim() }.filter { it.isNotBlank() }
        }
    }
}
