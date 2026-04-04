package com.phantombeats.player

import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Servicio en Foreground que mantiene a ExoPlayer con vida 
 * incluso cuando la app está minimizada o la pantalla apagada.
 * Utiliza Media3 (la API moderna que reemplazó a MediaBrowserServiceCompat).
 */
@AndroidEntryPoint
class PhantomMediaService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        // 3. Crear la Sesión Media vinculada al Player inyectado
        mediaSession = MediaSession.Builder(this, player).build()
    }

    // Android llamará a esto para que los clientes (MediaController) se conecten
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // Limpieza crítica para no dejar Memory Leaks cuando el SO destruya el servicio
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    // Previene reinicios indeseados del servicio (Típico en reproductores musicales)
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }
}
