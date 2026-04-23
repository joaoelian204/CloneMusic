package com.phantombeats.domain.repository

interface StreamResolver {
    suspend fun getStreamUrl(videoId: String): Result<String>
    suspend fun resolveBySearch(query: String): Result<String>
}