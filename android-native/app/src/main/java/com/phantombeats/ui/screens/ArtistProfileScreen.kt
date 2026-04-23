package com.phantombeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.phantombeats.domain.model.Song
import com.phantombeats.ui.viewmodels.PlayerViewModel
import com.phantombeats.ui.viewmodels.SearchUiState
import com.phantombeats.ui.viewmodels.SearchViewModel

private fun normalizeArtistText(value: String): String {
    return value
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun belongsToArtist(song: Song, artistName: String): Boolean {
    val expected = normalizeArtistText(artistName)
    if (expected.isBlank()) return true

    val artistField = normalizeArtistText(song.artist)
    if (artistField == expected || artistField.contains(expected)) return true

    val titleField = normalizeArtistText(song.title)
    return titleField.contains(expected)
}

@Composable
fun ArtistProfileScreen(
    artistName: String,
    imageUrl: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by searchViewModel.uiState.collectAsState()

    LaunchedEffect(artistName) {
        searchViewModel.search(artistName, "balanced")
    }

    GradientContainer(topPadding = 4.dp, bottomPadding = 0.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Volver")
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = artistName.take(1).uppercase(),
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                SearchUiState.Idle, SearchUiState.Loading -> {
                    ScreenLoadingSpinner()
                }
                is SearchUiState.Error -> {
                    EmptyPanel(title = "Error", subtitle = state.message)
                }
                is SearchUiState.Success -> {
                    val filteredSongs = state.songs.filter { belongsToArtist(it, artistName) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "Populares",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(filteredSongs.size) { index ->
                            val song = filteredSongs[index]
                            SongRowCard(
                                song = song,
                                onPlay = { playerViewModel.playSongsQueue(filteredSongs, index) },
                                onToggleFavorite = {
                                    searchViewModel.updateFavoriteLocal(song.id, !song.isFavorite)
                                },
                                trailingLabel = "Play"
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}
