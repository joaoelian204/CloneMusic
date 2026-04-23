package com.phantombeats.domain.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val coverUrl: String,
    val provider: String,
    val localPath: String? = null,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0L,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = localPath != null && !localPath.startsWith("__PENDING__")
)
