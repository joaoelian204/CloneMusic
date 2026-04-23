package com.phantombeats.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.phantombeats.ui.viewmodels.LibraryViewModel
import com.phantombeats.ui.viewmodels.LocalSongsViewModel
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel
import com.phantombeats.ui.viewmodels.ConnectivityViewModel
import com.phantombeats.ui.utils.buildAutoPlaylists
import com.phantombeats.ui.utils.buildHomeRecommendations
import com.phantombeats.ui.utils.loadPinnedAutoPlaylistSnapshots
import com.phantombeats.ui.utils.PinnedAutoPlaylistSnapshot
import com.phantombeats.ui.utils.savePinnedAutoPlaylistSnapshots
import com.phantombeats.ui.utils.toDisplayText
import com.phantombeats.ui.utils.AUTO_PLAYLIST_FEEDBACK_PREF
import com.phantombeats.ui.utils.AutoPlaylist
import com.phantombeats.ui.utils.HomeRecommendationResult
import com.phantombeats.ui.utils.loadAutoPlaylistFeedback
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    onOpenAutoPlaylist: (String) -> Unit = {},
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    localSongsViewModel: LocalSongsViewModel = hiltViewModel(),
    connectivityViewModel: ConnectivityViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val pinnedPrefs = remember(context) {
        context.getSharedPreferences(HOME_PINNED_PLAYLIST_PREF, Context.MODE_PRIVATE)
    }
    val feedbackPrefs = remember(context) {
        context.getSharedPreferences(AUTO_PLAYLIST_FEEDBACK_PREF, Context.MODE_PRIVATE)
    }
    val songs by libraryViewModel.cachedSongs.collectAsState()
    val recentSearchQueries by libraryViewModel.recentSearchQueries.collectAsState()
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

    var lastRenderablePool by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(recommendationPool) {
        if (recommendationPool.isNotEmpty()) {
            lastRenderablePool = recommendationPool
        }
    }
    val displayPool = if (recommendationPool.isNotEmpty()) recommendationPool else lastRenderablePool

    var rankingPool by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(displayPool) {
        rankingPool = withContext(Dispatchers.Default) {
            displayPool
                .asSequence()
                .sortedByDescending { (if (it.isFavorite) 1 else 0) * 10 + it.playCount }
                .take(500)
                .toList()
        }
    }

    val autoPlaylistFeedback = remember(feedbackPrefs, recentSearchQueries) {
        loadAutoPlaylistFeedback(feedbackPrefs)
    }

    var autoPlaylists by remember { mutableStateOf<List<AutoPlaylist>>(emptyList()) }
    LaunchedEffect(rankingPool, recentSearchQueries, autoPlaylistFeedback) {
        autoPlaylists = withContext(Dispatchers.Default) {
            buildAutoPlaylists(
                pool = rankingPool,
                recentQueries = recentSearchQueries,
                maxPlaylists = 6,
                tracksPerPlaylist = 40,
                likedSongKeys = autoPlaylistFeedback.likedSongKeys,
                blockedSongKeys = autoPlaylistFeedback.blockedSongKeys
            )
        }
    }

    var pinnedSnapshots by remember {
        mutableStateOf(loadPinnedAutoPlaylistSnapshots(pinnedPrefs, MAX_PINNED_AUTO_PLAYLISTS))
    }

    val pinnedPlaylists = remember(pinnedSnapshots, displayPool) {
        pinnedSnapshots.mapNotNull { it.toAutoPlaylist(displayPool) }
    }
    val pinnedIds = remember(pinnedPlaylists) { pinnedPlaylists.map { it.id }.toSet() }

    val homeAutoPlaylists = remember(autoPlaylists, pinnedPlaylists, pinnedIds) {
        if (pinnedPlaylists.isEmpty()) {
            autoPlaylists
        } else {
            val others = autoPlaylists.filterNot { it.id in pinnedIds }
            pinnedPlaylists + others
        }
    }

    var recommendations by remember { mutableStateOf(HomeRecommendationResult(emptyList(), emptyList())) }
    val recommendationFingerprint = remember(rankingPool, recentSearchQueries) {
        buildRecommendationFingerprint(rankingPool, recentSearchQueries)
    }

    LaunchedEffect(recommendationFingerprint) {
        recommendations = withContext(Dispatchers.Default) {
            buildHomeRecommendations(
                pool = rankingPool,
                recentQueries = recentSearchQueries,
                mixSize = 6,
                recommendedSize = 15
            )
        }
    }
    val updatedTodayLabel = remember {
        val hour = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        "Actualizada hoy $hour"
    }
    val mixSongs = recommendations.mixSongs
    val recommendedSongs = recommendations.recommendedSongs
    val fallbackRecommendedSong = remember(recommendedSongs, mixSongs) {
        val recommendedKeys = recommendedSongs.map { it.id }.toSet()
        mixSongs.firstOrNull { it.id !in recommendedKeys }
    }
    val recommendedGridSongs = remember(recommendedSongs, fallbackRecommendedSong) {
        if (recommendedSongs.size % 2 == 0 || fallbackRecommendedSong == null) {
            recommendedSongs
        } else {
            recommendedSongs + fallbackRecommendedSong
        }
    }

    val hasRenderableHomeContent = displayPool.isNotEmpty()
    val contentAlpha by animateFloatAsState(
        targetValue = if (hasRenderableHomeContent) 1f else 0f,
        label = "home-content-alpha"
    )

    GradientContainer(bottomPadding = 0.dp) {
        if (!cacheInitialized && displayPool.isEmpty()) {
            ScreenLoadingSpinner()
            return@GradientContainer
        }

        if (displayPool.isEmpty()) {
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeStatChip(icon = Icons.Default.Favorite, value = "$favoriteCount favoritas")
                    HomeStatChip(icon = Icons.Default.OfflinePin, value = "$offlineCount offline")
                    HomeStatChip(icon = Icons.Default.Folder, value = "$localCount local")
                }
            }

            if (homeAutoPlaylists.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Hechas para ti",
                        subtitle = "Listas automaticas segun tus gustos y busquedas recientes"
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(homeAutoPlaylists, key = { it.id }) { playlist ->
                            val pinnedIndex = pinnedPlaylists.indexOfFirst { it.id == playlist.id }
                            val isPinned = pinnedIndex >= 0
                            HomeAutoPlaylistCard(
                                playlist = playlist,
                                isPinned = isPinned,
                                updatedBadgeText = if (isPinned) null else updatedTodayLabel,
                                onOpen = { onOpenAutoPlaylist(playlist.id) },
                                onPlay = { playerViewModel.playSongsQueue(playlist.songs, 0) },
                                onTogglePin = {
                                    if (isPinned) {
                                        val next = pinnedSnapshots.filterNot { it.id == playlist.id }
                                        savePinnedAutoPlaylistSnapshots(pinnedPrefs, next)
                                        pinnedSnapshots = next
                                        Toast.makeText(context, "Playlist desfijada", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (pinnedSnapshots.size >= MAX_PINNED_AUTO_PLAYLISTS) {
                                            Toast.makeText(context, "Solo puedes fijar hasta $MAX_PINNED_AUTO_PLAYLISTS playlists", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val next = pinnedSnapshots + PinnedAutoPlaylistSnapshot.from(playlist)
                                            savePinnedAutoPlaylistSnapshots(pinnedPrefs, next)
                                            pinnedSnapshots = next
                                            Toast.makeText(context, "Playlist fijada en Home", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDragSwap = if (isPinned) {
                                    {
                                        val target = pinnedIndex + it
                                        if (target in pinnedSnapshots.indices) {
                                            val next = pinnedSnapshots.toMutableList()
                                            val current = next.removeAt(pinnedIndex)
                                            next.add(target, current)
                                            savePinnedAutoPlaylistSnapshots(pinnedPrefs, next)
                                            pinnedSnapshots = next
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = if (isOnline) "Mix del dia" else "Mix offline",
                    subtitle = if (isOnline) "Toques rapidos para arrancar" else "Descargadas y locales disponibles"
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(mixSongs, key = { _, s -> s.id }) { index, song ->
                        MixCard(song = song, onClick = { playerViewModel.playSongsQueue(mixSongs, index) }, footer = "Play")
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Recomendados",
                    subtitle = if (isOnline) "En tarjetas estilo Spotify" else "Basado en tu biblioteca sin internet",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            itemsIndexed(recommendedGridSongs.chunked(2), key = { index, _ -> "recommended-row-$index" }) { rowIndex, rowSongs ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSongs.forEachIndexed { colIndex, song ->
                        val absoluteIndex = rowIndex * 2 + colIndex
                        HomeRecommendationCard(
                            song = song,
                            onPlay = { playerViewModel.playSongsQueue(recommendedGridSongs, absoluteIndex) },
                            onToggleFavorite = { playerViewModel.setFavorite(song, !song.isFavorite) },
                            onAddToPlaylist = { selectedSongForPlaylist = song },
                            modifier = Modifier.weight(1f)
                        )
                    }
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

private const val HOME_PINNED_PLAYLIST_PREF = "home_pinned_auto_playlist"
private const val MAX_PINNED_AUTO_PLAYLISTS = 3

private fun buildRecommendationFingerprint(pool: List<Song>, recentQueries: List<String>): Int {
    var hash = 17

    pool.take(220).forEach { song ->
        hash = (31 * hash) + song.id.hashCode()
        hash = (31 * hash) + song.playCount
        hash = (31 * hash) + song.skipCount
        hash = (31 * hash) + (if (song.isFavorite) 1 else 0)
        hash = (31 * hash) + (song.lastPlayed xor (song.lastPlayed ushr 32)).toInt()
    }

    recentQueries.take(16).forEach { query ->
        hash = (31 * hash) + query.lowercase().trim().hashCode()
    }

    return hash
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
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val display = song.toDisplayText()
    Column(
        modifier = modifier
            .height(280.dp)
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
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
            Spacer(modifier = Modifier.weight(1f))
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
