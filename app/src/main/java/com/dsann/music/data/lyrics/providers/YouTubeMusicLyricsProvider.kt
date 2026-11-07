package com.dsann.music.data.lyrics.providers

import android.util.Log
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.data.lyrics.AdvancedLyricsParser
import com.dsann.music.data.lyrics.LrcParser
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.TrackMetadata
import com.dsann.music.data.lyrics.TtmlParser
import kotlinx.coroutines.runBlocking

/**
 * YouTube Music InnerTube lyrics provider.
 * Fetches official lyrics directly from YouTube Music servers using the
 * authenticated InnerTube API. Requires a videoId in the TrackMetadata.
 *
 * This provider is given the highest priority (1) because YouTube's lyrics
 * are the official source and perfectly match the playing track.
 */
class YouTubeMusicLyricsProvider : LyricsProvider {

    companion object {
        private const val TAG = "YTMusicLyricsProvider"
    }

    override fun getName(): String = "YouTube Music"

    override fun getPriority(): Int = 1 // Highest priority — direct source

    override fun search(track: TrackMetadata): LyricsResult? {
        val videoId = track.videoId
        if (videoId.isNullOrEmpty()) {
            Log.d(TAG, "No videoId available, skipping YouTube Music lyrics")
            return null
        }

        return try {
            val lyricsText = runBlocking {
                YouTubeMusic.lyrics(videoId).getOrNull()
            }

            if (lyricsText.isNullOrBlank()) {
                Log.d(TAG, "No lyrics returned from YouTube Music for $videoId")
                return null
            }

            val raw = lyricsText.trim()
            LyricsResult().apply {
                providerName = getName()
                confidence = 1.0f
                matchedTitle = track.rawTitle
                matchedArtist = track.rawArtist
                if (LyricsResult.looksSynchronized(raw)) {
                    setSyncedLyrics(raw)
                    isWordByWord = when {
                        AdvancedLyricsParser.recognizes(raw) ->
                            AdvancedLyricsParser.parse(raw).orEmpty().any { it.isWordByWord && it.words.isNotEmpty() }
                        raw.contains("<tt", ignoreCase = true) || raw.contains("<?xml", ignoreCase = true) ->
                            TtmlParser.parse(raw).any { it.isWordByWord && it.words.isNotEmpty() }
                        else -> LrcParser.parse(raw).any { it.isWordByWord && it.words.isNotEmpty() }
                    }
                } else {
                    plainLyrics = raw
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube Music lyrics fetch failed for $videoId: ${e.message}")
            null
        }
    }
}
