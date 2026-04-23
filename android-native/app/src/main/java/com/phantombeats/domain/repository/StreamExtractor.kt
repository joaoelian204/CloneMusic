package com.phantombeats.domain.repository

import com.phantombeats.domain.model.StreamInfo

interface StreamExtractor {
    val name: String
    val isStable: Boolean // Sirve para el Circuit Breaker (protección contra fallos constantes)
    suspend fun getStreamUrl(songId: String): Result<StreamInfo>
    fun markInestable() 
    fun resetStability()
}
