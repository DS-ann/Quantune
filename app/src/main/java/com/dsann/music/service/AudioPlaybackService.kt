@file:Suppress("DEPRECATION")

package com.dsann.music.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRouter2
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LineSyncedLyricTimeline
import com.dsann.music.R
import com.dsann.music.model.SongItem
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.ui.widget.DsmusicWidgetProvider
import com.dsann.music.ui.player.AppAnimationFrameRateController
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.TreeSet
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import android.media.AudioAttributes as LegacyAudioAttributes

/**
 * App/session fallback labels are not song titles. Reject them before they can be
 * persisted as Cache metadata or override a real title already saved for the stream.
 */
private fun usableCacheTrackTitle(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return candidate.takeUnless {
        it.equals("Quantune", ignoreCase = true) ||
            it.equals("D.S Music", ignoreCase = true) ||
            it.equals("Unknown Title", ignoreCase = true) ||
            it.equals("<unknown>", ignoreCase = true) ||
            it.equals("Unknown", ignoreCase = true) ||
            it.equals("Now Playing", ignoreCase = true)
    }
}

private fun usableCacheTrackArtist(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return candidate.takeUnless {
        it.equals("Quantune", ignoreCase = true) ||
            it.equals("D.S Music", ignoreCase = true) ||
            it.equals("Unknown Artist", ignoreCase = true) ||
            it.equals("<unknown>", ignoreCase = true) ||
            it.equals("Unknown", ignoreCase = true) ||
            it.equals("Now Playing", ignoreCase = true)
    }
}

@OptIn(UnstableApi::class)
data class CachedMediaMetadata(
    val title: String,
    val artist: String,
    val songId: String,
    val durationMs: Long = 0L,
    val videoId: String = "",
    val mimeType: String = "",
    val albumName: String = "",
    val thumbnailUrl: String = "",
    val cachedAtMs: Long = 0L
)

data class CachedAudioFacts(
    val cachedBytes: Long,
    val totalBytes: Long?,
    val mimeType: String?,
    val isComplete: Boolean
)

data class CompleteCachedMediaEntry(
    val cacheKey: String,
    val metadata: CachedMediaMetadata,
    val cachedBytes: Long,
    /** Oldest cached chunk file timestamp, used when older metadata has no cache-added date. */
    val cacheFileModifiedAtMs: Long = 0L
)


data class CurrentAudioFormatDetails(
    val bitrateBps: Int?,
    val sampleRateHz: Int?,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val albumTitle: String? = null
)

@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "AudioPlaybackService"
        
        const val ACTION_PLAY = "com.dsann.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.dsann.music.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.dsann.music.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.dsann.music.ACTION_NEXT"
        const val ACTION_TOGGLE_LOOP = "com.dsann.music.ACTION_TOGGLE_LOOP"
        const val ACTION_CYCLE_PLAYBACK_MODE = "com.dsann.music.ACTION_CYCLE_PLAYBACK_MODE"
        const val ACTION_TOGGLE_FAV = "com.dsann.music.ACTION_TOGGLE_FAV"
        const val ACTION_SESSION_CUSTOM_PREFIX = "com.dsann.music.ACTION_SESSION_CUSTOM."
        const val ACTION_WIDGET_FETCH_LYRICS = "com.dsann.music.ACTION_WIDGET_FETCH_LYRICS"
        const val ACTION_WIDGET_REFRESH_STATE = "com.dsann.music.ACTION_WIDGET_REFRESH_STATE"
        const val ACTION_WIDGET_TOGGLE_PLAY_PAUSE = "com.dsann.music.ACTION_WIDGET_TOGGLE_PLAY_PAUSE"

        private const val PREFS_NAME = "AppSettings"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_AUDIO_FOCUS = "audio_focus"

        val CMD_TOGGLE_LOOP = SessionCommand(ACTION_TOGGLE_LOOP, Bundle.EMPTY)
        val CMD_CYCLE_PLAYBACK_MODE = SessionCommand(ACTION_CYCLE_PLAYBACK_MODE, Bundle.EMPTY)
        val CMD_TOGGLE_FAV = SessionCommand(ACTION_TOGGLE_FAV, Bundle.EMPTY)

        private const val KEY_MEDIA_CACHE_ENABLED = "media_cache_enabled"
        private const val KEY_MEDIA_CACHE_LIMIT_GB = "media_cache_limit_gb"
        private const val DEFAULT_MEDIA_CACHE_LIMIT_GB = 1
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L
        private const val PREF_MEDIA_CACHE_METADATA = "media_cache_song_metadata"
        private const val CACHE_METADATA_ENTRY_PREFIX = "entry_"

        private var cache: SimpleCache? = null
        private var cacheEvictor: AdjustableCacheEvictor? = null
        private val _mediaCacheRevision = MutableStateFlow(0L)
        @JvmStatic val mediaCacheRevision: StateFlow<Long> = _mediaCacheRevision.asStateFlow()
        @Volatile private var activeService: AudioPlaybackService? = null

        private fun notifyMediaCacheChanged() {
            _mediaCacheRevision.value = _mediaCacheRevision.value + 1L
        }

        /**
         * Audio cache is user-valued offline data, not disposable temporary data.
         * Keep it under filesDir so Android's cache cleaner does not treat it as evictable.
         * Move the previous cacheDir folder on the first run after upgrade.
         */
        @Synchronized
        private fun getPersistentMediaDirectory(context: Context, directoryName: String): File {
            val appContext = context.applicationContext
            val persistentDirectory = File(appContext.filesDir, directoryName)
            val legacyDirectory = File(appContext.cacheDir, directoryName)

            if (legacyDirectory.exists()) {
                if (!persistentDirectory.exists() && legacyDirectory.renameTo(persistentDirectory)) {
                    Log.i(TAG, "Migrated $directoryName from cacheDir to persistent app storage")
                } else if (legacyDirectory.exists()) {
                    if (mergeLegacyDirectory(legacyDirectory, persistentDirectory)) {
                        Log.i(TAG, "Merged legacy $directoryName into persistent app storage")
                    } else {
                        // Keep the legacy folder intact when any file cannot be migrated.
                        // The persistent directory remains the active location for new writes.
                        Log.w(TAG, "Some legacy $directoryName files could not be migrated; original files were retained")
                    }
                }
            }

            if (!persistentDirectory.exists() && !persistentDirectory.mkdirs()) {
                throw IllegalStateException("Unable to create persistent media directory: ${persistentDirectory.absolutePath}")
            }
            return persistentDirectory
        }

        /**
         * Merge a legacy directory without overwriting files already present in the destination.
         * Files are copied through a temporary sibling and renamed only after the copy is complete.
         */
        private fun mergeLegacyDirectory(source: File, destination: File): Boolean {
            if (!source.exists()) return true
            if (!destination.exists() && !destination.mkdirs()) return false
            val children = source.listFiles() ?: return false

            for (child in children) {
                val target = File(destination, child.name)
                if (child.isDirectory) {
                    if (!mergeLegacyDirectory(child, target)) return false
                } else if (target.exists()) {
                    if (target.length() != child.length()) {
                        Log.w(TAG, "Skipped conflicting legacy cache file: ${child.name}")
                        return false
                    }
                    if (!child.delete()) return false
                } else {
                    val staged = File(destination, child.name + ".migration.tmp")
                    if (staged.exists() && !staged.delete()) return false
                    try {
                        java.io.FileInputStream(child).use { input ->
                            java.io.FileOutputStream(staged).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                                output.flush()
                                output.fd.sync()
                            }
                        }
                        if (!staged.renameTo(target)) {
                            staged.delete()
                            return false
                        }
                        if (!child.delete()) return false
                    } catch (error: Exception) {
                        staged.delete()
                        Log.w(TAG, "Unable to migrate legacy cache file: ${child.name}", error)
                        return false
                    }
                }
            }

            return source.delete() || !source.exists()
        }

        @Synchronized
        fun getCache(context: Context): SimpleCache {
            cache?.let { return it }

            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_MEDIA_CACHE_ENABLED, true)
            val limitBytes = prefs.getInt(KEY_MEDIA_CACHE_LIMIT_GB, DEFAULT_MEDIA_CACHE_LIMIT_GB)
                .coerceIn(1, 10).toLong() * BYTES_PER_GB
            val evictor = AdjustableCacheEvictor(enabled, limitBytes)
            val cacheDirectory = getPersistentMediaDirectory(appContext, "media_cache")
            val createdCache = SimpleCache(
                cacheDirectory,
                evictor,
                StandaloneDatabaseProvider(appContext)
            )
            try {
                // SimpleCache already reports its loaded spans to the evictor during construction.
                // Configure limits without rescanning every cache key a second time.
                evictor.configure(createdCache, enabled, limitBytes)
                cacheEvictor = evictor
                cache = createdCache
                return createdCache
            } catch (error: Exception) {
                try {
                    createdCache.release()
                } catch (releaseError: Exception) {
                    Log.w(TAG, "Failed to release an incompletely initialized media cache", releaseError)
                }
                throw error
            }
        }

        @Synchronized
        fun updateMediaCacheSettings(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_MEDIA_CACHE_ENABLED, true)
            val limitBytes = prefs.getInt(KEY_MEDIA_CACHE_LIMIT_GB, DEFAULT_MEDIA_CACHE_LIMIT_GB)
                .coerceIn(1, 10).toLong() * BYTES_PER_GB
            cache?.let { cacheEvictor?.configure(it, enabled, limitBytes) }
        }

        @Synchronized
        fun getMediaCacheSnapshot(context: Context): Pair<Long, List<Pair<String, Long>>> {
            val currentCache = getCache(context)
            val entries = currentCache.keys.map { key ->
                key to currentCache.getCachedSpans(key).map { it.length }.sum()
            }.filter { it.second > 0L }.sortedByDescending { it.second }
            return currentCache.cacheSpace to entries
        }
        
        /**
         * Reads complete cached audio entries in one pass for the Cache playlist.
         * Metadata preferences and the SimpleCache are opened once; optional artwork and
         * lyrics are deliberately not prerequisites for displaying a cached audio track.
         */
        @JvmStatic
        @Synchronized
        fun getCompleteCachedMediaEntries(context: Context): List<CompleteCachedMediaEntry> {
            val appContext = context.applicationContext
            val currentCache = getCache(appContext)
            val metadataPrefs = appContext.getSharedPreferences(
                PREF_MEDIA_CACHE_METADATA,
                Context.MODE_PRIVATE
            )
            val entries = ArrayList<CompleteCachedMediaEntry>()

            for (cacheKey in currentCache.keys) {
                val uri = Uri.parse(cacheKey)
                if (uri.scheme != "http" && uri.scheme != "https") continue

                val spans = currentCache.getCachedSpans(cacheKey)
                var cachedBytes = 0L
                var oldestCacheFileModifiedAtMs = Long.MAX_VALUE
                for (span in spans) {
                    cachedBytes += span.length
                    val modifiedAt = span.file?.lastModified() ?: 0L
                    if (modifiedAt > 0L && modifiedAt < oldestCacheFileModifiedAtMs) {
                        oldestCacheFileModifiedAtMs = modifiedAt
                    }
                }
                if (cachedBytes <= 0L) continue

                val stored = metadataPrefs.getString(cacheMetadataEntryKey(cacheKey), null)
                val metadata = parseCachedMediaMetadata(stored) ?: continue
                if (metadata.songId.isBlank() || metadata.title.isBlank()) continue

                val contentLength = androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(currentCache.getContentMetadata(cacheKey))
                // Do not display partial cache entries: Cache playlist playback is offline-only.
                if (contentLength <= 0L || !currentCache.isCached(cacheKey, 0L, contentLength)) continue

                entries.add(
                    CompleteCachedMediaEntry(
                        cacheKey = cacheKey,
                        metadata = metadata,
                        cachedBytes = cachedBytes,
                        cacheFileModifiedAtMs = oldestCacheFileModifiedAtMs.takeIf {
                            it != Long.MAX_VALUE
                        } ?: 0L
                    )
                )
            }

            entries.sortByDescending { it.cachedBytes }
            return entries
        }


        /**
         * Validates one already-known cache key without scanning every cached stream.
         */
        @JvmStatic
        @Synchronized
        fun getCompleteCachedPlaybackUriForKey(context: Context, cacheKey: String): Uri? {
            if (cacheKey.isBlank()) return null
            val uri = Uri.parse(cacheKey)
            if (uri.scheme != "http" && uri.scheme != "https") return null
            val currentCache = getCache(context.applicationContext)
            val contentLength = androidx.media3.datasource.cache.ContentMetadata
                .getContentLength(currentCache.getContentMetadata(cacheKey))
            if (contentLength <= 0L || !currentCache.isCached(cacheKey, 0L, contentLength)) return null
            return uri
        }

        /**
         * Finds a fully cached stream for a track without consulting the user's selected
         * format/bitrate. Returning the exact original URL preserves Media3's cache key, and
         * requiring the full content length prevents an offline tap from silently reaching
         * the network for missing byte ranges.
         */
        @JvmStatic
        @Synchronized
        fun getCachedPlaybackUri(
            context: Context,
            songId: String,
            videoId: String? = null,
            requireComplete: Boolean = true
        ): Uri? {
            val currentCache = getCache(context.applicationContext)
            val candidates = currentCache.keys.mapNotNull { key ->
                val metadata = getCachedMediaMetadata(context, key) ?: return@mapNotNull null
                val matchesSong = songId.isNotBlank() && metadata.songId == songId
                val matchesVideo = !videoId.isNullOrBlank() && metadata.videoId == videoId
                if (!matchesSong && !matchesVideo) return@mapNotNull null

                val contentMetadata = currentCache.getContentMetadata(key)
                val contentLength = androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(contentMetadata)
                val fullyCached = contentLength > 0L &&
                    currentCache.isCached(key, 0L, contentLength)
                if (requireComplete && !fullyCached) return@mapNotNull null

                val uri = Uri.parse(key)
                if (uri.scheme != "http" && uri.scheme != "https") return@mapNotNull null
                val cachedBytes = currentCache.getCachedSpans(key).sumOf { it.length }
                if (cachedBytes <= 0L) return@mapNotNull null
                Triple(key, cachedBytes, fullyCached)
            }
            // Prefer a completely cached version over a larger partial copy at another quality.
            val completeCandidates = candidates.filter { it.third }
            val preferredCandidates = completeCandidates.ifEmpty { candidates }
            return preferredCandidates.maxByOrNull { it.second }?.first?.let { Uri.parse(it) }
        }

        /** Returns cached byte count and detected format for the current track. */
        @JvmStatic
        @Synchronized
        fun getCachedAudioFacts(
            context: Context,
            songId: String,
            videoId: String? = null,
            streamUri: String? = null
        ): CachedAudioFacts? {
            val currentCache = getCache(context.applicationContext)

            // Song Info describes the selected playback source, not another cached quality
            // for the same song. When a URI is supplied, only that exact Media3 cache key counts.
            if (!streamUri.isNullOrBlank()) {
                if (streamUri !in currentCache.keys) return null
                val metadata = getCachedMediaMetadata(context, streamUri)
                val cachedBytes = currentCache.getCachedSpans(streamUri).sumOf { it.length }
                if (cachedBytes <= 0L) return null
                val contentLength = androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(currentCache.getContentMetadata(streamUri))
                    .takeIf { it > 0L }
                val complete = contentLength != null &&
                    currentCache.isCached(streamUri, 0L, contentLength)
                return CachedAudioFacts(
                    cachedBytes = cachedBytes,
                    totalBytes = contentLength,
                    mimeType = metadata?.mimeType?.takeIf { it.isNotBlank() },
                    isComplete = complete
                )
            }

            val candidates = currentCache.keys.mapNotNull { key ->
                val metadata = getCachedMediaMetadata(context, key) ?: return@mapNotNull null
                val matchesMetadata =
                    (songId.isNotBlank() && metadata.songId == songId) ||
                    (!videoId.isNullOrBlank() && metadata.videoId == videoId)
                if (!matchesMetadata) return@mapNotNull null

                val cachedBytes = currentCache.getCachedSpans(key).sumOf { it.length }
                if (cachedBytes <= 0L) return@mapNotNull null
                val contentLength = androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(currentCache.getContentMetadata(key))
                    .takeIf { it > 0L }
                val complete = contentLength != null &&
                    currentCache.isCached(key, 0L, contentLength)
                CachedAudioFacts(
                    cachedBytes = cachedBytes,
                    totalBytes = contentLength,
                    mimeType = metadata.mimeType.takeIf { it.isNotBlank() },
                    isComplete = complete
                )
            }
            val completeCandidates = candidates.filter { it.isComplete }
            return completeCandidates.ifEmpty { candidates }
                .maxByOrNull { it.totalBytes ?: it.cachedBytes }
        }

        /**
         * Associates the opaque Media3 cache key (typically a signed stream URL) with safe display
         * metadata. Only a SHA-256 digest is used as the preference key; signed URLs are not stored
         * in the metadata preference file.
         */
        @Synchronized
        fun rememberMediaCacheMetadata(
            context: Context,
            cacheKey: String,
            title: String,
            artist: String,
            songId: String = "",
            durationMs: Long = 0L,
            videoId: String = "",
            mimeType: String = "",
            albumName: String = "",
            thumbnailUrl: String = ""
        ) {
            if (cacheKey.isBlank()) return
            val digestKey = cacheMetadataEntryKey(cacheKey)
            // Keep the video ID when a cache playlist later reopens a track using its direct
            // cached URL (that offline queue intentionally has no online resolver ID).
            val previousMetadata = getCachedMediaMetadata(context, cacheKey)
            // currentSongName can be the app name when playback is restored without a
            // matching queue item. Never store that fallback as the actual track title.
            val safeTitle = usableCacheTrackTitle(title)
                ?: usableCacheTrackTitle(previousMetadata?.title)
                ?: return
            val safeArtist = usableCacheTrackArtist(artist)
                ?: usableCacheTrackArtist(previousMetadata?.artist)
                ?: "Unknown Artist"
            val payload = org.json.JSONObject()
                .put("title", safeTitle)
                .put("artist", safeArtist)
                .put("songId", songId.ifBlank { previousMetadata?.songId.orEmpty() })
                .put("durationMs", durationMs.takeIf { it > 0L } ?: previousMetadata?.durationMs ?: 0L)
                .put("videoId", videoId.ifBlank { previousMetadata?.videoId.orEmpty() })
                .put("mimeType", mimeType.ifBlank { previousMetadata?.mimeType.orEmpty() })
                .put("albumName", albumName.ifBlank { previousMetadata?.albumName.orEmpty() })
                .put("thumbnailUrl", thumbnailUrl.ifBlank { previousMetadata?.thumbnailUrl.orEmpty() })
                // Preserve the original cache timestamp when metadata is refreshed later.
                .put("cachedAtMs", previousMetadata?.cachedAtMs?.takeIf { it > 0L }
                    ?: System.currentTimeMillis())
                .toString()
            context.getSharedPreferences(PREF_MEDIA_CACHE_METADATA, Context.MODE_PRIVATE)
                .edit().putString(digestKey, payload).apply()
        }

        @JvmStatic
        fun getCachedMediaMetadata(context: Context, cacheKey: String): CachedMediaMetadata? {
            val stored = context.getSharedPreferences(PREF_MEDIA_CACHE_METADATA, Context.MODE_PRIVATE)
                .getString(cacheMetadataEntryKey(cacheKey), null)
            return parseCachedMediaMetadata(stored)
        }

        private fun parseCachedMediaMetadata(stored: String?): CachedMediaMetadata? {
            if (stored.isNullOrBlank()) return null
            return try {
                val json = org.json.JSONObject(stored)
                val title = json.optString("title", "").trim()
                if (title.isEmpty()) return null
                CachedMediaMetadata(
                    title = title,
                    artist = json.optString("artist", "").trim(),
                    songId = json.optString("songId", ""),
                    durationMs = json.optLong("durationMs", 0L),
                    videoId = json.optString("videoId", ""),
                    mimeType = json.optString("mimeType", ""),
                    albumName = json.optString("albumName", ""),
                    thumbnailUrl = json.optString("thumbnailUrl", ""),
                    cachedAtMs = json.optLong("cachedAtMs", 0L)
                )
            } catch (_: Exception) {
                null
            }
        }

        @JvmStatic
        fun getCachedMediaArtworkFile(context: Context, songId: String): File {
            val directory = getPersistentMediaDirectory(context.applicationContext, "media_artwork")
            return File(directory, cacheMetadataEntryKey("artwork:" + songId) + ".jpg")
        }

        @JvmStatic
        fun storeCachedMediaArtwork(context: Context, songId: String, bitmap: Bitmap): Boolean {
            if (songId.isBlank()) return false
            return try {
                val bytes = java.io.ByteArrayOutputStream().use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) return false
                    output.toByteArray()
                }
                storeCachedMediaArtworkBytes(context, songId, bytes)
            } catch (error: Exception) {
                Log.w(TAG, "Unable to persist cached artwork", error)
                false
            }
        }

        /** Persist an already encoded JPEG cover, including covers obtained by the downloader. */
        @JvmStatic
        fun storeCachedMediaArtworkBytes(context: Context, songId: String, jpegBytes: ByteArray): Boolean {
            if (songId.isBlank() || jpegBytes.isEmpty()) return false
            val file = getCachedMediaArtworkFile(context, songId)
            val directory = file.parentFile ?: return false
            if (!directory.exists() && !directory.mkdirs()) return false
            val staged = File(directory, file.name + ".tmp")
            return try {
                java.io.FileOutputStream(staged).use { output ->
                    output.write(jpegBytes)
                    output.flush()
                    output.fd.sync()
                }
                if (!staged.isFile || staged.length() <= 0L) {
                    staged.delete()
                    return false
                }
                if (file.exists() && !file.delete()) {
                    staged.delete()
                    return false
                }
                if (!staged.renameTo(file)) {
                    staged.delete()
                    false
                } else {
                    true
                }
            } catch (error: Exception) {
                staged.delete()
                Log.w(TAG, "Unable to persist cached artwork bytes", error)
                false
            }
        }

        private fun cacheMetadataEntryKey(cacheKey: String): String {
            val bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(cacheKey.toByteArray(Charsets.UTF_8))
            val hex = "0123456789abcdef"
            val out = StringBuilder(bytes.size * 2)
            bytes.forEach { value ->
                val unsigned = value.toInt() and 0xff
                out.append(hex[unsigned ushr 4]).append(hex[unsigned and 0x0f])
            }
            return CACHE_METADATA_ENTRY_PREFIX + out.toString()
        }

        /**
         * Removes all cached stream variants belonging to one song without affecting other songs.
         * The exact cache URI is also matched so entries with missing metadata can still be removed.
         */
        @JvmStatic
        @Synchronized
        fun removeMediaCacheForSong(
            context: Context,
            songId: String,
            videoId: String = "",
            cacheUri: String = ""
        ): Int {
            val appContext = context.applicationContext
            val currentCache = getCache(appContext)
            val metadataPrefs = appContext.getSharedPreferences(
                PREF_MEDIA_CACHE_METADATA,
                Context.MODE_PRIVATE
            )
            val metadataEditor = metadataPrefs.edit()
            val metadataKeysToRemove = metadataPrefs.all.mapNotNull { (preferenceKey, rawValue) ->
                val metadata = parseCachedMediaMetadata(rawValue as? String) ?: return@mapNotNull null
                val matchesSong = songId.isNotBlank() && metadata.songId == songId
                val matchesVideo = videoId.isNotBlank() && metadata.videoId == videoId
                if (matchesSong || matchesVideo) preferenceKey else null
            }.toMutableSet()
            if (cacheUri.isNotBlank()) {
                metadataKeysToRemove.add(cacheMetadataEntryKey(cacheUri))
            }

            val cacheKeysToRemove = currentCache.keys.toList().filter { key ->
                val metadata = getCachedMediaMetadata(appContext, key)
                (songId.isNotBlank() && metadata?.songId == songId) ||
                    (videoId.isNotBlank() && metadata?.videoId == videoId) ||
                    (cacheUri.isNotBlank() && key == cacheUri)
            }.toSet()

            // Block keys before deleting spans. If the currently playing stream still has an
            // open CacheDataSource, later chunks for that same URL must not recreate the entry.
            cacheEvictor?.blockKeys(currentCache, cacheKeysToRemove)
            activeService?.cancelCurrentTrackCacheForKeys(cacheKeysToRemove)

            var removedResources = 0
            cacheKeysToRemove.forEach { key ->
                try {
                    currentCache.removeResource(key)
                    removedResources++
                } catch (error: Exception) {
                    Log.w(TAG, "Could not remove cached resource for song $songId", error)
                }
            }
            metadataKeysToRemove.forEach(metadataEditor::remove)
            metadataEditor.apply()

            if (songId.isNotBlank()) {
                getCachedMediaArtworkFile(appContext, songId).delete()
            }
            cacheEvictor?.refresh(currentCache)
            if (removedResources > 0 || metadataKeysToRemove.isNotEmpty()) {
                notifyMediaCacheChanged()
                activeService?.onCachedSongRemoved(songId, videoId, cacheKeysToRemove)
            }
            return removedResources
        }

        /**
         * Removes only the supplied stream URLs. Temporary video cleanup must not remove
         * normal audio cache variants or the track's saved artwork.
         */
        @JvmStatic
        @Synchronized
        fun removeMediaCacheResources(context: Context, cacheUris: Set<String>): Int {
            if (cacheUris.isEmpty()) return 0
            val appContext = context.applicationContext
            val currentCache = getCache(appContext)
            val cachedKeys = currentCache.keys
            val metadataEditor = appContext.getSharedPreferences(
                PREF_MEDIA_CACHE_METADATA,
                Context.MODE_PRIVATE
            ).edit()
            var removedResources = 0

            cacheUris.asSequence()
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { key ->
                    if (key !in cachedKeys) {
                        metadataEditor.remove(cacheMetadataEntryKey(key))
                        return@forEach
                    }
                    try {
                        currentCache.removeResource(key)
                        metadataEditor.remove(cacheMetadataEntryKey(key))
                        removedResources++
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not remove ended video stream cache", error)
                    }
                }

            metadataEditor.apply()
            // SimpleCache reports each removed span to AdjustableCacheEvictor.onSpanRemoved.
            // Avoid refresh(), which would rescan every cache key on disk.
            return removedResources
        }

        @Synchronized
        fun clearMediaCache(context: Context) {
            val appContext = context.applicationContext
            val metadata = appContext.getSharedPreferences(
                PREF_MEDIA_CACHE_METADATA,
                Context.MODE_PRIVATE
            )
            val currentCache = cache
            val service = activeService
            service?.cancelCurrentTrackCache()

            val keys = currentCache?.keys?.toSet().orEmpty()
            val activeUri = runCatching {
                service?.takeIf { it.isPlayerInitialized }
                    ?.player?.currentMediaItem?.localConfiguration?.uri?.toString()
            }.getOrNull()
            val blockedKeys = keys + listOfNotNull(activeUri)
            currentCache?.let { cacheEvictor?.blockKeys(it, blockedKeys) }

            metadata.edit().clear().commit()
            // Artwork is part of the managed song cache, not the separate image cache.
            File(appContext.filesDir, "media_artwork").deleteRecursively()
            File(appContext.cacheDir, "media_artwork").deleteRecursively()

            if (currentCache == null) {
                File(appContext.filesDir, "media_cache").deleteRecursively()
                File(appContext.cacheDir, "media_cache").deleteRecursively()
            } else {
                currentCache.keys.toList().forEach { key ->
                    try {
                        currentCache.removeResource(key)
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not clear one cached resource", error)
                    }
                }
                cacheEvictor?.refresh(currentCache)
            }

            notifyMediaCacheChanged()
            service?.onMediaCacheCleared(blockedKeys)
        }
    }

    private val binder: IBinder = LocalBinder()
    private val serviceScope = MainScope()
    internal lateinit var player: ExoPlayer

    // Service callbacks must return quickly on older devices. Disk-cache initialization is
    // performed on IO before ExoPlayer is built; local UI binding remains available meanwhile.
    private var isPlayerInitialized = false
    private val playerReadyCallbacks = mutableListOf<() -> Unit>()
    private var pendingViewModel: PlayerSharedViewModel? = null
    private val pendingServiceActions = mutableListOf<String>()
    private var pendingPlaybackRequest: PendingPlaybackRequest? = null
    private var pendingSeekToPosition: Long? = null

    private data class PendingPlaybackRequest(
        val uri: Uri,
        val startPosition: Long,
        val autoPlay: Boolean
    )
    private var mediaSession: MediaSession? = null
    // Connection callbacks are on the application looper. This counter is diagnostic only;
    // it helps identify rapid controller reconnects without doing any additional IPC.
    private var mediaSessionConnectionRequestCount = 0
    private var lastMediaSessionLayoutSignature: String? = null
    private var viewModel: PlayerSharedViewModel? = null
    // Minimal persisted song/queue snapshot used when widget actions cold-start the service
    // after MediaSessionService has stopped normally on a paused task removal.
    private var widgetRestoredSong: SongItem? = null
    private var widgetRestoredQueue: List<SongItem> = emptyList()
    private var widgetRestoredIndex: Int = 0
    private var widgetLyricsObserverJob: Job? = null
    private var widgetPlainLyricsObserverJob: Job? = null
    private var widgetSongObserverJob: Job? = null
    @Volatile private var widgetLyricsRequestedSongId: Long? = null
    @Volatile private var widgetSyncedLyrics: List<LyricLine> = emptyList()
    @Volatile private var widgetPlainLyrics: String? = null
    @Volatile private var lyricsWidgetInstalledCached = false
    @Volatile private var lastLyricsWidgetInstalledCheckAtMs = 0L
    private var widgetLyricUpdateRunnable: Runnable? = null
    private var widgetLyricScheduledBoundaryMs: Long? = null
    private lateinit var handler: Handler
    // The Handler below does slower playback housekeeping; position snapshots use vsync.
    private lateinit var progressUpdateRunnable: Runnable
    private var progressFrameUpdatesRunning = false
    private var lastUiFrameBucket = Long.MIN_VALUE
    private var screenWakeReceiverRegistered = false
    private var pendingWidgetRefreshAfterScreenWake = false
    private val widgetRefreshAfterWakeRunnable = Runnable {
        if (isPlayerInitialized) {
            // Re-send the full RemoteViews state after keyguard/screen wake. This repairs
            // stale widget buttons, text, artwork references and progress on launchers that
            // defer or drop RemoteViews updates while the display is asleep.
            updateWidgetState(player.isPlaying)
            lastWidgetProgressUpdateAtMs = 0L
            // Follow with a lightweight control/progress update once the launcher has
            // finished restoring its widget host after keyguard dismissal.
            handler.removeCallbacks(widgetWakeFollowUpRunnable)
            handler.postDelayed(widgetWakeFollowUpRunnable, 1_200L)
        }
    }
    private val widgetWakeFollowUpRunnable = Runnable {
        if (isPlayerInitialized) {
            val isPlaying = player.isPlaying
            val position = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration.coerceAtLeast(0L)
            if (DsmusicWidgetProvider.hasAnyWidgets(applicationContext)) {
                DsmusicWidgetProvider.refreshAllWidgetControls(this, isPlaying)
                updateWidgetProgress(position, duration)
            }
        }
    }
    private val screenWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> scheduleWidgetRefreshAfterScreenWake()
            }
        }
    }
    private val progressFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!progressFrameUpdatesRunning || !isPlayerInitialized || !player.isPlaying) {
                progressFrameUpdatesRunning = false
                lastUiFrameBucket = Long.MIN_VALUE
                return
            }

            // Match Compose's absolute-vsync bucket calculation and AppSettings preference.
            // Device (0) emits every display frame; fixed modes cap snapshots to that rate.
            val selectedFrameRate = AppAnimationFrameRateController.frameRate.value
            val intervalNanos = if (selectedFrameRate > 0) {
                1_000_000_000L / selectedFrameRate
            } else {
                0L
            }
            val frameBucket = if (intervalNanos > 0L) frameTimeNanos / intervalNanos else frameTimeNanos
            if (intervalNanos == 0L || frameBucket != lastUiFrameBucket) {
                val positionMs = getStablePlaybackPositionMs()
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                viewModel?.setCurrentPosition(positionMs)
                lastUiFrameBucket = frameBucket
            }

            if (progressFrameUpdatesRunning && player.isPlaying) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                progressFrameUpdatesRunning = false
                lastUiFrameBucket = Long.MIN_VALUE
            }
        }
    }

    private var currentSourceUri: Uri? = null
    private var originalSourceUri: Uri? = null
    private var isNextSongPreCached = false

    // Cancellable, generation-checked work for speculative next-track queue insertion.
    private var nextQueuePreloadGeneration: Long = 0L
    private var nextQueuePreloadJob: Job? = null
    private var scheduledQueuePreload: Runnable? = null

    // Complete the active remote track in SimpleCache, not just ExoPlayer's short playback buffer.
    private var fullTrackCacheJob: Job? = null
    @Volatile private var fullTrackCacheKey: String? = null
    // After a user explicitly removes/clears a playing track, do not immediately refill it.
    @Volatile private var cacheSuppressedSongId: String? = null
    @Volatile private var activeFullTrackCacheWriter: CacheWriter? = null
    private var cacheAssetsSongId: Long? = null
    @Volatile private var activeVideoCacheVideoId: String? = null
    @Volatile private var activeVideoCacheUris: Set<String> = emptySet()
    private var currentSongName = "Quantune"
    private var currentAlbumArt: Bitmap? = null
    private var currentAlbumArtSongId: String? = null
    @Volatile private var artworkRequestGeneration: Long = 0L
    private var artworkLoadTarget: com.bumptech.glide.request.target.CustomTarget<Bitmap>? = null
    private var artworkDecodeJob: Job? = null
    private var artworkProcessingJob: Job? = null
    private var artworkRetryJob: Job? = null
    private val artworkRetryDelaysMs = longArrayOf(
        2_000L, 5_000L, 12_000L, 25_000L, 45_000L, 60_000L, 60_000L, 60_000L
    )
    private var lastWidgetProgressUpdateAtMs: Long = 0L
    private var lastPositionPersistedAtMs: Long = 0L
    private var lastTelemetryUpdateAtMs: Long = 0L
    private var shouldAutoPlay = false
    private var pendingSeekPos: Long = -1

    // Media3 can briefly report 0 while a remote stream buffers, recovers, or swaps
    // audio/video sources. Keep the last valid head for the selected song until an
    // explicit seek or a real song change establishes a new position.
    private var stablePositionTrackKey: String? = null
    private var stablePlaybackPositionMs: Long = 0L
    
    // Instant cancellation of stale stream resolutions — prevents race conditions on rapid clicks.
    private var resolveJob: Job? = null
    // Distinguishes repeated selections of the same ID (A → B → A).
    private var playbackResolveGeneration: Long = 0L
    // Track the unique song ID that the current resolution is targeting.
    private var resolveTargetSongId: String? = null
    private var lastPlaybackRecoveryVideoId: String? = null
    private var failedStreamVideoId: String? = null
    private var forceWebRemixOnNextResolveVideoId: String? = null
    private var forceCompatibleFallbackOnNextResolveVideoId: String? = null
    private var playbackRecoveryStage = 0
    private var isRecoveringPlaybackError = false
    @Volatile private var isResolvingVideoMode = false
    private var videoModeResolutionGeneration = 0L
    private var videoModeResolutionJob: Job? = null

    private lateinit var telemetryManager: com.dsann.music.data.innertube.YouTubeTelemetryManager

    private var audioManager: AudioManager? = null
    private var systemOutputRouteMonitor: SystemOutputRouteMonitor? = null
    private val mediaRouteNotificationRefreshRunnable = Runnable { refreshMediaNotification() }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPausedByFocusLoss = false
    private var audioFocusEnabled = true

    private val streamHttpClient: okhttp3.OkHttpClient by lazy {
        com.dsann.music.data.innertube.InnerTubeClient.httpClient.newBuilder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(com.dsann.music.data.stream.YouTubeStreamInterceptor())
            .build()
    }


    private var crossfadeDurationMs = 0
    private var isCrossfading = false
    private var crossfadeNextSongStarted = false
    private var nextPlayer: ExoPlayer? = null

    // Each ExoPlayer owns a dedicated Android audio session. The playback visualizer
    // attaches to the active session only when the user opens its screen.
    private val playerAudioSessionIds = java.util.WeakHashMap<ExoPlayer, Int>()
    private val playbackVisualizerLevelsState = MutableStateFlow(FloatArray(PlaybackVisualizerController.BAR_COUNT) { 0f })
    @Volatile private var playbackVisualizerController: PlaybackVisualizerController? = null
    
    private var currentBaseVolume = 1.0f

    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private var crossfadeAnimator: android.animation.ValueAnimator? = null

    private val mainPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            viewModel?.postIsPlaying(isPlaying)
            // Publish the pause/resume anchor immediately. While playing, the service then
            // publishes position snapshots using the same selected frame rate as Compose.
            viewModel?.setCurrentPosition(
                getStablePlaybackPositionMs()
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
            telemetryManager.onPlaybackStateChanged(isPlaying)
            if (isPlaying) {
                handler.removeCallbacks(progressUpdateRunnable)
                handler.post(progressUpdateRunnable)
                startProgressFrameUpdates()
                scheduleNextWidgetLyricRefresh(getStablePlaybackPositionMs())
            } else {
                cancelWidgetLyricRefresh()
                handler.removeCallbacks(progressUpdateRunnable)
                stopProgressFrameUpdates()
            }
            updateWidgetState(isPlaying)
            if (isPlaying) {
                ensureCurrentTrackCacheAssets()
                startCurrentTrackCache()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            viewModel?.let { vm ->
                if (vm.isShuffleEnabled.value != shuffleModeEnabled) {
                    vm.setShuffleEnabled(shuffleModeEnabled)
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPlaybackStateChanged(state: Int) {
            Log.d(TAG, "Player state=${state} ready=${state == Player.STATE_READY} playWhenReady=${player.playWhenReady} uri=${player.currentMediaItem?.localConfiguration?.uri}")
            val isPlayingState = player.playWhenReady && state == Player.STATE_READY
            viewModel?.postIsPlaying(isPlayingState)

            if (state == Player.STATE_BUFFERING) viewModel?.setLoading(true)
            if (state == Player.STATE_READY) {
                viewModel?.setLoading(false)
                viewModel?.setPlaybackReady(true)
                persistCurrentCacheMimeType()
                if (player.isPlaying) {
                    ensureCurrentTrackCacheAssets()
                    startCurrentTrackCache()
                }
                if (pendingSeekPos >= 0) {
                    // Keep the requested resume point until the replacement media item is READY.
                    // A pre-prepare seek can be discarded by a source transition on some devices.
                    val requestedPosition = pendingSeekPos
                    val duration = player.duration.takeIf {
                        it > 0L && it != C.TIME_UNSET
                    }
                    val targetPosition = duration?.let { requestedPosition.coerceAtMost((it - 1L).coerceAtLeast(0L)) }
                        ?: requestedPosition
                    rememberPlaybackPosition(targetPosition)
                    player.seekTo(targetPosition)
                    pendingSeekPos = -1
                }
            }
            
            if (state == Player.STATE_ENDED) {
                handlePlaybackCompleted()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val sourceUri = player.currentMediaItem?.localConfiguration?.uri ?: currentSourceUri
            Log.e(
                TAG,
                "Player failure: code=${error.errorCodeName} type=${error.javaClass.simpleName} " +
                    "source=$sourceUri cause=${error.cause?.javaClass?.name}: ${error.cause?.message}",
                error
            )
            viewModel?.setIsPlaying(false)

            val song = viewModel?.currentSong?.value
            val videoId = song?.videoId?.takeIf { it.isNotBlank() }

            if (viewModel?.isVideoMode?.value == true && !videoId.isNullOrBlank()) {
                val videoPosition = getStablePlaybackPositionMs()
                viewModel?.setVideoModeState(false)
                viewModel?.setLoading(true)
                Log.w(TAG, "Video decoder/stream failed for $videoId; restoring audio-only playback", error)
                showPlaybackFailure(
                    error,
                    context = "Video playback",
                    action = "switching to audio"
                )
                loadAndPlay("innertube://$videoId".toUri(), videoPosition)
                return
            }
            viewModel?.setLoading(videoId != null && playbackRecoveryStage < 2)
            if (videoId != null && playbackRecoveryStage < 2) {
                if (lastPlaybackRecoveryVideoId != videoId) {
                    lastPlaybackRecoveryVideoId = videoId
                    playbackRecoveryStage = 1
                    forceWebRemixOnNextResolveVideoId = videoId
                    Log.w(TAG, "Playback recovery 1/2: retrying $videoId with a fresh WEB_REMIX URL")
                    showPlaybackFailure(error, action = "trying alternate stream (1/2)")
                } else {
                    playbackRecoveryStage = 2
                    forceCompatibleFallbackOnNextResolveVideoId = videoId
                    Log.w(TAG, "Playback recovery 2/2: retrying $videoId with an AAC-compatible stream")
                    showPlaybackFailure(error, action = "trying compatible stream (2/2)")
                }

                val resumePosition = getStablePlaybackPositionMs()
                handler.postDelayed({
                    if (viewModel?.currentSong?.value?.videoId == videoId) {
                        // Invalidate both application-level and NewPipe URL/player caches before
                        // asking the next resolver. Media3 gets a new URI instead of reusing the
                        // URL that just failed.
                        YouTubeMusic.invalidateStreamUrl(videoId)
                        isRecoveringPlaybackError = true
                        try {
                            loadAndPlay("innertube://$videoId".toUri(), resumePosition)
                        } finally {
                            isRecoveringPlaybackError = false
                        }
                    }
                }, 250)
                return
            }

            viewModel?.setLoading(false)
            showPlaybackFailure(error, action = "automatic recovery exhausted")
            // Keep the failed item selected so the user can retry or inspect the failure.
            // Silently skipping every errored track made a complete stream failure look like
            // the Play button was broken.
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val newPositionMs = newPosition.positionMs.coerceAtLeast(0L)
            val positionSnapshot = if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                // An explicit seek is authoritative, including a seek back to 0:00.
                rememberPlaybackPosition(newPositionMs)
                newPositionMs
            } else {
                getStablePlaybackPositionMs()
            }
            viewModel?.setCurrentPosition(
                positionSnapshot.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
            if (hasLyricsWidgetInstalled()) {
                // Paused seeks do not run the playback progress loop. Update the widget
                // immediately so its displayed line follows the seek just like the player.
                val seekPosition = positionSnapshot
                handler.post {
                    updateWidgetProgress(seekPosition, player.duration.coerceAtLeast(0L))
                    scheduleNextWidgetLyricRefresh(seekPosition)
                }
            }
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                Log.d(TAG, "Auto-transition detected natively")
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                cancelQueuePreload()
                Log.d(TAG, "⚡ NATIVE GAPLESS TRANSITION COMPLETED! Resynchronizing UI.")
                
                // Sleep Timer Interception flags
                var interceptForSleepTimer = false
                viewModel?.let { vm ->
                    if (vm.sleepTimerMode.value == PlayerSharedViewModel.SleepTimerMode.END_OF_SONG) {
                        interceptForSleepTimer = true
                    }
                    if (vm.sleepTimerMode.value == PlayerSharedViewModel.SleepTimerMode.END_OF_QUEUE) {
                        if (vm.currentSongIndex.value == vm.songList.value.size - 1) {
                            interceptForSleepTimer = true
                        }
                    }
                }

                // Maintain a 2-item sliding window queue: 
                // Safely excise the deprecated previous item to purge expired URL reference and free heap memory.
                if (player.mediaItemCount > 1) {
                    player.removeMediaItem(0)
                }

                // Use mediaId-based resync for accuracy in Shuffle Mode
                val transitionedMediaId = mediaItem?.mediaId
                viewModel?.let { vm ->
                    val songs = vm.songList.value
                    
                    // Find exact match by mediaId (stable song identifier)
                    val matchedIdx = if (!transitionedMediaId.isNullOrEmpty()) {
                        songs.indexOfFirst { it.id.toString() == transitionedMediaId }
                    } else {
                        -1
                    }
                    
                    val nextIdx = if (matchedIdx >= 0) matchedIdx else vm.getNextSongIndex()
                    if (nextIdx >= 0) {
                        val nextSong = songs.getOrNull(nextIdx)
                        cleanupEndedVideoCacheIfNeeded(vm.currentSong.value, nextSong)
                        vm.updateSongIndexOnly(nextIdx)
                        // The native queue item has already been prepared by Media3. An
                        // automatic media transition can remain STATE_READY without a fresh
                        // state callback, so explicitly sync the ViewModel's loading state.
                        if (player.playbackState == Player.STATE_READY) {
                            vm.setLoading(false)
                            vm.setPlaybackReady(true)
                        } else {
                            vm.setLoading(player.playbackState == Player.STATE_BUFFERING)
                            vm.setPlaybackReady(false)
                        }
                        vm.postIsPlaying(player.isPlaying)
                        vm.setCurrentPosition(
                            getStablePlaybackPositionMs().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                        vm.setDuration(
                            player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                                ?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
                        )
                        
                        songs.getOrNull(nextIdx)?.let { song ->
                            currentSongName = song.title
                            if (song.thumbnailUrl != null) {
                                setSongMetadataByUrl(song.title, song.thumbnailUrl)
                            } else {
                                setSongMetadataById(song.title, song.albumId)
                            }
                            if (!song.videoId.isNullOrEmpty()) {
                                telemetryManager.onSongChanged(song.videoId!!, song.duration)
                            }
                        }
                        
                        if (interceptForSleepTimer) {
                            pause()
                            vm.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.OFF)
                        }

                        scheduleQueuePreload(300L)
                        // Automatic transitions must checkpoint the newly selected track
                        // and its queue index. Otherwise an app/process restart can restore
                        // the song selected before this one, even though playback advanced.
                        vm.savePlaybackStateImmediately()
                    }
                }
            }
            // Synchronize after the ViewModel has accepted any media-item transition.
            // The helper resets only when the selected song ID changes, not for an
            // audio/video source replacement of the same song.
            viewModel?.setCurrentPosition(
                getStablePlaybackPositionMs().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
            // A user-selected track can replace the current item without changing the
            // playing/paused state, so onIsPlayingChanged is not guaranteed to run.
            cancelCurrentTrackCache()
            cacheAssetsSongId = null
            widgetSyncedLyrics = emptyList()
            widgetPlainLyrics = null
            cancelWidgetLyricRefresh()
            updateWidgetState(player.isPlaying)
            // The ViewModel song observer refetches lyrics when it is attached.
            // The service-only widget fetch is needed only when no Activity ViewModel exists.
            if (viewModel == null && hasLyricsWidgetInstalled()) {
                requestWidgetLyrics()
            }
            if (player.isPlaying) {
                handler.post {
                    ensureCurrentTrackCacheAssets()
                    startCurrentTrackCache()
                }
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.volume = currentBaseVolume
                if (isPausedByFocusLoss) {
                    play()
                    isPausedByFocusLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
                isPausedByFocusLoss = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (player.isPlaying) {
                    pause()
                    isPausedByFocusLoss = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player.isPlaying) player.volume = currentBaseVolume * 0.3f
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        // Populate the widget snapshot asynchronously. Widget-presence checks below are
        // served from atomics and never call AppWidgetManager on the main looper.
        DsmusicWidgetProvider.refreshInstalledWidgetCache(applicationContext, true)
        // No-op for behavior in older Media3 or if unresolved
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        handler = Handler(Looper.getMainLooper())
        registerSystemOutputRouteMonitor()
        registerScreenWakeReceiver()

        telemetryManager = com.dsann.music.data.innertube.YouTubeTelemetryManager(
            httpClient = com.dsann.music.data.innertube.InnerTubeClient.httpClient,
            clientProvider = { com.dsann.music.data.innertube.InnerTubeClient }
        )
        
        // SimpleCache construction scans disk and can block for seconds on Android 6 storage.
        // Do not do that work inside Service.onCreate(), whose startup timeout is strict.
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getCache(applicationContext)
                }

                initializePlayer()
                setupMediaSession()
                loadAudioPreferences()
                setupPreferenceListener()
                AppAnimationFrameRateController.load(applicationContext)
                setupProgressTracking()
                isPlayerInitialized = true
                if (pendingWidgetRefreshAfterScreenWake) {
                    pendingWidgetRefreshAfterScreenWake = false
                    scheduleWidgetRefreshAfterScreenWake()
                }

                pendingViewModel?.let { vm ->
                    pendingViewModel = null
                    setViewModel(vm)
                }

                val readyCallbacks = playerReadyCallbacks.toList()
                playerReadyCallbacks.clear()
                readyCallbacks.forEach { callback ->
                    try {
                        callback()
                    } catch (error: Exception) {
                        Log.e(TAG, "Player-ready callback failed", error)
                    }
                }

                pendingPlaybackRequest?.let { request ->
                    pendingPlaybackRequest = null
                    if (request.autoPlay) {
                        loadAndPlay(request.uri, request.startPosition)
                    } else {
                        loadAudio(request.uri, request.startPosition)
                    }
                }
                pendingSeekToPosition?.let { position ->
                    pendingSeekToPosition = null
                    rememberPlaybackPosition(position)
                    player.seekTo(position)
                }

                val actions = pendingServiceActions.toList()
                pendingServiceActions.clear()
                actions.forEach(::handleServiceAction)
                Log.i(TAG, "Playback service initialized asynchronously")
            } catch (error: Exception) {
                Log.e(TAG, "Asynchronous playback service initialization failed", error)
            }
        }
    }

    /**
     * Run code that needs ExoPlayer only after its disk cache and player are ready.
     * Callbacks run on the service main thread.
     */
    fun runWhenPlayerReady(callback: () -> Unit) {
        if (isPlayerInitialized) {
            callback()
        } else {
            playerReadyCallbacks.add(callback)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            if (isPlayerInitialized) {
                handleServiceAction(action)
            } else {
                // Android can deliver media-button/notification actions while disk setup is running.
                pendingServiceActions.add(action)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleServiceAction(action: String) {
        when {
            action == ACTION_WIDGET_TOGGLE_PLAY_PAUSE -> {
                DsmusicWidgetProvider.animateSmallWidgetArtwork(applicationContext)
                if (player.isPlaying || player.playWhenReady) pause() else play()
            }
            action == ACTION_PLAY -> play()
            action == ACTION_PAUSE -> pause()
            action == ACTION_PREVIOUS -> playPrevious()
            action == ACTION_NEXT -> playNext()
            action == ACTION_TOGGLE_LOOP -> {
                viewModel?.toggleLoopMode()
                if (isPlayerInitialized) updateWidgetState(player.isPlaying)
            }
            action == ACTION_CYCLE_PLAYBACK_MODE -> cycleNotificationPlaybackMode()
            action == ACTION_TOGGLE_FAV -> viewModel?.toggleFavorite()
            action.startsWith(ACTION_SESSION_CUSTOM_PREFIX) ->
                handleSessionAction(action.removePrefix(ACTION_SESSION_CUSTOM_PREFIX))
            action == ACTION_WIDGET_FETCH_LYRICS -> requestWidgetLyrics()
            action == ACTION_WIDGET_REFRESH_STATE -> {
                if (player.currentMediaItem == null && viewModel == null) {
                    // Rebuild the widget's labels/artwork from disk without resolving a stream
                    // or starting network work just because a widget is being refreshed.
                    loadRestoredWidgetSnapshot()
                }
                updateWidgetState(player.isPlaying)
                // Start the lyric pipeline whenever a lyrics-capable widget is installed.
                // Its refresh loop must not depend on another widget's lyrics switch state.
                if (hasLyricsWidgetInstalled(forceRefresh = true)) {
                    requestWidgetLyrics()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (DsmusicWidgetProvider.hasAnyWidgets(applicationContext) && isPlayerInitialized) {
            // MediaSessionService stops itself when a paused task is removed. Persist the
            // selected song, queue and exact playback head before that normal teardown.
            val position = getStablePlaybackPositionMs()
            viewModel?.setCurrentPosition(
                position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
            if (viewModel != null) {
                viewModel?.savePlaybackStateImmediately()
            } else {
                widgetRestoredSong?.let { song ->
                    com.dsann.music.util.PlaybackStateManager.saveState(
                        applicationContext,
                        song,
                        position,
                        widgetRestoredQueue.ifEmpty { listOf(song) },
                        widgetRestoredIndex
                    )
                }
            }
            com.dsann.music.util.PlaybackStateManager.savePosition(applicationContext, position)
        }
        // Respect Media3's task-removal lifecycle. Widget PendingIntents restore paused
        // playback on demand instead of keeping a paused MediaSessionService alive.
        super.onTaskRemoved(rootIntent)
    }

    fun dismissService() {
        player.stop()
        player.clearMediaItems()
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackVisualizerController?.stop()
        playbackVisualizerController = null
        handler.removeCallbacks(mediaRouteNotificationRefreshRunnable)
        systemOutputRouteMonitor?.stop()
        systemOutputRouteMonitor = null
        prefListener?.let {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(it)
        }
        if (::progressUpdateRunnable.isInitialized) {
            handler.removeCallbacks(progressUpdateRunnable)
        }
        stopProgressFrameUpdates()
        cancelQueuePreload()
        handler.removeCallbacks(widgetRefreshAfterWakeRunnable)
        handler.removeCallbacks(widgetWakeFollowUpRunnable)
        if (screenWakeReceiverRegistered) {
            runCatching { unregisterReceiver(screenWakeReceiver) }
            screenWakeReceiverRegistered = false
        }
        abandonAudioFocus()
        cancelCurrentTrackCache()
        beginArtworkRequest()

        if (isPlayerInitialized) {
            viewModel?.cleanupBgScope()
            player.removeListener(mainPlayerListener)
            player.release()
            nextPlayer?.release()
        }
        mediaSession?.release()
        playerReadyCallbacks.clear()
        serviceScope.cancel()
        if (activeService === this) activeService = null
    }

    @OptIn(UnstableApi::class)
    private fun configurePlayerAudio(target: ExoPlayer) {
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        target.setAudioAttributes(attrs, audioFocusEnabled)
        target.setWakeMode(C.WAKE_MODE_NETWORK)
        target.skipSilenceEnabled = false

        // setAudioAttributes() can reset Media3's AudioTrack and clear its session.
        // Keep one stable app-owned ID per ExoPlayer, but reapply it after EVERY audio
        // attribute update. loadAudioPreferences() and preference changes both call
        // this method, so guarding on containsKey() here can silently detach the effect.
        val sessionId = playerAudioSessionIds[target] ?: runCatching {
            audioManager?.generateAudioSessionId() ?: 0
        }.getOrDefault(0)

        if (sessionId > 0) {
            try {
                target.setAudioSessionId(sessionId)
                playerAudioSessionIds[target] = sessionId
            } catch (error: Exception) {
                Log.w(TAG, "Could not reapply the dedicated audio session for visualization", error)
            }
        } else {
            Log.w(TAG, "Could not create a dedicated audio session for visualization")
        }
    }

    private fun initializePlayer() {
        player = buildExoPlayer()
        configurePlayerAudio(player)
        player.addListener(mainPlayerListener)
    }

    /**
     * Use Media3's stock audio renderer/sink for maximum decoder and output compatibility.
     * Custom PCM processing is deliberately kept out of the critical audio path: a failure
     * in a visualizer/haptics processor must never prevent both local and remote tracks.
     */
    @OptIn(UnstableApi::class)
    private fun createCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
        // Keep playback and full-track caching on the exact same upstream/interceptor path.
        val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
        return CacheDataSource.Factory()
            .setCache(getCache(context.applicationContext))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun buildExoPlayer(): ExoPlayer {
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val cacheDataSourceFactory = createCacheDataSourceFactory(this)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        // Leave load control at Media3's tested defaults instead of forcing unusually long
        // buffers for local files and short tracks.
        val builtPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        return builtPlayer
    }

    fun setViewModel(vm: PlayerSharedViewModel) {
        if (!isPlayerInitialized) {
            pendingViewModel = vm
            return
        }
        val oldVm = this.viewModel
        if (oldVm != null && oldVm !== vm) {
            // Memory Leak Extermination: 
            // The old ViewModel instance became a zombie when the Activity was destroyed.
            // Before discarding it, copy its queue and playback state to the new UI ViewModel.
            vm.restoreStateFrom(oldVm)
            oldVm.cleanupBgScope()
        }
        this.viewModel = vm
        val lyricsWidgetPresent = hasLyricsWidgetInstalled(forceRefresh = true)
        if (player.isPlaying) startProgressFrameUpdates()

        // Do not rely only on a future StateFlow emission: the first song may already
        // be selected/restored before this service attaches to the Activity ViewModel.
        if (lyricsWidgetPresent) {
            vm.currentSong.value?.let { song ->
                ensureWidgetLyricsForSong(vm, song, refreshWidgetDetection = false)
            }
        }

        // Retain the lyric timeline in the service so widgets stay live even after
        // the Activity is gone. The same sorted timeline drives full-screen lyric timing.
        widgetLyricsObserverJob?.cancel()
        widgetLyricsObserverJob = serviceScope.launch {
            vm.syncedLyrics.collect { lyrics ->
                widgetSyncedLyrics = lyrics.orEmpty().sortedBy { widgetLyricStartMs(it) }
                if (widgetSyncedLyrics.isNotEmpty()) widgetPlainLyrics = null
                else if (vm.plainLyrics.value.isNullOrBlank()) widgetPlainLyrics = null

                val position = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration.coerceAtLeast(0L)
                if (hasLyricsWidgetInstalled()) {
                    updateWidgetProgress(position, duration)
                    scheduleNextWidgetLyricRefresh(position)
                }
            }
        }
        widgetPlainLyricsObserverJob?.cancel()
        widgetPlainLyricsObserverJob = serviceScope.launch {
            vm.plainLyrics.collect { plain ->
                if (vm.syncedLyrics.value.isNullOrEmpty()) {
                    widgetPlainLyrics = plain?.takeIf { it.isNotBlank() }
                    if (hasLyricsWidgetInstalled() && !plain.isNullOrBlank()) {
                        val position = player.currentPosition.coerceAtLeast(0L)
                        updateWidgetProgress(position, player.duration.coerceAtLeast(0L))
                    }
                }
            }
        }
        widgetSongObserverJob?.cancel()
        widgetSongObserverJob = serviceScope.launch {
            // Treat the first StateFlow value as an attach-time snapshot. Seeding this
            // from vm.currentSong.value can swallow that first value if a track was
            // already selected/restored before the collector starts, leaving the widget
            // without a lyric request until the next track change.
            var previousSongId: Long? = null
            vm.currentSong.collect { song ->
                if (song?.id != previousSongId) {
                    previousSongId = song?.id

                    // Read the ViewModel's current lyric snapshot instead of unconditionally
                    // clearing it. The synced/plain observers may already have delivered
                    // their initial values before this song observer gets scheduled.
                    widgetSyncedLyrics = vm.syncedLyrics.value.orEmpty()
                        .sortedBy { widgetLyricStartMs(it) }
                    widgetPlainLyrics = vm.plainLyrics.value?.takeIf { it.isNotBlank() }
                    cancelWidgetLyricRefresh()

                    if (song != null) {
                        ensureWidgetLyricsForSong(
                            vm = vm,
                            song = song,
                            refreshWidgetDetection = true
                        )
                    }
                }
            }
        }

        player.shuffleModeEnabled = vm.isShuffleEnabled.value

        // Dynamic observations to trigger custom layout updates whenever loop/fav changes
        serviceScope.launch {
            vm.repeatMode.collect {
                updateMediaSessionCustomLayout()
                if (isPlayerInitialized) updateWidgetState(player.isPlaying)
            }
        }
        serviceScope.launch {
            vm.isCurrentSongFavorite.collect {
                updateMediaSessionCustomLayout()
            }
        }
        serviceScope.launch {
            vm.isShuffleEnabled.collect { enabled ->
                if (player.shuffleModeEnabled != enabled) {
                    player.shuffleModeEnabled = enabled
                }
                updateMediaSessionCustomLayout()
            }
        }

        // The service can begin playback before the Activity binds its ViewModel (for example,
        // after a notification/widget action). Resume cover/lyrics preparation when it binds.
        if (player.isPlaying) {
            cacheAssetsSongId = null
            ensureCurrentTrackCacheAssets()
            startCurrentTrackCache()
        }
    }

    @JvmOverloads
    fun loadAudio(uri: Uri, startPosition: Long = -1) {
        cancelQueuePreload()
        if (!isPlayerInitialized) {
            pendingPlaybackRequest = PendingPlaybackRequest(uri, startPosition, autoPlay = false)
            return
        }
        shouldAutoPlay = false
        pendingSeekPos = startPosition
        resolveAndLoad(uri)
    }

    /**
     * Publish the selected track to MediaSession before network stream resolution starts.
     * The temporary URI is never prepared or played; it only carries the new song metadata
     * so Android's notification changes immediately while the actual audio URL is loading.
     */
    private fun publishPendingTrackToMediaSession() {
        if (!isPlayerInitialized) return
        val song = viewModel?.currentSong?.value ?: return
        val songId = song.id.toString()
        val albumTitle = song.playbackContextName?.takeIf { it.isNotBlank() }
            ?: song.albumName?.takeIf { it.isNotBlank() }
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setDisplayTitle(song.title)
            .setArtist(song.artist.ifBlank { "Unknown Artist" })
            .apply {
                if (!albumTitle.isNullOrBlank()) setAlbumTitle(albumTitle)
                song.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { setArtworkUri(it.toUri()) }
            }
            .build()
        val pendingItem = MediaItem.Builder()
            .setMediaId(songId)
            .setUri(Uri.parse("dsmusic-pending://$songId"))
            .setMediaMetadata(metadata)
            .build()

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex != C.INDEX_UNSET && currentIndex in 0 until player.mediaItemCount) {
            player.replaceMediaItem(currentIndex, pendingItem)
        } else {
            player.setMediaItem(pendingItem)
        }

        currentSongName = song.title
        // Keep the already loaded cover when the same song is being re-resolved after
        // buffering or a stream error. Clearing it here makes the notification lose its
        // artwork even though the player still has that song's cover available.
        // A different song must never inherit the previous song's notification artwork.
        if (currentAlbumArtSongId != songId) {
            currentAlbumArt = null
            currentAlbumArtSongId = null
        }
        refreshMediaNotification()
    }

    @JvmOverloads
    fun loadAndPlay(uri: Uri, startPosition: Long = -1) {
        cancelQueuePreload()
        if (!isPlayerInitialized) {
            pendingPlaybackRequest = PendingPlaybackRequest(uri, startPosition, autoPlay = true)
            viewModel?.setLoading(true)
            return
        }
        // A user-initiated track load permits one new recovery attempt. The automatic
        // recovery itself must not reset its guard, or a broken URL can retry forever.
        if (!isRecoveringPlaybackError) {
            lastPlaybackRecoveryVideoId = null
            playbackRecoveryStage = 0
            forceWebRemixOnNextResolveVideoId = null
            forceCompatibleFallbackOnNextResolveVideoId = null
        }
        // Stop audible output immediately but retain the current MediaItem/session
        // notification while the replacement stream is resolving. The new MediaItem
        // replaces it only when loadAudioInternal has a usable source.
        player.playWhenReady = false
        player.pause()
        // Change the system notification to the selected song now, before the network
        // resolver runs. loadAudioInternal later replaces this placeholder with the real URI.
        publishPendingTrackToMediaSession()
        if (isCrossfading) {
            nextPlayer?.playWhenReady = false
            nextPlayer?.stop()
            nextPlayer?.release()
            nextPlayer = null
            crossfadeAnimator?.cancel()
            crossfadeAnimator = null
            isCrossfading = false
            crossfadeNextSongStarted = false
            player.volume = currentBaseVolume.coerceIn(0f, 1f)
        }
        shouldAutoPlay = true
        pendingSeekPos = startPosition
        resolveAndLoad(uri)
    }

    /**
     * Centralized execution gate resolving dynamic identifiers OUTSIDE the buffer pipeline
     * prior to injecting raw HTTP/File resource pointers into ExoPlayer.
     */
    private fun resolveAndLoad(uri: Uri) {
        val requestGeneration = ++playbackResolveGeneration
        resolveJob?.cancel()
        resolveJob = null
        resolveTargetSongId = null
        viewModel?.setLoading(true)
        originalSourceUri = uri
        val selectedSong = viewModel?.currentSong?.value
        val staleCachedItem = selectedSong?.takeIf {
            it.isCachedTrack &&
                it.contentUri == uri &&
                (uri.scheme == "http" || uri.scheme == "https")
        }
        if (staleCachedItem != null) {
            // Cache playlist rows intentionally carry their exact HTTP cache key. Verify that
            // the full resource still exists before selecting an offline-only MediaSource.
            resolveTargetSongId = staleCachedItem.videoId ?: staleCachedItem.id.toString()
            resolveJob = serviceScope.launch {
                try {
                    val stillComplete = withContext(Dispatchers.IO) {
                        getCompleteCachedPlaybackUriForKey(applicationContext, uri.toString()) != null
                    }
                    ensureActive()
                    if (
                        requestGeneration != playbackResolveGeneration ||
                        viewModel?.currentSong?.value?.id != staleCachedItem.id
                    ) return@launch

                    if (stillComplete) {
                        loadAudioInternal(uri, keepPlayingUntilReady = true)
                    } else {
                        val liveUri = staleCachedItem.videoId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "innertube://$it".toUri() }
                            ?: uri
                        viewModel?.refreshCurrentSongSource(
                            staleCachedItem.withPlaybackSource(
                                liveUri,
                                cached = false,
                                downloaded = false
                            )
                        )
                        Log.i(TAG, "Cached entry was removed or incomplete; falling back to online playback")
                        val resumePosition = pendingSeekPos.takeIf { it >= 0L }
                            ?: getStablePlaybackPositionMs()
                        loadAndPlay(liveUri, resumePosition)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (requestGeneration == playbackResolveGeneration) {
                        Log.w(TAG, "Could not validate cached playback source; retrying online", error)
                        val liveUri = staleCachedItem.videoId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "innertube://$it".toUri() }
                            ?: uri
                        viewModel?.refreshCurrentSongSource(
                            staleCachedItem.withPlaybackSource(liveUri, cached = false, downloaded = false)
                        )
                        loadAndPlay(liveUri, getStablePlaybackPositionMs())
                    }
                }
            }
        } else if (uri.scheme == "innertube") {
            val videoId = uri.host
            if (videoId.isNullOrBlank()) {
                viewModel?.setLoading(false)
                showPlaybackFailure(
                    IllegalArgumentException("Missing video ID in playback URI"),
                    context = "Stream resolution"
                )
                return
            }
            val useWebRemixRetry = forceWebRemixOnNextResolveVideoId == videoId
            val useCompatibleFallbackRetry = forceCompatibleFallbackOnNextResolveVideoId == videoId
            if (useWebRemixRetry) forceWebRemixOnNextResolveVideoId = null
            if (useCompatibleFallbackRetry) forceCompatibleFallbackOnNextResolveVideoId = null
            
            // requestGeneration is the authoritative guard against late responses.
            resolveTargetSongId = videoId
            
            resolveJob = serviceScope.launch {
              try {
                Log.d(TAG, "Pre-resolving stream ahead of injector injection: $videoId")
                val activeSong = viewModel?.currentSong?.value?.takeIf { it.videoId == videoId }

                // A fully cached stream wins over format/bitrate preferences. Play it through
                // its exact cache key, otherwise an explicit setting can force a network fetch
                // even though another quality/container is already available offline.
                if (!useWebRemixRetry && !useCompatibleFallbackRetry) {

                    // A real downloaded audio file is the highest-priority offline source.
                    // Resolve it by video ID first so even non-default format/bitrate downloads
                    // are used rather than extracting a fresh URL for another online stream.
                    val downloadedUri = activeSong?.let { song ->
                        withContext(Dispatchers.IO) {
                            com.dsann.music.util.SongDownloader.findDownloadedUri(
                                applicationContext,
                                videoId,
                                song.title,
                                song.artist
                            )
                        }
                    }
                    if (downloadedUri != null) {
                        ensureActive()
                        if (requestGeneration != playbackResolveGeneration || resolveTargetSongId != videoId) return@launch
                        viewModel?.refreshCurrentSongSource(
                            activeSong.withPlaybackSource(
                                downloadedUri,
                                cached = false,
                                downloaded = true
                            )
                        )
                        Log.i(TAG, "Using downloaded offline audio for $videoId: $downloadedUri")
                        originalSourceUri = downloadedUri
                        loadAudioInternal(downloadedUri, keepPlayingUntilReady = true)
                        return@launch
                    }

                    val cacheSongId = activeSong?.id?.toString()
                        ?: videoId.hashCode().toLong().toString()
                    val cachedUri = withContext(Dispatchers.IO) {
                        getCachedPlaybackUri(
                            applicationContext,
                            cacheSongId,
                            videoId,
                            requireComplete = true
                        )
                    }
                    if (cachedUri != null) {
                        ensureActive()
                        if (requestGeneration != playbackResolveGeneration || resolveTargetSongId != videoId) return@launch
                        activeSong?.let { song ->
                            viewModel?.refreshCurrentSongSource(
                                song.withPlaybackSource(
                                    cachedUri,
                                    cached = true,
                                    downloaded = false
                                )
                            )
                        }
                        Log.i(TAG, "Using cached stream for $videoId regardless of selected format/bitrate")
                        originalSourceUri = cachedUri
                        loadAudioInternal(cachedUri, keepPlayingUntilReady = true)
                        return@launch
                    }
                }

                // Fetch stream URL only, don't wait for position yet. Cancelling a format-choice
                // dialog is a user decision, not an extraction error: don't auto-switch formats
                // or immediately show the same request again.
                var formatChoiceCancelled = false
                var streamResolutionFailure: Throwable? = null
                fun rememberStreamResult(result: Result<String>): String? {
                    result.exceptionOrNull()?.let { streamResolutionFailure = it }
                    return result.getOrNull()
                }

                var streamUrl = async(Dispatchers.IO) {
                    val result = when {
                        useWebRemixRetry -> YouTubeMusic.getStreamUrlFromWebRemix(videoId)
                        useCompatibleFallbackRetry -> YouTubeMusic.getCompatibleFallbackStreamUrl(videoId)
                        else -> YouTubeMusic.getStreamUrl(videoId, promptForFormatChoice = true)
                    }
                    formatChoiceCancelled =
                        result.exceptionOrNull() is com.dsann.music.data.innertube.AudioFormatSelectionCancelledException
                    rememberStreamResult(result)
                }.await()

                if (formatChoiceCancelled) {
                    ensureActive()
                    if (requestGeneration == playbackResolveGeneration && resolveTargetSongId == videoId) {
                        shouldAutoPlay = false
                        viewModel?.setIsPlaying(false)
                        viewModel?.setLoading(false)
                    }
                    return@launch
                }

                if (streamUrl.isNullOrEmpty()) {
                    when {
                        useWebRemixRetry -> {
                            Log.w(TAG, "WEB_REMIX recovery URL unavailable for $videoId; trying Android/iOS InnerTube")
                            streamUrl = async(Dispatchers.IO) {
                                rememberStreamResult(YouTubeMusic.getStreamUrlFromInnerTube(videoId))
                            }.await()
                            if (streamUrl.isNullOrEmpty()) {
                                streamUrl = async(Dispatchers.IO) {
                                    rememberStreamResult(YouTubeMusic.getStreamUrl(videoId))
                                }.await()
                            }
                        }
                        useCompatibleFallbackRetry -> {
                            Log.e(TAG, "Compatible fallback stream resolution failed for $videoId")
                        }
                        else -> {
                            Log.w(TAG, "Initial stream resolution failed for $videoId; invalidating caches and retrying once")
                            withContext(Dispatchers.IO) { YouTubeMusic.invalidateStreamUrl(videoId) }
                            delay(250L)
                            streamUrl = async(Dispatchers.IO) {
                                rememberStreamResult(YouTubeMusic.getStreamUrl(videoId))
                            }.await()
                        }
                    }
                }

                // Guard: If user clicked another song while we were resolving, abort silently
                ensureActive()
                if (requestGeneration != playbackResolveGeneration || resolveTargetSongId != videoId) {
                    Log.d(TAG, "Stale resolution for $videoId discarded (target changed to $resolveTargetSongId)")
                    return@launch
                }
                
                if (!streamUrl.isNullOrEmpty()) {
                    failedStreamVideoId = null
                    activeSong?.let { song ->
                        val resolverUri = "innertube://$videoId".toUri()
                        val sourceChanged = song.isCachedTrack ||
                            song.isDownloadedOffline ||
                            song.contentUri != resolverUri
                        if (sourceChanged) {
                            viewModel?.refreshCurrentSongSource(
                                song.withPlaybackSource(
                                    resolverUri,
                                    cached = false,
                                    downloaded = false
                                )
                            )
                        }
                    }
                    loadAudioInternal(streamUrl.toUri(), keepPlayingUntilReady = true)
                    
                    // Asynchronously fetch and apply resume position with a 2-second timeout
                    if (pendingSeekPos <= 0) {
                        launch(Dispatchers.IO) {
                            val position = kotlinx.coroutines.withTimeoutOrNull(2000L.milliseconds) {
                                YouTubeMusic.getPlaybackPosition(videoId).getOrNull() ?: 0L
                            } ?: return@launch
                            
                            if (position > 2000L) {
                                withContext(Dispatchers.Main) {
                                    if (
                                        requestGeneration != playbackResolveGeneration ||
                                        viewModel?.currentSong?.value?.videoId != videoId
                                    ) return@withContext
                                    Log.d(TAG, "Synced cross-device playback position: ${position}ms asynchronously")
                                    pendingSeekPos = position
                                    // If player already started playing from 0, seek it now
                                    if (player.playbackState == Player.STATE_READY) {
                                        rememberPlaybackPosition(position)
                                        player.seekTo(position)
                                        pendingSeekPos = -1
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Failed resolving stream after retry for: $videoId")
                    failedStreamVideoId = videoId
                    shouldAutoPlay = false
                    viewModel?.setIsPlaying(false)
                    viewModel?.setLoading(false)
                    showPlaybackFailure(
                        streamResolutionFailure
                            ?: IllegalStateException("No playable audio stream was returned"),
                        context = "Stream resolution"
                    )
                }
              } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A resolver exception previously escaped without clearing the loading
                    // state. Only the latest request may clear its spinner or publish failure.
                    if (
                        requestGeneration == playbackResolveGeneration &&
                        resolveTargetSongId == videoId
                    ) {
                        Log.e(TAG, "Stream resolution crashed for $videoId", error)
                        failedStreamVideoId = videoId
                        shouldAutoPlay = false
                        viewModel?.setIsPlaying(false)
                        viewModel?.setLoading(false)
                        showPlaybackFailure(error, context = "Stream resolution")
                    }
                }
            }
        } else {
            // Local or pre-determined resource, injection is instant. Preserve the current
            // media-session notification until the new local item is installed.
            resolveJob?.cancel()
            resolveTargetSongId = null
            loadAudioInternal(uri, keepPlayingUntilReady = true)
        }
    }

    /**
     * The Ultimate Solution: Proactively builds the native ExoPlayer queue by resolving the NEXT track
     * in background and adding it to the pipeline BEFORE the current track completes.
     */
    private fun cancelQueuePreload() {
        scheduledQueuePreload?.let { task ->
            if (::handler.isInitialized) handler.removeCallbacks(task)
        }
        scheduledQueuePreload = null
        nextQueuePreloadGeneration += 1L
        nextQueuePreloadJob?.cancel()
        nextQueuePreloadJob = null
    }

    private fun scheduleQueuePreload(delayMs: Long) {
        cancelQueuePreload()
        if (!::handler.isInitialized) return
        val task = Runnable {
            scheduledQueuePreload = null
            preloadNextSongIntoQueue()
        }
        scheduledQueuePreload = task
        handler.postDelayed(task, delayMs)
    }

    private fun isQueuePreloadCurrent(
        generation: Long,
        vm: PlayerSharedViewModel,
        currentSongId: Long,
        queueSnapshot: List<Long>,
        shuffleEnabled: Boolean,
        repeatMode: Int
    ): Boolean {
        return generation == nextQueuePreloadGeneration &&
            vm.currentSong.value?.id == currentSongId &&
            vm.songList.value.map { it.id } == queueSnapshot &&
            vm.isShuffleEnabled.value == shuffleEnabled &&
            vm.repeatMode.value == repeatMode
    }

    /**
     * Resolve the next source in the background, but only inject it if the active song and
     * queue are still exactly the ones this preload was created for.
     */
    private fun preloadNextSongIntoQueue() {
        cancelQueuePreload()
        val vm = viewModel ?: return
        vm.maybeRefillAutoplayQueue()

        val currentSong = vm.currentSong.value ?: return
        val songs = vm.songList.value
        if (songs.isEmpty()) return

        // Capture one shuffle choice; never re-roll while validating this preload.
        val nextIndex = vm.getNextSongIndex()
        if (nextIndex < 0 || nextIndex >= songs.size) {
            if (isAutoplayEnabled()) {
                vm.getAutoplaySeedVideoId()?.takeIf { it.isNotBlank() }
                    ?.let { vm.prefetchAutoplayRecommendations(it) }
            }
            return
        }

        val nextSong = songs[nextIndex]
        val activeSongId = currentSong.id
        val queueSnapshot = songs.map { it.id }
        val shuffleEnabled = vm.isShuffleEnabled.value
        val repeatMode = vm.repeatMode.value
        val generation = nextQueuePreloadGeneration
        Log.d(TAG, "Preparing next-track preload: ${nextSong.title} after ${currentSong.title}")

        nextQueuePreloadJob = serviceScope.launch {
            try {
                var nextUri = nextSong.contentUri
                val targetVideoId = nextSong.videoId
                if (
                    !nextSong.isCachedTrack &&
                    !nextSong.isDownloadedOffline &&
                    !targetVideoId.isNullOrEmpty()
                ) {
                    val resolvedUrl = withContext(Dispatchers.IO) {
                        YouTubeMusic.getStreamUrl(targetVideoId).getOrNull()
                    }
                    ensureActive()
                    if (!isQueuePreloadCurrent(
                            generation, vm, activeSongId, queueSnapshot, shuffleEnabled, repeatMode
                        )
                    ) return@launch
                    if (!resolvedUrl.isNullOrEmpty()) {
                        nextUri = resolvedUrl.toUri()
                        preCacheFirstChunk(resolvedUrl)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (!isQueuePreloadCurrent(
                            generation, vm, activeSongId, queueSnapshot, shuffleEnabled, repeatMode
                        )
                    ) return@withContext
                    if (player.currentMediaItem?.mediaId != activeSongId.toString()) {
                        Log.d(TAG, "Skipped preload because the active Media3 item changed")
                        return@withContext
                    }

                    if (crossfadeDurationMs > 0) {
                        if (nextPlayer == null) {
                            nextPlayer = buildExoPlayer()
                            configurePlayerAudio(nextPlayer!!)
                        }
                        nextPlayer?.stop()
                        nextPlayer?.clearMediaItems()
                        val nextItem = buildMediaItem(
                            nextUri, nextSong.title, nextSong.artist, nextSong.id.toString()
                        )
                        if (nextSong.isCachedTrack) {
                            nextPlayer?.setMediaSource(buildCacheOnlyMediaSource(nextItem))
                        } else {
                            nextPlayer?.setMediaItem(nextItem)
                        }
                        nextPlayer?.prepare()
                        nextPlayer?.playWhenReady = false
                        nextPlayer?.volume = 0f
                        Log.d(TAG, "Preloaded next song into the crossfade player")
                    } else if (player.mediaItemCount < 2) {
                        val nextItem = buildMediaItem(
                            nextUri, nextSong.title, nextSong.artist, nextSong.id.toString()
                        )
                        if (nextSong.isCachedTrack) {
                            player.addMediaSource(buildCacheOnlyMediaSource(nextItem))
                        } else {
                            player.addMediaItem(nextItem)
                        }
                        Log.d(TAG, "Preloaded next song into the native queue")
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Next-track preload failed for ${nextSong.title}; active playback is unchanged", error)
            }
        }
    }

    fun refreshNativeQueue() {
        cancelQueuePreload()
        // Clear out any currently preloaded next song
        if (crossfadeDurationMs > 0) {
            nextPlayer?.stop()
            nextPlayer?.clearMediaItems()
            nextPlayer = null
        } else {
            // Keep the currently playing item (index 0) but remove the next ones
            while (player.mediaItemCount > 1) {
                player.removeMediaItem(1)
            }
        }
        // Force a fresh proactive preload with the newly updated queue
        preloadNextSongIntoQueue()
    }


    /**
     * Prepare the non-audio requirements of a Cache playlist item. The cache folder deliberately
     * contains only tracks with a saved cover and a positive lyrics result.
     */
    private fun ensureCurrentTrackCacheAssets() {
        val vm = viewModel ?: return
        val song = vm.currentSong.value ?: return
        if (song.isDownloadedOffline) return
        if (cacheAssetsSongId == song.id) return
        cacheAssetsSongId = song.id

        val currentUri = if (isPlayerInitialized) player.currentMediaItem?.localConfiguration?.uri else null
        val cacheMetadata = currentUri?.let {
            getCachedMediaMetadata(applicationContext, it.toString())
        }
        val artwork = getCachedMediaArtworkFile(applicationContext, song.id.toString())
        if (artwork.isFile && artwork.length() > 0L) {
            // A cache hit must also hydrate the live MediaSession/notification artwork.
            // Previously the file suppressed a network fetch but was never decoded again.
            setSongMetadataFromCache(song.title, artwork, song.id.toString())
        } else {
            when {
                !song.thumbnailUrl.isNullOrBlank() ->
                    setSongMetadataByUrl(song.title, song.thumbnailUrl)
                !cacheMetadata?.thumbnailUrl.isNullOrBlank() ->
                    setSongMetadataByUrl(song.title, cacheMetadata?.thumbnailUrl)
                !song.videoId.isNullOrBlank() ->
                    setSongMetadataByUrl(song.title, null)
                song.albumId > 0L ->
                    setSongMetadataById(song.title, song.albumId)
            }
        }

        // LyricsFetchManager checks its persistent cache first, so this does not re-download
        // existing lyrics. It fills the lyrics prerequisite automatically during normal playback.
        if (vm.syncedLyrics.value.isNullOrEmpty() &&
            vm.plainLyrics.value.isNullOrBlank() &&
            !vm.isLyricsLoading.value
        ) {
            vm.fetchLyricsForCurrentSong()
        }
    }

    private fun cancelCurrentTrackCacheForKeys(keys: Set<String>) {
        val activeKey = fullTrackCacheKey
        if (!activeKey.isNullOrBlank() && activeKey in keys) {
            cancelCurrentTrackCache()
        }
    }

    private fun onCachedSongRemoved(songId: String, videoId: String, cacheKeys: Set<String>) {
        serviceScope.launch {
            if (!isPlayerInitialized) return@launch
            val song = viewModel?.currentSong?.value ?: return@launch
            val matchesSong = songId.isNotBlank() && song.id.toString() == songId
            val matchesVideo = videoId.isNotBlank() && song.videoId == videoId
            if (!song.isCachedTrack || (!matchesSong && !matchesVideo)) return@launch
            if (cacheKeys.isNotEmpty() && song.contentUri.toString() !in cacheKeys) return@launch

            cacheSuppressedSongId = song.id.toString()
            cancelCurrentTrackCache()
            val resumePosition = getStablePlaybackPositionMs()
            val liveUri = song.videoId?.takeIf { it.isNotBlank() }
                ?.let { "innertube://$it".toUri() }
                ?: song.contentUri
            viewModel?.refreshCurrentSongSource(
                song.withPlaybackSource(liveUri, cached = false, downloaded = false)
            )
            if (player.isPlaying) {
                loadAndPlay(liveUri, resumePosition)
            } else {
                shouldAutoPlay = false
                pendingSeekPos = resumePosition
                resolveAndLoad(liveUri)
            }
        }
    }

    private fun onMediaCacheCleared(blockedKeys: Set<String>) {
        serviceScope.launch {
            if (!isPlayerInitialized) return@launch
            val song = viewModel?.currentSong?.value ?: return@launch
            val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                ?: song.contentUri.toString()
            if (currentUri !in blockedKeys) return@launch

            // A clear operation must not immediately refill the current track, even when it
            // had been streaming online rather than marked as a Cache playlist item.
            cacheSuppressedSongId = song.id.toString()
            cancelCurrentTrackCache()
            if (!song.isCachedTrack) return@launch

            val resumePosition = getStablePlaybackPositionMs()
            val liveUri = song.videoId?.takeIf { it.isNotBlank() }
                ?.let { "innertube://$it".toUri() }
                ?: song.contentUri
            viewModel?.refreshCurrentSongSource(
                song.withPlaybackSource(liveUri, cached = false, downloaded = false)
            )
            if (player.isPlaying) {
                loadAndPlay(liveUri, resumePosition)
            } else {
                shouldAutoPlay = false
                pendingSeekPos = resumePosition
                resolveAndLoad(liveUri)
            }
        }
    }

    private fun cancelCurrentTrackCache() {
        activeFullTrackCacheWriter?.cancel()
        activeFullTrackCacheWriter = null
        fullTrackCacheJob?.cancel()
        fullTrackCacheJob = null
        fullTrackCacheKey = null
    }

    /**
     * ExoPlayer normally caches only the ranges needed for its rolling playback buffer. The Cache
     * playlist requires a complete stream, so finish the current remote resource in the same
     * SimpleCache while playback continues. CacheWriter reuses every range already downloaded by
     * ExoPlayer and stores the remaining bytes under the exact same URI cache key.
     */
    @OptIn(UnstableApi::class)
    private fun startCurrentTrackCache() {
        if (!isPlayerInitialized || !player.isPlaying) return
        if (viewModel?.isVideoMode?.value == true) return
        if (!getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MEDIA_CACHE_ENABLED, true)
        ) return

        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return

        val song = viewModel?.currentSong?.value
        val songIdForCache = song?.id?.toString()
        if (cacheSuppressedSongId != null && cacheSuppressedSongId != songIdForCache) {
            cacheSuppressedSongId = null
        }
        if (songIdForCache != null && cacheSuppressedSongId == songIdForCache) return
        if (song?.isCachedTrack == true || song?.isDownloadedOffline == true) return

        val cacheKey = uri.toString()
        if (fullTrackCacheKey == cacheKey && fullTrackCacheJob?.isActive == true) return

        val songId = item.mediaId.takeIf { it.isNotBlank() }
            ?: song?.id?.toString()?.takeIf { it.isNotBlank() }
            ?: return
        val existingMetadata = getCachedMediaMetadata(applicationContext, cacheKey)
        val title = usableCacheTrackTitle(song?.title)
            ?: usableCacheTrackTitle(item.mediaMetadata.title?.toString())
            ?: usableCacheTrackTitle(existingMetadata?.title)
            ?: return
        val artist = usableCacheTrackArtist(song?.artist)
            ?: usableCacheTrackArtist(item.mediaMetadata.artist?.toString())
            ?: usableCacheTrackArtist(existingMetadata?.artist)
            ?: "Unknown Artist"
        val durationMs = player.duration.takeIf {
            it > 0L && it != C.TIME_UNSET
        } ?: song?.duration ?: 0L
        val videoId = song?.videoId.orEmpty()

        cancelCurrentTrackCache()
        fullTrackCacheKey = cacheKey

        fullTrackCacheJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // Give ExoPlayer time to establish its rolling buffer before a second reader
                // starts filling the full-track cache.
                kotlinx.coroutines.delay(2_000L)
                val canCacheNow = withContext(Dispatchers.Main) {
                    if (!player.isPlaying ||
                        player.currentMediaItem?.mediaId != songId ||
                        fullTrackCacheKey != cacheKey
                    ) {
                        false
                    } else {
                        val bufferedAhead = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                        val remaining = (player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                            ?: durationMs).minus(player.currentPosition).coerceAtLeast(0L)
                        bufferedAhead >= 8_000L || remaining <= 8_000L
                    }
                }
                if (!canCacheNow) {
                    withContext(Dispatchers.Main) {
                        if (fullTrackCacheKey == cacheKey) {
                            fullTrackCacheKey = null
                            handler.postDelayed({
                                if (player.isPlaying && player.currentMediaItem?.mediaId == songId) {
                                    startCurrentTrackCache()
                                }
                            }, 5_000L)
                        }
                    }
                    return@launch
                }
                rememberMediaCacheMetadata(
                    applicationContext,
                    cacheKey,
                    title,
                    artist,
                    songId,
                    durationMs,
                    videoId,
                    albumName = song?.albumName.orEmpty(),
                    thumbnailUrl = song?.thumbnailUrl.orEmpty()
                )

                val existingFacts = getCachedAudioFacts(
                    applicationContext,
                    songId,
                    videoId,
                    cacheKey
                )
                if (existingFacts?.isComplete == true) {
                    Log.d(TAG, "Current track is already fully cached: $title")
                    return@launch
                }

                val writer = CacheWriter(
                    createCacheDataSourceFactory(applicationContext).createDataSourceForDownloading(),
                    DataSpec.Builder().setUri(uri).setKey(cacheKey).build(),
                    null,
                    null
                )

                synchronized(this@AudioPlaybackService) {
                    // A newer song may have replaced this one while metadata/cache checks ran.
                    if (fullTrackCacheKey != cacheKey) {
                        writer.cancel()
                        return@launch
                    }
                    activeFullTrackCacheWriter = writer
                }

                try {
                    writer.cache()
                    val completedFacts = getCachedAudioFacts(
                        applicationContext,
                        songId,
                        videoId,
                        cacheKey
                    )
                    Log.i(
                        TAG,
                        "Full-track cache finished for '$title': " +
                            "${completedFacts?.cachedBytes ?: 0L} bytes, complete=${completedFacts?.isComplete == true}"
                    )
                    if (completedFacts?.isComplete == true) notifyMediaCacheChanged()
                } finally {
                    synchronized(this@AudioPlaybackService) {
                        if (activeFullTrackCacheWriter === writer) {
                            activeFullTrackCacheWriter = null
                        }
                    }
                }
            } catch (error: Exception) {
                if (fullTrackCacheKey == cacheKey) {
                    Log.w(TAG, "Full-track cache failed for '$title': ${error.message}", error)
                }
            }
        }
    }

    /**
     * Pre-cache the first 512KB of the audio stream into SimpleCache for instant playback start.
     */
    private fun preCacheFirstChunk(streamUrl: String) {
        if (!getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MEDIA_CACHE_ENABLED, true)) {
            Log.d(TAG, "Skipping first-chunk pre-cache because audio caching is disabled")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            try {
                val cacheDataSourceFactory = createCacheDataSourceFactory(applicationContext)
                val dataSpec = DataSpec.Builder()
                    .setUri(streamUrl)
                    .setLength(512 * 1024) // 512KB first chunk
                    .build()
                
                val cacheWriter = CacheWriter(
                    cacheDataSourceFactory.createDataSource(),
                    dataSpec,
                    null, // no progress listener
                    null  // no cancellation
                )
                cacheWriter.cache()
                Log.d(TAG, "⚡ First-chunk pre-cached (512KB) for instant start")
            } catch (e: Exception) {
                // Non-critical: pre-caching failure just means normal buffering on play
                Log.w(TAG, "First-chunk pre-cache failed (non-critical): ${e.message}")
            }
        }
    }

    fun play() {
        if (!isPlayerInitialized) {
            pendingServiceActions.add(ACTION_PLAY)
            return
        }
        val activeSong = viewModel?.currentSong?.value
        val failedVideoId = failedStreamVideoId
        if (!failedVideoId.isNullOrBlank() && activeSong?.videoId == failedVideoId) {
            loadAndPlay("innertube://$failedVideoId".toUri(), getStablePlaybackPositionMs())
            return
        }

        if (player.playerError != null) {
            val retryUri = viewModel?.songUri?.value ?: originalSourceUri ?: currentSourceUri
            if (retryUri != null) {
                loadAndPlay(retryUri, getStablePlaybackPositionMs())
                return
            }
        }

        if (player.currentMediaItem == null) {
            val uri = viewModel?.songUri?.value
            if (uri != null) {
                val pos = viewModel?.currentPosition?.value ?: 0
                loadAndPlay(uri, pos.toLong())
                return
            }
            if (viewModel == null && restorePersistedPlaybackForWidget(autoPlay = true)) {
                return
            }
        }
        if (!player.isPlaying) {
            player.play()
            if (isCrossfading) {
                nextPlayer?.play()
                crossfadeAnimator?.resume()
            }
            isPausedByFocusLoss = false
        }
    }

    fun pause() {
        // A remote pause can arrive while a new stream is still resolving. Clear the
        // autoplay intent as well as the player's playWhenReady flag, otherwise the
        // resolved stream can start after this pause has already been processed.
        shouldAutoPlay = false
        pendingPlaybackRequest = pendingPlaybackRequest?.copy(autoPlay = false)

        if (!isPlayerInitialized) {
            // A pause received during asynchronous service startup must supersede any
            // queued Play action, while preserving a later explicit Play tap.
            pendingServiceActions.removeAll { it == ACTION_PLAY }
            if (pendingServiceActions.lastOrNull() != ACTION_PAUSE) {
                pendingServiceActions.add(ACTION_PAUSE)
            }
            return
        }
        if (player.currentMediaItem == null && viewModel == null) {
            // A stale widget may still carry Pause after Android stopped the old service.
            // Playback is already stopped; refresh the saved display state without resolving
            // the stream. The next Play tap will rehydrate it when explicitly requested.
            loadRestoredWidgetSnapshot()
            updateWidgetState(false)
            return
        }
        if (player.isPlaying || player.playWhenReady) {
            player.pause()
            com.dsann.music.util.PlaybackStateManager.savePosition(applicationContext, player.currentPosition)
        }
        if (isCrossfading) {
            nextPlayer?.pause()
            crossfadeAnimator?.pause()
        }
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        if (!isPlayerInitialized) return
        if (player.shuffleModeEnabled != enabled) {
            player.shuffleModeEnabled = enabled
        }
    }

    fun stop() {
        if (!isPlayerInitialized) {
            pendingPlaybackRequest = null
            pendingServiceActions.clear()
            return
        }
        player.stop()
        if (isCrossfading) {
            nextPlayer?.stop()
            crossfadeAnimator?.cancel()
            crossfadeAnimator = null
        }
        abandonAudioFocus()
    }

    fun seekTo(position: Int) {
        if (!isPlayerInitialized) {
            pendingSeekToPosition = position.toLong()
            rememberPlaybackPosition(position.toLong())
            return
        }
        rememberPlaybackPosition(position.toLong())
        player.seekTo(position.toLong())
    }

    fun playPrevious() {
        val vm = viewModel
        if (vm != null) vm.playPreviousSong() else moveRestoredWidgetQueue(-1)
    }

    fun playNext() {
        val vm = viewModel
        if (vm != null) vm.playNextSong() else moveRestoredWidgetQueue(1)
    }

    private fun loadRestoredWidgetSnapshot(): Boolean {
        if (widgetRestoredSong != null) return true
        val song = com.dsann.music.util.PlaybackStateManager.getLastSong(applicationContext)
            ?: return false
        widgetRestoredSong = song
        widgetRestoredQueue = com.dsann.music.util.PlaybackStateManager.getLastQueue(applicationContext)
            .ifEmpty { listOf(song) }
        widgetRestoredIndex = com.dsann.music.util.PlaybackStateManager.getLastIndex(applicationContext)
            .coerceIn(0, widgetRestoredQueue.lastIndex.coerceAtLeast(0))
        currentSongName = song.title
        return true
    }

    private fun widgetSongUri(song: SongItem): Uri? {
        // Cached/downloaded tracks must restore from their exact offline URI, not re-resolve
        // a new online stream merely because the SongItem also has a YouTube video ID.
        if (song.isCachedTrack || song.isDownloadedOffline) {
            return runCatching { song.contentUri }.getOrNull()
        }
        val videoId = song.videoId?.takeIf { it.isNotBlank() }
        if (videoId != null) return "innertube://$videoId".toUri()
        return runCatching { song.contentUri }.getOrNull()
    }

    private fun restorePersistedPlaybackForWidget(autoPlay: Boolean): Boolean {
        if (!loadRestoredWidgetSnapshot()) return false
        val song = widgetRestoredSong ?: return false
        if (player.currentMediaItem != null) return true
        val uri = widgetSongUri(song) ?: return false
        val position = com.dsann.music.util.PlaybackStateManager
            .getLastPosition(applicationContext)
            .coerceAtLeast(0L)

        Log.i(TAG, "Restoring widget playback target '${song.title}' at $position ms")
        if (autoPlay) loadAndPlay(uri, position) else loadAudio(uri, position)
        return true
    }

    private fun moveRestoredWidgetQueue(delta: Int) {
        if (!loadRestoredWidgetSnapshot()) return
        val queue = widgetRestoredQueue
        if (queue.isEmpty()) return
        val currentIndex = queue.indexOfFirst { it.id == widgetRestoredSong?.id }
            .takeIf { it >= 0 } ?: widgetRestoredIndex

        if (delta < 0 && getStablePlaybackPositionMs() > 3_000L) {
            rememberPlaybackPosition(0L)
            player.seekTo(0L)
            com.dsann.music.util.PlaybackStateManager.savePosition(applicationContext, 0L)
            return
        }

        val nextIndex = ((currentIndex + delta) % queue.size + queue.size) % queue.size
        val nextSong = queue[nextIndex]
        val uri = widgetSongUri(nextSong) ?: return
        widgetRestoredIndex = nextIndex
        widgetRestoredSong = nextSong
        currentSongName = nextSong.title
        com.dsann.music.util.PlaybackStateManager.saveState(
            applicationContext, nextSong, 0L, queue, nextIndex
        )
        loadAndPlay(uri, 0L)
        updateWidgetState(true)
    }

    private fun buildMediaItem(uri: Uri): MediaItem {
        val song = viewModel?.currentSong?.value ?: widgetRestoredSong
        val songId = song?.id?.toString() ?: ""
        return buildMediaItem(
            uri,
            song?.title ?: currentSongName,
            song?.artist ?: "Unknown Artist",
            songId
        )
    }

    private fun buildMediaItem(uri: Uri, title: String, artist: String, songId: String = ""): MediaItem {
        val activeSong = (viewModel?.currentSong?.value ?: widgetRestoredSong)
            ?.takeIf { it.id.toString() == songId }
        val cachedMetadata = if (uri.scheme == "http" || uri.scheme == "https") {
            getCachedMediaMetadata(this, uri.toString())
        } else {
            null
        }
        val resolvedTitle = usableCacheTrackTitle(activeSong?.title)
            ?: usableCacheTrackTitle(title)
            ?: usableCacheTrackTitle(cachedMetadata?.title)
        val resolvedArtist = usableCacheTrackArtist(activeSong?.artist)
            ?: usableCacheTrackArtist(artist)
            ?: usableCacheTrackArtist(cachedMetadata?.artist)
        val resolvedAlbum = activeSong?.albumName?.takeIf { it.isNotBlank() }
            ?: cachedMetadata?.albumName?.takeIf { it.isNotBlank() }
        val resolvedThumbnail = activeSong?.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: cachedMetadata?.thumbnailUrl?.takeIf { it.isNotBlank() }
        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
        resolvedTitle?.let { metadataBuilder.setTitle(it) }
        resolvedArtist?.let { metadataBuilder.setArtist(it) }
        resolvedAlbum?.let { metadataBuilder.setAlbumTitle(it) }
        resolvedThumbnail?.let { metadataBuilder.setArtworkUri(it.toUri()) }

        // We only compress current cover for the active track to conserve heap space
        if (currentAlbumArtSongId == songId) {
            currentAlbumArt?.let {
                val stream = java.io.ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                metadataBuilder.setArtworkData(stream.toByteArray(), androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        
        if (uri.scheme == "http" || uri.scheme == "https") {
            val cachedSong = viewModel?.songList?.value?.firstOrNull { it.id.toString() == songId }
                ?: viewModel?.currentSong?.value?.takeIf { it.id.toString() == songId }
            resolvedTitle?.let { safeTitle ->
                rememberMediaCacheMetadata(
                    this,
                    uri.toString(),
                    safeTitle,
                    resolvedArtist ?: "Unknown Artist",
                    songId,
                    cachedSong?.duration ?: 0L,
                    cachedSong?.videoId.orEmpty(),
                    albumName = cachedSong?.albumName.orEmpty(),
                    thumbnailUrl = cachedSong?.thumbnailUrl.orEmpty()
                )
            }
        }

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(songId) // Stable ID for shuffle-mode resync
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /**
     * Create an offline-only media source for Cache playlist entries. Without an upstream data
     * source, evicted or missing bytes cannot silently fall back to online streaming.
     */
    @OptIn(UnstableApi::class)
    private fun buildCacheOnlyMediaSource(
        mediaItem: MediaItem
    ): androidx.media3.exoplayer.source.MediaSource {
        val cacheOnlyFactory = CacheDataSource.Factory()
            .setCache(getCache(applicationContext))
            .setUpstreamDataSourceFactory(null)
        return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheOnlyFactory)
            .createMediaSource(mediaItem)
    }

    /** Save the real container MIME after ExoPlayer has identified the active audio track. */
    private fun persistCurrentCacheMimeType() {
        if (!isPlayerInitialized) return
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return

        val audioFormat = player.currentTracks.groups
            .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
            .firstOrNull { it.sampleMimeType?.startsWith("audio/") == true }
        val mimeType = listOf(audioFormat?.containerMimeType, audioFormat?.sampleMimeType)
            .firstOrNull { candidate ->
                !candidate.isNullOrBlank() && listOf("webm", "mp4", "mpeg", "ogg", "opus", "flac", "wav")
                    .any { candidate.contains(it, ignoreCase = true) }
            } ?: audioFormat?.sampleMimeType ?: return

        val activeSong = viewModel?.currentSong?.value
        val durationMs = player.duration.takeIf {
            it > 0L && it != androidx.media3.common.C.TIME_UNSET
        } ?: activeSong?.duration ?: 0L
        val existingMetadata = getCachedMediaMetadata(applicationContext, uri.toString())
        val safeTitle = usableCacheTrackTitle(activeSong?.title)
            ?: usableCacheTrackTitle(item.mediaMetadata.title?.toString())
            ?: usableCacheTrackTitle(existingMetadata?.title)
            ?: return
        val safeArtist = usableCacheTrackArtist(activeSong?.artist)
            ?: usableCacheTrackArtist(item.mediaMetadata.artist?.toString())
            ?: usableCacheTrackArtist(existingMetadata?.artist)
            ?: "Unknown Artist"
        rememberMediaCacheMetadata(
            applicationContext,
            uri.toString(),
            safeTitle,
            safeArtist,
            item.mediaId.ifBlank { activeSong?.id?.toString().orEmpty() },
            durationMs,
            activeSong?.videoId.orEmpty(),
            mimeType,
            albumName = activeSong?.albumName.orEmpty(),
            thumbnailUrl = activeSong?.thumbnailUrl.orEmpty()
        )
    }

    private fun persistCurrentSongArtwork(bitmap: Bitmap?, expectedSongId: String) {
        val artwork = bitmap ?: return
        if (expectedSongId.isBlank()) return
        val activeSong = viewModel?.currentSong?.value ?: return
        // The request's captured song ID, not whichever track is active later, owns this image.
        if (activeSong.id.toString() != expectedSongId) return
        val mediaUri = if (isPlayerInitialized) player.currentMediaItem?.localConfiguration?.uri else null
        val isRemoteTrack = !activeSong.videoId.isNullOrBlank() ||
            mediaUri?.scheme == "http" || mediaUri?.scheme == "https"
        if (!isRemoteTrack) return
        serviceScope.launch(Dispatchers.IO) {
            storeCachedMediaArtwork(applicationContext, expectedSongId, artwork)
        }
    }

    fun getCurrentAudioFormatDetails(songId: String): CurrentAudioFormatDetails? {
        if (!isPlayerInitialized) return null
        return try {
            val activeItem = player.currentMediaItem ?: return null
            if (activeItem.mediaId != songId) return null
            val format = player.currentTracks.groups
                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
                .firstOrNull { it.sampleMimeType?.startsWith("audio/") == true }
            val durationMs = player.duration.takeIf {
                it > 0L && it != androidx.media3.common.C.TIME_UNSET
            }
            val details = CurrentAudioFormatDetails(
                bitrateBps = format?.bitrate?.takeIf { it > 0 },
                sampleRateHz = format?.sampleRate?.takeIf { it > 0 },
                mimeType = format?.sampleMimeType ?: format?.containerMimeType,
                durationMs = durationMs,
                albumTitle = activeItem.mediaMetadata.albumTitle?.toString()
                    ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            )
            // Duration or descriptive media metadata is still valuable before the audio
            // renderer has reported a complete Format object.
            details.takeIf {
                it.bitrateBps != null || it.sampleRateHz != null || it.mimeType != null ||
                    it.durationMs != null || it.albumTitle != null
            }
        } catch (error: Exception) {
            Log.d(TAG, "Current audio format is not available yet", error)
            null
        }
    }

    /**
     * HyperOS and other Android 11+ System UI builds can keep the media notification's
     * output chip on the phone speaker until the user opens the route picker. Observe the
     * system routing controller so a Bluetooth/speaker transfer causes a fresh session
     * notification without polling or touching the active audio stream.
     *
     * MediaRouter2 is API 30+. Older devices, including Android 6, never load this monitor.
     */
    private fun registerSystemOutputRouteMonitor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            systemOutputRouteMonitor = SystemOutputRouteMonitor().also { it.start() }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to observe system audio output route changes", error)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private inner class SystemOutputRouteMonitor {
        private val router = MediaRouter2.getInstance(this@AudioPlaybackService)
        private var selectedRouteIds = emptyList<String>()

        private val callback = object : MediaRouter2.ControllerCallback() {
            override fun onControllerUpdated(controller: MediaRouter2.RoutingController) {
                if (controller !== router.systemController) return

                val latestRouteIds = controller.selectedRoutes.map { it.id }.sorted()
                if (latestRouteIds == selectedRouteIds) return
                selectedRouteIds = latestRouteIds

                // Route updates can arrive in quick succession while System UI completes a
                // transfer. Coalesce them into one notification refresh on the main thread.
                handler.removeCallbacks(mediaRouteNotificationRefreshRunnable)
                handler.postDelayed(mediaRouteNotificationRefreshRunnable, 180L)
            }
        }

        fun start() {
            selectedRouteIds = router.systemController.selectedRoutes.map { it.id }.sorted()
            router.registerControllerCallback(
                ContextCompat.getMainExecutor(this@AudioPlaybackService),
                callback
            )
        }

        fun stop() {
            runCatching { router.unregisterControllerCallback(callback) }
        }
    }

    /**
     * Converts Media3's stable error code or the deepest useful resolver cause into a
     * short type label suitable for the global snackbar. The exception message itself is
     * intentionally omitted because it can contain long URLs or implementation details.
     */
    private fun playbackFailureType(error: Throwable?): String {
        val causes = ArrayList<Throwable>(8)
        var current = error
        while (current != null && causes.size < 8) {
            causes.add(current)
            current = current.cause
        }
        if (causes.isEmpty()) return "Unknown"

        val media3Error = causes.filterIsInstance<PlaybackException>().firstOrNull()
        if (media3Error != null) {
            val code = media3Error.errorCodeName
            val label = when {
                code.contains("NETWORK", ignoreCase = true) -> "Network error"
                code.contains("BAD_HTTP_STATUS", ignoreCase = true) -> "HTTP response error"
                code.contains("TIMEOUT", ignoreCase = true) -> "Timeout"
                code.contains("FILE_NOT_FOUND", ignoreCase = true) -> "File not found"
                code.contains("NO_PERMISSION", ignoreCase = true) -> "Permission denied"
                code.contains("DECODER_INIT", ignoreCase = true) ||
                    code.contains("DECODING", ignoreCase = true) -> "Decoder error"
                code.contains("PARSING", ignoreCase = true) -> "Unsupported media format"
                code.contains("AUDIO_TRACK", ignoreCase = true) -> "Audio output error"
                else -> code.removePrefix("ERROR_CODE_")
                    .lowercase()
                    .split('_')
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { char -> char.uppercase() }
                    }
            }
            return if (code.isBlank() || code == "ERROR_CODE_UNSPECIFIED") {
                "PlaybackException"
            } else {
                "$label ($code)"
            }
        }

        val usefulCause = causes.firstOrNull { cause ->
            cause.javaClass.simpleName in setOf(
                "UnknownHostException",
                "SocketTimeoutException",
                "ConnectException",
                "NoRouteToHostException",
                "SSLHandshakeException",
                "SSLException",
                "InvalidResponseCodeException",
                "FileNotFoundException",
                "SecurityException"
            )
        } ?: causes.last()

        val type = usefulCause.javaClass.simpleName.ifBlank { "Unknown" }
        return when (type) {
            "UnknownHostException", "NoRouteToHostException" -> "Network / DNS ($type)"
            "SocketTimeoutException" -> "Network timeout ($type)"
            "ConnectException" -> "Connection failed ($type)"
            "SSLHandshakeException", "SSLException" -> "Secure connection failed ($type)"
            "InvalidResponseCodeException" -> "HTTP response rejected ($type)"
            "FileNotFoundException" -> "File not found ($type)"
            "SecurityException" -> "Permission denied ($type)"
            "IOException" -> "I/O error ($type)"
            "IllegalStateException" -> {
                val message = usefulCause.message.orEmpty().lowercase()
                if (
                    message.contains("stream extraction") ||
                    message.contains("no usable audio stream") ||
                    message.contains("no compatible fallback")
                ) {
                    "Stream unavailable"
                } else {
                    "Illegal state ($type)"
                }
            }
            "IllegalArgumentException" -> "Invalid playback source ($type)"
            else -> type
        }
    }

    private fun showPlaybackFailure(
        error: Throwable?,
        context: String = "Playback",
        action: String? = null
    ) {
        val type = playbackFailureType(error)
        val actionSuffix = action?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        com.dsann.music.ui.SnackbarController.showMessage("$context error: $type$actionSuffix")
    }

    @OptIn(UnstableApi::class)
    private fun refreshMediaNotification() {
        if (!::handler.isInitialized) return
        handler.post {
            if (mediaSession != null) {
                try {
                    // Artwork can arrive after the notification has already been posted.
                    // Rebuild it so legacy notifications receive their large icon and newer
                    // System UI sees the updated MediaItem metadata.
                    triggerNotificationUpdate()
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to refresh the media notification artwork", error)
                }
            }
        }
    }

    private fun setSongMetadata(
        songName: String?,
        albumArt: Bitmap?,
        expectedSongId: String,
        requestGeneration: Long,
        persistArtwork: Boolean = true
    ) {
        val activeSong = viewModel?.currentSong?.value
        if (expectedSongId.isBlank() ||
            activeSong?.id?.toString() != expectedSongId ||
            requestGeneration != artworkRequestGeneration
        ) return

        val resolvedSongName = songName?.takeIf { it.isNotBlank() } ?: activeSong.title
        currentSongName = resolvedSongName
        if (albumArt == null) {
            applySongMetadata(
                resolvedSongName,
                null,
                null,
                expectedSongId,
                requestGeneration,
                persistArtwork
            )
            return
        }

        // Bitmap crop/scale and PNG encoding used to run on the main thread every time
        // artwork arrived. On API 23 that can stall all pages during local track changes.
        // Keep player/session mutations on Main, but do all pixel work on a worker thread.
        artworkProcessingJob?.cancel()
        artworkProcessingJob = serviceScope.launch(Dispatchers.Default) {
            if (requestGeneration != artworkRequestGeneration) return@launch
            val processedArtwork = try {
                val size = min(albumArt.width, albumArt.height)
                Bitmap.createBitmap(
                    albumArt,
                    (albumArt.width - size) / 2,
                    (albumArt.height - size) / 2,
                    size,
                    size
                ).scale(512, 512, true)
            } catch (error: Exception) {
                Log.w(TAG, "Unable to prepare artwork for $expectedSongId", error)
                null
            }
            val encodedArtwork = processedArtwork?.let { bitmap ->
                try {
                    java.io.ByteArrayOutputStream().use { stream ->
                        if (bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) {
                            stream.toByteArray()
                        } else null
                    }
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to encode artwork for $expectedSongId", error)
                    null
                }
            }
            if (requestGeneration != artworkRequestGeneration) return@launch
            withContext(Dispatchers.Main) {
                applySongMetadata(
                    resolvedSongName,
                    processedArtwork,
                    encodedArtwork,
                    expectedSongId,
                    requestGeneration,
                    persistArtwork
                )
            }
        }
    }

    private fun applySongMetadata(
        songName: String,
        albumArt: Bitmap?,
        encodedArtwork: ByteArray?,
        expectedSongId: String,
        requestGeneration: Long,
        persistArtwork: Boolean
    ) {
        val activeSong = viewModel?.currentSong?.value
        if (expectedSongId.isBlank() ||
            activeSong?.id?.toString() != expectedSongId ||
            requestGeneration != artworkRequestGeneration
        ) return

        currentSongName = songName
        currentAlbumArt = albumArt
        currentAlbumArtSongId = expectedSongId.takeIf { currentAlbumArt != null }
        if (persistArtwork) persistCurrentSongArtwork(currentAlbumArt, expectedSongId)

        val mediaItem = if (isPlayerInitialized) player.currentMediaItem else null
        if (mediaItem != null && mediaItem.mediaId == expectedSongId) {
            val metadataBuilder = mediaItem.mediaMetadata.buildUpon()
                .setTitle(currentSongName)
                .setArtist(activeSong.artist.ifBlank { "Unknown Artist" })
                .setArtworkUri(activeSong.thumbnailUrl?.takeIf { it.isNotBlank() }?.toUri())
                // Explicitly replace/clear artwork bytes; otherwise a prior song's bitmap
                // can remain attached while this song's cover is still downloading.
                .setArtworkData(
                    encodedArtwork,
                    androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER
                )
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                mediaItem.buildUpon().setMediaMetadata(metadataBuilder.build()).build()
            )
        }
        // Audio can begin before its remote cover finishes downloading. Re-publish the
        // widget snapshot when the cover arrives so the widget does not stay on its first
        // empty/stale artwork render.
        if (isPlayerInitialized) updateWidgetState(player.isPlaying)
        refreshMediaNotification()
    }

    /** Cancel artwork work belonging to tracks skipped before the current selection. */
    private fun beginArtworkRequest(): Long {
        artworkLoadTarget?.let { target ->
            runCatching { com.bumptech.glide.Glide.with(applicationContext).clear(target) }
        }
        artworkLoadTarget = null
        artworkDecodeJob?.cancel()
        artworkDecodeJob = null
        artworkProcessingJob?.cancel()
        artworkProcessingJob = null
        artworkRetryJob?.cancel()
        artworkRetryJob = null
        return ++artworkRequestGeneration
    }

    private fun setSongMetadataFromCache(songName: String?, artworkFile: File, expectedSongId: String) {
        if (expectedSongId.isBlank()) return
        val requestGeneration = beginArtworkRequest()
        artworkDecodeJob = serviceScope.launch(Dispatchers.IO) {
            val cachedArtwork = BitmapFactory.decodeFile(artworkFile.absolutePath)
            if (cachedArtwork == null) artworkFile.delete()
            withContext(Dispatchers.Main) {
                if (requestGeneration != artworkRequestGeneration) return@withContext
                artworkDecodeJob = null
                if (cachedArtwork != null) {
                    setSongMetadata(
                        songName,
                        cachedArtwork,
                        expectedSongId,
                        requestGeneration,
                        persistArtwork = false
                    )
                } else {
                    val activeSong = viewModel?.currentSong?.value
                        ?.takeIf { it.id.toString() == expectedSongId } ?: return@withContext
                    when {
                        !activeSong.thumbnailUrl.isNullOrBlank() ->
                            setSongMetadataByUrl(activeSong.title, activeSong.thumbnailUrl)
                        activeSong.albumId > 0L ->
                            setSongMetadataById(activeSong.title, activeSong.albumId)
                        else -> setSongMetadata(
                            songName,
                            null,
                            expectedSongId,
                            requestGeneration,
                            persistArtwork = false
                        )
                    }
                }
            }
        }
    }

    /**
     * API 23 has no ContentResolver.loadThumbnail(). Decode a sampled bitmap instead
     * of MediaStore.getBitmap(), which allocates the full-resolution cover only to
     * immediately scale it down for MediaSession/notification artwork.
     * Caller runs this on Dispatchers.IO.
     */
    private fun decodeAlbumArtThumbnail(uri: Uri, targetSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= targetSizePx &&
            bounds.outHeight / (sampleSize * 2) >= targetSizePx
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    fun setSongMetadataById(songName: String?, albumId: Long) {
        val expectedSongId = viewModel?.currentSong?.value?.id?.toString() ?: return
        val requestGeneration = beginArtworkRequest()
        setSongMetadata(songName, null, expectedSongId, requestGeneration)
        artworkDecodeJob = serviceScope.launch(Dispatchers.IO) {
            var albumArt: Bitmap? = null
            if (albumId > 0) {
                try {
                    val artUri = "content://media/external/audio/albumart/$albumId".toUri()
                    albumArt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentResolver.loadThumbnail(artUri, Size(512, 512), null)
                    } else {
                        decodeAlbumArtThumbnail(artUri, 512)
                    }
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                if (requestGeneration != artworkRequestGeneration) return@withContext
                artworkDecodeJob = null
                setSongMetadata(songName, albumArt, expectedSongId, requestGeneration)
            }
        }
    }

    fun setSongMetadataByUrl(songName: String?, url: String?) {
        val activeSong = viewModel?.currentSong?.value ?: return
        val expectedSongId = activeSong.id.toString()
        val requestGeneration = beginArtworkRequest()
        setSongMetadata(songName, null, expectedSongId, requestGeneration)

        val resolvedUrl = url?.takeIf { it.isNotBlank() }
            ?: activeSong.videoId?.takeIf { it.isNotBlank() }
                ?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
        if (resolvedUrl.isNullOrBlank()) return

        loadSongArtworkByUrl(
            songName = songName,
            resolvedUrl = resolvedUrl,
            expectedSongId = expectedSongId,
            requestGeneration = requestGeneration,
            retryAttempt = 0
        )
    }

    private fun loadSongArtworkByUrl(
        songName: String?,
        resolvedUrl: String,
        expectedSongId: String,
        requestGeneration: Long,
        retryAttempt: Int
    ) {
        val activeSong = viewModel?.currentSong?.value
        if (requestGeneration != artworkRequestGeneration ||
            activeSong?.id?.toString() != expectedSongId
        ) return

        val target = object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
            override fun onResourceReady(
                resource: Bitmap,
                transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
            ) {
                if (artworkLoadTarget === this) artworkLoadTarget = null
                if (requestGeneration != artworkRequestGeneration) return
                artworkRetryJob?.cancel()
                artworkRetryJob = null
                setSongMetadata(songName, resource, expectedSongId, requestGeneration)
            }

            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                if (artworkLoadTarget === this) artworkLoadTarget = null
            }

            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                if (artworkLoadTarget === this) artworkLoadTarget = null
                if (requestGeneration != artworkRequestGeneration) return
                scheduleSongArtworkRetry(
                    songName,
                    resolvedUrl,
                    expectedSongId,
                    requestGeneration,
                    retryAttempt + 1
                )
            }
        }
        artworkLoadTarget = target
        com.bumptech.glide.Glide.with(applicationContext)
            .asBitmap()
            .load(resolvedUrl)
            .override(512, 512)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
            .into(target)
    }

    /** Retry temporary image/network failures without restarting or blocking audio playback. */
    private fun scheduleSongArtworkRetry(
        songName: String?,
        resolvedUrl: String,
        expectedSongId: String,
        requestGeneration: Long,
        nextAttempt: Int
    ) {
        if (nextAttempt > artworkRetryDelaysMs.size) {
            Log.w(TAG, "Artwork retry limit reached for song $expectedSongId")
            return
        }

        artworkRetryJob?.cancel()
        val waitMs = artworkRetryDelaysMs[nextAttempt - 1]
        artworkRetryJob = serviceScope.launch {
            delay(waitMs)
            val activeSong = viewModel?.currentSong?.value
            if (requestGeneration != artworkRequestGeneration ||
                activeSong?.id?.toString() != expectedSongId
            ) return@launch

            artworkRetryJob = null
            loadSongArtworkByUrl(
                songName = songName,
                resolvedUrl = resolvedUrl,
                expectedSongId = expectedSongId,
                requestGeneration = requestGeneration,
                retryAttempt = nextAttempt
            )
        }
    }

    fun isPlaying(): Boolean = player.isPlaying
    fun getCurrentSourceUri(): Uri? = originalSourceUri ?: currentSourceUri

    private fun playbackPositionTrackKey(): String? {
        val song = viewModel?.currentSong?.value ?: widgetRestoredSong
        return song?.let { "${it.id}:${it.videoId.orEmpty()}" }
            ?: if (isPlayerInitialized) player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } else null
    }

    private fun rememberPlaybackPosition(positionMs: Long) {
        val trackKey = playbackPositionTrackKey()
        if (trackKey != null) stablePositionTrackKey = trackKey
        stablePlaybackPositionMs = positionMs.coerceAtLeast(0L)
    }

    private fun getStablePlaybackPositionMs(acceptZeroFromPlayer: Boolean = false): Long {
        val song = viewModel?.currentSong?.value ?: widgetRestoredSong
        val trackKey = playbackPositionTrackKey()
        if (trackKey != null && trackKey != stablePositionTrackKey) {
            val isInitialPositionSnapshot = stablePositionTrackKey == null
            stablePositionTrackKey = trackKey
            stablePlaybackPositionMs = if (isInitialPositionSnapshot) {
                viewModel?.currentPosition?.value?.toLong()?.coerceAtLeast(0L) ?: 0L
            } else {
                0L
            }
        }

        if (isPlayerInitialized) {
            val mediaId = player.currentMediaItem?.mediaId
            val expectedSongId = song?.id?.toString()
            val mediaItemMatchesSong = !mediaId.isNullOrBlank() &&
                (expectedSongId == null || mediaId == expectedSongId || mediaId == "$expectedSongId-audio")
            if (mediaItemMatchesSong && player.playbackState != Player.STATE_IDLE) {
                val livePosition = player.currentPosition.coerceAtLeast(0L)
                if (livePosition > 0L || acceptZeroFromPlayer) {
                    stablePlaybackPositionMs = livePosition
                }
            }
        }

        return stablePlaybackPositionMs.coerceAtLeast(0L)
    }

    private fun loadAudioInternal(uri: Uri, keepPlayingUntilReady: Boolean = false) {
        cancelCurrentTrackCache()
        cacheAssetsSongId = null
        if (!keepPlayingUntilReady) {
            player.pause()
            player.stop()
            player.clearMediaItems()
        }

        if (isCrossfading) {
            nextPlayer?.stop()
            nextPlayer?.release()
            nextPlayer = null
            crossfadeAnimator?.cancel()
            crossfadeAnimator = null
            isCrossfading = false
            crossfadeNextSongStarted = false
            player.volume = currentBaseVolume
        }

        currentSourceUri = uri
        isNextSongPreCached = false
        
        try {
            val scheme = uri.scheme
            if (scheme == "http" || scheme == "https" || scheme == "innertube") {
                Log.d(TAG, "Loading remote stream: ${uri.toString().take(80)}...")
            }
            
            val currentSong = viewModel?.currentSong?.value
            if (currentSong != null && !currentSong.videoId.isNullOrEmpty()) {
                telemetryManager.onSongChanged(currentSong.videoId!!, currentSong.duration)
            }
            
            val mediaItem = buildMediaItem(uri)
            val requestedStartPosition = pendingSeekPos
            if (requestedStartPosition >= 0L) {
                rememberPlaybackPosition(requestedStartPosition)
            }
            // A Cache playlist item is cache-only only while it is still using its exact
            // cached URI. Switching that item to a freshly resolved video/audio stream must use
            // the network source, not an offline-only CacheDataSource.
            if (currentSong?.isCachedTrack == true && uri == currentSong.contentUri) {
                val cacheSource = buildCacheOnlyMediaSource(mediaItem)
                if (requestedStartPosition >= 0L) {
                    player.setMediaSource(cacheSource, requestedStartPosition)
                } else {
                    player.setMediaSource(cacheSource)
                }
            } else {
                if (requestedStartPosition >= 0L) {
                    player.setMediaItem(mediaItem, requestedStartPosition)
                } else {
                    player.setMediaItem(mediaItem)
                }
            }
            // Store the resume point inside the new Media3 media source transaction. This
            // survives video/audio swaps more reliably than seeking before prepare. Later
            // asynchronous cross-device resume requests still use the READY listener.
            if (requestedStartPosition >= 0L) pendingSeekPos = -1L

            // Effects/normalization were removed; every newly loaded track must start
            // with normal playback gain rather than inherit a stale crossfade volume.
            player.volume = currentBaseVolume.coerceIn(0f, 1f)
            player.prepare()

            if (shouldAutoPlay) {
                // Media3 manages focus from AudioAttributes. Do not gate playback on a
                // separate focus request that can be denied/delayed before service promotion.
                player.play()
                isPausedByFocusLoss = false
                shouldAutoPlay = false
            }

            // Proactively stack the next song for gapless transition
            scheduleQueuePreload(200L)
        } catch (e: Exception) {
            shouldAutoPlay = false
            viewModel?.setIsPlaying(false)
            viewModel?.setLoading(false)
            Log.e(TAG, "Failed to load audio", e)
            showPlaybackFailure(e)
        }
    }

    private fun registerScreenWakeReceiver() {
        if (screenWakeReceiverRegistered) return
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenWakeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenWakeReceiverRegistered = true
    }

    private fun scheduleWidgetRefreshAfterScreenWake() {
        if (!isPlayerInitialized) {
            pendingWidgetRefreshAfterScreenWake = true
            return
        }
        handler.removeCallbacks(widgetRefreshAfterWakeRunnable)
        handler.removeCallbacks(widgetWakeFollowUpRunnable)
        // SCREEN_ON and USER_PRESENT can both fire for one unlock. Debounce them to one
        // complete widget refresh after the launcher/keyguard has resumed.
        handler.postDelayed(widgetRefreshAfterWakeRunnable, 500L)
    }

    private fun startProgressFrameUpdates() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) return
        if (progressFrameUpdatesRunning || viewModel == null || !isPlayerInitialized || !player.isPlaying) {
            return
        }
        // Service may start before the user has opened Playback settings.
        AppAnimationFrameRateController.load(applicationContext)
        progressFrameUpdatesRunning = true
        lastUiFrameBucket = Long.MIN_VALUE
        Choreographer.getInstance().postFrameCallback(progressFrameCallback)
    }

    private fun stopProgressFrameUpdates() {
        if (!progressFrameUpdatesRunning) return
        progressFrameUpdatesRunning = false
        lastUiFrameBucket = Long.MIN_VALUE
        Choreographer.getInstance().removeFrameCallback(progressFrameCallback)
    }

    private fun setupProgressTracking() {
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                    val pos = getStablePlaybackPositionMs()
                    val dur = player.duration
                    // API 23 intentionally does not run a Choreographer publisher. Publish a
                    // 4 Hz shared snapshot here for elapsed-time labels and non-draw consumers;
                    // the visible mini/full-player seek bars sample Media3 directly during draw.
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && player.isPlaying) {
                        viewModel?.setCurrentPosition(
                            pos.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                    }
                    val nowMs = System.currentTimeMillis()
                    val lyricsModeActive = viewModel?.isLyricsModeEnabled?.value == true
                    // On API 23, publish only a 4 Hz UI snapshot. The full-player seek track
                    // and mini-player artwork ring read Media3 directly in their draw passes,
                    // so a 60 Hz StateFlow emission only recomposes unrelated UI for no gain.
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && player.isPlaying) {
                        viewModel?.setCurrentPosition(
                            pos.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                    }
                    if (dur > 0) viewModel?.setDuration(dur.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

                    // Telemetry has second-level heartbeat semantics; update it once per second.
                    if (nowMs - lastTelemetryUpdateAtMs >= 1_000L) {
                        telemetryManager.updateProgress(pos, dur)
                        lastTelemetryUpdateAtMs = nowMs
                    }

                    // Persist a single position snapshot every five seconds. The old modulo test
                    // could persist repeatedly across the same 250ms boundary.
                    if (player.isPlaying && nowMs - lastPositionPersistedAtMs >= 5_000L) {
                        serviceScope.launch(Dispatchers.IO) {
                            com.dsann.music.util.PlaybackStateManager.savePosition(applicationContext, pos)
                        }
                        lastPositionPersistedAtMs = nowMs
                    }

                    val lyricsWidgetActive = hasLyricsWidgetInstalled()
                    if (player.isPlaying && lyricsWidgetActive) {
                        // Re-evaluate after seeks and track changes, but the scheduled task
                        // itself fires at the next timed lyric/word boundary.
                        scheduleNextWidgetLyricRefresh(pos)
                    }
                    // Keep widget progress predictable across Android versions. Synced lyric
                    // boundaries below still trigger independent immediate updates.
                    val widgetProgressIntervalMs = 2_000L
                    if (player.isPlaying &&
                        nowMs - lastWidgetProgressUpdateAtMs >= widgetProgressIntervalMs
                    ) {
                        lastWidgetProgressUpdateAtMs = nowMs
                        // Progress runs on its own channel. It must never publish a lyric
                        // snapshot, because a delayed progress job could overwrite a newer
                        // line-boundary event with stale lyric text.
                        updateWidgetProgress(pos, dur, updateLyrics = false)
                    }

                    if (player.isPlaying && crossfadeDurationMs > 0 && dur > 0 &&
                        (dur - pos) <= crossfadeDurationMs && !isCrossfading
                    ) {
                        startCrossfadeOut()
                    }

                    if (player.isPlaying && dur > 0 && !isNextSongPreCached &&
                        (pos.toFloat() / dur.toFloat() >= 0.8f)
                    ) {
                        isNextSongPreCached = true
                        viewModel?.let { vm ->
                            val songs = vm.songList.value
                            val nextIndex = vm.getNextSongIndex()
                            if (nextIndex >= 0 && nextIndex < songs.size) {
                                YouTubeMusic.preCacheNextSong(songs[nextIndex])
                            }
                        }
                    }
                }
                // Keep housekeeping low-frequency. Only newer Android versions publish
                // shared position snapshots on display vsync; API 23 uses the 4 Hz Handler path.
                val lyricsModeActive = viewModel?.isLyricsModeEnabled?.value == true
                val intervalMs = when {
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && player.isPlaying -> 250L
                    lyricsModeActive && player.isPlaying -> 180L
                    // API 23 uses a 4 Hz shared UI snapshot; newer versions publish on vsync.
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.M -> 2_000L
                    else -> 400L
                }
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.post(progressUpdateRunnable)
    }

    /**
     * Starts one widget lyric request for the active song, including the first song
     * already selected when the service attaches. The request is deduplicated by song ID
     * and never cancels a fetch that the player UI has already started.
     */
    private fun ensureWidgetLyricsForSong(
        vm: PlayerSharedViewModel,
        song: com.dsann.music.model.SongItem,
        refreshWidgetDetection: Boolean,
        allowRetry: Boolean = false
    ) {
        if (!hasLyricsWidgetInstalled(forceRefresh = refreshWidgetDetection)) return
        if (vm.currentSong.value?.id != song.id) return

        widgetSyncedLyrics = vm.syncedLyrics.value.orEmpty()
            .sortedBy { widgetLyricStartMs(it) }
        widgetPlainLyrics = vm.plainLyrics.value?.takeIf { it.isNotBlank() }

        val hasLyrics = widgetSyncedLyrics.isNotEmpty() || !widgetPlainLyrics.isNullOrBlank()
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.coerceAtLeast(0L)
        if (hasLyrics) {
            updateWidgetProgress(position, duration)
            scheduleNextWidgetLyricRefresh(position)
            return
        }

        if (vm.isLyricsLoading.value) {
            // The main player may have started the same request before the service observer.
            DsmusicWidgetProvider.updateAllWidgetsLyrics(
                applicationContext,
                "Searching for lyrics…"
            )
            return
        }

        if (widgetLyricsRequestedSongId == song.id && !allowRetry) return
        widgetLyricsRequestedSongId = song.id
        cancelWidgetLyricRefresh()
        DsmusicWidgetProvider.updateAllWidgetsLyrics(
            applicationContext,
            "Searching for lyrics…"
        )
        Log.i(TAG, "Starting widget lyrics for '${song.title}' (songId=${song.id})")
        vm.fetchLyricsForCurrentSong()
    }

    private fun requestWidgetLyrics() {
        if (!hasLyricsWidgetInstalled(forceRefresh = true)) {
            cancelWidgetLyricRefresh()
            return
        }
        val vm = viewModel
        val activeSong = vm?.currentSong?.value
        if (vm != null && activeSong != null) {
            ensureWidgetLyricsForSong(
                vm = vm,
                song = activeSong,
                refreshWidgetDetection = false,
                allowRetry = true
            )
            updateWidgetState(player.isPlaying)
            scheduleNextWidgetLyricRefresh(player.currentPosition.coerceAtLeast(0L))
            return
        }

        // A widget can be tapped while the Activity (and its ViewModel) is not bound.
        // Recover the current track from the active Media3 item and use the same lyrics
        // provider pipeline instead of leaving the lyrics page permanently blank.
        val item = player.currentMediaItem ?: return
        val requestedMediaId = item.mediaId
        widgetSyncedLyrics = emptyList()
        widgetPlainLyrics = null
        val title = item.mediaMetadata.title?.toString()?.trim().orEmpty()
        val artist = item.mediaMetadata.artist?.toString()?.trim().orEmpty()
        if (title.isBlank()) return
        val uri = item.localConfiguration?.uri
        val cachedMetadata = uri?.let { getCachedMediaMetadata(applicationContext, it.toString()) }
        val durationMs = player.duration.takeIf {
            it > 0L && it != androidx.media3.common.C.TIME_UNSET
        } ?: cachedMetadata?.durationMs ?: 0L

        com.dsann.music.data.lyrics.LyricsRepository.getInstance(applicationContext).fetchLyrics(
            title = title,
            artist = artist.ifBlank { "Unknown Artist" },
            album = item.mediaMetadata.albumTitle?.toString(),
            durationMs = durationMs,
            filePath = uri?.toString(),
            videoId = cachedMetadata?.videoId,
            callback = object : com.dsann.music.data.lyrics.LyricsRepository.ExtendedLyricsCallback {
                override fun onLyricsFound(result: com.dsann.music.data.lyrics.LyricsResult) {
                    publishWidgetLyrics(result, expectedMediaId = requestedMediaId)
                }

                override fun onLyricsUpgraded(betterResult: com.dsann.music.data.lyrics.LyricsResult) {
                    publishWidgetLyrics(betterResult, expectedMediaId = requestedMediaId)
                }

                override fun onLyricsNotFound(reason: String) {
                    publishWidgetLyrics(null, "Lyrics not found", requestedMediaId)
                }

                override fun onInstrumental() {
                    publishWidgetLyrics(null, "Instrumental", requestedMediaId)
                }

                override fun onSearchStatus(message: String) {
                    publishWidgetLyrics(null, message, requestedMediaId)
                }
            }
        )
    }

    private fun publishWidgetLyrics(
        result: com.dsann.music.data.lyrics.LyricsResult?,
        fallbackText: String? = null,
        expectedMediaId: String? = null
    ) {
        handler.post {
            if (expectedMediaId != null && player.currentMediaItem?.mediaId != expectedMediaId) {
                return@post
            }
            val position = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration.coerceAtLeast(0L)
            widgetSyncedLyrics = result?.syncedLyrics
                ?.takeIf { it.isNotBlank() }
                ?.let { com.dsann.music.data.lyrics.LrcParser.parse(it).sortedBy { line -> widgetLyricStartMs(line) } }
                .orEmpty()
            widgetPlainLyrics = result?.plainLyrics?.takeIf { it.isNotBlank() }
                ?: fallbackText?.takeIf { it.isNotBlank() }
            if (widgetSyncedLyrics.isEmpty() && widgetPlainLyrics.isNullOrBlank()) {
                widgetPlainLyrics = fallbackText
            }
            updateWidgetProgress(position, duration)
            scheduleNextWidgetLyricRefresh(position)
        }
    }

    /**
     * Select the same active lyric line as the full-screen player, then use RemoteViews
     * spans for the selected lyrics font/colour and the available karaoke word timing.
     */
    private fun widgetLyricStartMs(line: LyricLine): Long =
        if (line.isWordByWord && line.words.isNotEmpty()) {
            line.words.minOfOrNull { it.absoluteStartTimeMs } ?: line.startTimeMs
        } else {
            line.startTimeMs
        }

    /** Last timed word boundary; used as the end of a karaoke-origin line window. */
    private fun widgetLyricEndMs(line: LyricLine): Long =
        line.words.maxOfOrNull {
            it.absoluteStartTimeMs + it.durationMs.coerceAtLeast(1L)
        } ?: line.startTimeMs

    /**
     * Widgets render line-synced lyrics, not the full-player karaoke sweep.
     * For word-timed sources, the first word timestamp starts the whole line and
     * the last word end is its final timing boundary. The full text stays together
     * until the next line begins, matching the portrait player's one-active-line model.
     */
    private fun currentWidgetLyricText(positionMs: Long, playbackDurationMs: Long): CharSequence {
        val timeline = LineSyncedLyricTimeline.build(widgetSyncedLyrics)
        // Use the duration snapshot captured on the player's application looper. This
        // renderer is also called from Dispatchers.Default during widget progress updates,
        // where touching ExoPlayer would violate Media3's thread-affinity requirement.
        val durationMs = playbackDurationMs.takeIf {
            it > 0L && it != androidx.media3.common.C.TIME_UNSET
        } ?: 0L
        val activeIndex = LineSyncedLyricTimeline.activeIndexAt(
            timeline = timeline,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs
        )
        val line = timeline.getOrNull(activeIndex)?.line
        if (line != null) return styleWidgetLyricText(line.content)

        val plain = (widgetPlainLyrics ?: viewModel?.plainLyrics?.value).orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(4)
            .joinToString("\n")
        return if (plain.isBlank()) "..." else styleWidgetLyricText(plain)
    }

    /** Widget lyric pages show one complete line, never a word-by-word karaoke sweep. */
    private fun styleWidgetLyricText(text: String): CharSequence {
        val value = text.trim()
        if (value.isEmpty()) return "..."
        val appearance = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val colourStyle = when (appearance.getString("lyrics_color_style", "Home Studio Gradient")) {
            "UI Matching" -> "UI Matching"
            "White" -> "White"
            else -> "Home Studio Gradient"
        }
        val family = when (appearance.getString("lyrics_font_family", "Rounded")) {
            "Sans" -> "sans-serif"
            "Serif" -> "serif"
            "Mono" -> "monospace"
            else -> "sans-serif"
        }
        val output = SpannableString(value)
        // TypefaceSpan(Typeface) was added in API 28. Use the legacy family-name
        // constructor so widget lyrics remain safe on Android 6 (API 23).
        output.setSpan(TypefaceSpan(family), 0, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        output.setSpan(StyleSpan(Typeface.BOLD), 0, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val widgetPrefs = getSharedPreferences("dsmusic_widget_prefs", Context.MODE_PRIVATE)
        val accentColor = widgetPrefs.getInt(
            "widget_lyrics_dynamic_accent",
            android.graphics.Color.rgb(124, 156, 255)
        )
        val gradientStops = intArrayOf(
            android.graphics.Color.rgb(83, 246, 255),
            android.graphics.Color.rgb(105, 157, 255),
            android.graphics.Color.rgb(174, 126, 255),
            android.graphics.Color.rgb(255, 112, 205),
            android.graphics.Color.rgb(255, 207, 127)
        )
        for (index in value.indices) {
            val fraction = if (value.length <= 1) 0f else index.toFloat() / (value.length - 1)
            val scaled = fraction * (gradientStops.size - 1)
            val lower = scaled.toInt().coerceIn(0, gradientStops.lastIndex)
            val upper = (lower + 1).coerceAtMost(gradientStops.lastIndex)
            val blend = (scaled - lower).coerceIn(0f, 1f)
            val color = when (colourStyle) {
                "White" -> android.graphics.Color.WHITE
                "UI Matching" -> accentColor
                else -> {
                    val start = gradientStops[lower]
                    val end = gradientStops[upper]
                    android.graphics.Color.rgb(
                        (android.graphics.Color.red(start) +
                            (android.graphics.Color.red(end) - android.graphics.Color.red(start)) * blend).toInt(),
                        (android.graphics.Color.green(start) +
                            (android.graphics.Color.green(end) - android.graphics.Color.green(start)) * blend).toInt(),
                        (android.graphics.Color.blue(start) +
                            (android.graphics.Color.blue(end) - android.graphics.Color.blue(start)) * blend).toInt()
                    )
                }
            }
            output.setSpan(
                ForegroundColorSpan(color),
                index,
                index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return output
    }

    private fun hasLyricsWidgetInstalled(forceRefresh: Boolean = false): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (forceRefresh) {
            // This only schedules background Binder work; it never waits for the launcher.
            DsmusicWidgetProvider.refreshInstalledWidgetCache(applicationContext, true)
        }
        if (forceRefresh || now - lastLyricsWidgetInstalledCheckAtMs >= 1_200L) {
            lyricsWidgetInstalledCached =
                DsmusicWidgetProvider.hasLyricsWidgetInstalled(applicationContext)
            lastLyricsWidgetInstalledCheckAtMs = now
        }
        return lyricsWidgetInstalledCached
    }

    private fun cancelWidgetLyricRefresh() {
        widgetLyricUpdateRunnable?.let { runnable ->
            if (::handler.isInitialized) handler.removeCallbacks(runnable)
        }
        widgetLyricUpdateRunnable = null
        widgetLyricScheduledBoundaryMs = null
    }

    /**
     * Schedule only at the next lyric/word timestamp. This keeps the widget close to the
     * full-player line timing without redrawing RemoteViews every display frame.
     */
    private fun scheduleNextWidgetLyricRefresh(positionMs: Long) {
        if (!::handler.isInitialized || !player.isPlaying ||
            !hasLyricsWidgetInstalled() || widgetSyncedLyrics.isEmpty()
        ) {
            cancelWidgetLyricRefresh()
            return
        }

        val timeline = LineSyncedLyricTimeline.build(widgetSyncedLyrics)
        val nextEvent = LineSyncedLyricTimeline.nextBoundaryAfter(
            timeline = timeline,
            positionMs = positionMs.coerceAtLeast(0L)
        )
        if (nextEvent == null) {
            cancelWidgetLyricRefresh()
            return
        }
        if (widgetLyricScheduledBoundaryMs == nextEvent && widgetLyricUpdateRunnable != null) {
            return
        }

        cancelWidgetLyricRefresh()
        val speed = player.playbackParameters.speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val delayMs = kotlin.math.ceil(
            (nextEvent - positionMs).coerceAtLeast(0L).toDouble() / speed.toDouble()
        ).toLong().coerceIn(1L, 60_000L)
        val update = Runnable {
            widgetLyricUpdateRunnable = null
            widgetLyricScheduledBoundaryMs = null
            if (!player.isPlaying) return@Runnable
            val position = player.currentPosition.coerceAtLeast(0L)
            // Handler timing may wake a fraction early. Never publish the old line at a
            // boundary: reschedule for the remaining playback time instead.
            if (position < nextEvent) {
                scheduleNextWidgetLyricRefresh(position)
                return@Runnable
            }
            updateWidgetLyrics(position, player.duration.coerceAtLeast(0L))
            scheduleNextWidgetLyricRefresh(position)
        }
        widgetLyricScheduledBoundaryMs = nextEvent
        widgetLyricUpdateRunnable = update
        handler.postDelayed(update, delayMs)
    }

    private fun updateWidgetProgress(
        pos: Long,
        dur: Long,
        updateLyrics: Boolean = true
    ) {
        // Do not dispatch RemoteViews work unless at least one D.S Music widget is installed.
        if (!DsmusicWidgetProvider.hasAnyWidgets(applicationContext)) return
        DsmusicWidgetProvider.updateAllWidgetsProgress(
            this,
            pos.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            dur.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
        if (updateLyrics) updateWidgetLyrics(pos, dur)
    }

    /**
     * Publishes only the current complete lyric line. Progress refreshes use a separate
     * queue so they cannot replace this event with an older lyric snapshot.
     */
    private fun updateWidgetLyrics(pos: Long, dur: Long) {
        if (!DsmusicWidgetProvider.hasAnyWidgets(applicationContext)) return
        DsmusicWidgetProvider.updateAllWidgetsLyrics(
            this,
            currentWidgetLyricText(pos, dur)
        )
    }

    private fun currentWidgetRepeatIconResource(): Int = when (
        viewModel?.repeatMode?.value ?: PlayerSharedViewModel.REPEAT_OFF
    ) {
        PlayerSharedViewModel.REPEAT_ONCE -> R.drawable.ic_notification_repeat_one
        PlayerSharedViewModel.REPEAT_INFINITY -> R.drawable.ic_notification_repeat_infinity
        else -> R.drawable.ic_notification_repeat_off
    }

    fun updateWidgetState(isPlaying: Boolean) {
        // Playback notifications and song changes also arrive when the user has never
        // added a widget. Return before formatting lyrics or reading widget artwork state.
        if (!DsmusicWidgetProvider.hasAnyWidgets(applicationContext)) return
        val item = player.currentMediaItem
        val mediaId = item?.mediaId?.takeIf { it.isNotBlank() }

        // During a Media3 transition, the active item can change before the Activity
        // ViewModel publishes its new currentSong value. Resolve the widget track by the
        // active mediaId first so its title and artwork cannot lag one track behind.
        val queuedSong = mediaId?.let { activeMediaId ->
            viewModel?.songList?.value?.firstOrNull {
                it.id.toString() == activeMediaId
            }
        }
        val viewModelSong = viewModel?.currentSong?.value
        val activeSong = if (item != null) {
            queuedSong
                ?: viewModelSong?.takeIf { it.id.toString() == mediaId }
                ?: widgetRestoredSong?.takeIf { it.id.toString() == mediaId }
        } else {
            viewModelSong ?: widgetRestoredSong
        }

        val playerHasItem = item != null
        val position = if (playerHasItem) {
            player.currentPosition.coerceAtLeast(0L)
        } else {
            com.dsann.music.util.PlaybackStateManager.getLastPosition(applicationContext).coerceAtLeast(0L)
        }
        val duration = player.duration.takeIf {
            playerHasItem && it > 0L && it != androidx.media3.common.C.TIME_UNSET
        } ?: activeSong?.duration?.coerceAtLeast(0L) ?: 0L
        val song = activeSong ?: item?.let { mediaItem ->
            val uri = mediaItem.localConfiguration?.uri
            val id = mediaId?.toLongOrNull()
                ?: uri?.toString()?.hashCode()?.toLong()
                ?: mediaId?.hashCode()?.toLong()
                ?: return@let null
            com.dsann.music.model.SongItem(
                id,
                mediaItem.mediaMetadata.title?.toString()?.ifBlank { "Quantune" } ?: "Quantune",
                mediaItem.mediaMetadata.artist?.toString()?.ifBlank { "Unknown Artist" } ?: "Unknown Artist",
                -1L,
                uri?.toString(),
                duration,
                System.currentTimeMillis()
            ).apply {
                if (uri != null) contentUri = uri
                thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString()
                albumName = mediaItem.mediaMetadata.albumTitle?.toString()
            }
        }
        DsmusicWidgetProvider.updateAllWidgets(
            this,
            song,
            isPlaying,
            position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            currentWidgetLyricText(position, duration),
            currentWidgetRepeatIconResource()
        )
    }

    private fun setupMediaSession() {
        createNotificationChannel()
        val intent = Intent(this, com.dsann.music.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Force standard commands to be available so buttons show in System UI
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SET_SHUFFLE_MODE)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SET_SHUFFLE_MODE -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
                super.setShuffleModeEnabled(shuffleModeEnabled)
                viewModel?.let { vm ->
                    if (vm.isShuffleEnabled.value != shuffleModeEnabled) {
                        vm.setShuffleEnabled(shuffleModeEnabled)
                    }
                }
            }

            override fun seekToNext() { playNext() }
            override fun seekToPrevious() { playPrevious() }
            override fun seekToNextMediaItem() { playNext() }
            override fun seekToPreviousMediaItem() { playPrevious() }
        }

        val session = MediaSession.Builder(this, forwardingPlayer)
            .setId(TAG)
            .setCallback(mediaSessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
            
        mediaSession = session
        addSession(session)

        setMediaNotificationProvider(CustomMediaNotificationProvider(this))
        updateMediaSessionCustomLayout()
    }

    private enum class NotificationPlaybackMode {
        SHUFFLE,
        REPEAT_OFF,
        REPEAT_ONCE,
        REPEAT_INFINITY
    }

    private fun currentNotificationPlaybackMode(vm: PlayerSharedViewModel?): NotificationPlaybackMode {
        val repeatMode = vm?.repeatMode?.value ?: PlayerSharedViewModel.REPEAT_OFF
        val shuffleEnabled = vm?.isShuffleEnabled?.value == true
        return when {
            shuffleEnabled -> NotificationPlaybackMode.SHUFFLE
            repeatMode == PlayerSharedViewModel.REPEAT_ONCE -> NotificationPlaybackMode.REPEAT_ONCE
            repeatMode == PlayerSharedViewModel.REPEAT_INFINITY -> NotificationPlaybackMode.REPEAT_INFINITY
            else -> NotificationPlaybackMode.REPEAT_OFF
        }
    }

    private fun notificationPlaybackModeIcon(mode: NotificationPlaybackMode): Int {
        return when (mode) {
            NotificationPlaybackMode.SHUFFLE -> R.drawable.ic_shuffle_noti
            NotificationPlaybackMode.REPEAT_OFF -> R.drawable.ic_notification_repeat_off
            NotificationPlaybackMode.REPEAT_ONCE -> R.drawable.ic_notification_repeat_one
            NotificationPlaybackMode.REPEAT_INFINITY -> R.drawable.ic_notification_repeat_infinity
        }
    }

    private val supportedSessionButtons = listOf("like", "shuffle", "repeat", "dislike", "back10", "ahead30")
    private val defaultSessionButtons = listOf("like", "shuffle")

    private fun configuredSessionButtons(): List<String> {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val raw = preferences.getString("session_buttons", null) ?: return defaultSessionButtons
        return raw.split(",").map { it.trim().lowercase() }
            .filter { it in supportedSessionButtons }.distinct().take(2)
    }

    private fun sessionButtonCommand(button: String): SessionCommand =
        SessionCommand(ACTION_SESSION_CUSTOM_PREFIX + button, Bundle.EMPTY)

    private fun sessionButtonLabel(button: String): String = when (button) {
        "like" -> "Like"
        "shuffle" -> "Shuffle"
        "repeat" -> when (viewModel?.repeatMode?.value ?: PlayerSharedViewModel.REPEAT_OFF) {
            PlayerSharedViewModel.REPEAT_ONCE -> "Repeat once"
            PlayerSharedViewModel.REPEAT_INFINITY -> "Repeat infinity"
            else -> "No repeat"
        }
        "dislike" -> "Not for me"
        "back10" -> "Back 10 seconds"
        "ahead30" -> "Forward 30 seconds"
        else -> button
    }

    private fun sessionButtonIcon(button: String): Int = when (button) {
        // Notification/lock-screen actions run in SystemUI, outside the app's theme.
        // These dedicated monochrome vectors use literal white paths instead of
        // ?attr/colorOnSurface, which may render as empty icons in notification rows.
        "like" -> if (viewModel?.isCurrentSongFavorite?.value == true) {
            R.drawable.ic_notification_like_filled
        } else {
            R.drawable.ic_notification_like_outline
        }
        "shuffle" -> R.drawable.ic_notification_shuffle
        "repeat" -> when (viewModel?.repeatMode?.value ?: PlayerSharedViewModel.REPEAT_OFF) {
            PlayerSharedViewModel.REPEAT_ONCE -> R.drawable.ic_notification_repeat_one
            PlayerSharedViewModel.REPEAT_INFINITY -> R.drawable.ic_notification_repeat_infinity
            else -> R.drawable.ic_notification_repeat_off
        }
        "dislike" -> R.drawable.ic_notification_dislike
        "back10" -> android.R.drawable.ic_media_rew
        "ahead30" -> android.R.drawable.ic_media_ff
        else -> R.drawable.ic_music_note
    }

    private fun handleSessionAction(button: String) {
        val vm = viewModel
        when (button) {
            "like" -> vm?.toggleFavorite()
            "shuffle" -> vm?.toggleShuffle()
            "repeat" -> vm?.toggleLoopMode()
            "dislike" -> vm?.toggleDislike()
            "back10" -> {
                val targetPosition = (getStablePlaybackPositionMs() - 10_000L).coerceAtLeast(0L)
                rememberPlaybackPosition(targetPosition)
                player.seekTo(targetPosition)
            }
            "ahead30" -> {
                val nextPosition = getStablePlaybackPositionMs() + 30_000L
                val duration = player.duration
                val targetPosition = if (duration > 0L) nextPosition.coerceAtMost(duration) else nextPosition
                rememberPlaybackPosition(targetPosition)
                player.seekTo(targetPosition)
            }
        }
        updateMediaSessionCustomLayout()
        updateWidgetState(player.isPlaying)
    }

    private fun cycleNotificationPlaybackMode() {
        val vm = viewModel ?: return
        val repeatMode = vm.repeatMode.value
        val shuffleEnabled = vm.isShuffleEnabled.value
        when {
            !shuffleEnabled && repeatMode == PlayerSharedViewModel.REPEAT_OFF -> {
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_OFF)
                vm.setShuffleEnabled(true)
            }
            shuffleEnabled -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_ONCE)
            }
            repeatMode == PlayerSharedViewModel.REPEAT_ONCE -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_INFINITY)
            }
            else -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_OFF)
            }
        }
        updateMediaSessionCustomLayout()
        updateWidgetState(player.isPlaying)
    }

    fun updateMediaSessionCustomLayout() {
        val session = mediaSession ?: return
        val selectedButtons = configuredSessionButtons()

        // Repeat/favorite observers can emit snapshots that do not change the visible
        // actions. Do not send an identical layout to every connected media controller.
        // The signature includes dynamic labels/icons so real repeat/favorite changes
        // still refresh the lock-screen and notification controls.
        val layoutSignature = selectedButtons.joinToString("|") { key ->
            "$key:${sessionButtonLabel(key)}:${sessionButtonIcon(key)}"
        }
        if (layoutSignature == lastMediaSessionLayoutSignature) return

        val buttons = selectedButtons.map { key ->
            CommandButton.Builder()
                .setDisplayName(sessionButtonLabel(key))
                .setSessionCommand(sessionButtonCommand(key))
                .setIconResId(sessionButtonIcon(key))
                .setEnabled(true)
                .build()
        }
        session.setCustomLayout(buttons)
        lastMediaSessionLayoutSignature = layoutSignature

        // The MediaSession layout updates lock-screen controls. Rebuild the
        // notification as well because its custom actions are added separately.
        refreshMediaNotification()
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            mediaSessionConnectionRequestCount++
            val requestNumber = mediaSessionConnectionRequestCount
            if (requestNumber <= 8 || requestNumber % 50 == 0) {
                Log.i(
                    TAG,
                    "MediaSession controller connection request #$requestNumber: " +
                        "package=${controller.packageName}, uid=${controller.uid}"
                )
            }

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()

            // Quantune exposes a regular MediaSession, not a MediaLibrarySession.
            // Advertising library commands is unnecessary and can cause controllers to
            // probe browsing operations that this service does not implement.
            val sessionCommandsBuilder = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(CMD_TOGGLE_LOOP)
                .add(CMD_CYCLE_PLAYBACK_MODE)
                .add(CMD_TOGGLE_FAV)
            // Authorise the whole supported action set for this controller connection.
            // The visible custom layout can then be reordered or changed without a
            // controller reconnecting just to receive a newly selected command.
            supportedSessionButtons.forEach { key ->
                sessionCommandsBuilder.add(sessionButtonCommand(key))
            }
            val sessionCommands = sessionCommandsBuilder.build()

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            Log.i(
                TAG,
                "MediaSession controller connected: package=${controller.packageName}, " +
                    "uid=${controller.uid}, activeControllers=${session.connectedControllers.size}"
            )
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            Log.i(
                TAG,
                "MediaSession controller disconnected: package=${controller.packageName}, " +
                    "uid=${controller.uid}"
            )
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_LOOP -> {
                    viewModel?.toggleLoopMode()
                    updateWidgetState(player.isPlaying)
                }
                ACTION_CYCLE_PLAYBACK_MODE -> {
                    cycleNotificationPlaybackMode()
                }
                ACTION_TOGGLE_FAV -> viewModel?.toggleFavorite()
                else -> if (customCommand.customAction.startsWith(ACTION_SESSION_CUSTOM_PREFIX)) {
                    handleSessionAction(customCommand.customAction.removePrefix(ACTION_SESSION_CUSTOM_PREFIX))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun getActionIntent(action: String, requestCode: Int): android.app.PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).apply { this.action = action }
        return android.app.PendingIntent.getService(
            this, requestCode, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "audio_playback_channel", "Audio Playback", android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows currently playing audio"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }




    private fun requestAudioFocus(): Boolean {
        if (!audioFocusEnabled) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttrs = LegacyAudioAttributes.Builder()
                .setUsage(LegacyAudioAttributes.USAGE_MEDIA)
                .setContentType(LegacyAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun startCrossfadeOut() {
        if (isCrossfading || nextPlayer == null) return
        isCrossfading = true
        crossfadeNextSongStarted = true

        val incomingPlayer = nextPlayer!!
        incomingPlayer.volume = 0f
        incomingPlayer.play()

        crossfadeAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = crossfadeDurationMs.toLong()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                player.volume = currentBaseVolume * (1f - fraction)
                incomingPlayer.volume = currentBaseVolume * fraction
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    completeCrossfadeHandoff()
                }
            })
            start()
        }
    }

    private fun completeCrossfadeHandoff() {
        crossfadeAnimator = null
        val oldPlayer = player
        val newPlayer = nextPlayer ?: return

        player = newPlayer

        // Follow the new primary player's session only while the visualizer is open.
        playbackVisualizerController?.switchSession(playerAudioSessionIds[newPlayer] ?: 0)
        

        // Stop old player and release it
        oldPlayer.stop()
        oldPlayer.removeListener(mainPlayerListener)
        playerAudioSessionIds.remove(oldPlayer)
        oldPlayer.release()

        // Re-initialize a fresh nextPlayer for the future
        nextPlayer = buildExoPlayer()
                    configurePlayerAudio(nextPlayer!!)

        // Apply listeners to the new primary player
        player.addListener(mainPlayerListener)
        mediaSession?.player = player

        // Re-attach hardware audio effects to the new audio session ID!

        isCrossfading = false
        crossfadeNextSongStarted = false

        // Inform the view model that a transition has occurred
        viewModel?.let { vm ->
            val nextIdx = vm.getNextSongIndex()
            if (nextIdx >= 0) {
                vm.updateSongIndexOnly(nextIdx)
                vm.songList.value.getOrNull(nextIdx)?.let { song ->
                    currentSongName = song.title
                    if (song.thumbnailUrl != null) {
                        setSongMetadataByUrl(song.title, song.thumbnailUrl)
                    } else {
                        setSongMetadataById(song.title, song.albumId)
                    }
                    if (!song.videoId.isNullOrEmpty()) {
                        telemetryManager.onSongChanged(song.videoId!!, song.duration)
                    }
                }
                // Preload the next song into the newly initialized nextPlayer
                scheduleQueuePreload(300L)
            }
        }
    }

    private fun handlePlaybackCompleted() {
        if (isCrossfading && crossfadeNextSongStarted) return
        viewModel?.let { vm ->
            // Clear the finished track's transient loading state. The next explicit
            // selection sets loading again while its stream is being resolved.
            vm.setLoading(false)
            vm.setPlaybackReady(false)
            vm.setIsPlaying(false)
            val endedSong = vm.currentSong.value
            val nextSong = vm.getNextSongIndex().let { index ->
                vm.songList.value.getOrNull(index)
            }
            cleanupEndedVideoCacheIfNeeded(endedSong, nextSong)
            rememberPlaybackPosition(0L)
            vm.setCurrentPosition(0)
            handler.post { vm.playNextSong() }
        }
    }

    /**
     * Remove only the video URL(s) used by the finished video session. Audio cache variants
     * and artwork are deliberately retained. The short delay lets Media3 finish replacing the
     * ended item; a quick replay of the same cached video cancels this cleanup safely.
     */
    private fun cleanupEndedVideoCacheIfNeeded(
        endedSong: com.dsann.music.model.SongItem?,
        nextSong: com.dsann.music.model.SongItem?
    ) {
        val finishedSong = endedSong ?: return
        val endedVideoId = finishedSong.videoId?.takeIf { it.isNotBlank() } ?: return
        if (activeVideoCacheVideoId != endedVideoId) return
        if (nextSong?.videoId == endedVideoId) return

        val cacheUris = activeVideoCacheUris
        if (cacheUris.isEmpty()) {
            activeVideoCacheVideoId = null
            return
        }

        // ExoPlayer is main-looper confined. Inspect its active item here, then do disk/cache
        // removal on IO so cleanup cannot stall playback or access the player from the wrong thread.
        serviceScope.launch {
            delay(180L)
            val currentSong = viewModel?.currentSong?.value
            val currentUri = if (isPlayerInitialized) {
                player.currentMediaItem?.localConfiguration?.uri?.toString()
            } else null
            val sameVideoRestarted = currentSong?.videoId == endedVideoId &&
                currentUri != null && currentUri in cacheUris &&
                isPlayerInitialized &&
                player.playbackState != Player.STATE_ENDED
            if (sameVideoRestarted) return@launch

            val removed = withContext(Dispatchers.IO) {
                removeMediaCacheResources(applicationContext, cacheUris)
            }
            Log.i(TAG, "Removed " + removed + " temporary video cache resource(s) after '" +
                finishedSong.title + "' ended")
            if (activeVideoCacheVideoId == endedVideoId && activeVideoCacheUris == cacheUris) {
                activeVideoCacheVideoId = null
                activeVideoCacheUris = emptySet()
            }
        }
    }

    private fun loadAudioPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        crossfadeDurationMs = (prefs.getFloat(KEY_CROSSFADE_DURATION, 0f) * 1000).toInt()
        audioFocusEnabled = prefs.getBoolean(KEY_AUDIO_FOCUS, true)
        if (::player.isInitialized) configurePlayerAudio(player)
        nextPlayer?.let(::configurePlayerAudio)
    }

    private fun setupPreferenceListener() {
        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (KEY_CROSSFADE_DURATION == key || KEY_AUDIO_FOCUS == key) loadAudioPreferences()
            if (com.dsann.music.ui.SettingsViewModel.KEY_VOLUME_NORMALIZATION_ENABLED == key || 
                com.dsann.music.ui.SettingsViewModel.KEY_TARGET_LUFS == key) {
                }
        }
    }

    private fun isAutoplayEnabled(): Boolean =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("autoplay_enabled", true)

    /**
     * Load a progressive/muxed video stream with an explicit video MIME type. Signed
     * videoplayback URLs have no useful extension, so the generic audio URL loader may
     * initialize the first stream incorrectly; this path mirrors the later quality reload.
     */
    private fun loadVideoWithMuxedStream(
        videoUrl: String,
        startPositionMs: Long,
        autoPlay: Boolean,
        videoMimeType: String?
    ) {
        if (!isPlayerInitialized || videoUrl.isBlank()) return
        val song = viewModel?.currentSong?.value
        val videoUri = videoUrl.toUri()
        rememberPlaybackPosition(startPositionMs)

        cancelCurrentTrackCache()
        cacheAssetsSongId = null
        player.pause()
        player.stop()
        player.clearMediaItems()

        if (isCrossfading) {
            nextPlayer?.stop()
            nextPlayer?.release()
            nextPlayer = null
            crossfadeAnimator?.cancel()
            crossfadeAnimator = null
            isCrossfading = false
            crossfadeNextSongStarted = false
        }

        currentSourceUri = videoUri
        originalSourceUri = song?.videoId?.let { "innertube://$it".toUri() } ?: videoUri
        isNextSongPreCached = false
        pendingSeekPos = -1L
        shouldAutoPlay = false
        viewModel?.setLoading(true)

        try {
            val mediaItem = buildMediaItem(videoUri).buildUpon()
                .setMimeType(videoMimeType?.takeIf { it.isNotBlank() } ?: "video/mp4")
                .build()
            val sourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                createCacheDataSourceFactory(applicationContext)
            )
            val source = sourceFactory.createMediaSource(mediaItem)

            player.volume = currentBaseVolume.coerceIn(0f, 1f)
            player.setMediaSource(source, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            if (autoPlay) {
                player.play()
                isPausedByFocusLoss = false
            }
            Log.d(
                TAG,
                "Loaded muxed video stream: mime=${videoMimeType ?: "video/mp4"}, " +
                    "start=${startPositionMs}ms autoPlay=${autoPlay}"
            )
        } catch (error: Exception) {
            viewModel?.setLoading(false)
            Log.e(TAG, "Could not load muxed video stream", error)
            throw error
        }
    }

    /**
     * Play YouTube's adaptive DASH video-only and audio-only streams as one synchronized
     * Media3 item. This fallback is required for videos where YouTube no longer supplies
     * progressive/muxed streams.
     */
    private fun loadVideoWithSeparateAudio(
        videoUrl: String,
        audioUrl: String,
        startPositionMs: Long,
        autoPlay: Boolean,
        videoMimeType: String? = null,
        audioMimeType: String? = null
    ) {
        if (!isPlayerInitialized || videoUrl.isBlank() || audioUrl.isBlank()) return
        val song = viewModel?.currentSong?.value
        val songId = song?.id?.toString().orEmpty()
        val videoUri = videoUrl.toUri()
        val audioUri = audioUrl.toUri()
        rememberPlaybackPosition(startPositionMs)

        cancelCurrentTrackCache()
        cacheAssetsSongId = null
        player.pause()
        player.stop()
        player.clearMediaItems()

        if (isCrossfading) {
            nextPlayer?.stop()
            nextPlayer?.release()
            nextPlayer = null
            crossfadeAnimator?.cancel()
            crossfadeAnimator = null
            isCrossfading = false
            crossfadeNextSongStarted = false
        }

        currentSourceUri = videoUri
        originalSourceUri = song?.videoId?.let { "innertube://$it".toUri() } ?: videoUri
        isNextSongPreCached = false
        pendingSeekPos = -1L
        shouldAutoPlay = false
        viewModel?.setLoading(true)

        try {
            val videoItem = buildMediaItem(videoUri).buildUpon()
                .setMimeType(videoMimeType?.takeIf { it.isNotBlank() } ?: "video/mp4")
                .build()
            val audioItem = MediaItem.Builder()
                .setUri(audioUri)
                .setMediaId("$songId-audio")
                .setMimeType(audioMimeType?.takeIf { it.isNotBlank() } ?: "audio/mp4")
                .build()

            val dataSourceFactory = createCacheDataSourceFactory(applicationContext)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            val videoSource = mediaSourceFactory.createMediaSource(videoItem)
            val audioSource = mediaSourceFactory.createMediaSource(audioItem)
            val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(
                true,
                videoSource,
                audioSource
            )

            player.volume = currentBaseVolume.coerceIn(0f, 1f)
            player.setMediaSource(mergedSource, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            if (autoPlay) {
                player.play()
                isPausedByFocusLoss = false
            }
            Log.d(
                TAG,
                "Loaded adaptive video/audio pair: video=${videoMimeType ?: "video/mp4"}, " +
                    "audio=${audioMimeType ?: "audio/mp4"}, start=${startPositionMs}ms"
            )
        } catch (error: Exception) {
            viewModel?.setLoading(false)
            Log.e(TAG, "Could not merge YouTube video/audio streams", error)
            throw error
        }
    }

    /**
     * Switch the current YouTube Music track between its audio stream and a compatible
     * muxed video+audio stream. The same ExoPlayer and queue item remain in use.
     */
    fun setVideoMode(enabled: Boolean) {
        val vm = viewModel ?: return
        if (!isPlayerInitialized) return

        val song = vm.currentSong.value ?: return
        val videoId = song.videoId?.takeIf { it.isNotBlank() }

        if (!enabled) {
            // Explicitly switching to audio also invalidates any video lookup still in flight.
            videoModeResolutionGeneration += 1L
            videoModeResolutionJob?.cancel()
            videoModeResolutionJob = null
            isResolvingVideoMode = false

            if (!vm.isVideoMode.value) return
            val position = captureVideoModePosition(vm)
            val wasPlaying = player.isPlaying || player.playWhenReady || vm.isPlaying.value
            player.pause()
            vm.setVideoModeState(false)
            vm.setVideoPlaybackMetadata(null)

            // A local track may have replaced the previous video while its mode was active.
            // In that case its own media item is already loaded; do not build an innertube URI.
            if (videoId != null) {
                if (wasPlaying) {
                    loadAndPlay("innertube://$videoId".toUri(), position)
                } else {
                    loadAudio("innertube://$videoId".toUri(), position)
                }
            } else if (wasPlaying) {
                player.play()
            }
            return
        }

        if (videoId == null) {
            // Track changes can move from a YouTube song to a local file while video mode
            // is active. Drop video UI immediately and leave the new audio source untouched.
            if (vm.isVideoMode.value) {
                vm.setVideoModeState(false)
                vm.setVideoPlaybackMetadata(null)
            }
            vm.setLoading(false)
            com.dsann.music.ui.SnackbarController.showMessage(
                "No online video is available for this track. Switched to audio."
            )
            return
        }

        // Reopening Video for the already-bound track is a no-op. When the queue changes,
        // activeVideoCacheVideoId still points at the previous track, so a fresh resolve runs.
        if (vm.isVideoMode.value && activeVideoCacheVideoId == videoId) return

        // Cancel an older track's resolver instead of allowing it to install a stale video
        // over the song the listener has since selected.
        videoModeResolutionGeneration += 1L
        val requestGeneration = videoModeResolutionGeneration
        videoModeResolutionJob?.cancel()

        val songId = song.id
        val isRebindingTrack = vm.isVideoMode.value &&
            !activeVideoCacheVideoId.isNullOrBlank() && activeVideoCacheVideoId != videoId
        // A queue transition starts the new song at zero; the previous video player's
        // timestamp belongs to another track and must never leak into this video.
        val position = if (isRebindingTrack) 0L else captureVideoModePosition(vm)
        val wasPlaying = player.isPlaying || player.playWhenReady ||
            vm.isPlaying.value || shouldAutoPlay
        // A new queue item must not keep the previous track's video window alive while
        // its own stream is resolving. Audio for this track is already loaded underneath.
        if (vm.isVideoMode.value) {
            vm.setVideoModeState(false)
            vm.setVideoPlaybackMetadata(null)
        }
        isResolvingVideoMode = true
        vm.setLoading(true)
        vm.setVideoPlaybackMetadata(null)

        // The queue has already loaded this new song's audio source. Pause it while looking
        // for a matching video, then resume that exact audio item if the lookup fails.
        player.pause()
        videoModeResolutionJob = serviceScope.launch {
            try {
                val videoOption = withContext(Dispatchers.IO) {
                    com.dsann.music.data.innertube.NewPipeStreamExtractor
                        .getDataSaverVideoOption(videoId)
                }

                val latestSong = viewModel?.currentSong?.value
                if (
                    requestGeneration != videoModeResolutionGeneration ||
                    latestSong?.id != songId ||
                    latestSong?.videoId != videoId
                ) {
                    return@launch
                }

                if (videoOption == null) {
                    vm.setVideoModeState(false)
                    vm.setVideoPlaybackMetadata(null)
                    vm.setLoading(false)
                    if (wasPlaying) player.play()
                    showPlaybackFailure(
                        IllegalStateException("No video stream is available for this track"),
                        context = "Video playback",
                        action = "staying in audio mode"
                    )
                    return@launch
                }

                // Audio resolution may still be finishing for this exact queue item.
                // Once a matching video is ready, cancel it so it cannot overwrite the
                // video MediaItem after the video source has been installed.
                resolveJob?.cancel()
                resolveTargetSongId = "video-mode:$videoId:$requestGeneration"

                activeVideoCacheVideoId = videoId
                activeVideoCacheUris = buildSet {
                    add(videoOption.url)
                    videoOption.audioUrl?.takeIf { it.isNotBlank() }?.let(::add)
                }
                vm.setVideoPlaybackMetadata(
                    com.dsann.music.viewmodel.VideoPlaybackMetadata(
                        title = videoOption.videoTitle?.takeIf { it.isNotBlank() } ?: song.title,
                        uploader = videoOption.videoUploader?.takeIf { it.isNotBlank() } ?: song.artist,
                        durationMs = videoOption.durationMs.takeIf { it > 0L } ?: song.duration,
                        contentLengthBytes = videoOption.contentLengthBytes,
                        bitrateBps = videoOption.bitrateBps,
                        resolution = videoOption.label,
                        mimeType = videoOption.mimeType,
                        description = videoOption.videoDescription,
                        viewCount = videoOption.videoViewCount,
                        likeCount = videoOption.videoLikeCount,
                        uploadDate = videoOption.videoUploadDate,
                        thumbnailUrl = videoOption.videoThumbnailUrl,
                        uploaderUrl = videoOption.videoUploaderUrl
                    )
                )

                if (videoOption.usesSeparateAudio) {
                    loadVideoWithSeparateAudio(
                        videoUrl = videoOption.url,
                        audioUrl = videoOption.audioUrl.orEmpty(),
                        startPositionMs = position,
                        autoPlay = wasPlaying,
                        videoMimeType = videoOption.mimeType,
                        audioMimeType = videoOption.audioMimeType
                    )
                } else {
                    loadVideoWithMuxedStream(
                        videoUrl = videoOption.url,
                        startPositionMs = position,
                        autoPlay = wasPlaying,
                        videoMimeType = videoOption.mimeType
                    )
                }
                // Publish Video mode only after the matching video item has been installed.
                vm.setVideoModeState(true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val latestSong = viewModel?.currentSong?.value
                if (
                    requestGeneration == videoModeResolutionGeneration &&
                    latestSong?.id == songId &&
                    latestSong?.videoId == videoId
                ) {
                    vm.setLoading(false)
                    vm.setVideoModeState(false)
                    vm.setVideoPlaybackMetadata(null)
                    if (wasPlaying) player.play()
                    Log.w(TAG, "Video mode switch failed for $videoId", error)
                    showPlaybackFailure(
                        error,
                        context = "Video playback",
                        action = "switching to audio"
                    )
                }
            } finally {
                if (requestGeneration == videoModeResolutionGeneration) {
                    isResolvingVideoMode = false
                    videoModeResolutionJob = null
                }
            }
        }
    }

    /**
     * Switch video quality without changing the queue item or playback timestamp.
     * The URL is a muxed audio+video stream selected from the available NewPipe formats.
     */
    fun setVideoStreamQuality(
        streamUrl: String,
        audioUrl: String? = null,
        videoMimeType: String? = null,
        audioMimeType: String? = null
    ) {
        val vm = viewModel ?: return
        if (!isPlayerInitialized || !vm.isVideoMode.value || streamUrl.isBlank()) return
        val song = vm.currentSong.value ?: return
        if (song.videoId.isNullOrBlank()) return
        val position = captureVideoModePosition(vm)
        val wasPlaying = player.isPlaying
        if (activeVideoCacheVideoId != song.videoId) {
            activeVideoCacheVideoId = song.videoId
            activeVideoCacheUris = emptySet()
        }
        activeVideoCacheUris = activeVideoCacheUris + listOfNotNull(
            streamUrl.takeIf { it.isNotBlank() },
            audioUrl?.takeIf { it.isNotBlank() }
        )
        // Stop the old resolution before loading the new one to avoid overlapping network use.
        player.pause()
        if (!audioUrl.isNullOrBlank()) {
            loadVideoWithSeparateAudio(
                videoUrl = streamUrl,
                audioUrl = audioUrl,
                startPositionMs = position,
                autoPlay = wasPlaying,
                videoMimeType = videoMimeType,
                audioMimeType = audioMimeType
            )
        } else {
            loadVideoWithMuxedStream(
                videoUrl = streamUrl,
                startPositionMs = position,
                autoPlay = wasPlaying,
                videoMimeType = videoMimeType
            )
        }
    }

    private fun captureVideoModePosition(vm: PlayerSharedViewModel): Long {
        val fallbackPosition = vm.currentPosition.value.toLong().coerceAtLeast(0L)
        if (!isPlayerInitialized) return fallbackPosition
        return getStablePlaybackPositionMs()
    }

    /**
     * Direct application-thread playback clock for frame-paced lyric rendering.
     * The public UI StateFlow is intentionally throttled on older devices; karaoke
     * must not use that coarse UI snapshot as its audio clock.
     */
    fun getCurrentPositionForLyrics(): Int? {
        if (!isPlayerInitialized) return null
        return getStablePlaybackPositionMs()
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun getPlaybackSpeedForLyrics(): Float {
        if (!isPlayerInitialized) return 1f
        return player.playbackParameters.speed.takeIf { it.isFinite() && it > 0f } ?: 1f
    }

    fun getPlayerInstance(): ExoPlayer = player

    /** Starts playback-output FFT capture only for the full-screen visualizer. */
    @OptIn(UnstableApi::class)
    fun startPlaybackVisualizer(): Boolean {
        if (!isPlayerInitialized) return false

        val activePlayer = player
        // Prefer Media3's live session ID. The cached generated ID is only a fallback:
        // an AudioTrack recreation can otherwise leave the effect attached to an old session.
        val reportedSessionId = runCatching { activePlayer.audioSessionId }.getOrDefault(-1)
        val cachedSessionId = playerAudioSessionIds[activePlayer] ?: 0
        val sessionId = reportedSessionId.takeIf { it > 0 } ?: cachedSessionId
        if (sessionId <= 0) {
            Log.w(TAG, "Visualizer start skipped: no active audio session (reported=$reportedSessionId, cached=$cachedSessionId)")
            return false
        }
        playerAudioSessionIds[activePlayer] = sessionId

        val controller = playbackVisualizerController ?: synchronized(this) {
            playbackVisualizerController ?: PlaybackVisualizerController { levels ->
                playbackVisualizerLevelsState.value = levels
            }.also {
                playbackVisualizerController = it
            }
        }
        val started = controller.start(sessionId)
        Log.d(TAG, "Visualizer start: session=$sessionId started=$started playing=${activePlayer.isPlaying}")
        return started
    }

    fun stopPlaybackVisualizer() {
        playbackVisualizerController?.stop()
    }

    /** Stable stream observed by Compose before and after lazy capture starts. */
    val playbackVisualizerLevels: StateFlow<FloatArray>
        get() = playbackVisualizerLevelsState

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    @OptIn(UnstableApi::class)
    private inner class CustomMediaNotificationProvider(context: Context) : 
        DefaultMediaNotificationProvider(context) {
        
        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: com.google.common.collect.ImmutableList<CommandButton>,
            notificationBuilder: androidx.core.app.NotificationCompat.Builder,
            actionFactory: androidx.media3.session.MediaNotification.ActionFactory
        ): IntArray {
            val isPlaying = player.isPlaying
            Log.d(TAG, "Notification update: isPlaying=$isPlaying, song=$currentSongName")
            
            // Explicitly set channel and properties to match Java example exactly
            notificationBuilder.setSmallIcon(R.drawable.ic_notification_music)
            val activeMediaItem = player.currentMediaItem
            val activeMetadata = activeMediaItem?.mediaMetadata
            val notificationTitle = activeMetadata?.title?.toString()
                ?.takeIf { it.isNotBlank() } ?: currentSongName
            val notificationArtist = activeMetadata?.artist?.toString()
                ?.takeIf { it.isNotBlank() } ?: "Now Playing"
            val notificationArtwork = currentAlbumArt?.takeIf {
                !activeMediaItem?.mediaId.isNullOrBlank() &&
                    currentAlbumArtSongId == activeMediaItem?.mediaId
            }
            notificationBuilder.setContentTitle(notificationTitle)
            notificationBuilder.setContentText(notificationArtist)
            notificationBuilder.setSubText("Quantune")
            notificationBuilder.setChannelId("audio_playback_channel")
            notificationBuilder.setOngoing(isPlaying)
            notificationBuilder.setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            notificationBuilder.setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            notificationBuilder.setOnlyAlertOnce(true)
            notificationBuilder.setShowWhen(false)
            notificationBuilder.setCategory(androidx.core.app.NotificationCompat.CATEGORY_TRANSPORT)
            
            // Do not clear Media3's metadata artwork when our asynchronous bitmap has not
            // arrived yet. Only replace the provider's icon with a confirmed matching cover.
            notificationArtwork?.let(notificationBuilder::setLargeIcon)

            // The first two selected session actions sit either side of the three
            // transport buttons, matching the configured media-session action ordering.
            notificationBuilder.clearActions()
            val selectedNotificationButtons = configuredSessionButtons().take(2)
            selectedNotificationButtons.getOrNull(0)?.let { key ->
                notificationBuilder.addAction(
                    sessionButtonIcon(key),
                    sessionButtonLabel(key),
                    getActionIntent(ACTION_SESSION_CUSTOM_PREFIX + key, 20)
                )
            }

            val previousIndex = if (selectedNotificationButtons.isNotEmpty()) 1 else 0
            notificationBuilder.addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                getActionIntent(ACTION_PREVIOUS, 11)
            )

            val playIndex = previousIndex + 1
            notificationBuilder.addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) getActionIntent(ACTION_PAUSE, 12) else getActionIntent(ACTION_PLAY, 13)
            )

            val nextIndex = playIndex + 1
            notificationBuilder.addAction(
                android.R.drawable.ic_media_next,
                "Next",
                getActionIntent(ACTION_NEXT, 14)
            )

            selectedNotificationButtons.getOrNull(1)?.let { key ->
                notificationBuilder.addAction(
                    sessionButtonIcon(key),
                    sessionButtonLabel(key),
                    getActionIntent(ACTION_SESSION_CUSTOM_PREFIX + key, 21)
                )
            }

            // Compact MediaStyle notifications show only these three actions. The
            // previous implementation always selected Previous/Play/Next, so the two
            // user-configured actions were invisible unless the notification was expanded.
            // Keep Play visible and surface the selected actions in the collapsed row.
            val compactIndices = when (selectedNotificationButtons.size) {
                0 -> intArrayOf(previousIndex, playIndex, nextIndex)
                1 -> intArrayOf(0, playIndex, nextIndex)
                else -> intArrayOf(0, playIndex, nextIndex + 1)
            }
            val style = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(*compactIndices)
            notificationBuilder.setStyle(style)

            return compactIndices
        }
    }

}


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class AdjustableCacheEvictor(initialEnabled: Boolean, initialMaxBytes: Long) : CacheEvictor {
    private val lock = Any()
    private val spans = TreeSet<CacheSpan>(Comparator { a, b ->
        val touched = a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
        if (touched != 0) touched else {
            val keyOrder = a.key.compareTo(b.key)
            if (keyOrder != 0) keyOrder else a.position.compareTo(b.position)
        }
    })
    private val blockedKeys = HashSet<String>()
    @Volatile private var enabled = initialEnabled
    @Volatile private var maxBytes = initialMaxBytes
    @Volatile private var initialized = false
    @Volatile private var activeCache: Cache? = null

    override fun requiresCacheSpanTouches(): Boolean = true
    override fun onCacheInitialized() {
        initialized = true
        activeCache?.let { evictIfNeeded(it) }
    }
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        activeCache = cache
        if (initialized) evictIfNeeded(cache, length.coerceAtLeast(0L))
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        activeCache = cache
        val blocked = synchronized(lock) {
            if (span.key in blockedKeys) true else {
                spans.add(span)
                false
            }
        }
        if (blocked) {
            // A still-open player data source may finish a write after a user removes a
            // cached song. Drop that span immediately instead of resurrecting the entry.
            try {
                cache.removeSpan(span)
            } catch (error: Exception) {
                android.util.Log.w("DsmusicCache", "Unable to discard a removed cache span", error)
            }
            return
        }
        if (initialized) evictIfNeeded(cache)
    }
    fun blockKeys(cache: Cache, keys: Set<String>) {
        if (keys.isEmpty()) return
        synchronized(lock) { blockedKeys.addAll(keys) }
        keys.forEach { key ->
            cache.getCachedSpans(key).forEach { span ->
                try {
                    cache.removeSpan(span)
                } catch (error: Exception) {
                    android.util.Log.w("DsmusicCache", "Unable to clear blocked cache span", error)
                }
            }
        }
    }
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        activeCache = cache
        synchronized(lock) { spans.remove(span) }
    }
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        activeCache = cache
        synchronized(lock) { spans.remove(oldSpan); spans.add(newSpan) }
        if (initialized) evictIfNeeded(cache)
    }
    fun configure(cache: Cache, enabled: Boolean, maxBytes: Long) {
        this.enabled = enabled
        this.maxBytes = maxBytes.coerceAtLeast(1L)
        activeCache = cache
        // Spans are tracked by onSpanAdded/onSpanRemoved callbacks from SimpleCache. Avoid
        // cache.keys/getCachedSpans scans here: they duplicate SimpleCache's startup index scan.
        if (initialized) evictIfNeeded(cache)
    }
    fun refresh(cache: Cache) {
        activeCache = cache
        synchronized(lock) {
            spans.clear()
            cache.keys.forEach { key ->
                if (key !in blockedKeys) spans.addAll(cache.getCachedSpans(key))
            }
        }
        if (initialized) evictIfNeeded(cache)
    }
    private fun evictIfNeeded(cache: Cache, incomingBytes: Long = 0L) {
        while (true) {
            val removeOne = synchronized(lock) { !enabled || cache.cacheSpace + incomingBytes > maxBytes }
            if (!removeOne) return
            val oldest = synchronized(lock) { if (spans.isEmpty()) null else spans.pollFirst() } ?: return
            try { cache.removeSpan(oldest) }
            catch (error: Exception) { android.util.Log.w("DsmusicCache", "Unable to evict cached audio", error) }
        }
    }
}
