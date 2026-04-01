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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel
import com.phantombeats.ui.viewmodels.SearchUiState
import com.phantombeats.ui.viewmodels.SearchViewModel

@Composable
fun ExploreScreen(
    playerViewModel: PlayerViewModel,
    searchViewModel: SearchViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by searchViewModel.uiState.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    var query by remember { mutableStateOf("") }
    var performanceMode by remember { mutableStateOf("balanced") }
    var selectedSongForPlaylist by remember { mutableStateOf<com.phantombeats.domain.model.Song?>(null) }
    val successState = uiState as? SearchUiState.Success
    val resultCount = successState?.songs?.size ?: 0
    val listState = rememberLazyListState()

    LaunchedEffect(successState?.songs?.size, successState?.hasMore, successState?.isLoadingMore) {
        val stateSnapshot = successState ?: return@LaunchedEffect
        if (!stateSnapshot.hasMore) return@LaunchedEffect

        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layoutInfo.totalItemsCount
            lastVisible to total
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total <= 0) return@collect
                val shouldLoadMore = lastVisible >= total - 5
                if (shouldLoadMore) {
                    searchViewModel.loadMore()
                }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = "Balanced",
                            selected = performanceMode == "balanced",
                            onClick = { performanceMode = "balanced" }
                        )
                        ModeChip(
                            label = "Turbo",
                            selected = performanceMode == "turbo",
                            onClick = { performanceMode = "turbo" }
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
                                resultCount > 0 && successState?.hasMore == true -> "$resultCount resultados (parcial)"
                                resultCount > 0 -> "$resultCount resultados"
                                else -> "Sin resultados"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                ActionButton(label = "Buscar ahora", onClick = { searchViewModel.search(query, performanceMode) })
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
                            subtitle = "Realiza una busqueda para cargar canciones en tiempo real."
                        )
                    }
                    SearchUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is SearchUiState.Error -> {
                        EmptyPanel(
                            title = "Error de busqueda",
                            subtitle = state.message
                        )
                    }
                    is SearchUiState.Success -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Resultados",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.songs, key = { it.id }) { song ->
                                    SongRowCard(
                                        song = song,
                                        onPlay = { playerViewModel.playSong(song) },
                                        onToggleFavorite = {
                                            val newFav = !song.isFavorite
                                            searchViewModel.updateFavoriteLocal(song.id, newFav)
                                            playerViewModel.setFavorite(song, newFav)
                                        },
                                        trailingLabel = "Lista",
                                        onTrailingClick = { selectedSongForPlaylist = song }
                                    )
                                }
                            }

                            if (state.isLoadingMore) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
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
}
