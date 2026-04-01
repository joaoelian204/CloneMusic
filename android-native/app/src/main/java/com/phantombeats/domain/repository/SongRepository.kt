package com.phantombeats.domain.repository

import com.phantombeats.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    
    /**
     * Búsqueda inteligente Offline-First. 
     * 1. Busca en la API.
     * 2. Si falla la API, busca en la caché (Room) vía término (LIKE).
     */
    suspend fun searchSongs(query: String, mode: String = "balanced"): Result<List<Song>>

    /**
     * Variante paginada para explorar catálogos largos sin cargar todo de una sola vez.
     */
    suspend fun searchSongsPaged(
        query: String,
        limit: Int,
        offset: Int,
        mode: String = "balanced"
    ): Result<List<Song>>
    
    /**
     * Obtiene la URL de stream de la canción.
     * Si la canción tiene localPath, retorna una URL "file://".
     * Si no, pide al backend proxy la URL en crudo.
     */
    suspend fun getStreamUrl(song: Song): Result<String>

    /**
     * Retorna el flujo continuo de todas las canciones en BD (como historial o caché principal)
     */
    fun getAllCachedSongs(): Flow<List<Song>>

    /**
     * Actualiza metadatos como "reproducido 1 vez más"
     */
    suspend fun markAsPlayed(songId: String)

    /**
     * Agrega o quita una cancion de favoritos y sincroniza su estado local.
     */
    suspend fun setFavorite(songId: String, isFavorite: Boolean): Result<Unit>
    
    /**
     * Descarga y guarda en disco.
     */
    suspend fun downloadSong(song: Song): Result<Unit>
}
