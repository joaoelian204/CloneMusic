package com.phantombeats.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantombeats.domain.model.Song
import com.phantombeats.domain.repository.SongRepository
import com.phantombeats.player.PhantomPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

sealed class PlayerUiState {
    object Idle : PlayerUiState()
    object Buffering : PlayerUiState()
    data class Playing(val song: Song, val useLocalCache: Boolean = false) : PlayerUiState()
    data class Error(val message: String, val song: Song? = null) : PlayerUiState()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerController: PhantomPlayerController,
    private val repository: SongRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val currentSong: StateFlow<Song?> = playerController.currentSong
    val positionMs: StateFlow<Long> = playerController.positionMs
    val durationMs: StateFlow<Long> = playerController.durationMs
    val bufferedPositionMs: StateFlow<Long> = playerController.bufferedPositionMs

    private val playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    private var currentQueueIndex = -1

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            playerController.lastErrorMessage.collectLatest { errorMessage ->
                if (!errorMessage.isNullOrBlank()) {
                    _uiState.value = PlayerUiState.Error(
                        message = "Reproducción fallida: $errorMessage",
                        song = currentSong.value
                    )
                }
            }
        }
    }

    fun stop() {
        playerController.stopPlayback()
        _uiState.value = PlayerUiState.Idle
    }

    fun playSong(song: Song) {
        playSongsQueue(listOf(song), 0)
    }

    fun playSongsQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        playbackQueue.value = songs
        currentQueueIndex = startIndex.coerceIn(0, songs.lastIndex)
        _uiState.value = PlayerUiState.Buffering
        playerController.playSongs(songs, currentQueueIndex)
        val song = songs.getOrNull(currentQueueIndex)
        if (song != null) {
            _uiState.value = PlayerUiState.Playing(song = song, useLocalCache = false)
        }
    }

    fun playNextInQueue() {
        playerController.playNext()
    }

    fun playPreviousInQueue() {
        playerController.playPrevious()
    }

    fun toggleShuffleMode() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        playerController.setShuffleMode(_shuffleEnabled.value)
    }

    fun setShuffleMode(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        playerController.setShuffleMode(enabled)
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun seekToFraction(progress: Float) {
        val duration = durationMs.value
        if (duration <= 0L) return
        val clamped = progress.coerceIn(0f, 1f)
        val target = (duration * clamped).toLong()
        playerController.seekTo(target)
    }

    fun setFavorite(songId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(songId, isFavorite)

            val current = _uiState.value
            if (current is PlayerUiState.Playing && current.song.id == songId) {
                val updated = current.song.copy(isFavorite = isFavorite)
                _uiState.value = current.copy(song = updated)
                playerController.updateCurrentSong(updated)
            }
        }
    }

    fun setFavorite(song: Song, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(song.id, isFavorite)
            if (isFavorite) {
                repository.downloadSong(song)
            }

            val current = _uiState.value
            if (current is PlayerUiState.Playing && current.song.id == song.id) {
                val updated = current.song.copy(isFavorite = isFavorite)
                _uiState.value = current.copy(song = updated)
                playerController.updateCurrentSong(updated)
            }
        }
    }
}
