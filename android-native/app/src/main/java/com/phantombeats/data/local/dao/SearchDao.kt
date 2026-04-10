package com.phantombeats.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.phantombeats.data.local.entity.SongEntity

@Dao
interface SearchDao {
    /**
     * Búsqueda ultra-rápida utilizando Full-Text Search (FTS4).
     * Devuelve los resultados de la tabla original ordenados por relevancia implícita match.
     */
    @Query("""
        SELECT songs.* FROM songs 
        JOIN songs_fts ON songs.rowid = songs_fts.rowid 
        WHERE songs_fts MATCH :query
    """)
    suspend fun searchSongsFts(query: String): List<SongEntity>
}
