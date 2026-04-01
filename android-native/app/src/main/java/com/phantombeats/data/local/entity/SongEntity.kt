package com.phantombeats.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val coverUrl: String,
    val provider: String,
    val localPath: String?,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0L,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
