package com.phantombeats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.viewmodels.LibraryViewModel
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel
import java.io.File

@Composable
fun OfflineScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val allSongs by libraryViewModel.cachedSongs.collectAsState()
    val songs by libraryViewModel.downloadedSongs.collectAsState()
    val pendingSongs = remember(allSongs) {
        allSongs
            .filter { it.localPath?.startsWith("__PENDING__") == true }
            .distinctBy { it.id }
    }
    val playlistDownloadState by playerViewModel.playlistDownloadState.collectAsState()
    val activeSongDownloadProgress by playerViewModel.activeSongDownloadProgress.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    val totalDownloadedBytes = remember(songs) {
        songs.sumOf { song ->
            val path = song.localPath
            if (path.isNullOrBlank() || path.startsWith("__PENDING__")) {
                0L
            } else {
                val normalized = if (path.startsWith("file://")) {
                    path.removePrefix("file://")
                } else {
                    path
                }
                val file = File(normalized)
                if (file.exists()) file.length() else 0L
            }
        }
    }
    var selectedSongForPlaylist by remember { mutableStateOf<com.phantombeats.domain.model.Song?>(null) }
    var songToDelete by remember { mutableStateOf<com.phantombeats.domain.model.Song?>(null) }

    GradientContainer {
        if (songs.isEmpty() && pendingSongs.isEmpty()) {
            EmptyPanel(
                title = "Sin descargas",
                subtitle = "Tus canciones descargadas apareceran aqui para escuchar sin conexion."
            )
            return@GradientContainer
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SectionHeader(
                    title = "Descargas",
                    subtitle = "${songs.size} descargadas • ${pendingSongs.size} pendientes"
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Espacio local usado: ${formatOfflineBytes(totalDownloadedBytes)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Mantén presionada una canción para eliminar su archivo local y liberar espacio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)
                    )
                }
            }

            if (pendingSongs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "En descarga",
                        subtitle = "Control individual estilo Netflix"
                    )
                }

                items(
                    count = pendingSongs.size,
                    key = { index -> pendingSongs[index].id }
                ) { index ->
                    val song = pendingSongs[index]
                    val isCurrentDownloading = playlistDownloadState?.currentSongId == song.id
                    val isPausedCurrent = playlistDownloadState?.pausedSongId == song.id && playlistDownloadState?.isPaused == true
                    val isRunning = playlistDownloadState?.isRunning == true && isCurrentDownloading
                    val showControls = isCurrentDownloading || isPausedCurrent
                    val songProgress = if (activeSongDownloadProgress?.songId == song.id) activeSongDownloadProgress else null

                    val progressText = when {
                        songProgress != null -> {
                            val total = songProgress.totalBytes.coerceAtLeast(1L)
                            val percent = ((songProgress.downloadedBytes.toDouble() / total.toDouble()) * 100.0)
                                .toInt()
                                .coerceIn(0, 100)
                            "$percent% • ${formatOfflineBytes(songProgress.downloadedBytes)} / ${formatOfflineBytes(songProgress.totalBytes)}"
                        }
                        isPausedCurrent -> "Pausada • lista para seguir"
                        isCurrentDownloading -> "Preparando descarga..."
                        else -> "En cola"
                    }

                    SongRowCard(
                        song = song,
                        onPlay = { },
                        onToggleFavorite = { playerViewModel.setFavorite(song, !song.isFavorite) },
                        trailingLabel = progressText,
                        onTrailingClick = { selectedSongForPlaylist = song },
                        extraActions = {
                            Row {
                                if (showControls && isRunning) {
                                    IconButton(onClick = { playerViewModel.pauseCurrentDownloadSong(song.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pausar descarga",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (showControls && isPausedCurrent) {
                                    IconButton(onClick = { playerViewModel.resumeCurrentDownloadSong(song.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Seguir descarga",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (showControls) {
                                    IconButton(
                                        onClick = {
                                            playerViewModel.cancelCurrentDownloadSong(song.id)
                                            Toast.makeText(context, "Descarga cancelada", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Cancelar descarga",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    )

                    if (songProgress != null) {
                        val total = songProgress.totalBytes.coerceAtLeast(1L)
                        val progress = (songProgress.downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            }

            if (songs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Descargadas",
                        subtitle = "${songs.size} canciones • ${formatOfflineBytes(totalDownloadedBytes)}"
                    )
                }
            }

            items(
                count = songs.size,
                key = { index -> songs[index].id }
            ) { index ->
                val song = songs[index]
                SongRowCard(
                    song = song,
                    onPlay = { playerViewModel.playSongsQueue(songs, index) },
                    onToggleFavorite = { playerViewModel.setFavorite(song, !song.isFavorite) },
                    trailingLabel = "Lista",
                    onTrailingClick = { selectedSongForPlaylist = song },
                    onLongPress = { songToDelete = song },
                    extraActions = {
                        IconButton(
                            onClick = {
                                playerViewModel.removeOfflineDownload(song)
                                Toast.makeText(context, "Descarga eliminada", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar descarga",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        songToDelete?.let { targetSong ->
            AlertDialog(
                onDismissRequest = { songToDelete = null },
                title = { Text("Eliminar descarga") },
                text = { Text("Se eliminará el archivo local de \"${targetSong.title}\" para liberar espacio.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            playerViewModel.removeOfflineDownload(targetSong)
                            Toast.makeText(context, "Descarga eliminada", Toast.LENGTH_SHORT).show()
                            songToDelete = null
                        }
                    ) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { songToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        selectedSongForPlaylist?.let { song ->
            ChoosePlaylistDialog(
                playlists = playlists,
                songTitle = song.title,
                onDismiss = { selectedSongForPlaylist = null },
                onSelect = { playlist ->
                    playlistViewModel.addSongToPlaylist(playlist.id, song.id)
                    Toast.makeText(context, "Añadida a ${playlist.name}", Toast.LENGTH_SHORT).show()
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

private fun formatOfflineBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0

    return when {
        safe >= gb -> String.format("%.1f GB", safe / gb)
        safe >= mb -> String.format("%.1f MB", safe / mb)
        safe >= kb -> String.format("%.0f KB", safe / kb)
        else -> "$safe B"
    }
}
