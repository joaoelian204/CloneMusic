package com.phantombeats.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantombeats.domain.model.Song
import com.phantombeats.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: SongRepository
) : ViewModel() {

    val cacheInitialized: StateFlow<Boolean> = repository.getAllCachedSongs()
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val cachedSongs: StateFlow<List<Song>> = repository.getAllCachedSongs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.getAllCachedSongs()
        .map { songs -> songs.filter { it.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val downloadedSongs: StateFlow<List<Song>> = repository.getAllCachedSongs()
        .map { songs -> songs.filter { it.isDownloaded } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingDownloadsCount: StateFlow<Int> = repository.getAllCachedSongs()
        .map { songs -> songs.count { it.localPath?.startsWith("__PENDING__") == true } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val recentSearchQueries: StateFlow<List<String>> = repository.getRecentSearchQueries(limit = 20)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
