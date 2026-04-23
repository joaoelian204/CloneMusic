package com.phantombeats.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantombeats.domain.model.Song
import com.phantombeats.domain.model.Artist
import com.phantombeats.domain.model.Album
import com.phantombeats.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
        private const val PAGE_SIZE = 15
    }

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    private fun isCancellationError(error: Throwable): Boolean {
        if (error is CancellationException) return true
        val msg = error.localizedMessage?.lowercase() ?: return false
        return msg.contains("cancel")
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun findArtistCandidate(query: String, artists: List<Artist>): Artist? {
        if (artists.isEmpty()) return null
        val queryNorm = normalize(query)
        if (queryNorm.length < 3) return null

        return artists.firstOrNull { artist ->
            val artistNorm = normalize(artist.name)
            artistNorm == queryNorm || artistNorm.contains(queryNorm) || queryNorm.contains(artistNorm)
        } ?: artists.firstOrNull()
    }

    private fun queryTokens(query: String): List<String> =
        normalize(query)
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 }

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

    private fun normalizedSongKey(song: Song): String {
        return "${canonicalSongTitle(song.title)}|${normalize(song.artist)}"
    }

    private fun queryRequestsVersion(query: String): Boolean {
        val q = normalize(query)
        val versionTokens = listOf("live", "remix", "acoustic", "remaster", "version", "edit")
        return versionTokens.any { q.contains(it) }
    }

    private fun isVersionedTitle(title: String): Boolean {
        val t = normalize(title)
        return listOf("live", "remix", "acoustic", "remaster", "version", "edit", "karaoke").any { t.contains(it) }
    }

    private fun albumRelevanceScore(album: Album, artistName: String, query: String): Int {
        val artistNorm = normalize(album.artistName)
        val artistTarget = normalize(artistName)
        val titleNorm = normalize(album.title)
        val tokens = queryTokens(query)

        var score = 0
        if (artistNorm == artistTarget) score += 100
        else if (artistNorm.contains(artistTarget)) score += 70

        score += tokens.count { token -> titleNorm.contains(token) || artistNorm.contains(token) } * 8
        score += (album.releaseYear.toIntOrNull() ?: 0) / 10
        return score
    }

    private fun songRelevanceScore(song: Song, artistName: String, query: String, popularityRank: Int): Int {
        val artistNorm = normalize(song.artist)
        val artistTarget = normalize(artistName)
        val titleNorm = normalize(song.title)
        val tokens = queryTokens(query)
        val queryRequestsVersion = queryRequestsVersion(query)

        var score = 0
        if (artistNorm == artistTarget) score += 100
        else if (artistNorm.contains(artistTarget)) score += 70

        score += tokens.count { token -> titleNorm.contains(token) || artistNorm.contains(token) } * 6
        if (song.isFavorite) score += 10
        score += (500 - popularityRank).coerceAtLeast(0) / 5

        if (!queryRequestsVersion && isVersionedTitle(song.title)) {
            score -= 24
        }
        return score
    }

    fun search(query: String, mode: String = "balanced") {
        if (query.isBlank()) {
            searchJob?.cancel()
            _uiState.value = SearchUiState.Idle
            return
        }
        
        // Evitar recarga si ya tenemos la misma búsqueda cargada exitosamente
        val current = _uiState.value
        if (current is SearchUiState.Success && current.query == query && current.mode == mode) {
            return
        }

        searchJob?.cancel()
        _uiState.value = SearchUiState.Loading

        searchJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val songsDeferred = async { songRepository.searchSongsPaged(query, PAGE_SIZE, 0, mode) }
                    val artistsDeferred = async {
                        if (mode == "turbo") emptyList()
                        else withTimeoutOrNull(1_500L) { songRepository.searchArtists(query, 8).getOrDefault(emptyList()) }
                            ?: emptyList()
                    }
                    val albumsDeferred = async {
                        if (mode == "turbo") emptyList()
                        else withTimeoutOrNull(1_500L) { songRepository.searchAlbums(query, 8).getOrDefault(emptyList()) }
                            ?: emptyList()
                    }

                    val songsResult = songsDeferred.await()
                    val artists = artistsDeferred.await()
                    val albums = albumsDeferred.await()

                    songsResult.onSuccess { songs ->
                        val fastSongs = songs.distinctBy { normalizedSongKey(it) }

                        _uiState.value = SearchUiState.Success(
                            songs = fastSongs,
                            artists = artists,
                            albums = albums,
                            query = query,
                            mode = mode,
                            hasMore = songs.size >= PAGE_SIZE,
                            isLoadingMore = false
                        )

                        // Enriquecimiento no bloqueante: solo para busqueda balanceada y consulta larga.
                        if (mode == "balanced" && query.length >= 3) {
                            val baseState = _uiState.value as? SearchUiState.Success ?: return@onSuccess
                            val artistCandidate = findArtistCandidate(query, artists)
                            if (artistCandidate != null) {
                                viewModelScope.launch enrich@ {
                                    val fetchedSongs = withTimeoutOrNull(2_500L) {
                                        songRepository
                                            .searchAllSongsByArtist(artistCandidate.name, maxSongs = 120)
                                            .getOrDefault(baseState.songs)
                                    } ?: baseState.songs

                                    val fetchedAlbums = withTimeoutOrNull(2_000L) {
                                        songRepository
                                            .searchAllAlbumsByArtist(artistCandidate.name, maxAlbums = 80)
                                            .getOrDefault(baseState.albums)
                                    } ?: baseState.albums

                                    val popularityMap = fetchedSongs
                                        .withIndex()
                                        .associate { (index, song) -> song.id to index }

                                    val rankedSongs = fetchedSongs
                                        .distinctBy { normalizedSongKey(it) }
                                        .sortedWith(
                                            compareByDescending<Song> {
                                                songRelevanceScore(
                                                    song = it,
                                                    artistName = artistCandidate.name,
                                                    query = query,
                                                    popularityRank = popularityMap[it.id] ?: Int.MAX_VALUE
                                                )
                                            }
                                                .thenBy { normalize(it.title) }
                                        )

                                    val rankedAlbums = fetchedAlbums
                                        .sortedWith(
                                            compareByDescending<Album> {
                                                albumRelevanceScore(it, artistCandidate.name, query)
                                            }
                                                .thenByDescending { it.releaseYear.toIntOrNull() ?: 0 }
                                                .thenBy { normalize(it.title) }
                                        )

                                    val currentState = _uiState.value as? SearchUiState.Success ?: return@enrich
                                    if (currentState.query == query && currentState.mode == mode) {
                                        _uiState.value = currentState.copy(
                                            songs = rankedSongs,
                                            albums = rankedAlbums
                                        )
                                    }
                                }
                            }
                        }
                    }.onFailure { error ->
                        if (!isCancellationError(error)) {
                            _uiState.value = SearchUiState.Error(error.localizedMessage ?: "No se pudo completar la busqueda")
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Cancelaciones por escritura rápida/debounce: no son errores de UX.
            } catch (e: Exception) {
                if (isCancellationError(e)) {
                    return@launch
                }
                _uiState.value = SearchUiState.Error(e.localizedMessage ?: "No se pudo completar la busqueda")
            }
        }
    }

    fun searchAlbumTracks(albumId: String, albumTitle: String, artistName: String) {
        _uiState.value = SearchUiState.Loading

        viewModelScope.launch {
            songRepository
                .getAlbumTracks(albumId = albumId, albumTitle = albumTitle, artistName = artistName)
                .onSuccess { songs ->
                    _uiState.value = SearchUiState.Success(
                        songs = songs,
                        artists = emptyList(),
                        albums = emptyList(),
                        query = "$artistName $albumTitle",
                        mode = "album",
                        hasMore = false,
                        isLoadingMore = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = SearchUiState.Error(
                        error.localizedMessage ?: "No se pudo cargar este album"
                    )
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
