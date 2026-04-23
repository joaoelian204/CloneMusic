package com.phantombeats.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.phantombeats.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton inyectable en ViewModels para actuar como el "Puente / Control Remoto"
 * hacia el PhantomMediaService (Servicio en Segundo Plano).
 */
@Singleton
class PhantomPlayerController @Inject constructor(
    private val context: Context
) {

    data class ResolvedQueueItem(
        val song: Song,
        val streamUri: String
    )

    private data class PendingPlayback(
        val queueItems: List<ResolvedQueueItem>,
        val startIndex: Int
    )

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var pendingPlayback: PendingPlayback? = null

    companion object {
        private const val PROGRESS_TICK_MS = 250L
    }

    // Flujos reactivos de Compose para dibujar la UI (Play/Pause, progreso, etc)
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()
    
    // Mantenemos referencia de las canciones para buscarlas por mediaId
    private var currentPlaylist = emptyList<Song>()

    @Volatile
    private var controllerInitializationStarted = false

    private fun ensureControllerInitialized() {
        if (controllerInitializationStarted || mediaController != null) return
        controllerInitializationStarted = true
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PhantomMediaService::class.java)
        )
        
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                setupPlayerListeners()
                playPendingIfAny()
            } catch (t: Throwable) {
                controllerInitializationStarted = false
                _lastErrorMessage.value = "No se pudo inicializar el reproductor. Intenta nuevamente."
            }
        }, MoreExecutors.directExecutor())
    }

    fun preWarm() {
        ensureControllerInitialized()
    }

    private fun playPendingIfAny() {
        val pending = pendingPlayback ?: return
        val controller = mediaController ?: return

        pendingPlayback = null
        startPlayback(controller, pending.queueItems, pending.startIndex)
    }

    private fun setupPlayerListeners() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressUpdater()
                } else {
                    stopProgressUpdater()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    stopProgressUpdater()
                }
                syncProgressFromController()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    _currentSong.value = currentPlaylist.find { it.id == mediaItem.mediaId }
                }
                syncProgressFromController()
            }

            override fun onPlayerError(error: PlaybackException) {
                _isPlaying.value = false
                _lastErrorMessage.value = error.localizedMessage ?: "No se pudo reproducir el audio."
                syncProgressFromController()
            }
        })
    }

    private fun startProgressUpdater() {
        if (progressJob?.isActive == true) return

        progressJob = controllerScope.launch {
            while (isActive) {
                syncProgressFromController()
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    private fun stopProgressUpdater() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun syncProgressFromController() {
        val controller = mediaController ?: return
        _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
        _bufferedPositionMs.value = controller.bufferedPosition.coerceAtLeast(0L)

        val duration = controller.duration
        _durationMs.value = if (duration == C.TIME_UNSET || duration <= 0L) 0L else duration
    }

    /**
     * Ordena a Media3 ExoPlayer que reproduzca una lista de canciones 
     * activando Gapless Playback nativo.
     * Soporta URLs crudas "phantom-yt://" para delegar Client-Side Stream Resolution.
     */
    fun playResolvedQueue(queueItems: List<ResolvedQueueItem>, startIndex: Int) {
        ensureControllerInitialized()
        val controller = mediaController
        if (controller == null) {
            pendingPlayback = PendingPlayback(queueItems = queueItems, startIndex = startIndex)
            _currentSong.value = queueItems.getOrNull(startIndex)?.song
            _isPlaying.value = false
            return
        }

        startPlayback(controller, queueItems, startIndex)
    }

    private fun startPlayback(
        controller: MediaController,
        queueItems: List<ResolvedQueueItem>,
        startIndex: Int
    ) {
        if (queueItems.isEmpty()) {
            _lastErrorMessage.value = "No hay canciones reproducibles en esta selección."
            return
        }

        _lastErrorMessage.value = null
        currentPlaylist = queueItems.map { it.song }

        val mediaItems = queueItems.map { item ->
            val song = item.song
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)

            if (song.coverUrl.isNotBlank()) {
                metadataBuilder.setArtworkUri(android.net.Uri.parse(song.coverUrl))
            }

            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(item.streamUri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }

        // Ajustamos el startIndex por si se filtraron canciones
        val adjustedIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        _currentSong.value = queueItems.getOrNull(startIndex)?.song

        controller.apply {
            setMediaItems(mediaItems, adjustedIndex, C.TIME_UNSET)
            prepare()
            play()
        }

        syncProgressFromController()
    }

    fun playNext() {
        ensureControllerInitialized()
        mediaController?.seekToNext()
    }

    fun playPrevious() {
        ensureControllerInitialized()
        mediaController?.seekToPrevious()
    }

    fun setShuffleMode(enabled: Boolean) {
        ensureControllerInitialized()
        mediaController?.shuffleModeEnabled = enabled
    }

    fun stopPlayback() {
        mediaController?.let {
            if (it.isPlaying) {
                it.pause()
            }
            it.stop()
            it.clearMediaItems()
        }
        stopProgressUpdater()
    }

    fun togglePlayPause() {
        ensureControllerInitialized()
        if (mediaController?.isPlaying == true) {
            mediaController?.pause()
        } else {
            mediaController?.play()
        }
    }

    fun seekTo(positionMs: Long) {
        ensureControllerInitialized()
        mediaController?.seekTo(positionMs)
        _positionMs.value = positionMs.coerceAtLeast(0L)
    }

    fun updateCurrentSong(updatedSong: Song) {
        val current = _currentSong.value
        if (current?.id == updatedSong.id) {
            _currentSong.value = updatedSong
        }
    }

    fun destroy() {
        stopProgressUpdater()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
