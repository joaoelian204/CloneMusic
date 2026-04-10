package com.phantombeats.player

import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Servicio en Foreground que mantiene a ExoPlayer con vida 
 * y lo hace DISPONIBLE (Visible) nativamente para Android Auto,
 * WearOS, auriculares Bluetooth y Pantalla de Bloqueo.
 */
@AndroidEntryPoint
class PhantomMediaService : MediaLibraryService() {

    @Inject
    lateinit var player: ExoPlayer

    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        
        // 3. Crear Session Library vinculada (Abre pasarela para coches y BT)
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, CustomMediaLibrarySessionCallback()).build()
    }

    // Callback para interconectar con Android Auto y WearOS (Devuelve raíces de música) 
    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        // Todo: Implementaciones de .onGetLibraryRoot(), .onGetChildren()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
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
