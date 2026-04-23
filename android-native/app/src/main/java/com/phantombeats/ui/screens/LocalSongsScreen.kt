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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val folderLabel = remember(folderUri) { folderUri.toReadableFolderLabel() }

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

            LocalFolderSummaryCard(songCount = songs.size)

            ActionButton(label = "Elegir carpeta de musica") {
                folderPickerLauncher.launch(null)
            }

            if (loading && songs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = LoadingCopy.localRefresh,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                    )
                }
            }

            if (!folderLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                        .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Carpeta: $folderLabel",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                        style = MaterialTheme.typography.bodySmall,
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
                    Text(
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

            AnimatedVisibility(
                visible = isLocalPlaying && currentSong != null,
                enter = fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.985f, animationSpec = tween(180))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ahora suena",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = currentSong?.title.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            when {
                loading && songs.isEmpty() -> {
                    ScreenLoadingSpinner()
                }

                songs.isEmpty() -> {
                    EmptyPanel(
                        title = "Sin canciones locales",
                        subtitle = error ?: "Selecciona una carpeta con archivos mp3/m4a/wav/ogg/flac."
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Canciones de la carpeta",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${songs.size} tracks",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                                val isCurrentSong = isLocalPlaying && currentSong?.id == song.id
                                val animatedBg = animateColorAsState(
                                    targetValue = if (isCurrentSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    animationSpec = tween(durationMillis = 240),
                                    label = "local-current-song-bg"
                                )
                                val animatedBorder = animateColorAsState(
                                    targetValue = if (isCurrentSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    animationSpec = tween(durationMillis = 240),
                                    label = "local-current-song-border"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(animatedBg.value)
                                        .border(
                                            width = if (isCurrentSong) 1.dp else 0.dp,
                                            color = animatedBorder.value,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .padding(1.dp)
                                ) {
                                    SongRowCard(
                                        song = song,
                                        onPlay = { playerViewModel.playSongsQueue(songs, index) },
                                        onToggleFavorite = { playerViewModel.setFavorite(song, !song.isFavorite) },
                                        trailingLabel = "Play",
                                        onTrailingClick = { playerViewModel.playSongsQueue(songs, index) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalFolderSummaryCard(songCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Carpeta local",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (songCount == 0) "Sin canciones detectadas" else "$songCount canciones detectadas",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
                Text(
                    text = "Local",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun String?.toReadableFolderLabel(): String? {
    if (this.isNullOrBlank()) return null
    val decoded = Uri.decode(this)
    val simplified = decoded
        .removePrefix("content://")
        .replace("com.android.externalstorage.documents/tree/", "")
        .replace("primary:", "")
        .replace("document/", "")
        .replace('%', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    if (simplified.isBlank()) return decoded.takeLast(48)
    return simplified.takeLast(56)
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
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
