package com.phantombeats.data.repository

import com.phantombeats.data.remote.api.InnerTubeApi
import com.phantombeats.data.remote.api.InnerTubeSearchRequest
import com.phantombeats.data.remote.api.ItunesApi
import com.phantombeats.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnerTubeRepositoryImpl @Inject constructor(
    private val api: InnerTubeApi,
    private val itunesApi: ItunesApi
) {
    // FASE 1: Búsqueda y Obtención de Metadatos (InnerTube API + iTunes Covers)
    suspend fun searchTrack(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = api.search(request = InnerTubeSearchRequest(query = query))
            
            if (response.isSuccessful && response.body() != null) {
                val parsedSongs = parseInnerTubeJson(response.body()!!.toString())
                
                // Fetch official HD covers from iTunes in parallel using supervisorScope 
                // so a failing request won't cancel the others.
                val songs = supervisorScope {
                    val deferredSongs = parsedSongs.map { song ->
                        async {
                            try {
                                val cleanArtist = song.artist.substringBefore(" • ").replace("Topic", "").trim()
                                val itunesRes = itunesApi.searchSongs("${song.title} $cleanArtist")
                                val officialCover = itunesRes.results.firstOrNull()?.artworkUrl100
                                if (!officialCover.isNullOrEmpty()) {
                                    // iTunes devuelve 100x100 por defecto. Cambiamos a 1000x1000 HD real sin barras negras.
                                    val hdCover = officialCover.replace("100x100bb", "1000x1000bb")
                                    song.copy(coverUrl = hdCover)
                                } else {
                                    song // fallback al cover local de Youtube
                                }
                            } catch (e: Exception) {
                                song // fallback al cover local de Youtube en caso de falla de red
                            }
                        }
                    }
                    deferredSongs.awaitAll()
                }

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
                val section = contents.getJSONObject(i)
                val shelfContents = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents") ?:
                                    section.optJSONObject("musicCardShelfRenderer")?.optJSONArray("contents") ?: continue

                for (j in 0 until shelfContents.length()) {
                    val item = shelfContents.getJSONObject(j)
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
                    
                    val authorRawBuilder = StringBuilder()
                    for (k in 0 until descObj.length()) {
                        authorRawBuilder.append(descObj.getJSONObject(k).getString("text"))
                    }
                    var authorRaw = authorRawBuilder.toString()
                    // Remove exact prefixes (handling Unicode correctly for UI)
                    authorRaw = authorRaw.replace(Regex("^(Canción|Song|Vídeo|Video|Podcast)\\s*[•\\-]\\s*", RegexOption.IGNORE_CASE), "")
                    
                    // Filter out duration at the end (e.g. " • 3:45") and views (e.g. " • 2.5M views")
                    authorRaw = authorRaw.replace(Regex("\\s*[•\\-]\\s*(?:\\d+:)?\\d+:\\d+\\s*$"), "")
                    authorRaw = authorRaw.replace(Regex("(?i)\\s*[•\\-]\\s*[0-9.,]+[kmblr]?\\s*(vistas|views|reproducciones)\\s*$"), "")
                    
                    val author = if (authorRaw.isNotBlank()) authorRaw else "Unknown"

                    val thumbnails = renderer.getJSONObject("thumbnail")
                        .getJSONObject("musicThumbnailRenderer")
                        .getJSONObject("thumbnail")
                        .getJSONArray("thumbnails")
                        
                    val coverUrl = if (thumbnails.length() > 0) {
                        var rawUrl = thumbnails.getJSONObject(thumbnails.length() - 1).getString("url")
                        if (rawUrl.startsWith("//")) rawUrl = "https:$rawUrl"
                        
                        if (rawUrl.contains("lh3.googleusercontent.com") || rawUrl.contains("yt3.ggpht.com")) {
                            // Cambiamos por 544x544 que es tamaño oficial de portadas HD (evitamos pixelado por sobreescalado)
                            rawUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")
                                  .replace(Regex("-s\\d+.*"), "-s544")
                                  .replace(Regex("=s\\d+.*"), "=s544")
                        } else if (rawUrl.contains("i.ytimg.com")) {
                            // Es un video de YouTube (miniatura 16:9 extraida de cuadros del video).
                            // Limpiamos los crops (sqp) y pedimos resolucion HD (720p) sin barras negras extrañas
                            rawUrl.substringBefore("?")
                                  .replace(Regex("(hqdefault|mqdefault|sddefault|default)\\.jpg"), "hq720.jpg")
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs
    }
}
