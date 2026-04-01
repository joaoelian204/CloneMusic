package com.phantombeats.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.phantombeats.data.local.entity.PlaylistEntity
import com.phantombeats.data.local.entity.PlaylistSongCrossRef
import com.phantombeats.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, newName: String)

    @Query("DELETE FROM playlists WHERE name = :name")
    suspend fun deletePlaylistByName(name: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("SELECT id FROM playlists WHERE name = :name LIMIT 1")
    suspend fun findPlaylistIdByName(name: String): String?

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongIdsInPlaylist(playlistId: String): List<String>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)

    @Query("UPDATE playlists SET coverUrl = :coverUri WHERE id = :playlistId")
    suspend fun updatePlaylistCover(playlistId: String, coverUri: String?)

    @Transaction
    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN playlist_songs ON songs.id = playlist_songs.songId 
        WHERE playlist_songs.playlistId = :playlistId 
        ORDER BY playlist_songs.addedAt DESC
    """)
    fun getSongsInPlaylist(playlistId: String): Flow<List<SongEntity>>
}
