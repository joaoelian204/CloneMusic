package com.phantombeats.domain.model

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String,
    val provider: String = "itunes"
)
