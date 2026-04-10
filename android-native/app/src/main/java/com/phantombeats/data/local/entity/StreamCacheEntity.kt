package com.phantombeats.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_cache")
data class StreamCacheEntity(
    @PrimaryKey val songId: String,
    val url: String,
    val format: String,
    val expiryTimeMs: Long
)
