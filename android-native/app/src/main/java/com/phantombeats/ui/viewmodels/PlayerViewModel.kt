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
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

sealed class PlayerUiState {
    object Idle : PlayerUiState()
    object Buffering : PlayerUiState()
    data class Playing(val song: Song, val useLocalCache: Boolean = false) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
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

    fun playSong(song: Song) {
        if (playbackQueue.value.none { it.id == song.id }) {
            playbackQueue.value = listOf(song)
            currentQueueIndex = 0
        } else {
            currentQueueIndex = playbackQueue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        }

        playSongInternal(song)
    }

    fun playSongsQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return

        playbackQueue.value = songs
        currentQueueIndex = startIndex.coerceIn(0, songs.lastIndex)
        playSongInternal(songs[currentQueueIndex])
    }

    fun playNextInQueue() {
        val queue = playbackQueue.value
        if (queue.isEmpty()) return

        currentQueueIndex = when {
            queue.size == 1 -> 0
            _shuffleEnabled.value -> {
                var next = currentQueueIndex
                while (next == currentQueueIndex) {
                    next = Random.nextInt(queue.size)
                }
                next
            }
            else -> {
                val current = if (currentQueueIndex in queue.indices) currentQueueIndex else 0
                (current + 1) % queue.size
            }
        }

        playSongInternal(queue[currentQueueIndex])
    }

    fun playPreviousInQueue() {
        val queue = playbackQueue.value
        if (queue.isEmpty()) return

        val current = if (currentQueueIndex in queue.indices) currentQueueIndex else 0
        currentQueueIndex = if (current <= 0) queue.lastIndex else current - 1
        playSongInternal(queue[currentQueueIndex])
    }

    fun toggleShuffleMode() {
        _shuffleEnabled.value = !_shuffleEnabled.value
    }

    fun setShuffleMode(enabled: Boolean) {
        _shuffleEnabled.value = enabled
    }

    private fun playSongInternal(song: Song) {
        _uiState.value = PlayerUiState.Buffering

        val localUri = song.localPath
        if (
            song.provider == "Local" &&
            !localUri.isNullOrBlank() &&
            (localUri.startsWith("content://") || localUri.startsWith("file://"))
        ) {
            playerController.playSong(song, localUri)
            _uiState.value = PlayerUiState.Playing(song = song, useLocalCache = true)
            return
        }

        viewModelScope.launch {
            // 1. Resolver la URL (Puede ser Local File o HTTP Stream de Go)
            val streamResult = repository.getStreamUrl(song)

            streamResult.onSuccess { streamUrl ->
                // 2. Darle play en ExoPlayer
                playerController.playSong(song, streamUrl)
                
                // 3. Registrar "Escuchada" para el historial
                repository.markAsPlayed(song.id)

                _uiState.value = PlayerUiState.Playing(
                    song = song, 
                    useLocalCache = streamUrl.startsWith("file://")
                )
            }.onFailure { error ->
                _uiState.value = PlayerUiState.Error("Stream no disponible: ${error.message}")
            }
        }
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
                _uiState.value = current.copy(song = current.song.copy(isFavorite = isFavorite))
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
                _uiState.value = current.copy(song = current.song.copy(isFavorite = isFavorite))
            }
        }
    }
}
