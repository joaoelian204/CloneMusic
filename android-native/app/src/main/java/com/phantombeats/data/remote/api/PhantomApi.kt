package com.phantombeats.data.remote.api

import com.phantombeats.data.remote.dto.SongDto
import com.phantombeats.data.remote.dto.StreamResultDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PhantomApi {

    @GET("/api/v1/search")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("mode") mode: String? = null
    ): List<SongDto>
}
