package com.phantombeats.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.phantombeats.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): SongEntity?

    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE localPath IS NOT NULL")
    fun getDownloadedSongs(): Flow<List<SongEntity>>

    // Búsqueda Offline Fuzzy en Título y Artista
    @Query("""
        SELECT * FROM songs 
        WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' 
           OR LOWER(artist) LIKE '%' || LOWER(:query) || '%'
        ORDER BY playCount DESC, lastPlayed DESC
    """)
    suspend fun searchSongsOffline(query: String): List<SongEntity>

    // Incrementar contadores 
    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET isFavorite = :isFav WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: String, isFav: Boolean)

    @Query("UPDATE songs SET localPath = :localPath WHERE id = :songId")
    suspend fun updateLocalPath(songId: String, localPath: String)
}
