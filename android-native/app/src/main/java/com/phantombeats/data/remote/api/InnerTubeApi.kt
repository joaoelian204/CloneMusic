package com.phantombeats.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface InnerTubeApi {
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
        "Origin: https://music.youtube.com",
        "Content-Type: application/json"
    )
    @POST
    suspend fun search(
        @Url url: String = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false",
        @Body request: InnerTubeSearchRequest
    ): Response<com.google.gson.JsonObject>
}

data class InnerTubeSearchRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val query: String,
    val params: String = "EgWKAQIIAWoMEAMQBBAJEA4QChAF" // Filtra estrictamente por canciones (Song) oficial en YTM
)

data class InnerTubeContext(
    val client: ClientInfo = ClientInfo()
)

data class ClientInfo(
    val clientName: String = "WEB_REMIX",
    val clientVersion: String = "1.20230213.01.00",
    val hl: String = "es"
)
