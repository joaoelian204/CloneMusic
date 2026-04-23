package com.phantombeats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel
import com.phantombeats.ui.viewmodels.SearchUiState
import com.phantombeats.ui.viewmodels.SearchViewModel

@Composable
fun ExploreScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToArtist: (com.phantombeats.domain.model.Artist) -> Unit = {},
    onNavigateToAlbum: (com.phantombeats.domain.model.Album) -> Unit = {},
    searchViewModel: SearchViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by searchViewModel.uiState.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSongForPlaylist by remember { mutableStateOf<com.phantombeats.domain.model.Song?>(null) }
    val successState = uiState as? SearchUiState.Success
    var lastSuccessState by remember { mutableStateOf<SearchUiState.Success?>(null) }
    val resultCount = successState?.songs?.size ?: 0
    var visibleSongsCount by rememberSaveable { mutableStateOf(15) }
    var isDebouncing by rememberSaveable { mutableStateOf(false) }
    val normalizedQuery = query.trim()

    LaunchedEffect(successState?.query, successState?.mode) {
        visibleSongsCount = 15
        if (successState != null) {
            lastSuccessState = successState
        }
    }

    LaunchedEffect(normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            isDebouncing = false
            searchViewModel.search("")
            return@LaunchedEffect
        }

        if (normalizedQuery.length < 2) {
            isDebouncing = false
            searchViewModel.search("")
            return@LaunchedEffect
        }

        isDebouncing = true
        try {
            // Debounce corto para evitar disparos en cada tecla y mantener sensación de tiempo real.
            delay(320)
            searchViewModel.search(normalizedQuery, "balanced")
        } catch (_: CancellationException) {
            // Cambio de query durante debounce: flujo esperado.
        } finally {
            isDebouncing = false
        }
    }

    GradientContainer(topPadding = 4.dp, bottomPadding = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            SectionHeader(title = "Explorar", subtitle = "Busca por titulo o artista")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(18.dp))
                    .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(18.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar") },
                    placeholder = { Text("Escribe tu busqueda") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Busqueda en vivo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when {
                                isDebouncing || uiState == SearchUiState.Loading -> "Buscando..."
                                resultCount > 0 && successState?.hasMore == true -> "$resultCount resultados (parcial)"
                                resultCount > 0 -> "$resultCount resultados"
                                normalizedQuery.length < 2 -> "Escribe para buscar"
                                else -> "Sin resultados"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), RoundedCornerShape(16.dp))
                    .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                when (val state = uiState) {
                    SearchUiState.Idle -> {
                        EmptyPanel(
                            title = "Sin resultados aun",
                            subtitle = "Escribe el nombre de una cancion o artista para buscar en tiempo real."
                        )
                    }
                    SearchUiState.Loading -> {
                        val previous = lastSuccessState
                        if (previous != null && normalizedQuery.length >= 2) {
                            ExploreResultsContent(
                                state = previous,
                                visibleSongsCount = visibleSongsCount,
                                onVisibleSongsCountChange = { visibleSongsCount = it },
                                onPlaySong = { songs, index -> playerViewModel.playSongsQueue(songs, index) },
                                onToggleFavorite = { song ->
                                    val newFav = !song.isFavorite
                                    searchViewModel.updateFavoriteLocal(song.id, newFav)
                                    playerViewModel.setFavorite(song, newFav)
                                },
                                onSongToPlaylist = { selectedSongForPlaylist = it },
                                onLoadMore = { searchViewModel.loadMore() },
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToAlbum = onNavigateToAlbum,
                                showTopSpinner = true
                            )
                        } else {
                            ScreenLoadingSpinner()
                        }
                    }
                    is SearchUiState.Error -> {
                        if (isDebouncing) {
                            ScreenLoadingSpinner()
                        } else {
                            EmptyPanel(
                                title = "Error de busqueda",
                                subtitle = state.message
                            )
                        }
                    }
                    is SearchUiState.Success -> {
                        ExploreResultsContent(
                            state = state,
                            visibleSongsCount = visibleSongsCount,
                            onVisibleSongsCountChange = { visibleSongsCount = it },
                            onPlaySong = { songs, index -> playerViewModel.playSongsQueue(songs, index) },
                            onToggleFavorite = { song ->
                                val newFav = !song.isFavorite
                                searchViewModel.updateFavoriteLocal(song.id, newFav)
                                playerViewModel.setFavorite(song, newFav)
                            },
                            onSongToPlaylist = { selectedSongForPlaylist = it },
                            onLoadMore = { searchViewModel.loadMore() },
                            onNavigateToArtist = onNavigateToArtist,
                            onNavigateToAlbum = onNavigateToAlbum,
                            showTopSpinner = false
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
}

@Composable
private fun ExploreResultsContent(
    state: SearchUiState.Success,
    visibleSongsCount: Int,
    onVisibleSongsCountChange: (Int) -> Unit,
    onPlaySong: (List<com.phantombeats.domain.model.Song>, Int) -> Unit,
    onToggleFavorite: (com.phantombeats.domain.model.Song) -> Unit,
    onSongToPlaylist: (com.phantombeats.domain.model.Song) -> Unit,
    onLoadMore: () -> Unit,
    onNavigateToArtist: (com.phantombeats.domain.model.Artist) -> Unit,
    onNavigateToAlbum: (com.phantombeats.domain.model.Album) -> Unit,
    showTopSpinner: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Resultados",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold
            )

            if (showTopSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.height(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.artists.isNotEmpty()) {
                item {
                    ArtistsRow(
                        artists = state.artists,
                        onArtistClick = { artist -> onNavigateToArtist(artist) }
                    )
                }
            }

            if (state.albums.isNotEmpty()) {
                item {
                    AlbumsRow(
                        albums = state.albums,
                        onAlbumClick = { album -> onNavigateToAlbum(album) }
                    )
                }
            }

            if (state.songs.isNotEmpty()) {
                item {
                    Text(
                        text = "Canciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                item {
                    val loadedSongsText = if (state.hasMore) "${state.songs.size}+" else state.songs.size.toString()
                    Text(
                        text = "Mostrando ${min(visibleSongsCount, state.songs.size)} de $loadedSongsText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            items(
                count = state.songs.take(visibleSongsCount).size,
                key = { index -> state.songs[index].id }
            ) { index ->
                val song = state.songs[index]
                SongRowCard(
                    song = song,
                    onPlay = { onPlaySong(state.songs, index) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    trailingLabel = "Lista",
                    onTrailingClick = { onSongToPlaylist(song) }
                )
            }

            if (state.songs.size > visibleSongsCount || state.hasMore) {
                item {
                    ActionButton(
                        label = "Ver más",
                        onClick = {
                            val nextVisible = visibleSongsCount + 15
                            onVisibleSongsCountChange(nextVisible)
                            if (state.hasMore && nextVisible > state.songs.size && !state.isLoadingMore) {
                                onLoadMore()
                            }
                        }
                    )
                }
            } else if (state.songs.isNotEmpty()) {
                item {
                    Text(
                        text = "No hay más resultados",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (state.isLoadingMore) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                ScreenLoadingSpinner(modifier = Modifier.padding(8.dp))
            }
        }
    }
}

