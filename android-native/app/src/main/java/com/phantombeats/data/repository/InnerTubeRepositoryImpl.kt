package com.phantombeats.data.repository

import com.phantombeats.data.remote.api.InnerTubeApi
import com.phantombeats.data.remote.api.InnerTubeSearchRequest
import com.phantombeats.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnerTubeRepositoryImpl @Inject constructor(
    private val api: InnerTubeApi
) {
    // FASE 1: Búsqueda y Obtención de Metadatos (InnerTube API)
    suspend fun searchTrack(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = api.search(request = InnerTubeSearchRequest(query = query))
            
            if (response.isSuccessful && response.body() != null) {
                val songs = parseInnerTubeJson(response.body()!!)
                Result.success(songs)
            } else {
                Result.failure(Exception("HTTP Error: ${response.code()} en InnerTube"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseInnerTubeJson(jsonString: String): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val root = JSONObject(jsonString)
            val contents = root.getJSONObject("contents")
                .getJSONObject("tabbedSearchResultsRenderer")
                .getJSONArray("tabs").getJSONObject(0)
                .getJSONObject("tabRenderer")
                .getJSONObject("content")
                .getJSONObject("sectionListRenderer")
                .getJSONArray("contents")

            // Recorrer los nodos para extraer videoId, title, author y thumbnail
            for (i in 0 until contents.length()) {
                val item = contents.getJSONObject(i)
                if (item.has("musicResponsiveListItemRenderer")) {
                    val renderer = item.getJSONObject("musicResponsiveListItemRenderer")
                    
                    // Extraer los datos principales
                    val flexColumns = renderer.getJSONArray("flexColumns")
                    
                    val titleObj = flexColumns.getJSONObject(0)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text").getJSONArray("runs").getJSONObject(0)
                    val title = titleObj.getString("text")

                    val descObj = flexColumns.getJSONObject(1)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        .getJSONObject("text").getJSONArray("runs")
                    val author = if (descObj.length() > 0) descObj.getJSONObject(0).getString("text") else "Unknown"

                    val thumbnails = renderer.getJSONObject("thumbnail")
                        .getJSONObject("musicThumbnailRenderer")
                        .getJSONObject("thumbnail")
                        .getJSONArray("thumbnails")
                        
                    val coverUrl = if (thumbnails.length() > 0) {
                        val rawUrl = thumbnails.getJSONObject(thumbnails.length() - 1).getString("url")
                        // YouTube Music recorta severamente la calidad en las búsquedas (=w60-h60).
                        // Modificamos el parámetro en la URL para forzar The High Definition Album Art (1080p).
                        if (rawUrl.contains("=w")) {
                            rawUrl.substringBefore("=w") + "=w1080-h1080-l90-rj"
                        } else {
                            rawUrl
                        }
                    } else ""

                    // Buscar el videoId
                    var videoId = ""
                    val endpoints = renderer.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        
                    if (endpoints != null && endpoints.has("videoId")) {
                        videoId = endpoints.getString("videoId")
                    }

                    if (videoId.isNotEmpty()) {
                        songs.add(
                            Song(
                                id = videoId,
                                title = title,
                                artist = author,
                                duration = 0,
                                coverUrl = coverUrl,
                                provider = "YouTube",
                                playCount = 0,
                                isFavorite = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs
    }
}
