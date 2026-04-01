package com.phantombeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.phantombeats.R
import com.phantombeats.domain.model.Song
import com.phantombeats.ui.theme.PhantomBorderAlpha
import com.phantombeats.ui.utils.toDisplayText

@Composable
fun FullPlayerHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = "Reproduciendo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
fun FullPlayerArtwork(song: Song?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (song == null) {
            Text(text = "Sin reproduccion activa", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        } else {
            AsyncImage(
                model = song.coverUrl.takeIf { it.isNotBlank() },
                contentDescription = song.title,
                placeholder = painterResource(R.drawable.cover_placeholder),
                error = painterResource(R.drawable.cover_placeholder),
                fallback = painterResource(R.drawable.cover_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun FullPlayerTrackInfo(song: Song?) {
    val display = song?.toDisplayText()
    Text(
        text = display?.title ?: "Selecciona una cancion",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = display?.subtitle ?: "",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)
    )
}
