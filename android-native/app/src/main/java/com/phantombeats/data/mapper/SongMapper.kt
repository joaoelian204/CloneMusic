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
        skipCount = this.skipCount,
        lastPlayed = this.lastPlayed,
        isFavorite = this.isFavorite
    )
}

fun Song.toEntity(): SongEntity {
    return SongEntity(
        id = this.id,
        title = this.title,
        artist = this.artist,
        duration = this.duration,
        coverUrl = this.coverUrl,
        provider = this.provider,
        localPath = this.localPath,
        playCount = this.playCount,
        skipCount = this.skipCount,
        lastPlayed = if (this.lastPlayed > 0L) this.lastPlayed else System.currentTimeMillis(),
        isFavorite = this.isFavorite
    )
}
