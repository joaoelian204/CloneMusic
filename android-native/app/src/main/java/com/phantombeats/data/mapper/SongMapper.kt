package com.phantombeats.data.mapper

import com.phantombeats.data.local.entity.SongEntity
import com.phantombeats.domain.model.Song

fun SongEntity.toDomain(): Song {
    return Song(
        id = this.id,
        title = this.title,
        artist = this.artist,
        duration = this.duration,
        coverUrl = this.coverUrl,
        provider = this.provider,
        localPath = this.localPath,
        playCount = this.playCount,
        isFavorite = this.isFavorite
    )
}
