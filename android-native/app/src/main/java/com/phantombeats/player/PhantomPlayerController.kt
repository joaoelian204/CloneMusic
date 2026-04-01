package com.phantombeats.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

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
        }, MoreExecutors.directExecutor())
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
     * Ordena a Media3 ExoPlayer que reproduzca un Stream URL 
     * inyectándole Metadata para que el SO dibuje la notificación del LockScreen.
     */
    fun playSong(song: Song, streamUrl: String) {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)

        if (song.coverUrl.isNotBlank()) {
            metadataBuilder.setArtworkUri(android.net.Uri.parse(song.coverUrl))
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(streamUrl)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        _currentSong.value = song

        mediaController?.apply {
            setMediaItem(mediaItem)
            prepare()
            play() // Ejecuta el buffering y dispara reproducción!
        }

        syncProgressFromController()
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

    fun destroy() {
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}