package com.dsann.music.data.lyrics.providers.paxsenix

import android.util.Log
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.PaxsenixApi
import com.dsann.music.data.lyrics.TrackMetadata
import com.dsann.music.data.lyrics.providers.LyricsProvider
import com.google.gson.JsonParser

class PaxsenixSpotifyProvider(private val api: PaxsenixApi) : LyricsProvider {
    companion object { private const val TAG = "PaxsenixSpotify" }
    override fun getName(): String = "Spotify"
    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        return try {
            val searchResponse = api.searchSpotify("${track.cleanedTitle} ${track.cleanedArtist}").execute()
            if (!searchResponse.isSuccessful || searchResponse.body() == null) {
                Log.w(TAG, "Search HTTP ${searchResponse.code()}")
                return null
            }
            val match = PaxsenixProviderUtils.firstSearchResult(searchResponse.body()!!) ?: return null
            val rawId = PaxsenixProviderUtils.string(
                match, "trackId", "track_id", "id", "spotifyId", "spotify_id", "uri"
            ) ?: return null
            val id = PaxsenixProviderUtils.normalizeSpotifyTrackId(rawId) ?: return null
            val lyricsResponse = api.getSpotifyLyrics(id).execute()
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
                matchedTitle = PaxsenixProviderUtils.string(match, "name", "title", "trackName") ?: track.cleanedTitle,
                matchedArtist = PaxsenixProviderUtils.string(match, "artistName", "artist") ?: track.cleanedArtist
            )
            if (LyricsResult.looksSynchronized(decoded)) result.setSyncedLyrics(decoded)
            else result.setPlainLyrics(decoded)
            if (PaxsenixProviderUtils.isWordByWord(decoded)) result.setWordByWord(true)
            result.takeIf { it.hasLyrics() }
        } catch (error: Exception) {
            Log.w(TAG, "Spotify lookup failed: ${error.message}")
            null
        }
    }
}
