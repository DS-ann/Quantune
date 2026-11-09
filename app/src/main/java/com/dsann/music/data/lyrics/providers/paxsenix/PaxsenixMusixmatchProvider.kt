package com.dsann.music.data.lyrics.providers.paxsenix

import android.util.Log
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.PaxsenixApi
import com.dsann.music.data.lyrics.TrackMetadata
import com.dsann.music.data.lyrics.providers.LyricsProvider
import com.google.gson.JsonParser

class PaxsenixMusixmatchProvider(private val api: PaxsenixApi) : LyricsProvider {
    companion object { private const val TAG = "PaxsenixMusixmatch" }
    override fun getName(): String = "Musixmatch"
    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        return try {
            val query = "${track.cleanedTitle} ${track.cleanedArtist}"
            val duration = if (track.durationMs > 0L) (track.durationMs / 1000L).toString() else null
            // A successful HTTP response can still contain an empty/error envelope; validate before fallback.
            for (type in listOf("word", "default")) {
                val response = api.getMusixmatchLyrics(
                    query = query, trackTitle = track.cleanedTitle,
                    artistName = track.cleanedArtist, duration = duration, type = type
                ).execute()
                if (!response.isSuccessful || response.body() == null) {
                    Log.w(TAG, "$type request HTTP ${response.code()}")
                    continue
                }
                val raw = response.body()!!.string()
                val decoded = try { PaxsenixProviderUtils.extractLyrics(JsonParser.parseString(raw)) }
                    catch (_: Exception) { null }
                    ?: raw.trim().takeIf { it.isNotEmpty() && !it.startsWith("{") }
                    ?: continue
                val result = LyricsResult(
                    providerName = getName(),
                    matchedTitle = track.cleanedTitle,
                    matchedArtist = track.cleanedArtist
                )
                if (LyricsResult.looksSynchronized(decoded)) result.setSyncedLyrics(decoded)
                else result.setPlainLyrics(decoded)
                if (PaxsenixProviderUtils.isWordByWord(decoded)) result.setWordByWord(true)
                if (result.hasLyrics()) return result
            }
            null
        } catch (error: Exception) {
            Log.w(TAG, "Musixmatch lookup failed: ${error.message}")
            null
        }
    }
}
