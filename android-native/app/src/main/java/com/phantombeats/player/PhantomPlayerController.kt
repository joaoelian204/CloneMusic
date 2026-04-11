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

    private data class PendingPlayback(
        val songs: List<Song>,
        val startIndex: Int
    )

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var pendingPlayback: PendingPlayback? = null

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

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PhantomMediaService::class.java)
        )
        
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            setupPlayerListeners()
            playPendingIfAny()
        }, MoreExecutors.directExecutor())
    }

    private fun playPendingIfAny() {
        val pending = pendingPlayback ?: return
        val controller = mediaController ?: return

        pendingPlayback = null
        startPlayback(controller, pending.songs, pending.startIndex)
    }

    private fun setupPlayerListeners() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
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

        startProgressUpdater()
    }

    private fun startProgressUpdater() {
        if (progressJob?.isActive == true) return

        progressJob = controllerScope.launch {
            while (isActive) {
                syncProgressFromController()
                delay(500)
            }
        }
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
    fun playSongs(songs: List<Song>, startIndex: Int) {
        val controller = mediaController
        if (controller == null) {
            pendingPlayback = PendingPlayback(songs = songs, startIndex = startIndex)
            _currentSong.value = songs.getOrNull(startIndex)
            _isPlaying.value = false
            return
        }

        startPlayback(controller, songs, startIndex)
    }

    private fun startPlayback(controller: MediaController, songs: List<Song>, startIndex: Int) {
        _lastErrorMessage.value = null
        currentPlaylist = songs

        val mediaItems = songs.mapNotNull { song ->
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)

            if (song.coverUrl.isNotBlank()) {
                metadataBuilder.setArtworkUri(android.net.Uri.parse(song.coverUrl))
            }
            
            // Determinamos la URI de audio según el origen
            val directUri = if (song.isDownloaded && song.localPath != null) {
                if (song.localPath.startsWith("content://") || song.localPath.startsWith("file://")) {
                    song.localPath
                } else {
                    "file://${song.localPath}"
                }
            } else if (song.provider == "YouTube") {
                "phantom-yt:${song.id}"
            } else {
                // iTunes y otros usamos búsqueda
                "phantom-search:${song.title} - ${song.artist}"
            }

            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(directUri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }

        if (mediaItems.isEmpty()) {
            _lastErrorMessage.value = "No hay canciones reproducibles en esta selección."
            return
        }

        // Ajustamos el startIndex por si se filtraron canciones
        val adjustedIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        _currentSong.value = songs.getOrNull(startIndex)

        controller.apply {
            setMediaItems(mediaItems, adjustedIndex, C.TIME_UNSET)
            prepare()
            play()
        }

        syncProgressFromController()
    }

    fun playNext() {
        mediaController?.seekToNext()
    }

    fun playPrevious() {
        mediaController?.seekToPrevious()
    }

    fun setShuffleMode(enabled: Boolean) {
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
    }

    fun togglePlayPause() {
        if (mediaController?.isPlaying == true) {
            mediaController?.pause()
        } else {
            mediaController?.play()
        }
    }

    fun seekTo(positionMs: Long) {
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
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
