package com.phantombeats.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import com.phantombeats.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en Foreground que mantiene a ExoPlayer con vida 
 * y lo hace DISPONIBLE (Visible) nativamente para Android Auto,
 * WearOS, auriculares Bluetooth y Pantalla de Bloqueo.
 */
@AndroidEntryPoint
class PhantomMediaService : MediaLibraryService() {

    companion object {
        private const val STOP_AFTER_PAUSE_DELAY_MS = 45_000L
    }

    @Inject
    lateinit var player: ExoPlayer

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var servicePlayerListener: Player.Listener? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var delayedStopJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        val openPlayerIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_FULL_PLAYER, true)
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            1001,
            openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Crear Session Library vinculada (Abre pasarela para coches y BT)
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, CustomMediaLibrarySessionCallback())
            .setSessionActivity(sessionActivity)
            .build()

        servicePlayerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    delayedStopJob?.cancel()
                    delayedStopJob = null
                    return
                }

                delayedStopJob?.cancel()
                delayedStopJob = serviceScope.launch {
                    delay(STOP_AFTER_PAUSE_DELAY_MS)
                    val stillPaused = !player.isPlaying && !player.playWhenReady
                    val idleOrReady = player.playbackState == Player.STATE_IDLE ||
                        player.playbackState == Player.STATE_READY ||
                        player.playbackState == Player.STATE_ENDED

                    if (stillPaused && idleOrReady) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val shouldStop = playbackState == Player.STATE_IDLE &&
                    player.mediaItemCount == 0 &&
                    !player.playWhenReady

                if (shouldStop) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        player.addListener(servicePlayerListener!!)
    }

    // Callback para interconectar con Android Auto y WearOS (Devuelve raíces de música) 
    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        // Todo: Implementaciones de .onGetLibraryRoot(), .onGetChildren()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        delayedStopJob?.cancel()
        delayedStopJob = null
        serviceScope.coroutineContext.cancel()
        servicePlayerListener?.let { player.removeListener(it) }
        servicePlayerListener = null
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        super.onDestroy()
    }

    // Previene reinicios indeseados del servicio (Típico en reproductores musicales)
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }
}
