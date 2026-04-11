package com.phantombeats.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.phantombeats.ui.viewmodels.PlayerUiState
import com.phantombeats.ui.viewmodels.PlayerViewModel

@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    onNavigateToFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by playerViewModel.uiState.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val positionMs by playerViewModel.positionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val bufferedPositionMs by playerViewModel.bufferedPositionMs.collectAsState()

    // Si está Idle (sin nada reproduciendo), no mostramos el MiniPlayer.
    if (uiState is PlayerUiState.Idle) {
        return
    }

    val song = currentSong ?: when (val state = uiState) {
        is PlayerUiState.Playing -> state.song
        is PlayerUiState.Error -> state.song
        is PlayerUiState.Buffering -> null
        else -> null
    }

    val streamErrorMessage = (uiState as? PlayerUiState.Error)?.message

    val isOffline = uiState is PlayerUiState.Playing && (uiState as PlayerUiState.Playing).useLocalCache
    val isPendingDownload = song?.localPath?.startsWith("__PENDING__") == true
    val isDownloaded = song?.isDownloaded == true
    val progress = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f
    val buffered = if (durationMs > 0L) bufferedPositionMs.toFloat() / durationMs.toFloat() else 0f

    Column(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable { onNavigateToFullPlayer() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { /* Final del gesto */ },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 15f || dragAmount < -15f) {
                            playerViewModel.stop()
                        }
                    }
                )
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
    ) {
        MiniPlayerProgress(
            buffering = uiState is PlayerUiState.Buffering,
            progress = progress,
            bufferedProgress = buffered
        )
        MiniPlayerControls(
            song = song,
            isPlaying = isPlaying,
            isOffline = isOffline,
            isDownloadPending = isPendingDownload,
            isDownloaded = isDownloaded,
            streamErrorMessage = streamErrorMessage,
            onTogglePlay = { playerViewModel.togglePlayPause() },
            onNext = { playerViewModel.playNextInQueue() },
            onToggleFavorite = { favoriteSong ->
                playerViewModel.setFavorite(favoriteSong, !favoriteSong.isFavorite)
            }
        )
    }
}
