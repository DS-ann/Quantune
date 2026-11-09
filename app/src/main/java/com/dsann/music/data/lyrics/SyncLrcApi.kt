package com.dsann.music.data.lyrics

import retrofit2.Call
import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SyncLrcApi {
    @GET("/lyrics")
    fun getLyrics(
        @Query("track") track: String,
        @Query("artist") artist: String,
        @Query("album") album: String?,
        @Query("duration") duration: Int? // duration in seconds
    ): Call<JsonElement>

    @GET("/search")
    fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 8
    ): Call<JsonElement>

    @GET("/lyrics/{id}")
    fun getLyricsById(@Path("id") id: String): Call<JsonElement>
}
