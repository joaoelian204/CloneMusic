package com.phantombeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import coil.compose.AsyncImage
import com.phantombeats.R
import com.phantombeats.ui.utils.AutoPlaylist
import kotlin.math.roundToInt

@Composable
fun HomeAutoPlaylistCard(
    playlist: AutoPlaylist,
    isPinned: Boolean,
    updatedBadgeText: String?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onTogglePin: () -> Unit,
    onDragSwap: ((direction: Int) -> Unit)? = null
) {
    val covers = playlist.songs.mapNotNull { it.coverUrl.takeIf(String::isNotBlank) }.take(3)
    var dragX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .size(width = 232.dp, height = 232.dp)
            .offset { IntOffset(dragX.roundToInt(), 0) }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(isPinned, onDragSwap) {
                if (isPinned && onDragSwap != null) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount.x
                        },
                        onDragEnd = {
                            when {
                                dragX > 140f -> onDragSwap(1)
                                dragX < -140f -> onDragSwap(-1)
                            }
                            dragX = 0f
                            isDragging = false
                        },
                        onDragCancel = {
                            dragX = 0f
                            isDragging = false
                        }
                    )
                }
            }
            .clickable(enabled = !isDragging, onClick = onOpen)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            if (covers.isEmpty()) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                )
            } else {
                val offsets = listOf((-52).dp, 0.dp, 52.dp)
                covers.forEachIndexed { index, cover ->
                    AsyncImage(
                        model = cover,
                        contentDescription = playlist.title,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.cover_placeholder),
                        error = painterResource(R.drawable.cover_placeholder),
                        fallback = painterResource(R.drawable.cover_placeholder),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = offsets.getOrElse(index) { 0.dp })
                            .size(if (index == 1) 86.dp else 72.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!updatedBadgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = updatedBadgeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontStyle = FontStyle.Italic,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${playlist.songs.size} canciones",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isPinned) "Desfijar playlist" else "Fijar playlist",
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
                IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir playlist",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
