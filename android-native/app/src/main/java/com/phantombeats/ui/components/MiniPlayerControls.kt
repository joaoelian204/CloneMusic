package com.phantombeats.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phantombeats.R
import com.phantombeats.domain.model.Song
import com.phantombeats.ui.utils.toDisplayText

@Composable
fun MiniPlayerControls(
    song: Song?,
    isPlaying: Boolean,
    isOffline: Boolean,
    isDownloadPending: Boolean,
    isDownloaded: Boolean,
    streamErrorMessage: String?,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: (Song) -> Unit
) {
    val display = song?.toDisplayText()
    var optimisticFavorite by remember(song?.id) { mutableStateOf(song?.isFavorite == true) }

    LaunchedEffect(song?.isFavorite) {
        optimisticFavorite = song?.isFavorite == true
    }

    val heartScale by animateFloatAsState(
        targetValue = if (optimisticFavorite) 1.15f else 1f,
        animationSpec = tween(180),
        label = "mini-heart-scale"
    )
    val heartTint by animateColorAsState(
        targetValue = if (optimisticFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        animationSpec = tween(220),
        label = "mini-heart-tint"
    )

    val statusText = when {
        !streamErrorMessage.isNullOrBlank() -> streamErrorMessage
        isDownloadPending -> "Descargando..."
        isOffline || isDownloaded -> "Offline"
        else -> display?.subtitle ?: ""
    }

    val statusIcon = when {
        isDownloadPending -> Icons.Default.Download
        isOffline || isDownloaded -> Icons.Default.CheckCircle
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song?.coverUrl?.takeIf { it.isNotBlank() },
            contentDescription = "Cover",
            placeholder = painterResource(R.drawable.cover_placeholder),
            error = painterResource(R.drawable.cover_placeholder),
            fallback = painterResource(R.drawable.cover_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = display?.title ?: if (streamErrorMessage != null) "Error de reproduccion" else "Cargando...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (statusIcon != null) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .scale(0.85f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (song != null) {
                    optimisticFavorite = !optimisticFavorite
                    onToggleFavorite(song)
                }
            }) {
                Icon(
                    imageVector = if (optimisticFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = heartTint,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(heartScale)
                )
            }

            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

