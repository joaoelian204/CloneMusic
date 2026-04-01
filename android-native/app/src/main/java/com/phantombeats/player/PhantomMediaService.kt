package com.phantombeats.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Servicio en Foreground que mantiene a ExoPlayer con vida 
 * incluso cuando la app está minimizada o la pantalla apagada.
 * Utiliza Media3 (la API moderna que reemplazó a MediaBrowserServiceCompat).
 */
@AndroidEntryPoint
class PhantomMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        // 1. Configurar atributos para que Android sepa que esto es Música
        // y gestione correctamente el Audio Focus (ej. pausar si entra una llamada)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. Construir la instancia de ExoPlayer
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            // Maneja pausas automáticas si el usuario desconecta los audífonos
            .setHandleAudioBecomingNoisy(true) 
            .build()

        // 3. Crear la Sesión Media vinculada al Player
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
