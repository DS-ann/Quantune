package com.dsann.music.data.lyrics.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.LruCache
import com.dsann.music.data.lyrics.LrcParser
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.TrackMetadata
import com.google.gson.Gson
import androidx.core.content.edit

/**
 * Three-tier cache for lyrics results:
 * 1. Memory (LruCache) — instant
 * 2. SharedPreferences (serialized JSON) — fast disk
 * 3. Negative cache with TTL — prevents hammering providers for missing lyrics
 */
class LyricsCacheManager(context: Context) {

    private val memoryCache: LruCache<String, LyricsResult> = LruCache(MEMORY_CACHE_SIZE)
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val negativePrefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREF_NEGATIVE, Context.MODE_PRIVATE)
    private val gson: Gson = Gson()

    companion object {
        private const val TAG = "LyricsCacheManager"
        private const val PREF_NAME = "lyrics_cache_v2"
        private const val PREF_NEGATIVE = "lyrics_negative_cache"
        private const val MEMORY_CACHE_SIZE = 50
        private const val NEGATIVE_CACHE_TTL_MS = 30L * 60L * 1000L // 30 minutes
    }

    /**
     * Get cached lyrics result for a track.
     * Returns null if not cached.
     */
    fun get(track: TrackMetadata): LyricsResult? {
        val key = track.getCacheKey()
        memoryCache.get(key)?.let {
            Log.d(TAG, "Memory cache hit")
            // Disk entries are normalized before insertion. Keep memory hits allocation-free
            // so API 23 playback never reparses full TTML/word-level payloads per track switch.
            return it
        }
        val legacyKey = track.getLegacyCacheKey()?.takeIf { it != key }
        for (candidateKey in listOfNotNull(key, legacyKey)) {
            val json = prefs.getString(candidateKey, null) ?: continue
            try {
                val result = gson.fromJson(json, LyricsResult::class.java)
                if (result != null && result.hasLyrics()) {
                    val timingFlagsChanged = normalizeCachedWordTiming(result)
                    memoryCache.put(key, result)
                    if (candidateKey != key || timingFlagsChanged) {
                        persistNormalizedCacheEntry(key, result)
                        if (candidateKey != key) prefs.edit { remove(candidateKey) }
                    }
                    return result
                }
                prefs.edit { remove(candidateKey) }
            } catch (error: Exception) {
                Log.w(TAG, "Could not decode stored lyrics: " + error.message)
                prefs.edit { remove(candidateKey) }
            }
        }
        return null
    }

    /**
     * Reparse every cached synchronized payload with the current parsers. Earlier
     * builds marked every TTML span as karaoke even when it had no word timestamps;
     * trusting that persisted Boolean would keep the bad timing after the parser fix.
     */
    private fun normalizeCachedWordTiming(result: LyricsResult): Boolean {
        val timed = result.syncedLyrics?.takeIf { it.isNotBlank() } ?: return false
        if (!LyricsResult.looksSynchronized(timed)) return false

        val parsedLines = LrcParser.parse(timed)
        val normalizedSynced = result.isSynced || parsedLines.isNotEmpty()
        val normalizedWordByWord = parsedLines.any { it.isWordByWord && it.words.isNotEmpty() }
        val changed = result.isSynced != normalizedSynced || result.isWordByWord != normalizedWordByWord
        result.isSynced = normalizedSynced
        result.isWordByWord = normalizedWordByWord
        return changed
    }

    private fun persistNormalizedCacheEntry(key: String, result: LyricsResult) {
        runCatching {
            prefs.edit { putString(key, gson.toJson(result)) }
        }.onFailure {
            Log.w(TAG, "Could not persist normalized lyric timing flags: " + it.message)
        }
    }

    fun hasCachedLyrics(track: TrackMetadata): Boolean = get(track)?.hasLyrics() == true

    fun put(track: TrackMetadata, result: LyricsResult?) {
        val key = track.getCacheKey()

        if (result != null && result.hasLyrics()) {
            memoryCache.put(key, result)
            try {
                val json = gson.toJson(result)
                prefs.edit { putString(key, json) }
                Log.d(TAG, "Cached result for: $key (provider=${result.providerName})")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to serialize cache: ${e.message}")
            }

            // Clear any negative cache for this key
            clearNegativeCache(key)
        }
    }

    /**
     * Check if negative cache is active for this track (search already failed
     * recently).
     */
    fun isNegativeCacheActive(track: TrackMetadata): Boolean {
        val key = track.getCacheKey()
        val legacyKey = track.getLegacyCacheKey()?.takeIf { it != key }
        var timestamp = negativePrefs.getLong(key, 0L)
        if (timestamp <= 0L && legacyKey != null) {
            timestamp = negativePrefs.getLong(legacyKey, 0L)
            if (timestamp > 0L) {
                negativePrefs.edit { putLong(key, timestamp); remove(legacyKey) }
            }
        }
        if (timestamp <= 0L) return false
        if (System.currentTimeMillis() - timestamp > NEGATIVE_CACHE_TTL_MS) {
            negativePrefs.edit {
                remove(key)
                if (legacyKey != null) remove(legacyKey)
            }
            return false
        }
        return true
    }

    fun putNegative(track: TrackMetadata) {
        val key = track.getCacheKey()
        negativePrefs.edit { putLong(key, System.currentTimeMillis()) }
        Log.d(TAG, "Negative cache set for: $key")
    }

    /**
     * Clear negative cache for a specific track (used by Retry).
     */
    fun clearNegativeCache(key: String) {
        negativePrefs.edit { remove(key) }
    }

    /**
     * Evict a specific track from cache (for forcing re-fetch).
     */
    fun evict(track: TrackMetadata) {
        val key = track.getCacheKey()
        val legacyKey = track.getLegacyCacheKey()
        memoryCache.remove(key)
        prefs.edit {
            remove(key)
            if (legacyKey != null) remove(legacyKey)
        }
        negativePrefs.edit {
            remove(key)
            if (legacyKey != null) remove(legacyKey)
        }
    }
}
