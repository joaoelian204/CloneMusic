package com.phantombeats.ui.utils

import android.content.SharedPreferences
import com.phantombeats.domain.model.Song

data class AutoPlaylistFeedback(
    val likedSongKeys: Set<String>,
    val blockedSongKeys: Set<String>
)

const val AUTO_PLAYLIST_FEEDBACK_PREF = "home_auto_playlist_feedback"
private const val AUTO_PLAYLIST_LIKED_KEY = "auto_playlist_liked_keys"
private const val AUTO_PLAYLIST_BLOCKED_KEY = "auto_playlist_blocked_keys"

fun loadAutoPlaylistFeedback(prefs: SharedPreferences): AutoPlaylistFeedback {
    val liked = prefs.getStringSet(AUTO_PLAYLIST_LIKED_KEY, emptySet()).orEmpty()
    val blocked = prefs.getStringSet(AUTO_PLAYLIST_BLOCKED_KEY, emptySet()).orEmpty()
    return AutoPlaylistFeedback(likedSongKeys = liked, blockedSongKeys = blocked)
}

fun markSongLikedInAutoPlaylists(prefs: SharedPreferences, song: Song, liked: Boolean) {
    val key = songFeedbackKey(song)
    val current = loadAutoPlaylistFeedback(prefs)
    val nextLiked = current.likedSongKeys.toMutableSet()
    val nextBlocked = current.blockedSongKeys.toMutableSet()

    if (liked) {
        nextLiked.add(key)
        nextBlocked.remove(key)
    } else {
        nextLiked.remove(key)
    }

    prefs.edit()
        .putStringSet(AUTO_PLAYLIST_LIKED_KEY, nextLiked)
        .putStringSet(AUTO_PLAYLIST_BLOCKED_KEY, nextBlocked)
        .apply()
}

fun blockSongInAutoPlaylists(prefs: SharedPreferences, song: Song, blocked: Boolean) {
    val key = songFeedbackKey(song)
    val current = loadAutoPlaylistFeedback(prefs)
    val nextLiked = current.likedSongKeys.toMutableSet()
    val nextBlocked = current.blockedSongKeys.toMutableSet()

    if (blocked) {
        nextBlocked.add(key)
        nextLiked.remove(key)
    } else {
        nextBlocked.remove(key)
    }

    prefs.edit()
        .putStringSet(AUTO_PLAYLIST_LIKED_KEY, nextLiked)
        .putStringSet(AUTO_PLAYLIST_BLOCKED_KEY, nextBlocked)
        .apply()
}
