package com.phantombeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phantombeats.R
import com.phantombeats.domain.model.Song
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.theme.PhantomDarkGray
import com.phantombeats.ui.utils.toDisplayText
import androidx.compose.foundation.ExperimentalFoundationApi

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun SongRowCard(
    modifier: Modifier = Modifier,
    song: Song,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    trailingLabel: String,
    onTrailingClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    extraActions: (@Composable RowScope.() -> Unit)? = null
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongPress
            )
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 2.dp)
        ) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (extraActions == null) {
                TextButton(
                    onClick = { (onTrailingClick ?: onPlay).invoke() },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = trailingLabel,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            } else {
                CompactSongActionButton(
                    onClick = { (onTrailingClick ?: onPlay).invoke() },
                    tint = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                extraActions()
            }

            CompactSongActionButton(
                onClick = {
                    optimisticFavorite = !optimisticFavorite
                    onToggleFavorite()
                },
                tint = favoriteTint
            ) {
                Icon(
                    imageVector = if (optimisticFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(favoriteScale)
                )
            }
        }
    }
}

@Composable
private fun CompactSongActionButton(
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint
        ) {
            content()
        }
    }
}

@Composable
fun MixCard(song: Song, onClick: () -> Unit, footer: String = "Play") {
    val display = song.toDisplayText()
    Column(
        modifier = Modifier
            .width(115.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))   
            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(12.dp))        
            .clickable(onClick = onClick)
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
                .background(PhantomDarkGray)
        )
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subText = if (footer == "Play") display.subtitle else footer    
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
