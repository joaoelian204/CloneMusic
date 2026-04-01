package com.phantombeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantombeats.data.local.entity.PlaylistEntity
import com.phantombeats.ui.theme.PhantomBorderAlpha

@Composable
fun CreatePlaylistDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva playlist") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                placeholder = { Text("Nombre de playlist") }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ChoosePlaylistDialog(
    playlists: List<PlaylistEntity>,
    songTitle: String,
    onDismiss: () -> Unit,
	 onSelect: (PlaylistEntity) -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    onRenamePlaylist: (PlaylistEntity, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (PlaylistEntity) -> Unit = {},
    canEditPlaylist: (PlaylistEntity) -> Boolean = { true }
) {
    var draftName by remember { mutableStateOf("") }
    var editingPlaylistId by remember { mutableStateOf<String?>(null) }

    val safeSongTitle = songTitle.trim().ifBlank { "Cancion actual" }
    val inputValue = draftName.trim()
    val isEditing = editingPlaylistId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Guardar en playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = safeSongTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = draftName,
                            onValueChange = { draftName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(if (isEditing) "Nuevo nombre" else "Nueva playlist")
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    if (inputValue.isBlank()) return@TextButton
                                    if (isEditing) {
                                        val target = playlists.firstOrNull { it.id == editingPlaylistId }
                                        if (target != null) onRenamePlaylist(target, inputValue)
                                    } else {
                                        onCreatePlaylist(inputValue)
                                    }
                                    draftName = ""
                                    editingPlaylistId = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(if (isEditing) "Guardar cambio" else "Crear")
                            }

                            if (isEditing) {
                                TextButton(
                                    onClick = {
                                        draftName = ""
                                        editingPlaylistId = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                ) {
                                    Text("Cancelar")
                                }
                            }
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    Text("No hay playlists disponibles. Crea la primera aqui.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            val editable = canEditPlaylist(playlist)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, PhantomBorderAlpha, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onSelect(playlist) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = playlist.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (editable) {
                                        IconButton(onClick = {
                                            editingPlaylistId = playlist.id
                                            draftName = playlist.name
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar playlist",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = {
                                            onDeletePlaylist(playlist)
                                            if (editingPlaylistId == playlist.id) {
                                                editingPlaylistId = null
                                                draftName = ""
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Borrar playlist",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
