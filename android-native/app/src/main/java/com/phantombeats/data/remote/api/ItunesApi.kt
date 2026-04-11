package com.phantombeats.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesApi {
    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 1
    ): ItunesResponse

    @GET("search")
    suspend fun searchArtists(
        @Query("term") term: String,
        @Query("entity") entity: String = "musicArtist",
        @Query("limit") limit: Int = 5
    ): ItunesResponse

    @GET("search")
    suspend fun searchAlbums(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 5
    ): ItunesResponse
}

data class ItunesResponse(val results: List<ItunesTrack>)
data class ItunesTrack(
    val artworkUrl100: String?,
    val artistName: String?,
    val artistId: Long?,
    val collectionName: String?,
    val collectionId: Long?,
    val releaseDate: String?
)
