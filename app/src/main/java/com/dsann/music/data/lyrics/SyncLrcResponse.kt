package com.dsann.music.data.lyrics

import com.google.gson.annotations.SerializedName

data class SyncLrcResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("track") val track: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("album") val album: String?,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("instrumental") val instrumental: Boolean?,
    @SerializedName("karaoke") val karaoke: String?,
    @SerializedName("synced") val synced: String?,
    @SerializedName("plain") val plain: String?,
    // New API variants can return a single selected field rather than all three.
    @SerializedName("lyrics") val lyrics: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("format") val format: String? = null
)
