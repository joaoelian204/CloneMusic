package com.phantombeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun SongRowCard(
    song: Song,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    trailingLabel: String,
    onTrailingClick: (() -> Unit)? = null
) {
    val display = song.toDisplayText()
    var optimisticFavorite by remember(song.id) { mutableStateOf(song.isFavorite) }

    LaunchedEffect(song.isFavorite) {
        optimisticFavorite = song.isFavorite
    }

    val favoriteScale by animateFloatAsState(
        targetValue = if (optimisticFavorite) 1.14f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "row-favorite-scale"
    )
    val favoriteTint by animateColorAsState(
        targetValue = if (optimisticFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        animationSpec = tween(durationMillis = 220),
        label = "row-favorite-tint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(14.dp))
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.coverUrl.takeIf { it.isNotBlank() },
            contentDescription = song.title,
            placeholder = painterResource(R.drawable.cover_placeholder),
            error = painterResource(R.drawable.cover_placeholder),
            fallback = painterResource(R.drawable.cover_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PhantomDarkGray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth(0.62f)) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        }

        TextButton(onClick = { (onTrailingClick ?: onPlay).invoke() }) {
            Text(text = trailingLabel, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = {
            optimisticFavorite = !optimisticFavorite
            onToggleFavorite()
        }) {
            Icon(
                imageVector = if (optimisticFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = favoriteTint,
                modifier = Modifier.scale(favoriteScale)
            )
        }
    }
}

@Composable
fun MixCard(song: Song, onClick: () -> Unit, footer: String = "Play") {
    val display = song.toDisplayText()
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
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
                .height(92.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PhantomDarkGray)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = display.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = footer,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
