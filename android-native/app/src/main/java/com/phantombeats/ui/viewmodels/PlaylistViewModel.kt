package com.phantombeats.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantombeats.data.local.dao.PlaylistDao
import com.phantombeats.data.local.entity.PlaylistEntity
import com.phantombeats.data.local.entity.PlaylistSongCrossRef
import com.phantombeats.data.mapper.toDomain
import com.phantombeats.domain.model.Song
import com.phantombeats.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    repository: SongRepository
) : ViewModel() {

    companion object {
        private const val FAVORITES_PLAYLIST = "Mis Favoritas"
    }

    private val selectedPlaylistId = MutableStateFlow<String?>(null)

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedPlaylistSongs: StateFlow<List<Song>> = selectedPlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                playlistDao.getSongsInPlaylist(playlistId).map { rows -> rows.map { it.toDomain() } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cachedSongs: StateFlow<List<Song>> = repository.getAllCachedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedPlaylist: StateFlow<PlaylistEntity?> = combine(playlists, selectedPlaylistId) { all, selectedId ->
        all.firstOrNull { it.id == selectedId } ?: all.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            ensureDefaultPlaylists()
            val firstPlaylist = playlists.value.firstOrNull()
            selectedPlaylistId.value = firstPlaylist?.id
        }

        viewModelScope.launch {
            combine(playlists, cachedSongs) { allPlaylists, allSongs ->
                allPlaylists to allSongs
            }.collect { (allPlaylists, allSongs) ->
                val favoritesPlaylist = allPlaylists.firstOrNull { it.name == FAVORITES_PLAYLIST }

                if (favoritesPlaylist != null) {
                    val favoriteIds = allSongs.filter { it.isFavorite }.map { it.id }.toSet()
                    syncPlaylist(favoritesPlaylist.id, favoriteIds)
                }
            }
        }
    }

    fun selectPlaylist(playlistId: String) {
        selectedPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String) {
        val safeName = name.trim()
        if (safeName.isBlank()) return

        viewModelScope.launch {
            val playlist = PlaylistEntity(name = safeName)
            playlistDao.insertPlaylist(playlist)
            selectedPlaylistId.value = playlist.id
        }
    }

    fun removePlaylist(playlistId: String) {
        viewModelScope.launch {
            val playlist = playlists.value.firstOrNull { it.id == playlistId } ?: return@launch
            if (playlist.name == FAVORITES_PLAYLIST) return@launch

            playlistDao.deletePlaylist(playlistId)
            val remaining = playlists.value.filterNot { it.id == playlistId }
            selectedPlaylistId.value = remaining.firstOrNull()?.id
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val safeName = newName.trim()
        if (safeName.isBlank()) return

        viewModelScope.launch {
            val playlist = playlists.value.firstOrNull { it.id == playlistId } ?: return@launch
            if (playlist.name == FAVORITES_PLAYLIST) return@launch
            playlistDao.renamePlaylist(playlistId, safeName)
        }
    }

    fun addSongToSelected(songId: String) {
        val playlistId = selectedPlaylist.value?.id ?: return
        addSongToPlaylist(playlistId, songId)
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = songId))
        }
    }

    fun setPlaylistCover(playlistId: String, coverUri: String?) {
        viewModelScope.launch {
            playlistDao.updatePlaylistCover(playlistId, coverUri)
        }
    }

    fun removeSongFromSelected(songId: String) {
        val playlistId = selectedPlaylist.value?.id ?: return
        viewModelScope.launch {
            playlistDao.removeSongFromPlaylist(playlistId, songId)
        }
    }

    private suspend fun ensureDefaultPlaylists() {
        playlistDao.deletePlaylistByName("Offline Ready")

        val favId = playlistDao.findPlaylistIdByName(FAVORITES_PLAYLIST)
        if (favId == null) {
            playlistDao.insertPlaylist(PlaylistEntity(name = FAVORITES_PLAYLIST))
        }
    }

    private suspend fun syncPlaylist(playlistId: String, targetSongIds: Set<String>) {
        val currentIds = playlistDao.getSongIdsInPlaylist(playlistId).toSet()
        if (currentIds == targetSongIds) return

        playlistDao.clearPlaylistSongs(playlistId)
        targetSongIds.forEach { songId ->
            playlistDao.addSongToPlaylist(
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = songId
                )
            )
        }
    }
}
