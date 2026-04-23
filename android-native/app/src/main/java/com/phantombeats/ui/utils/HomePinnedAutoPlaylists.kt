package com.phantombeats.ui.utils

import android.content.SharedPreferences
import android.net.Uri
import com.phantombeats.domain.model.Song

private const val KEY_PINNED_LIST = "pinned_list"

data class PinnedAutoPlaylistSnapshot(
    val id: String,
    val title: String,
    val subtitle: String,
    val songIds: List<String>
) {
    fun toAutoPlaylist(pool: List<Song>): AutoPlaylist? {
        val byId = pool.associateBy { it.id }
        val songs = songIds.mapNotNull { byId[it] }
        if (songs.size < 3) return null
        return AutoPlaylist(id = id, title = title, subtitle = subtitle, songs = songs)
    }

    companion object {
        fun from(playlist: AutoPlaylist): PinnedAutoPlaylistSnapshot {
            return PinnedAutoPlaylistSnapshot(
                id = playlist.id,
                title = playlist.title,
                subtitle = playlist.subtitle,
                songIds = playlist.songs.map { it.id }.distinct().take(30)
            )
        }
    }
}

fun loadPinnedAutoPlaylistSnapshots(
    prefs: SharedPreferences,
    maxPins: Int
): List<PinnedAutoPlaylistSnapshot> {
    val raw = prefs.getString(KEY_PINNED_LIST, "")?.trim().orEmpty()
    if (raw.isBlank()) return emptyList()

    return raw
        .split(";;")
        .mapNotNull { decodeSnapshot(it) }
        .distinctBy { it.id }
        .take(maxPins)
}

fun savePinnedAutoPlaylistSnapshots(
    prefs: SharedPreferences,
    snapshots: List<PinnedAutoPlaylistSnapshot>
) {
    val serialized = snapshots
        .map { encodeSnapshot(it) }
        .joinToString(";;")

    prefs.edit().putString(KEY_PINNED_LIST, serialized).apply()
}

private fun encodeSnapshot(snapshot: PinnedAutoPlaylistSnapshot): String {
    val songsRaw = snapshot.songIds.joinToString(",")
    return listOf(snapshot.id, snapshot.title, snapshot.subtitle, songsRaw)
        .joinToString("|") { Uri.encode(it) }
}

private fun decodeSnapshot(raw: String): PinnedAutoPlaylistSnapshot? {
    val parts = raw.split("|")
    if (parts.size < 4) return null

    val id = Uri.decode(parts[0]).trim()
    if (id.isBlank()) return null

    val title = Uri.decode(parts[1]).trim().ifBlank { "Playlist fijada" }
    val subtitle = Uri.decode(parts[2]).trim().ifBlank { "Se mantiene hasta que la desfijes" }
    val songIds = Uri.decode(parts[3])
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    if (songIds.isEmpty()) return null

    return PinnedAutoPlaylistSnapshot(
        id = id,
        title = title,
        subtitle = subtitle,
        songIds = songIds
    )
}
