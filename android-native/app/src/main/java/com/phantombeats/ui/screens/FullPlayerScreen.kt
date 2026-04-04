package com.phantombeats.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.phantombeats.ui.viewmodels.PlayerUiState
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel
import kotlin.math.max

@Composable
fun FullPlayerScreen(
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uiState by playerViewModel.uiState.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val positionMs by playerViewModel.positionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()

    val fallbackSong = when (val state = uiState) {
        is PlayerUiState.Playing -> state.song
        is PlayerUiState.Error -> state.song
        else -> null
    }
    val song = currentSong ?: fallbackSong
    val progress = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f
    var selectedSongForPlaylist by remember { mutableStateOf<com.phantombeats.domain.model.Song?>(null) }
    var optimisticFavorite by remember(song?.id) { mutableStateOf(song?.isFavorite == true) }

    LaunchedEffect(song?.isFavorite) {
        optimisticFavorite = song?.isFavorite == true
    }

    val favoriteScale by animateFloatAsState(
        targetValue = if (optimisticFavorite) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "full-favorite-scale"
    )
    val favoriteTint by animateColorAsState(
        targetValue = if (optimisticFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "full-favorite-tint"
    )

    GradientContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            FullPlayerHeader(onDismiss = onDismiss)

            Spacer(modifier = Modifier.height(14.dp))

            FullPlayerArtwork(song = song)

            Spacer(modifier = Modifier.height(24.dp))

            FullPlayerTrackInfo(song = song)

            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = progress,
                onValueChange = { playerViewModel.seekToFraction(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = durationMs > 0L
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMs(positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                )
                Text(
                    text = formatMs(max(durationMs, 0L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerViewModel.toggleShuffleMode() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.playPreviousInQueue() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(92.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.playNextInQueue() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.setShuffleMode(false) }) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "En orden",
                        tint = if (!shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = {
                        if (song != null) {
                            val newFav = !optimisticFavorite
                            optimisticFavorite = newFav
                            playerViewModel.setFavorite(song, newFav)
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (optimisticFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (optimisticFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = favoriteTint,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(favoriteScale)
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (song != null) {
                            selectedSongForPlaylist = song
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agregar a playlist")
                }
            }

            selectedSongForPlaylist?.let { selectedSong ->
                ChoosePlaylistDialog(
                    playlists = playlists,
                    songTitle = selectedSong.title,
                    onDismiss = { selectedSongForPlaylist = null },
                    onSelect = { playlist ->
                        playlistViewModel.addSongToPlaylist(playlist.id, selectedSong.id)
                        Toast.makeText(context, "Anadida a ${playlist.name}", Toast.LENGTH_SHORT).show()
                        selectedSongForPlaylist = null
                    },
                    onCreatePlaylist = { name ->
                        playlistViewModel.createPlaylist(name)
                        Toast.makeText(context, "Playlist creada", Toast.LENGTH_SHORT).show()
                    },
                    onRenamePlaylist = { playlist, newName ->
                        playlistViewModel.renamePlaylist(playlist.id, newName)
                        Toast.makeText(context, "Playlist actualizada", Toast.LENGTH_SHORT).show()
                    },
                    onDeletePlaylist = { playlist ->
                        playlistViewModel.removePlaylist(playlist.id)
                        Toast.makeText(context, "Playlist eliminada", Toast.LENGTH_SHORT).show()
                    },
                    canEditPlaylist = { it.name != "Mis Favoritas" }
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
