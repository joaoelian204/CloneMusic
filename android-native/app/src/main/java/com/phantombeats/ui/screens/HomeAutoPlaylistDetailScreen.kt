package com.phantombeats.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.phantombeats.R
import com.phantombeats.domain.model.Song
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.utils.AutoPlaylist
import com.phantombeats.ui.utils.AUTO_PLAYLIST_FEEDBACK_PREF
import com.phantombeats.ui.utils.buildAutoPlaylists
import com.phantombeats.ui.utils.blockSongInAutoPlaylists
import com.phantombeats.ui.utils.loadAutoPlaylistFeedback
import com.phantombeats.ui.utils.markSongLikedInAutoPlaylists
import com.phantombeats.ui.utils.songFeedbackKey
import com.phantombeats.ui.viewmodels.ConnectivityViewModel
import com.phantombeats.ui.viewmodels.LibraryViewModel
import com.phantombeats.ui.viewmodels.LocalSongsViewModel
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeAutoPlaylistDetailScreen(
    playlistId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    localSongsViewModel: LocalSongsViewModel = hiltViewModel(),
    connectivityViewModel: ConnectivityViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val feedbackPrefs = remember(context) {
        context.getSharedPreferences(AUTO_PLAYLIST_FEEDBACK_PREF, Context.MODE_PRIVATE)
    }
    var feedbackState by remember { mutableStateOf(loadAutoPlaylistFeedback(feedbackPrefs)) }
    val songs by libraryViewModel.cachedSongs.collectAsState()
    val recentSearchQueries by libraryViewModel.recentSearchQueries.collectAsState()
    val localSongs by localSongsViewModel.songs.collectAsState()
    val isOnline by connectivityViewModel.isOnline.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    val playlistDownloadState by playerViewModel.playlistDownloadState.collectAsState()
    val queuedPlaylistIds by playerViewModel.queuedPlaylistIds.collectAsState()
    val adaptiveBatteryModeEnabled by playerViewModel.adaptiveBatteryModeEnabled.collectAsState()
    val wifiOnlyDownloadsEnabled by playerViewModel.wifiOnlyDownloadsEnabled.collectAsState()
    var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showAutoDownloadOptions by remember { mutableStateOf(false) }
    var showSongDownloadOptionsForId by remember { mutableStateOf<String?>(null) }

    val offlineAndLocalSongs = remember(songs, localSongs) {
        val downloaded = songs.filter { it.isDownloaded }
        val merged = linkedMapOf<String, Song>()
        downloaded.forEach { merged[it.id] = it }
        localSongs.forEach { merged[it.id] = it }
        merged.values.toList()
    }

    val recommendationPool = remember(songs, offlineAndLocalSongs, isOnline) {
        if (isOnline) songs else offlineAndLocalSongs
    }

    var autoPlaylists by remember { mutableStateOf<List<AutoPlaylist>>(emptyList()) }
    var lastRenderableAutoPlaylists by remember { mutableStateOf<List<AutoPlaylist>>(emptyList()) }
    var isComputingAutoPlaylists by remember { mutableStateOf(false) }
    LaunchedEffect(recommendationPool, recentSearchQueries, feedbackState) {
        isComputingAutoPlaylists = true
        autoPlaylists = withContext(Dispatchers.Default) {
            buildAutoPlaylists(
                pool = recommendationPool,
                recentQueries = recentSearchQueries,
                maxPlaylists = 12,
                tracksPerPlaylist = 60,
                likedSongKeys = feedbackState.likedSongKeys,
                blockedSongKeys = feedbackState.blockedSongKeys
            )
        }
        if (autoPlaylists.isNotEmpty()) {
            lastRenderableAutoPlaylists = autoPlaylists
        }
        isComputingAutoPlaylists = false
    }

    val displayAutoPlaylists = if (autoPlaylists.isNotEmpty()) autoPlaylists else lastRenderableAutoPlaylists
    val playlist = displayAutoPlaylists.firstOrNull { it.id == playlistId }

    GradientContainer(bottomPadding = 0.dp) {
        if (playlist == null) {
            if (isComputingAutoPlaylists) {
                ScreenLoadingSpinner()
                return@GradientContainer
            }
            EmptyPanel(
                title = "No se encontro esta playlist",
                subtitle = "Vuelve a Para ti y toca otra lista automatica."
            )
            return@GradientContainer
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Playlist para ti",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
            }

            item {
                AutoPlaylistHero(playlist = playlist)
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutoPlaylistControlChip(icon = Icons.Default.PlayArrow, contentDescription = "Reproducir") {
                        playerViewModel.setShuffleMode(false)
                        playerViewModel.playSongsQueue(playlist.songs, 0)
                    }
                    AutoPlaylistControlChip(icon = Icons.Default.Shuffle, contentDescription = "Aleatorio") {
                        playerViewModel.setShuffleMode(true)
                        playerViewModel.playSongsQueue(playlist.songs, playlist.songs.indices.random())
                    }
                    Box {
                        val autoPlaylistDownload = playlistDownloadState?.takeIf { it.playlistId == playlist.id }
                        val autoPlaylistInQueue = playlist.id in queuedPlaylistIds
                        val autoPlaylistDownloading = autoPlaylistDownload?.isRunning == true
                        val autoPlaylistPaused = autoPlaylistDownload?.isPaused == true

                        AutoPlaylistControlChip(
                            icon = if (autoPlaylistDownloading) Icons.Default.Pause else Icons.Default.Download,
                            contentDescription = if (autoPlaylistDownloading || autoPlaylistPaused || autoPlaylistInQueue) {
                                "Opciones de descarga"
                            } else {
                                "Descargar"
                            },
                            isActive = autoPlaylistDownloading || autoPlaylistPaused || autoPlaylistInQueue,
                            onLongPress = {
                                showAutoDownloadOptions = true
                            }
                        ) {
                            val songsToDownload = playlist.songs.filter { it.provider != "Local" }
                            if (songsToDownload.isEmpty()) {
                                Toast.makeText(context, "No hay canciones online para descargar", Toast.LENGTH_SHORT).show()
                            } else if (autoPlaylistDownload != null || autoPlaylistInQueue) {
                                showAutoDownloadOptions = true
                            } else {
                                playerViewModel.togglePlaylistDownload(playlist.id, playlist.songs)
                                Toast.makeText(context, "Descargando playlist (${songsToDownload.size})", Toast.LENGTH_SHORT).show()
                            }
                        }

                        DropdownMenu(
                            expanded = showAutoDownloadOptions,
                            onDismissRequest = { showAutoDownloadOptions = false }
                        ) {
                            if (autoPlaylistDownloading) {
                                DropdownMenuItem(
                                    text = { Text("Pausar") },
                                    onClick = {
                                        playerViewModel.pausePlaylistDownload(playlist.id)
                                        showAutoDownloadOptions = false
                                    }
                                )
                            }

                            if (autoPlaylistPaused) {
                                DropdownMenuItem(
                                    text = { Text("Seguir") },
                                    onClick = {
                                        playerViewModel.resumePlaylistDownload(playlist.id)
                                        showAutoDownloadOptions = false
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (adaptiveBatteryModeEnabled) "Modo ahorro: activo" else "Modo ahorro: inactivo"
                                    )
                                },
                                onClick = {
                                    playerViewModel.setAdaptiveBatteryModeEnabled(!adaptiveBatteryModeEnabled)
                                    showAutoDownloadOptions = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (wifiOnlyDownloadsEnabled) "Descargar solo con Wi-Fi: activo" else "Descargar solo con Wi-Fi: inactivo"
                                    )
                                },
                                onClick = {
                                    playerViewModel.setWifiOnlyDownloadsEnabled(!wifiOnlyDownloadsEnabled)
                                    showAutoDownloadOptions = false
                                }
                            )

                            if (autoPlaylistDownload != null) {
                                DropdownMenuItem(
                                    text = { Text("Cancelar descarga") },
                                    onClick = {
                                        playerViewModel.cancelPlaylistDownload(
                                            playlistId = playlist.id,
                                            clearDownloadedFromPlaylist = true
                                        )
                                        showAutoDownloadOptions = false
                                    }
                                )
                            }

                            if (autoPlaylistInQueue && autoPlaylistDownload == null) {
                                DropdownMenuItem(
                                    text = { Text("Cancelar cola") },
                                    onClick = {
                                        playerViewModel.cancelPlaylistDownload(playlist.id)
                                        showAutoDownloadOptions = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Canciones",
                    subtitle = "${playlist.songs.size} temas en esta lista"
                )
            }

            itemsIndexed(playlist.songs, key = { _, song -> song.id }) { index, song ->
                val autoPlaylistDownload = playlistDownloadState?.takeIf { it.playlistId == playlist.id }
                val isSongCurrentDownload = autoPlaylistDownload?.currentSongId == song.id
                val isSongPausedDownload = autoPlaylistDownload?.pausedSongId == song.id
                val isSongPendingDownload = song.localPath?.startsWith("__PENDING__") == true
                val feedbackKey = songFeedbackKey(song)
                val liked = feedbackKey in feedbackState.likedSongKeys
                val blocked = feedbackKey in feedbackState.blockedSongKeys
                SongRowCard(
                    song = song,
                    onPlay = { playerViewModel.playSongsQueue(playlist.songs, index) },
                    onToggleFavorite = { playerViewModel.setFavorite(song, !song.isFavorite) },
                    trailingLabel = "Lista",
                    onTrailingClick = { selectedSongForPlaylist = song },
                    onLongPress = {
                        if (isSongCurrentDownload || isSongPausedDownload || isSongPendingDownload) {
                            showSongDownloadOptionsForId = song.id
                        }
                    },
                    extraActions = {
                        CompactFeedbackIconButton(onClick = {
                            markSongLikedInAutoPlaylists(feedbackPrefs, song, liked = !liked)
                            feedbackState = loadAutoPlaylistFeedback(feedbackPrefs)
                            Toast.makeText(
                                context,
                                if (!liked) "Tomado en cuenta: me gusta" else "Preferencia removida",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = "Me gusta esto",
                                modifier = Modifier.size(18.dp),
                                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )
                        }
                        CompactFeedbackIconButton(onClick = {
                            blockSongInAutoPlaylists(feedbackPrefs, song, blocked = !blocked)
                            feedbackState = loadAutoPlaylistFeedback(feedbackPrefs)
                            Toast.makeText(
                                context,
                                if (!blocked) "No te la volveremos a recomendar" else "Bloqueo removido",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ThumbDown,
                                contentDescription = "No me pongas esto",
                                modifier = Modifier.size(18.dp),
                                tint = if (blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )
                        }
                    }
                )
            }
        }

        showSongDownloadOptionsForId?.let { songId ->
            val autoState = playlistDownloadState?.takeIf { it.playlistId == playlist.id }
            val canPause = autoState?.isRunning == true && autoState.currentSongId == songId
            val canResume = autoState?.isPaused == true && autoState.pausedSongId == songId
            val canCancel = canPause || canResume || playlist.songs.any { it.id == songId && it.localPath?.startsWith("__PENDING__") == true }

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
                        showSongDownloadOptionsForId = null
                    }
                )
            } else {
                showSongDownloadOptionsForId = null
            }
        }

        selectedSongForPlaylist?.let { song ->
            ChoosePlaylistDialog(
                playlists = playlists,
                songTitle = song.title,
                onDismiss = { selectedSongForPlaylist = null },
                onSelect = { targetPlaylist ->
                    playlistViewModel.addSongToPlaylist(targetPlaylist.id, song.id)
                    Toast.makeText(context, "Añadida a ${targetPlaylist.name}", Toast.LENGTH_SHORT).show()
                    selectedSongForPlaylist = null
                },
                onCreatePlaylist = { name ->
                    playlistViewModel.createPlaylist(name)
                    Toast.makeText(context, "Playlist creada", Toast.LENGTH_SHORT).show()
                },
                onRenamePlaylist = { targetPlaylist, newName ->
                    playlistViewModel.renamePlaylist(targetPlaylist.id, newName)
                    Toast.makeText(context, "Playlist actualizada", Toast.LENGTH_SHORT).show()
                },
                onDeletePlaylist = { targetPlaylist ->
                    playlistViewModel.removePlaylist(targetPlaylist.id)
                    Toast.makeText(context, "Playlist eliminada", Toast.LENGTH_SHORT).show()
                },
                canEditPlaylist = { it.name != "Mis Favoritas" }
            )
        }
    }
}

@Composable
private fun AutoPlaylistHero(playlist: AutoPlaylist) {
    val firstCover = playlist.songs.firstOrNull()?.coverUrl
    val extraCovers = playlist.songs.drop(1).mapNotNull { it.coverUrl.takeIf(String::isNotBlank) }.take(2)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(18.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(92.dp)) {
            AsyncImage(
                model = firstCover,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.cover_placeholder),
                error = painterResource(R.drawable.cover_placeholder),
                fallback = painterResource(R.drawable.cover_placeholder),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(92.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            extraCovers.forEachIndexed { index, cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.cover_placeholder),
                    error = painterResource(R.drawable.cover_placeholder),
                    fallback = painterResource(R.drawable.cover_placeholder),
                    modifier = Modifier
                        .align(if (index == 0) Alignment.BottomEnd else Alignment.TopEnd)
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutoPlaylistControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            )
            .border(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else PhantomBorderAlpha,
                RoundedCornerShape(999.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
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

@Composable
private fun CompactFeedbackIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp)
    ) {
        content()
    }
}