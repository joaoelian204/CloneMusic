package com.phantombeats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.phantombeats.ui.viewmodels.LibraryViewModel
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.PlaylistViewModel

@Composable
fun OfflineScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val songs by libraryViewModel.downloadedSongs.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    var selectedSongForPlaylist by remember { mutableStateOf<com.phantombeats.domain.model.Song?>(null) }

    GradientContainer {
        if (songs.isEmpty()) {
            EmptyPanel(
                title = "No tienes canciones offline",
                subtitle = "Marca canciones con el corazon para descargarlas y verlas aqui."
            )
            return@GradientContainer
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SectionHeader(title = "Offline", subtitle = "${songs.size} canciones descargadas")
            }
            items(songs, key = { it.id }) { song ->
                SongRowCard(
                    song = song,
                    onPlay = { playerViewModel.playSong(song) },
                    onToggleFavorite = { playerViewModel.setFavorite(song, !song.isFavorite) },
                    trailingLabel = "Lista",
                    onTrailingClick = { selectedSongForPlaylist = song }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
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
