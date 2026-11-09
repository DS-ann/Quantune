package com.dsann.music.data.lyrics.providers.paxsenix

import android.util.Log
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.PaxsenixApi
import com.dsann.music.data.lyrics.TrackMetadata
import com.dsann.music.data.lyrics.providers.LyricsProvider
import com.google.gson.JsonParser

class PaxsenixYoutubeProvider(private val api: PaxsenixApi) : LyricsProvider {
    companion object { private const val TAG = "PaxsenixYoutube" }
    override fun getName(): String = "YouTube (Paxsenix)"
    override fun getPriority(): Int = 3

    override fun search(track: TrackMetadata): LyricsResult? {
        return try {
            val searchResponse = api.searchYouTube("${track.cleanedTitle} ${track.cleanedArtist}").execute()
            if (!searchResponse.isSuccessful || searchResponse.body() == null) {
                Log.w(TAG, "Search HTTP ${searchResponse.code()}")
                return null
            }
            val match = PaxsenixProviderUtils.firstSearchResult(searchResponse.body()!!) ?: return null
            val id = PaxsenixProviderUtils.string(match, "videoId", "id", "trackId") ?: return null
            val lyricsResponse = api.getYouTubeLyrics(id).execute()
            if (!lyricsResponse.isSuccessful || lyricsResponse.body() == null) {
                Log.w(TAG, "Lyrics HTTP ${lyricsResponse.code()}")
                return null
            }
            val raw = lyricsResponse.body()!!.string()
            val decoded = try {
                PaxsenixProviderUtils.extractLyrics(JsonParser.parseString(raw))
            } catch (_: Exception) { null } ?: raw.trim().takeIf { it.isNotEmpty() && !it.startsWith("{") } ?: return null
            val result = LyricsResult(
                providerName = getName(),
                matchedTitle = PaxsenixProviderUtils.string(match, "title", "name") ?: track.cleanedTitle,
                matchedArtist = PaxsenixProviderUtils.string(match, "artist", "artistName") ?: track.cleanedArtist
            )
            if (decoded.contains("[00:") || decoded.contains("[0:")) result.setSyncedLyrics(decoded)
            else result.setPlainLyrics(decoded)
            if (PaxsenixProviderUtils.isWordByWord(decoded)) result.setWordByWord(true)
            result.takeIf { it.hasLyrics() }
        } catch (error: Exception) {
            Log.w(TAG, "YouTube lookup failed: ${error.message}")
            null
        }
    }
}
