package com.phantombeats.data.repository

import com.phantombeats.data.local.dao.FavoriteDao
import com.phantombeats.data.local.dao.SearchDao
import com.phantombeats.data.local.dao.SearchHistoryDao
import com.phantombeats.data.local.dao.SongDao
import com.phantombeats.data.local.entity.FavoriteEntity
import com.phantombeats.data.local.entity.SearchHistoryEntity
import com.phantombeats.data.mapper.toDomain
import com.phantombeats.data.mapper.toEntity
import com.phantombeats.domain.model.Album
import com.phantombeats.domain.model.Artist
import com.phantombeats.domain.model.Song
import com.phantombeats.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CancellationException
import javax.inject.Inject
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.phantombeats.data.download.SongDownloadWorker

class SongRepositoryImpl @Inject constructor(
    private val searchDao: SearchDao,
    private val songDao: SongDao,
    private val favoriteDao: FavoriteDao,
    private val searchHistoryDao: SearchHistoryDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : SongRepository {

    override suspend fun searchSongs(query: String, mode: String): Result<List<Song>> {
        return try {
            if (query.isNotBlank()) {
                searchHistoryDao.insertSearchQuery(SearchHistoryEntity(query, System.currentTimeMillis()))
            }
            val songs = searchDao.searchSongsFts("*${query}*").map { it.toDomain() }
            Result.success(songs)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> {
        return Result.success(emptyList()) // Offline-first proxy
    }

    override suspend fun searchAlbums(query: String, limit: Int): Result<List<Album>> {
        return Result.success(emptyList()) // Offline-first proxy
    }

    override suspend fun searchSongsPaged(
        query: String,
        limit: Int,
        offset: Int,
        mode: String
    ): Result<List<Song>> {
        return try {
            val allSongs = searchDao.searchSongsFts("*${query}*").map { it.toDomain() }
            val paged = allSongs.drop(offset).take(limit)
            Result.success(paged)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> {
        return if (!song.localPath.isNullOrBlank()) {
            Result.success("file://${song.localPath}")
        } else {
            // Requiere StreamResolver en otra capa. Acoplado por dominio a URL crudo fallara si no esta local.
            Result.failure(Exception("Not found locally"))
        }
    }

    override fun getAllCachedSongs(): Flow<List<Song>> {
        return songDao.getAllSongs().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun markAsPlayed(songId: String) {
        songDao.incrementPlayCount(songId, System.currentTimeMillis())
    }

    override suspend fun setFavorite(songId: String, isFavorite: Boolean): Result<Unit> {
        return try {
            if (isFavorite) {
                val entity = FavoriteEntity(songId, System.currentTimeMillis())
                favoriteDao.insertFavorite(entity)
                val request = androidx.work.OneTimeWorkRequestBuilder<com.phantombeats.data.download.SongDownloadWorker>()
                    .setInputData(androidx.work.workDataOf(com.phantombeats.data.download.SongDownloadWorker.KEY_SONG_ID to songId))
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueue(request)            } else {
                favoriteDao.deleteFavorite(songId)
            }
            songDao.updateFavoriteStatus(songId, isFavorite)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadSong(song: Song): Result<Unit> {
        val request = androidx.work.OneTimeWorkRequestBuilder<com.phantombeats.data.download.SongDownloadWorker>()
            .setInputData(androidx.work.workDataOf(com.phantombeats.data.download.SongDownloadWorker.KEY_SONG_ID to song.id))
            .build()
        androidx.work.WorkManager.getInstance(context).enqueue(request)
        return Result.success(Unit)
    }
}
