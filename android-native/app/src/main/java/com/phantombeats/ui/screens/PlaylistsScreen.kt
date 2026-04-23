package com.phantombeats.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.phantombeats.R
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel

@Composable
fun PlaylistsScreen(
    playerViewModel: PlayerViewModel,
    onOpenDownloads: () -> Unit = {},
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playlists by playlistViewModel.playlists.collectAsState()
    val selectedPlaylist by playlistViewModel.selectedPlaylist.collectAsState()
    val selectedSongs by playlistViewModel.selectedPlaylistSongs.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playlistDownloadState by playerViewModel.playlistDownloadState.collectAsState()
    val queuedPlaylistCount by playerViewModel.queuedPlaylistCount.collectAsState()
    val queuedPlaylistIds by playerViewModel.queuedPlaylistIds.collectAsState()
    val adaptiveBatteryModeEnabled by playerViewModel.adaptiveBatteryModeEnabled.collectAsState()
    val wifiOnlyDownloadsEnabled by playerViewModel.wifiOnlyDownloadsEnabled.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showPlaylistActions by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var showDownloadOptions by remember { mutableStateOf(false) }
    var showSongDownloadOptionsForId by remember { mutableStateOf<String?>(null) }

    val canManageSelectedPlaylist = selectedPlaylist?.name != "Mis Favoritas"
    val isPlaylistPlaying = isPlaying && currentSong != null && selectedSongs.any { it.id == currentSong?.id }
    val selectedPlaylistDownload = playlistDownloadState?.takeIf { it.playlistId == selectedPlaylist?.id }
    val selectedPlaylistInQueue = selectedPlaylist?.id?.let { it in queuedPlaylistIds } == true
    val isDownloadingPlaylist = selectedPlaylistDownload?.isRunning == true
    val isPausedPlaylistDownload = selectedPlaylistDownload?.isPaused == true
    val downloadProgress = selectedPlaylistDownload?.progress
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }

            selectedPlaylist?.id?.let { playlistId ->
                playlistViewModel.setPlaylistCover(playlistId, uri.toString())
            }
        }
    }

    GradientContainer(bottomPadding = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "Playlists", subtitle = "Organiza tu musica")
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nueva playlist",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistSelectableChip(
                        label = playlist.name,
                        selected = selectedPlaylist?.id == playlist.id,
                        onClick = { playlistViewModel.selectPlaylist(playlist.id) },
                        onLongPress = {
                            playlistViewModel.selectPlaylist(playlist.id)
                            showDownloadOptions = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            selectedPlaylist?.let { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = playlist.coverUrl?.takeIf { it.isNotBlank() } ?: R.drawable.cover_placeholder,
                        contentDescription = "Portada playlist",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(82.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = canManageSelectedPlaylist) {
                                    renamePlaylistName = playlist.name
                                    showPlaylistActions = true
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (canManageSelectedPlaylist) {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "Opciones de playlist",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "${selectedSongs.size} canciones",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeChip(label = "Portada", selected = false) {
                                imagePickerLauncher.launch(arrayOf("image/*"))
                            }
                            ModeChip(label = "Offline", selected = false) {
                                selectedSongs
                                    .filter { it.provider != "Local" }
                                    .forEach { playerViewModel.setFavorite(it, true) }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconActionChip(
                            icon = if (isPlaylistPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaylistPlaying) "Pausar playlist" else "Play playlist",
                            isActive = isPlaylistPlaying
                        ) {
                            if (isPlaylistPlaying) {
                                playerViewModel.togglePlayPause()
                            } else if (selectedSongs.isNotEmpty()) {
                                playerViewModel.setShuffleMode(false)
                                playerViewModel.playSongsQueue(selectedSongs, 0)
                            }
                        }
                        IconActionChip(
                            icon = Icons.Default.Shuffle,
                            contentDescription = "Play aleatorio"
                        ) {
                            if (selectedSongs.isNotEmpty()) {
                                playerViewModel.setShuffleMode(true)
                                playerViewModel.playSongsQueue(selectedSongs, selectedSongs.indices.random())
                            }
                        }
                        Box {
                            IconActionChip(
                                icon = if (isDownloadingPlaylist) Icons.Default.Pause else Icons.Default.Download,
                                contentDescription = if (isDownloadingPlaylist || isPausedPlaylistDownload || selectedPlaylistInQueue) "Opciones de descarga" else "Descargar playlist",
                                isActive = isDownloadingPlaylist || isPausedPlaylistDownload || selectedPlaylistInQueue,
                                progress = downloadProgress
                                ,onLongClick = {
                                    if (selectedPlaylist != null) {
                                        showDownloadOptions = true
                                    }
                                }
                            ) {
                                val currentPlaylist = selectedPlaylist ?: return@IconActionChip

                                if (selectedPlaylistDownload != null || selectedPlaylistInQueue) {
                                    showDownloadOptions = true
                                } else {
                                    val songsToDownload = selectedSongs.filter { it.provider != "Local" }
                                    if (songsToDownload.isNotEmpty()) {
                                        playerViewModel.togglePlaylistDownload(currentPlaylist.id, selectedSongs)
                                        Toast.makeText(context, "Descarga iniciada (${songsToDownload.size})", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No hay canciones online para descargar", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showDownloadOptions,
                                onDismissRequest = { showDownloadOptions = false }
                            ) {
                                val playlistId = selectedPlaylist?.id

                                if (isDownloadingPlaylist && playlistId != null) {
                                    DropdownMenuItem(
                                        text = { Text("Pausar") },
                                        onClick = {
                                            playerViewModel.pausePlaylistDownload(playlistId)
                                            showDownloadOptions = false
                                        }
                                    )
                                }

                                if (isPausedPlaylistDownload && playlistId != null) {
                                    DropdownMenuItem(
                                        text = { Text("Seguir") },
                                        onClick = {
                                            playerViewModel.resumePlaylistDownload(playlistId)
                                            showDownloadOptions = false
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (adaptiveBatteryModeEnabled) {
                                                "Modo ahorro: activo"
                                            } else {
                                                "Modo ahorro: inactivo"
                                            }
                                        )
                                    },
                                    onClick = {
                                        playerViewModel.setAdaptiveBatteryModeEnabled(!adaptiveBatteryModeEnabled)
                                        showDownloadOptions = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (wifiOnlyDownloadsEnabled) {
                                                "Descargar solo con Wi-Fi: activo"
                                            } else {
                                                "Descargar solo con Wi-Fi: inactivo"
                                            }
                                        )
                                    },
                                    onClick = {
                                        playerViewModel.setWifiOnlyDownloadsEnabled(!wifiOnlyDownloadsEnabled)
                                        showDownloadOptions = false
                                    }
                                )

                                if (selectedPlaylistDownload != null && playlistId != null) {
                                    DropdownMenuItem(
                                        text = { Text("Cancelar descarga") },
                                        onClick = {
                                            playerViewModel.cancelPlaylistDownload(
                                                playlistId = playlistId,
                                                clearDownloadedFromPlaylist = true
                                            )
                                            Toast.makeText(context, "Descarga cancelada y limpiada", Toast.LENGTH_SHORT).show()
                                            showDownloadOptions = false
                                        }
                                    )
                                }

                                if (selectedPlaylistInQueue && playlistId != null && selectedPlaylistDownload == null) {
                                    DropdownMenuItem(
                                        text = { Text("Cancelar cola") },
                                        onClick = {
                                            playerViewModel.cancelPlaylistDownload(playlistId)
                                            Toast.makeText(context, "Playlist removida de la cola", Toast.LENGTH_SHORT).show()
                                            showDownloadOptions = false
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Ver mis descargas") },
                                    onClick = {
                                        onOpenDownloads()
                                        showDownloadOptions = false
                                    }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isPlaylistPlaying) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when {
                                isDownloadingPlaylist -> {
                                    val state = selectedPlaylistDownload
                                    if (state == null) {
                                        "Descargando"
                                    } else {
                                        val percent = (state.progress * 100).toInt().coerceIn(0, 100)
                                        "$percent% • ${formatBytes(state.estimatedDownloadedBytes)} / ${formatBytes(state.estimatedTotalBytes)}"
                                    }
                                }
                                isPausedPlaylistDownload -> {
                                    val state = selectedPlaylistDownload
                                    if (state == null) {
                                        "Pausado"
                                    } else {
                                        val reason = state.pauseReason?.let { " • $it" } ?: ""
                                        "Pausado$reason • faltan ${formatBytes(state.remainingBytes)}"
                                    }
                                }
                                selectedPlaylistInQueue -> "En cola para descargar"
                                queuedPlaylistCount > 0 -> "En cola: $queuedPlaylistCount playlists"
                                isPlaylistPlaying -> "Reproduciendo"
                                else -> "Lista preparada"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDownloadingPlaylist || isPausedPlaylistDownload || isPlaylistPlaying) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isDownloadingPlaylist || isPausedPlaylistDownload) {
                    val progressValue = selectedPlaylistDownload?.progress ?: 0f
                    val animatedBarProgress by animateFloatAsState(
                        targetValue = progressValue.coerceIn(0f, 1f),
                        label = "playlist-download-bar"
                    )
                    LinearProgressIndicator(
                        progress = animatedBarProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = if (isPausedPlaylistDownload) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }

            if (selectedSongs.isEmpty()) {
                EmptyPanel(
                    title = "Playlist vacia",
                    subtitle = "Agrega canciones desde otras vistas."
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Dentro de esta playlist",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        fontWeight = FontWeight.SemiBold
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedSongs, key = { it.id }) { song ->
                            val isSongCurrentDownload = selectedPlaylistDownload?.currentSongId == song.id
                            val isSongPausedDownload = selectedPlaylistDownload?.pausedSongId == song.id
                            val isSongPendingDownload = song.localPath?.startsWith("__PENDING__") == true

                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                SongRowCard(
                                    song = song,
                                    onPlay = {
                                        playerViewModel.playSongsQueue(
                                            selectedSongs,
                                            selectedSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                        )
                                    },
                                    onToggleFavorite = { playerViewModel.setFavorite(song, !song.isFavorite) },
                                    trailingLabel = "Quitar",
                                    onTrailingClick = { playlistViewModel.removeSongFromSelected(song.id) },
                                    onLongPress = {
                                        if (isSongCurrentDownload || isSongPausedDownload || isSongPendingDownload) {
                                            showSongDownloadOptionsForId = song.id
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        showSongDownloadOptionsForId?.let { songId ->
            val activeState = selectedPlaylistDownload
            val canPause = activeState?.isRunning == true && activeState.currentSongId == songId
            val canResume = activeState?.isPaused == true && activeState.pausedSongId == songId
            val canCancel = canPause || canResume || selectedSongs.any { it.id == songId && it.localPath?.startsWith("__PENDING__") == true }

            if (canPause || canResume || canCancel) {
                SongDownloadActionsDialog(
                    canPause = canPause,
                    canResume = canResume,
                    canCancel = canCancel,
                    onDismiss = { showSongDownloadOptionsForId = null },
                    onPause = {
                        playerViewModel.pauseCurrentDownloadSong(songId)
                        showSongDownloadOptionsForId = null
                    },
                    onResume = {
                        playerViewModel.resumeCurrentDownloadSong(songId)
                        showSongDownloadOptionsForId = null
                    },
                    onCancel = {
                        playerViewModel.cancelCurrentDownloadSong(songId)
                        Toast.makeText(context, "Descarga cancelada", Toast.LENGTH_SHORT).show()
                        showSongDownloadOptionsForId = null
                    }
                )
            } else {
                showSongDownloadOptionsForId = null
            }
        }

        if (showCreateDialog) {
            CreatePlaylistDialog(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                onDismiss = { showCreateDialog = false },
                onConfirm = {
                    playlistViewModel.createPlaylist(newPlaylistName)
                    newPlaylistName = ""
                    showCreateDialog = false
                }
            )
        }

        if (showPlaylistActions && selectedPlaylist != null) {
            PlaylistActionsDialog(
                playlistName = selectedPlaylist!!.name,
                onDismiss = { showPlaylistActions = false },
                onEdit = {
                    renamePlaylistName = selectedPlaylist!!.name
                    showPlaylistActions = false
                    showRenameDialog = true
                },
                onDelete = {
                    showPlaylistActions = false
                    showDeleteDialog = true
                }
            )
        }

        if (showRenameDialog && selectedPlaylist != null) {
            RenamePlaylistDialog(
                value = renamePlaylistName,
                onValueChange = { renamePlaylistName = it },
                onDismiss = { showRenameDialog = false },
                onConfirm = {
                    playlistViewModel.renamePlaylist(selectedPlaylist!!.id, renamePlaylistName)
                    showRenameDialog = false
                }
            )
        }

        if (showDeleteDialog && selectedPlaylist != null) {
            ConfirmDeletePlaylistDialog(
                playlistName = selectedPlaylist!!.name,
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    playlistViewModel.removePlaylist(selectedPlaylist!!.id)
                    showDeleteDialog = false
                }
            )
        }
    }
}

@Composable
private fun PlaylistActionsDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 10.dp,
        title = {
            Text(
                text = playlistName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Editar nombre")
                }

                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Eliminar playlist", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
            }
        }
    )
}

@Composable
private fun RenamePlaylistDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 10.dp,
        title = {
            Text(
                text = "Editar playlist",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    placeholder = { Text("Nuevo nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
                Text(
                    text = "Puedes renombrarla cuando quieras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ConfirmDeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 10.dp,
        title = {
            Text(
                text = "Eliminar playlist",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Se eliminara \"$playlistName\". Esta accion no se puede deshacer.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        label = "icon-chip-progress"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                }
            )
            .border(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else PhantomBorderAlpha,
                RoundedCornerShape(999.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = animatedProgress,
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(10.dp)
                .size(22.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistSelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
            )
            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(999.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SongDownloadActionsDialog(
    canPause: Boolean,
    canResume: Boolean,
    canCancel: Boolean,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        title = {
            Text(
                text = "Opciones de descarga",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canPause) {
                    TextButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                        Text("Pausar")
                    }
                }
                if (canResume) {
                    TextButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                        Text("Seguir")
                    }
                }
                if (canCancel) {
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancelar")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
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
