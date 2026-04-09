package com.phantombeats.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.phantombeats.R
import com.phantombeats.domain.model.Song
import com.phantombeats.ui.viewmodels.LibraryViewModel
import com.phantombeats.ui.viewmodels.LocalSongsViewModel
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel
import com.phantombeats.ui.viewmodels.ConnectivityViewModel
import com.phantombeats.ui.utils.toDisplayText

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    localSongsViewModel: LocalSongsViewModel = hiltViewModel(),
    connectivityViewModel: ConnectivityViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val songs by libraryViewModel.cachedSongs.collectAsState()
    val cacheInitialized by libraryViewModel.cacheInitialized.collectAsState()
    val localSongs by localSongsViewModel.songs.collectAsState()
    val isOnline by connectivityViewModel.isOnline.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    val favoriteCount = remember(songs) { songs.count { it.isFavorite } }
    val offlineCount = remember(songs) { songs.count { it.isDownloaded } }
    val localCount = remember(localSongs) { localSongs.size }
    var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }

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

    val mixSongs = remember(recommendationPool) { recommendationPool.shuffled().take(6) }
    val recommendedSongs = remember(recommendationPool) {
        val base = recommendationPool.sortedByDescending { it.playCount }.ifEmpty { recommendationPool }
        if (base.isEmpty()) {
            emptyList()
        } else {
            val targetSize = 12
            List(targetSize) { index -> base[index % base.size] }
        }
    }

    GradientContainer(bottomPadding = 0.dp) {
        if (!cacheInitialized) {
            Spacer(modifier = Modifier.fillMaxSize())
            return@GradientContainer
        }

        if (recommendationPool.isEmpty()) {
            EmptyPanel(
                title = if (isOnline) "Sin musica todavia" else "Sin musica offline",
                subtitle = if (isOnline) {
                    "Ve a Explorar, busca canciones y tu Home se llenara automaticamente."
                } else {
                    "Descarga canciones o agrega carpeta local para ver recomendaciones sin internet."
                }
            )
            return@GradientContainer
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                title = if (isOnline) "Mix del dia" else "Mix offline",
                subtitle = if (isOnline) "Toques rapidos para arrancar" else "Descargadas y locales disponibles"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(mixSongs, key = { _, s -> s.id }) { index, song ->
                    MixCard(song = song, onClick = { playerViewModel.playSongsQueue(mixSongs, index) }, footer = "Play")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeStatChip(icon = Icons.Default.Favorite, value = "$favoriteCount favoritas")
                HomeStatChip(icon = Icons.Default.OfflinePin, value = "$offlineCount offline")
                HomeStatChip(icon = Icons.Default.Folder, value = "$localCount local")
            }

            SectionHeader(
                title = "Recomendados",
                subtitle = if (isOnline) "En tarjetas estilo Spotify" else "Basado en tu biblioteca sin internet"
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(recommendedSongs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                    HomeRecommendationCard(
                        song = song,
                        onPlay = { playerViewModel.playSongsQueue(recommendedSongs, index) },
                        onToggleFavorite = { playerViewModel.setFavorite(song, !song.isFavorite) },
                        onAddToPlaylist = { selectedSongForPlaylist = song }
                    )
                }
            }
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

@Composable
private fun HomeStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp
        )
    }
}


@Composable
private fun HomeRecommendationCard(
    song: Song,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val display = song.toDisplayText()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
    ) {
        AsyncImage(
            model = song.coverUrl.takeIf { it.isNotBlank() },
            contentDescription = song.title,
            placeholder = painterResource(R.drawable.cover_placeholder),
            error = painterResource(R.drawable.cover_placeholder),
            fallback = painterResource(R.drawable.cover_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = display.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlay, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Lista",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
