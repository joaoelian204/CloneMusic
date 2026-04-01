package com.phantombeats.data.remote.dto

import com.phantombeats.data.local.entity.SongEntity
import com.phantombeats.domain.model.Song

data class SongDto(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val coverUrl: String,
    val provider: String
) {
    fun toEntity(): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artist = artist,
            duration = duration,
            coverUrl = coverUrl,
            provider = provider,
            localPath = null, // Viene de la red, aún no está descargado
            playCount = 0,
            skipCount = 0,
            lastPlayed = 0L,
            isFavorite = false,
            addedAt = System.currentTimeMillis()
        )
    }
}

data class StreamResultDto(
    val url: String,
    val contentType: String,
    val provider: String
)
