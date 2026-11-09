package com.dsann.music.data.lyrics.providers

import android.util.Log
import com.dsann.music.data.lyrics.SyncLrcApi
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.TrackMetadata
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.abs

/**
 * SyncLRC supports direct metadata lookup, fuzzy search, and canonical-ID lookup.
 * Try the precise lookup first, then use its search index when metadata hints miss.
 */
class SyncLrcProvider(private val api: SyncLrcApi) : LyricsProvider {

    companion object {
        private const val TAG = "SyncLrcProvider"
        private const val SEARCH_LIMIT = 8
        private val KARAOKE_WORD_PATTERN = Regex("""<\d{1,3}:\d{2}[.:]\d{1,3}>""")
    }

    override fun getName(): String = "SyncLRC"
    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        return try {
            val durationSec = track.durationMs.takeIf { it > 0L }
                ?.div(1000L)?.coerceAtLeast(1L)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
            val album = track.album.takeIf { it.isNotBlank() }

            // Exact lookup, first with all available hints.
            val first = api.getLyrics(
                track.cleanedTitle, track.cleanedArtist, album, durationSec
            ).execute()
            if (first.isSuccessful) {
                parsePayload(first.body(), track)?.let { return it }
            } else {
                Log.w(TAG, "SyncLRC exact lookup HTTP ${first.code()}")
            }

            // Some catalog matches are indexed without album/duration metadata.
            if (album != null || durationSec != null) {
                val relaxed = api.getLyrics(
                    track.cleanedTitle, track.cleanedArtist, null, null
                ).execute()
                if (relaxed.isSuccessful) {
                    parsePayload(relaxed.body(), track)?.let { return it }
                } else {
                    Log.w(TAG, "SyncLRC relaxed lookup HTTP ${relaxed.code()}")
                }
            }

            // Fuzzy search is a separate API path and can return matches the exact
            // endpoint does not resolve. Its result can contain lyrics inline.
            searchIndex(track)
        } catch (error: Exception) {
            Log.w(TAG, "SyncLRC lookup failed: ${error.javaClass.simpleName}: ${error.message}")
            null
        }
    }

    private fun searchIndex(track: TrackMetadata): LyricsResult? {
        val response = api.searchTracks(
            "${track.cleanedTitle} ${track.cleanedArtist}",
            SEARCH_LIMIT
        ).execute()

        if (!response.isSuccessful || response.body() == null) {
            Log.w(TAG, "SyncLRC search HTTP ${response.code()}")
            return null
        }

        val root = response.body() ?: return null
        val candidates = findResultsArray(root)
            ?.mapNotNull { item -> if (item.isJsonObject) item.asJsonObject else null }
            .orEmpty()
            .mapNotNull { candidate ->
                val score = candidateScore(candidate, track)
                if (score < 4) null else candidate to score
            }
            .sortedByDescending { it.second }

        for ((candidate, _) in candidates.take(3)) {
            parsePayload(candidate, track)?.let { return it }

            val id = string(candidate, "id") ?: continue
            val byId = api.getLyricsById(id).execute()
            if (byId.isSuccessful) {
                parsePayload(byId.body(), track)?.let { return it }
            } else {
                Log.w(TAG, "SyncLRC ID lookup HTTP ${byId.code()}")
            }
        }
        Log.d(TAG, "SyncLRC search returned no usable lyrics for ${track.cleanedTitle}")
        return null
    }

    private fun parsePayload(payload: JsonElement?, track: TrackMetadata): LyricsResult? {
        val root = unwrapObject(payload) ?: return null
        val nestedLyrics = root.get("lyrics")?.takeIf { it.isJsonObject }?.asJsonObject

        fun text(key: String): String? =
            string(root, key) ?: nestedLyrics?.let { string(it, key) }

        val matchedTitle = text("track") ?: text("title") ?: track.cleanedTitle
        val matchedArtist = text("artist") ?: track.cleanedArtist
        val matchedDuration = number(root, "duration")
            ?: nestedLyrics?.let { number(it, "duration") }
            ?: (track.durationMs / 1000.0)

        val result = LyricsResult(
            providerName = getName(),
            matchedTitle = matchedTitle,
            matchedArtist = matchedArtist,
            matchedDuration = matchedDuration.toFloat()
        )

        val instrumental = boolean(root, "instrumental")
            ?: nestedLyrics?.let { boolean(it, "instrumental") }
        if (instrumental == true) {
            result.isInstrumental = true
            return result
        }

        val karaoke = text("karaoke")
        val synced = text("synced")
        val plain = text("plain")
        val singleLyrics = root.get("lyrics")
            ?.takeIf { it.isJsonPrimitive && !it.isJsonNull }
            ?.let { runCatching { it.asString.trim() }.getOrNull() }
            ?: nestedLyrics?.let { string(it, "lyrics") }
        val type = text("type")?.lowercase()

        when {
            !karaoke.isNullOrBlank() -> {
                result.setSyncedLyrics(karaoke)
                if (KARAOKE_WORD_PATTERN.containsMatchIn(karaoke)) {
                    result.setWordByWord(true)
                }
            }
            !synced.isNullOrBlank() -> result.setSyncedLyrics(synced)
            !singleLyrics.isNullOrBlank() -> {
                if (LyricsResult.looksSynchronized(singleLyrics) ||
                    type == "karaoke" || type == "synced"
                ) {
                    result.setSyncedLyrics(singleLyrics)
                    if (type == "karaoke" || KARAOKE_WORD_PATTERN.containsMatchIn(singleLyrics)) {
                        result.setWordByWord(true)
                    }
                } else {
                    result.setPlainLyrics(singleLyrics)
                }
            }
        }

        if (!plain.isNullOrBlank()) result.plainLyrics = plain
        if (result.hasLyrics()) {
            result.matchedAlbum = text("album")
            return result
        }
        return null
    }

    private fun unwrapObject(payload: JsonElement?): JsonObject? {
        if (payload == null || !payload.isJsonObject) return null
        val root = payload.asJsonObject
        val data = root.get("data")
        return if (data != null && data.isJsonObject) data.asJsonObject else root
    }

    private fun findResultsArray(payload: JsonElement): JsonArray? {
        if (payload.isJsonArray) return payload.asJsonArray
        if (!payload.isJsonObject) return null
        val root = payload.asJsonObject
        val results = root.get("results") ?: root.get("data") ?: root.get("items")
        if (results == null || results.isJsonNull) return null
        if (results.isJsonArray) return results.asJsonArray
        return if (results.isJsonObject) findResultsArray(results) else null
    }

    private fun candidateScore(candidate: JsonObject, track: TrackMetadata): Int {
        val candidateTitle = string(candidate, "track", "title", "name").orEmpty()
        val candidateArtist = string(candidate, "artist", "artistName").orEmpty()
        val wantedTitle = normalize(track.cleanedTitle)
        val wantedArtist = normalize(track.cleanedArtist)
        val title = normalize(candidateTitle)
        val artist = normalize(candidateArtist)
        if (wantedArtist.isNotBlank() && artist.isNotBlank() &&
            !artist.contains(wantedArtist) && !wantedArtist.contains(artist)
        ) return 0

        val titleScore = when {
            title.isBlank() || wantedTitle.isBlank() -> 0
            title == wantedTitle -> 8
            title.contains(wantedTitle) || wantedTitle.contains(title) -> 5
            else -> 0
        }
        val artistScore = when {
            wantedArtist.isBlank() || artist.isBlank() -> 0
            artist == wantedArtist -> 5
            artist.contains(wantedArtist) || wantedArtist.contains(artist) -> 3
            else -> 0
        }
        val seconds = number(candidate, "duration")
        val durationScore = if (
            seconds != null && seconds > 0.0 && track.durationMs > 0L &&
            abs(seconds - track.durationMs / 1000.0) <= 8.0
        ) 2 else 0

        // Do not select a result for a different song merely because the search
        // endpoint returned it near the top.
        return if (titleScore >= 5) titleScore + artistScore + durationScore else 0
    }

    private fun normalize(value: String): String =
        value.lowercase(java.util.Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    private fun string(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (!value.isJsonPrimitive || value.isJsonNull) continue
            val text = runCatching { value.asString.trim() }.getOrNull()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun number(obj: JsonObject, key: String): Double? = runCatching {
        obj.get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asDouble
    }.getOrNull()

    private fun boolean(obj: JsonObject, key: String): Boolean? = runCatching {
        obj.get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asBoolean
    }.getOrNull()
}
