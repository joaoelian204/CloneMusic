package com.phantombeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.phantombeats.R
import com.phantombeats.domain.model.Song
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.theme.PhantomDarkGray
import com.phantombeats.ui.utils.toDisplayText

@Composable
fun SongsGrid(
    songs: List<Song>,
    onPlay: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyPanel(title = "No hay canciones", subtitle = "Todavia no existen elementos para mostrar en esta vista.")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            GridSongCard(song = song, onPlay = { onPlay(song) }, onToggleFavorite = { onToggleFavorite(song) })
        }
    }
}

@Composable
private fun GridSongCard(song: Song, onPlay: () -> Unit, onToggleFavorite: () -> Unit) {
    val display = song.toDisplayText()
    val favoriteScale by animateFloatAsState(
        targetValue = if (song.isFavorite) 1.16f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "grid-favorite-scale"
    )
    val favoriteTint by animateColorAsState(
        targetValue = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        animationSpec = tween(durationMillis = 220),
        label = "grid-favorite-tint"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(14.dp))
            .clickable(onClick = onPlay)
            .padding(10.dp)
    ) {
        AsyncImage(
            model = song.coverUrl.takeIf { it.isNotBlank() },
            contentDescription = display.title,
            placeholder = painterResource(R.drawable.cover_placeholder),
            error = painterResource(R.drawable.cover_placeholder),
            fallback = painterResource(R.drawable.cover_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PhantomDarkGray)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = display.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = display.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (song.localPath?.startsWith("__PENDING__") == true) {
            Text(
                text = "Descargando...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (song.isDownloaded) {
            Text(
                text = "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = song.provider,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = favoriteTint,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(favoriteScale)
                )
            }
        }
    }
}
