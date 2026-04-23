package com.phantombeats.domain.model

data class StreamInfo(
    val url: String,
    val format: String,     // Ej: "m4a", "webm"
    val expiryTimeMs: Long  // TTL del stream en la sesión actual
)
