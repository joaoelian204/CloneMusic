package com.phantombeats.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.phantombeats.data.local.entity.FavoriteEntity
import com.phantombeats.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun deleteFavorite(songId: String)

    @Transaction
    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN favorites ON songs.id = favorites.songId 
        ORDER BY favorites.addedAt DESC
    """)
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: String): Flow<Boolean>
}
