package com.phantombeats.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.phantombeats.data.local.entity.StreamCacheEntity

@Dao
interface StreamCacheDao {
    @Query("SELECT * FROM stream_cache WHERE songId = :songId")
    suspend fun getStream(songId: String): StreamCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStream(stream: StreamCacheEntity)

    @Query("DELETE FROM stream_cache WHERE expiryTimeMs < :currentTime")
    suspend fun cleanExpiredStreams(currentTime: Long)

    @Query("DELETE FROM stream_cache")
    suspend fun clearAll()
}
