package com.phantombeats.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesApi {
    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 1,
        @Query("offset") offset: Int = 0,
        @Query("attribute") attribute: String? = null
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
        @Query("attribute") attribute: String = "albumTerm",
        @Query("limit") limit: Int = 5,
        @Query("offset") offset: Int = 0
    ): ItunesResponse

    @GET("lookup")
    suspend fun lookupAlbumTracks(
        @Query("id") id: String,
        @Query("entity") entity: String = "song"
    ): ItunesResponse
}

data class ItunesResponse(val results: List<ItunesTrack>)
data class ItunesTrack(
    val wrapperType: String?,
    val kind: String?,
    val trackId: Long?,
    val trackName: String?,
    val trackCount: Int?,
    val artworkUrl100: String?,
    val artistName: String?,
    val artistId: Long?,
    val collectionType: String?,
    val collectionName: String?,
    val collectionId: Long?,
    val releaseDate: String?
)
