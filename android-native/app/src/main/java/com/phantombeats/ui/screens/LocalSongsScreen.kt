package com.phantombeats.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.viewmodels.LocalSongsViewModel
import com.phantombeats.ui.viewmodels.PlayerViewModel

@Composable
fun LocalSongsScreen(
    playerViewModel: PlayerViewModel,
    localSongsViewModel: LocalSongsViewModel = hiltViewModel()
) {
    val songs by localSongsViewModel.songs.collectAsState()
    val folderUri by localSongsViewModel.folderUri.collectAsState()
    val loading by localSongsViewModel.loading.collectAsState()
    val error by localSongsViewModel.error.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isLocalPlaying = isPlaying && currentSong?.provider == "Local"

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            localSongsViewModel.pickFolder(uri)
        }
    }

    GradientContainer(bottomPadding = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(
                title = "Mis canciones",
                subtitle = "Escucha audios de una carpeta local de tu telefono"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = "Carpeta local",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    androidx.compose.material3.Text(
                        text = if (songs.isEmpty()) "Sin canciones detectadas" else "${songs.size} canciones detectadas",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
            }

            ActionButton(label = "Elegir carpeta de musica") {
                folderPickerLauncher.launch(null)
            }

            if (!folderUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "Carpeta: ${folderUri?.takeLast(46)}",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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
                        icon = if (isLocalPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isLocalPlaying) "Pausar carpeta" else "Play carpeta",
                        isActive = isLocalPlaying
                    ) {
                        if (isLocalPlaying) {
                            playerViewModel.togglePlayPause()
                        } else if (songs.isNotEmpty()) {
                            playerViewModel.setShuffleMode(false)
                            playerViewModel.playSongsQueue(songs, 0)
                        }
                    }
                    IconActionChip(
                        icon = Icons.Default.Shuffle,
                        contentDescription = "Play aleatorio"
                    ) {
                        if (songs.isNotEmpty()) {
                            playerViewModel.setShuffleMode(true)
                            playerViewModel.playSongsQueue(songs, songs.indices.random())
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (shuffleEnabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = if (shuffleEnabled) "Modo: Aleatorio" else "Modo: En orden",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            when {
                loading -> {
                    EmptyPanel(
                        title = "Escaneando carpeta",
                        subtitle = "Buscando archivos de audio locales..."
                    )
                }

                songs.isEmpty() -> {
                    EmptyPanel(
                        title = "Sin canciones locales",
                        subtitle = error ?: "Selecciona una carpeta que contenga archivos mp3/m4a/wav/ogg/flac."
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(16.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "Canciones de la carpeta",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                                SongRowCard(
                                    song = song,
                                    onPlay = {
                                        playerViewModel.playSongsQueue(songs, index)
                                    },
                                    onToggleFavorite = {
                                        playerViewModel.setFavorite(song, !song.isFavorite)
                                    },
                                    trailingLabel = "Play",
                                    onTrailingClick = {
                                        playerViewModel.playSongsQueue(songs, index)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
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
                if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)
                } else {
                    PhantomBorderAlpha
                },
                RoundedCornerShape(999.dp)
            )
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}
