package com.phantombeats.domain.model

data class Album(
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String,
    val coverUrl: String,
    val releaseYear: String = "",
    val provider: String = "itunes"
)
