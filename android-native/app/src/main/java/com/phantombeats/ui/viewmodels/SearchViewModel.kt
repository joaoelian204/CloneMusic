package com.phantombeats.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantombeats.domain.model.Song
import com.phantombeats.domain.model.Artist
import com.phantombeats.domain.model.Album
import com.phantombeats.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(
        val songs: List<Song>,
        val artists: List<Artist> = emptyList(),
        val albums: List<Album> = emptyList(),
        val query: String,
        val mode: String,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false,
        val isOfflineFallback: Boolean = false
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 25
    }

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(query: String, mode: String = "balanced") {
        if (query.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }

        _uiState.value = SearchUiState.Loading

        viewModelScope.launch {
            try {
                coroutineScope {
                    val songsDeferred = async { songRepository.searchSongsPaged(query, PAGE_SIZE, 0, mode) }
                    val artistsDeferred = async { songRepository.searchArtists(query, 5) }
                    val albumsDeferred = async { songRepository.searchAlbums(query, 5) }

                    val songsResult = songsDeferred.await()
                    val artistsResult = artistsDeferred.await()
                    val albumsResult = albumsDeferred.await()

                    songsResult.onSuccess { songs ->
                        _uiState.value = SearchUiState.Success(
                            songs = songs,
                            artists = artistsResult.getOrDefault(emptyList()),
                            albums = albumsResult.getOrDefault(emptyList()),
                            query = query,
                            mode = mode,
                            hasMore = songs.size >= PAGE_SIZE,
                            isLoadingMore = false
                        )
                    }.onFailure { error ->
                        _uiState.value = SearchUiState.Error(error.localizedMessage ?: "Error desconocido")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.localizedMessage ?: "Error desconocido")
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value as? SearchUiState.Success ?: return
        if (!state.hasMore || state.isLoadingMore) return

        _uiState.value = state.copy(isLoadingMore = true)

        viewModelScope.launch {
            val result = songRepository.searchSongsPaged(
                query = state.query,
                limit = PAGE_SIZE,
                offset = state.songs.size,
                mode = state.mode
            )

            result.onSuccess { newSongs ->
                val mergedSongs = (state.songs + newSongs)
                    .distinctBy { it.id }
                _uiState.value = state.copy(
                    songs = mergedSongs,
                    hasMore = newSongs.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
            }.onFailure {
                _uiState.value = state.copy(isLoadingMore = false)
            }
        }
    }

    fun updateFavoriteLocal(songId: String, isFavorite: Boolean) {
        val state = _uiState.value
        if (state is SearchUiState.Success) {
            _uiState.value = state.copy(
                songs = state.songs.map { song ->
                    if (song.id == songId) song.copy(isFavorite = isFavorite) else song
                }
            )
        }
    }
}
