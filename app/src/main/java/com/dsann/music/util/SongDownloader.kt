package com.dsann.music.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.ContentUris
import android.net.Uri
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.dsann.music.R
import com.dsann.music.data.innertube.NewPipeStreamExtractor
import com.dsann.music.model.SongItem
import com.dsann.music.service.AudioPlaybackService
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object SongDownloader {
    private const val TAG = "SongDownloader"
    private const val CHANNEL_ID = "dsmusic_downloads"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    private val _activeDownloadIds = MutableStateFlow<Set<String>>(emptySet())
    val activeDownloadIds: StateFlow<Set<String>> = _activeDownloadIds.asStateFlow()

    // Shared completed state keeps every download control consistent across screens.
    private val _downloadedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedVideoIds: StateFlow<Set<String>> = _downloadedVideoIds.asStateFlow()

    fun cleanTitle(title: String): String {
        var clean = title
        val regexes = listOf(
            "(?i)\\[official music video\\]",
            "(?i)\\(official music video\\)",
            "(?i)\\[official video\\]",
            "(?i)\\(official video\\)",
            "(?i)\\[lyrics\\]",
            "(?i)\\(lyrics\\)",
            "(?i)\\[lyric video\\]",
            "(?i)\\(lyric video\\)",
            "(?i)\\[official audio\\]",
            "(?i)\\(official audio\\)",
            "(?i)official music video",
            "(?i)official video",
            "(?i)lyric video",
            "(?i)\\[video\\]",
            "(?i)\\(video\\)",
            "(?i)\\[official release\\]",
            "(?i)\\(official release\\)"
        )
        for (regex in regexes) {
            clean = clean.replace(Regex(regex), "")
        }
        // Remove trailing dashes, slashes, and spaces
        return clean.trim().replace(Regex("\\s+"), " ").removeSuffix("-").removeSuffix("/").trim()
    }

    fun cleanArtist(artist: String): String {
        return artist.replace(Regex("(?i)\\s*-\\s*Topic"), "").trim().replace(Regex("\\s+"), " ")
    }

    fun cleanAlbum(title: String): String {
        var clean = cleanTitle(title)
        val albumRegexes = listOf(
            "(?i)\\s*\\((slowed|reverb|speed up|sped up|remix|slowed\\s*\\+\\s*reverb|slowed\\s*and\\s*reverb)\\)",
            "(?i)\\s*\\[(slowed|reverb|speed up|sped up|remix|slowed\\s*\\+\\s*reverb|slowed\\s*and\\s*reverb)\\]"
        )
        for (regex in albumRegexes) {
            clean = clean.replace(Regex(regex), "")
        }
        return clean.trim().replace(Regex("\\s+"), " ")
    }

    private class DownloadTask {
        @Volatile var job: Job? = null
        @Volatile var connection: HttpURLConnection? = null
        @Volatile var call: okhttp3.Call? = null
        @Volatile var outputFile: File? = null
        @Volatile var cancelled: Boolean = false
    }

    private data class CachedStreamCandidate(
        val cacheKey: String,
        val spans: List<CacheSpan>,
        val contentLength: Long,
        val mimeType: String?,
        val cachedBytes: Long,
        val isComplete: Boolean,
        val metadata: com.dsann.music.service.CachedMediaMetadata
    )

    private val activeDownloads = ConcurrentHashMap<String, DownloadTask>()

    // SongItem.id is the raw String.hashCode() widened to Long and may be negative.
    // Keep notification and PendingIntent IDs positive without collapsing negatives to 1.
    private fun notificationIdFor(videoId: String): Int =
        (videoId.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    fun isDownloading(videoId: String?): Boolean =
        !videoId.isNullOrBlank() && activeDownloads.containsKey(videoId)

    /** Refresh persisted download state off the main thread, including deleted-file results. */
    fun refreshDownloadedStatus(context: Context, song: SongItem) {
        refreshDownloadedStatuses(context, listOf(song))
    }

    fun refreshDownloadedStatuses(context: Context, songs: List<SongItem>) {
        val candidates = songs.filter { !it.videoId.isNullOrBlank() }.distinctBy { it.videoId }
        if (candidates.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            candidates.forEach { song ->
                val videoId = song.videoId ?: return@forEach
                val downloaded = isSongDownloaded(context.applicationContext, song)
                _downloadedVideoIds.update { current ->
                    if (downloaded) current + videoId else current - videoId
                }
            }
        }
    }

    private const val PREF_DOWNLOADED_TRACK_URIS = "dsmusic_downloaded_track_uris"

    @Volatile
    private var downloadUriPreferencesMigrated = false

    /**
     * Moves saved download URI mappings into the current preference file.
     * Prior files are identified by their uri_/format_uri_ keys, not their old names.
     */
    private fun downloadUriPreferences(context: Context): android.content.SharedPreferences {
        val target = context.getSharedPreferences(PREF_DOWNLOADED_TRACK_URIS, Context.MODE_PRIVATE)
        if (downloadUriPreferencesMigrated) return target

        synchronized(this) {
            if (downloadUriPreferencesMigrated) return target
            if (!target.getBoolean("_dsmusic_storage_migrated", false)) {
                val directory = File(context.applicationInfo.dataDir, "shared_prefs")
                directory.listFiles()?.forEach { file ->
                    val filename = file.name
                    if (!filename.endsWith(".xml") ||
                        filename == "$PREF_DOWNLOADED_TRACK_URIS.xml"
                    ) return@forEach

                    val sourceName = filename.removeSuffix(".xml")
                    val source = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE)
                    val mappings = source.all.filter { (key, value) ->
                        value is String &&
                            (key.startsWith("uri_") || "_uri_" in key || key.startsWith("name_"))
                    }
                    if (mappings.isEmpty()) return@forEach

                    val destination = target.edit()
                    mappings.forEach { (key, value) ->
                        if (!target.contains(key)) destination.putString(key, value as String)
                    }
                    if (destination.commit()) {
                        val cleanup = source.edit()
                        mappings.keys.forEach { cleanup.remove(it) }
                        cleanup.commit()
                        if (source.all.isEmpty()) file.delete()
                    }
                }
                target.edit().putBoolean("_dsmusic_storage_migrated", true).apply()
            }
            downloadUriPreferencesMigrated = true
        }
        return target
    }

    /**
     * Resolve the original online video IDs for D.S Music downloaded MediaStore URIs.
     * This is metadata only: callers continue to play the exact local URI.
     */
    fun getDownloadedVideoIdsByUri(context: Context): Map<String, String> {
        val saved = downloadUriPreferences(context).all
        return saved.entries.mapNotNull { entry ->
            val key = entry.key
            // The generic playback mapping is uri_<videoId>. Converted exports use
            // <format>_uri_<videoId> so MP3/M4A/Opus copies retain the same source identity.
            val videoId = when {
                key.startsWith("uri_") -> key.removePrefix("uri_")
                "_uri_" in key -> key.substringAfter("_uri_")
                else -> ""
            }.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val uri = entry.value as? String ?: return@mapNotNull null
            if (uri.isBlank()) null else uri to videoId
        }.toMap()
    }

    /**
     * Resolve an app-downloaded audio file before trying Media3's online stream resolver.
     * Video-ID mappings are preferred; title/artist filenames and the legacy D.S Music folder
     * provide a migration path for downloads created before mappings were recorded.
     */
    fun findDownloadedUri(
        context: Context,
        videoId: String?,
        title: String,
        artist: String
    ): Uri? {
        if (videoId.isNullOrBlank()) return null
        val prefs = downloadUriPreferences(context)
        val mappingKey = "uri_$videoId"
        val mapped = prefs.getString(mappingKey, null)?.let { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()
        }
        if (mapped != null && isUriReadable(context, mapped)) return mapped
        if (mapped != null) prefs.edit().remove(mappingKey).apply()

        val baseName = "${cleanTitle(title)} - ${cleanArtist(artist)}"
            .replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
        // Prefer the new Ogg Opus export while retaining lookup for older Matroska/WebM downloads.
        val extensions = listOf("m4a", "mp3", "opus", "mka", "webm", "ogg", "flac", "aac", "wav")
        for (extension in extensions) {
            val filename = "$baseName.$extension"
            val found = findDsmusicMediaStoreUri(context, filename)
                ?: findLegacyDownloadedFile(filename)
            if (found != null && isUriReadable(context, found)) {
                prefs.edit().putString(mappingKey, found.toString()).apply()
                return found
            }
        }
        return null
    }

    /**
     * Resolve only the app's Ogg Opus download. Do not return an MP3/M4A playback file:
     * conversion must always read the original Opus bitstream.
     */
    fun findDownloadedOpusUri(context: Context, song: SongItem): Uri? {
        val directPath = song.path?.takeIf {
            it.isNotBlank() && !it.contains("://") && it.endsWith(".opus", ignoreCase = true)
        }?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        if (directPath != null) return Uri.fromFile(directPath)

        val possibleUris = listOfNotNull(
            song.path?.takeIf { it.startsWith("content://") || it.startsWith("file://") }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() },
            song.contentUri
        )
        possibleUris.firstOrNull { uri ->
            uriExtension(context, uri) == "opus" && isUriReadable(context, uri)
        }?.let { return it }

        val videoId = song.videoId?.takeIf { it.isNotBlank() } ?: return null
        val prefs = downloadUriPreferences(context)
        // Keep an Opus-specific mapping. The generic uri_$videoId entry is shared by the
        // app's selected download formats and can be replaced by later M4A/MP3 downloads.
        val mappedOpus = prefs.getString("opus_uri_$videoId", null)?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
        if (mappedOpus != null && uriExtension(context, mappedOpus) == "opus" &&
            isUriReadable(context, mappedOpus)
        ) {
            return mappedOpus
        }
        if (mappedOpus != null) prefs.edit().remove("opus_uri_$videoId").apply()

        val genericMapped = prefs.getString("uri_$videoId", null)?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
        if (genericMapped != null && uriExtension(context, genericMapped) == "opus" &&
            isUriReadable(context, genericMapped)
        ) {
            prefs.edit().putString("opus_uri_$videoId", genericMapped.toString()).apply()
            return genericMapped
        }

        val baseName = "${cleanTitle(song.title)} - ${cleanArtist(song.artist)}"
            .replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
        val filename = "$baseName.opus"
        return (findDsmusicMediaStoreUri(context, filename) ?: findLegacyDownloadedFile(filename))
            ?.takeIf { isUriReadable(context, it) }
    }

    /** Resolve only the currently selected download format, rather than any old format. */
    fun findDownloadedFormatUri(context: Context, song: SongItem, format: String): Uri? {
        val extension = NewPipeStreamExtractor.normalizeDownloadAudioFormat(format)
        val directPath = song.path?.takeIf {
            it.isNotBlank() && !it.contains("://") &&
                it.endsWith(".$extension", ignoreCase = true)
        }?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        if (directPath != null) return Uri.fromFile(directPath)

        val possibleUris = listOfNotNull(
            song.path?.takeIf { it.startsWith("content://") || it.startsWith("file://") }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() },
            song.contentUri
        )
        possibleUris.firstOrNull { uri ->
            uriExtension(context, uri) == extension && isUriReadable(context, uri)
        }?.let { return it }

        val videoId = song.videoId?.takeIf { it.isNotBlank() }
        if (videoId != null) {
            val prefs = downloadUriPreferences(context)
            val formatMapped = prefs.getString("${extension}_uri_$videoId", null)?.let {
                runCatching { Uri.parse(it) }.getOrNull()
            }
            if (formatMapped != null && uriExtension(context, formatMapped) == extension &&
                isUriReadable(context, formatMapped)
            ) {
                return formatMapped
            }
            if (formatMapped != null) prefs.edit().remove("${extension}_uri_$videoId").apply()

            val genericMapped = prefs.getString("uri_$videoId", null)?.let {
                runCatching { Uri.parse(it) }.getOrNull()
            }
            if (genericMapped != null && uriExtension(context, genericMapped) == extension &&
                isUriReadable(context, genericMapped)
            ) {
                prefs.edit().putString("${extension}_uri_$videoId", genericMapped.toString()).apply()
                return genericMapped
            }
        }

        val baseName = "${cleanTitle(song.title)} - ${cleanArtist(song.artist)}"
            .replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
        val filename = "$baseName.$extension"
        return (findDsmusicMediaStoreUri(context, filename) ?: findLegacyDownloadedFile(filename))
            ?.takeIf { isUriReadable(context, it) }
    }

    /** Resolve an app-published conversion/export by exact display name. */
    fun findDownloadedExportUri(context: Context, filename: String): Uri? =
        (findDsmusicMediaStoreUri(context, filename) ?: findLegacyDownloadedFile(filename))
            ?.takeIf { isUriReadable(context, it) }

    private fun uriExtension(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        }
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)?.substringAfterLast('.', "")?.lowercase().orEmpty()
                } else ""
            } ?: uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        }
    }

    /** Record the permanent MediaStore URI immediately after the file is published. */
    private fun rememberDownloadedSong(context: Context, videoId: String, outputFile: File) {
        if (videoId.isBlank()) return
        val uri = findDsmusicMediaStoreUri(context, outputFile.name)
            ?: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && outputFile.isFile) {
                Uri.fromFile(outputFile)
            } else null
        if (uri != null) {
            val editor = downloadUriPreferences(context)
                .edit()
                .putString("uri_$videoId", uri.toString())
                .putString("name_$videoId", outputFile.name)
            editor.putString("${outputFile.extension.lowercase()}_uri_$videoId", uri.toString())
            editor.apply()
        } else {
            Log.w(TAG, "Download completed but permanent MediaStore URI was not found for $videoId")
        }
    }

    /** Remember a converted/exported format without replacing the generic playback URI. */
    fun rememberDownloadedExport(context: Context, song: SongItem, outputFile: File) {
        val videoId = song.videoId?.takeIf { it.isNotBlank() } ?: return
        val uri = findDsmusicMediaStoreUri(context, outputFile.name)
            ?: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && outputFile.isFile) {
                Uri.fromFile(outputFile)
            } else null
        if (uri == null) {
            Log.w(TAG, "Could not remember converted export ${outputFile.name} for $videoId")
            return
        }
        downloadUriPreferences(context)
            .edit()
            .putString("${outputFile.extension.lowercase()}_uri_$videoId", uri.toString())
            .putString("export_name_${outputFile.extension.lowercase()}_$videoId", outputFile.name)
            .apply()
    }

    /**
     * Remove an app-owned Ogg Opus source after a verified MP3 export.
     * Refuse to delete an arbitrary third-party .opus file passed through a SongItem.
     */
    fun deleteDownloadedOpusSource(context: Context, song: SongItem, sourceUri: Uri): Boolean {
        if (!uriExtension(context, sourceUri).equals("opus", ignoreCase = true)) return false

        val videoId = song.videoId?.takeIf { it.isNotBlank() }
        val prefs = downloadUriPreferences(context)
        val rawUri = sourceUri.toString()
        val mappedUris = videoId?.let {
            listOfNotNull(
                prefs.getString("opus_uri_$it", null),
                prefs.getString("uri_$it", null)
            )
        }.orEmpty()
        val baseName = "${cleanTitle(song.title)} - ${cleanArtist(song.artist)}"
            .replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
        val expectedName = "$baseName.opus"
        val canonicalUri = findDsmusicMediaStoreUri(context, expectedName)
            ?: findLegacyDownloadedFile(expectedName)
        val sourceFile = sourceUri.path?.let(::File)
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val isInsideDsmusicDownloads = sourceFile?.let { candidate ->
            val candidatePath = runCatching { candidate.canonicalPath }.getOrNull()
            val musicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val allowedRoots = listOf(downloadsRoot, musicRoot)
            candidatePath != null && candidate.name.equals(expectedName, ignoreCase = true) &&
                allowedRoots.any { root ->
                    val rootPath = runCatching { root.canonicalPath }.getOrNull()
                    rootPath != null && candidatePath.startsWith(rootPath + File.separator)
                }
        } == true
        val isAppOwned = rawUri in mappedUris || canonicalUri?.toString() == rawUri ||
            (sourceUri.scheme == "file" && isInsideDsmusicDownloads)
        if (!isAppOwned) {
            Log.w(TAG, "Refusing to delete Opus source outside the D.S Music download store: $sourceUri")
            return false
        }

        val deleted = try {
            when (sourceUri.scheme) {
                "file" -> {
                    val path = sourceUri.path ?: return false
                    val file = File(path)
                    val removed = !file.exists() || file.delete()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // Remove the stale legacy MediaStore row as well as the physical file.
                        runCatching {
                            context.contentResolver.delete(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                "${MediaStore.Audio.Media.DATA} = ?",
                                arrayOf(path)
                            )
                        }
                    }
                    removed
                }
                "content" -> {
                    val rows = context.contentResolver.delete(sourceUri, null, null)
                    rows > 0 || !isUriReadable(context, sourceUri)
                }
                else -> false
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not delete converted Opus source $sourceUri", error)
            false
        }

        if (!deleted) {
            Log.w(TAG, "MP3 was saved, but the original Opus file could not be deleted: $sourceUri")
            return false
        }

        // Android 10+ downloads are first staged in this app cache folder and then
        // copied into MediaStore. Clean up any legacy staging copy left by an older build.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val stagedCopy = File(File(context.cacheDir, "D.S-Music_output"), expectedName)
            if (stagedCopy.exists() && !stagedCopy.delete()) {
                Log.w(TAG, "Could not delete old Opus staging copy: ${stagedCopy.absolutePath}")
            }
        }
        if (videoId != null) {
            val editor = prefs.edit()
                .remove("opus_uri_$videoId")
            if (prefs.getString("uri_$videoId", null) == rawUri) {
                editor.remove("uri_$videoId").remove("name_$videoId")
            }
            editor.apply()
        }
        Log.i(TAG, "Deleted converted Opus source: $sourceUri")
        return true
    }

    private fun findDsmusicMediaStoreUri(context: Context, filename: String): Uri? {
        val audioUri = try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf(filename),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idColumn)
                    )
                } else null
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not resolve audio-library download $filename", error)
            null
        }

        return audioUri ?: findDsmusicDownloadsUri(context, filename)
    }

    /**
     * WebM audio is stored through MediaStore.Downloads on Android 10+ because
     * audio/webm is not consistently registered in Android's MIME-to-extension map.
     * The generic Downloads collection accepts the real container MIME without relabeling
     * the WebM bytes as Ogg/MP3 or losing the file when Audio.Media rejects the insert.
     */
    private fun findDsmusicDownloadsUri(context: Context, filename: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        return try {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(filename),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                } else null
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not resolve generic WebM download $filename", error)
            null
        }
    }

    private fun findLegacyDownloadedFile(filename: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        )
        val directories = roots.flatMap { root ->
            listOf(root) + root.listFiles().orEmpty().filter { it.isDirectory }
        }
        return directories.asSequence()
            .map { File(it, filename) }
            .firstOrNull { it.isFile }
            ?.let(Uri::fromFile)
    }

    private fun isUriReadable(context: Context, uri: Uri): Boolean = try {
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).isFile } == true
            "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length != 0L
            } == true
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Tapping Download twice for the same track toggles it off. We keep the
     * connection handle so cancellation also interrupts a blocked HTTP read.
     */
    fun downloadSong(context: Context, song: SongItem) {
        val videoId = song.videoId
        if (videoId.isNullOrBlank()) {
            com.dsann.music.ui.SnackbarController.showMessage("Local songs are already offline")
            return
        }

        if (activeDownloads.containsKey(videoId)) {
            cancelDownload(context, song)
            return
        }

        val task = DownloadTask()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            performDownload(context.applicationContext, song, videoId, task)
        }
        task.job = job

        val alreadyActive = activeDownloads.putIfAbsent(videoId, task)
        if (alreadyActive != null) {
            job.cancel()
            // The second tap is a cancellation request for the active task.
            cancelDownload(context, song)
            return
        }

        _activeDownloadIds.value = activeDownloads.keys.toSet()
        _downloadProgress.value += (videoId to 0)
        job.invokeOnCompletion {
            if (task.cancelled) task.outputFile?.delete()
            activeDownloads.remove(videoId, task)
            _activeDownloadIds.value = activeDownloads.keys.toSet()
            _downloadProgress.value -= videoId
        }
        job.start()
    }

    fun cancelDownload(context: Context, song: SongItem) {
        val videoId = song.videoId ?: return
        cancelDownloadInternal(
            context = context.applicationContext,
            videoId = videoId,
            notificationId = notificationIdFor(videoId),
            title = song.title
        )
    }

    fun cancelDownloadFromNotification(context: Context, videoId: String, notificationId: Int) {
        cancelDownloadInternal(
            context = context.applicationContext,
            videoId = videoId,
            notificationId = notificationId,
            title = null
        )
    }

    private fun cancelDownloadInternal(
        context: Context,
        videoId: String,
        notificationId: Int,
        title: String?
    ) {
        val task = activeDownloads[videoId]
        if (task == null) {
            cancelNotification(context, notificationId)
            return
        }
        if (task.cancelled) return
        task.cancelled = true
        // Socket teardown and Job.cancel may execute synchronous TLS callbacks.
        // Always schedule both on the IO scope to keep Android 6's main thread clear.
        scope.launch(Dispatchers.IO) {
            runCatching { task.connection?.disconnect() }
            runCatching { task.call?.cancel() }
        }
        scope.launch(Dispatchers.IO) {
            task.job?.cancel(CancellationException("Download cancelled by user"))
        }
        cancelNotification(context, notificationId)
        if (!title.isNullOrBlank()) {
            com.dsann.music.ui.SnackbarController.showMessage(
                "Download cancelled: ${cleanTitle(title)}"
            )
        }
    }

    private suspend fun performDownload(
        context: Context,
        song: SongItem,
        videoId: String,
        task: DownloadTask
    ) {
        val notificationId = notificationIdFor(videoId)
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        val coroutineContext = currentCoroutineContext()
        var automaticMp3Conversion: Mp3ConversionResult? = null

        try {
            val settings = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val selectedDownloadFormat = NewPipeStreamExtractor.normalizeDownloadAudioFormat(
                settings.getString("download_audio_format", "m4a")
            )
            // MP3 is always sourced from Opus and encoded locally at the selected download bitrate.
            val convertToMp3AfterDownload = selectedDownloadFormat == "mp3"
            val downloadSourceFormat = if (convertToMp3AfterDownload) "opus" else selectedDownloadFormat
            val playbackBitrate = NewPipeStreamExtractor.normalizeAudioQuality(
                settings.getString("audio_quality", "256 kbps")
            )
            val downloadBitrate = NewPipeStreamExtractor.normalizeAudioQuality(
                settings.getString("download_bitrate", "256 kbps")
            )

            // Check the selected output format, not whether any other format was downloaded.
            if (isSongDownloaded(context, song)) {
                _downloadedVideoIds.update { it + videoId }
                cancelNotification(context, notificationId)
                withContext(Dispatchers.Main) {
                    com.dsann.music.ui.SnackbarController.showMessage(
                        "Already downloaded in $selectedDownloadFormat: $cleanTitleStr",
                        iconResId = R.drawable.ic_downloaded
                    )
                }
                return
            }

            // Reuse an existing Opus source when MP3 is selected later; do not download it again.
            if (convertToMp3AfterDownload && findDownloadedOpusUri(context, song) != null) {
                updateNotification(context, cleanTitleStr, 75, notificationId, videoId)
                val conversion = OpusToMp3Converter.convert(context, song)
                automaticMp3Conversion = conversion
                if (!conversion.success) {
                    _downloadedVideoIds.update { it - videoId }
                    cancelNotification(context, notificationId)
                    withContext(Dispatchers.Main) {
                        com.dsann.music.ui.SnackbarController.showMessage(
                            "MP3 conversion failed: ${conversion.message}. The Opus original was kept; retry from the three-dot menu."
                        )
                    }
                    return
                }
                _downloadedVideoIds.update { it + videoId }
                _downloadProgress.value += (videoId to 100)
                showFinishedNotification(context, cleanTitleStr, cleanArtistStr, notificationId)
                withContext(Dispatchers.Main) {
                    com.dsann.music.ui.SnackbarController.showMessage(
                        conversion.message,
                        iconResId = R.drawable.ic_downloaded
                    )
                }
                return
            }

            updateNotification(context, cleanTitleStr, 0, notificationId, videoId)

            // Reuse a playback cache entry only when its actual MIME type matches the selected
            // source container and its target bitrate is unchanged. This keeps Opus distinct
            // from the per-track M4A playback fallback.
            // Cache entries may contain Opus or the per-track M4A playback fallback.
            val cachedCandidate = if (playbackBitrate == downloadBitrate) {
                findCachedStream(context, song.id.toString())
            } else {
                null
            }
            val cachedStream = cachedCandidate?.takeIf {
                NewPipeStreamExtractor.matchesAudioFormat(
                    it.mimeType,
                    preferredFormat = downloadSourceFormat
                )
            }
            coroutineContext.ensureActive()

            val outputFile: File
            if (cachedStream != null) {
                if (!cachedStream.isComplete) {
                    cancelNotification(context, notificationId)
                    val cacheSize = android.text.format.Formatter.formatFileSize(context, cachedStream.cachedBytes)
                    val totalSize = if (cachedStream.contentLength > 0L) {
                        android.text.format.Formatter.formatFileSize(context, cachedStream.contentLength)
                    } else {
                        "unknown total size"
                    }
                    withContext(Dispatchers.Main) {
                        com.dsann.music.ui.SnackbarController.showMessage(
                            "Only $cacheSize of this song is cached ($totalSize). No network download was started; play it through to cache the full track."
                        )
                    }
                    return
                }

                val extension = extensionForMimeType(cachedStream.mimeType)
                outputFile = AudioFileManager.createOutputFile(
                    context,
                    "$cleanTitleStr - $cleanArtistStr.$extension"
                )
                task.outputFile = outputFile
                copyCachedStream(cachedStream, outputFile, coroutineContext)
                _downloadProgress.value += (videoId to 90)
                updateNotification(context, cleanTitleStr, 90, notificationId, videoId)
            } else {
                // No cache entry for this track: use the standard online download path.
                val downloadStream = NewPipeStreamExtractor.getDownloadStreamData(
                    videoId = videoId,
                    allowFormatChoice = !convertToMp3AfterDownload,
                    preferredFormatOverride = downloadSourceFormat
                )
                coroutineContext.ensureActive()
                if (convertToMp3AfterDownload && !NewPipeStreamExtractor.matchesAudioFormat(
                        downloadStream?.mimeType,
                        preferredFormat = "opus"
                    )
                ) {
                    throw IOException("MP3 download requires an Opus source stream, but no compatible Opus stream was returned")
                }
                val streamUrl = downloadStream?.url
                if (streamUrl.isNullOrBlank()) {
                    throw IOException("Failed to extract download URL")
                }

                val extension = when {
                    downloadStream?.mimeType?.startsWith("audio/mp4", ignoreCase = true) == true -> "m4a"
                    downloadStream?.mimeType?.startsWith("audio/mpeg", ignoreCase = true) == true -> "mp3"
                    downloadStream?.mimeType?.contains("webm", ignoreCase = true) == true -> "opus"
                    downloadStream?.mimeType?.contains("opus", ignoreCase = true) == true -> "opus"
                    downloadStream?.mimeType?.contains("ogg", ignoreCase = true) == true -> "ogg"
                    downloadStream?.mimeType?.contains("flac", ignoreCase = true) == true -> "flac"
                    downloadStream?.mimeType?.contains("aac", ignoreCase = true) == true -> "aac"
                    downloadStream?.mimeType?.contains("wav", ignoreCase = true) == true -> "wav"
                    else -> "m4a"
                }
                outputFile = AudioFileManager.createOutputFile(
                    context,
                    "$cleanTitleStr - $cleanArtistStr.$extension"
                )
                task.outputFile = outputFile

                val resolvedDownloadFormat = when {
                    NewPipeStreamExtractor.matchesAudioFormat(downloadStream?.mimeType, preferredFormat = "m4a") -> "m4a"
                    NewPipeStreamExtractor.matchesAudioFormat(downloadStream?.mimeType, preferredFormat = "mp3") -> "mp3"
                    NewPipeStreamExtractor.matchesAudioFormat(downloadStream?.mimeType, preferredFormat = "opus") -> "opus"
                    else -> downloadSourceFormat
                }

                try {
                    downloadNetworkStream(
                        context = context,
                        title = cleanTitleStr,
                        streamUrl = streamUrl,
                        expectedBytes = downloadStream?.contentLength,
                        outputFile = outputFile,
                        task = task,
                        videoId = videoId,
                        notificationId = notificationId,
                        coroutineContext = coroutineContext
                    )
                } catch (downloadError: IOException) {
                    val wasForbidden = downloadError.message?.contains("HTTP 403", ignoreCase = true) == true
                    if (!wasForbidden) throw downloadError

                    // googlevideo URLs are signed and can expire. Re-extract once, retaining
                    // the concrete format returned by the initial picker (including Opus).
                    Log.w(TAG, "Refreshing expired $resolvedDownloadFormat URL after HTTP 403 for $videoId")
                    NewPipeStreamExtractor.clearVideoCache(videoId)
                    val refreshedStream = NewPipeStreamExtractor.getDownloadStreamData(
                        videoId = videoId,
                        allowFormatChoice = false,
                        preferredFormatOverride = resolvedDownloadFormat
                    ) ?: throw IOException("The source returned HTTP 403 and did not provide a refreshed stream", downloadError)
                    val refreshedUrl = refreshedStream.url
                        .takeIf { it.startsWith("https://", ignoreCase = true) }
                        ?: throw IOException("The source returned HTTP 403 and did not provide a refreshed download URL", downloadError)
                    if (!NewPipeStreamExtractor.matchesAudioFormat(
                            refreshedStream.mimeType,
                            preferredFormat = resolvedDownloadFormat
                        )
                    ) {
                        throw IOException("The refreshed stream does not match the selected $resolvedDownloadFormat format")
                    }

                    downloadNetworkStream(
                        context = context,
                        title = cleanTitleStr,
                        streamUrl = refreshedUrl,
                        expectedBytes = refreshedStream.contentLength,
                        outputFile = outputFile,
                        task = task,
                        videoId = videoId,
                        notificationId = notificationId,
                        coroutineContext = coroutineContext
                    )
                }
            }

            coroutineContext.ensureActive()

            val cachedMetadata = cachedStream?.metadata
            var artworkBytes: ByteArray? = null
            if (cachedStream != null) {
                // Reuse artwork captured with cached playback when available.
                val cachedArtwork = AudioPlaybackService.getCachedMediaArtworkFile(context, song.id.toString())
                if (cachedArtwork.isFile && cachedArtwork.length() > 0L) {
                    artworkBytes = runCatching { cachedArtwork.readBytes() }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                }
            }

            val needsSourceMetadata = !isUsableMetadataText(song.title) ||
                !isUsableMetadataText(song.artist)
            val sourceMetadata = if (needsSourceMetadata) {
                runCatching {
                    com.dsann.music.data.innertube.YouTubeMusic
                        .player(videoId, metadataOnly = true)
                        .getOrNull()
                }.getOrNull()
            } else null

            // Try each known artwork URL. A non-empty but stale URL must not prevent trying
            // the cache thumbnail, InnerTube metadata thumbnail, or the video-ID fallback.
            val artworkUrls = mutableListOf<String>()
            song.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(artworkUrls::add)
            cachedMetadata?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(artworkUrls::add)
            sourceMetadata?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(artworkUrls::add)

            if (artworkBytes == null || artworkBytes.isEmpty()) {
                for (artworkUrl in artworkUrls.distinct()) {
                    val fetchedArtwork = fetchArtworkBytes(context, artworkUrl)
                    if (fetchedArtwork != null && fetchedArtwork.isNotEmpty()) {
                        artworkBytes = fetchedArtwork
                        break
                    }
                }
            }

            // Only request source metadata after all already-known image URLs have failed.
            if (artworkBytes == null || artworkBytes.isEmpty()) {
                val metadataThumbnail = runCatching {
                    com.dsann.music.data.innertube.YouTubeMusic
                        .player(videoId, metadataOnly = true)
                        .getOrNull()
                        ?.thumbnailUrl
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
                if (!metadataThumbnail.isNullOrBlank() && metadataThumbnail !in artworkUrls) {
                    artworkBytes = fetchArtworkBytes(context, metadataThumbnail)
                }
            }
            if ((artworkBytes == null || artworkBytes.isEmpty()) && videoId.isNotBlank()) {
                artworkBytes = fetchArtworkBytes(
                    context,
                    "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                )
            }

            coroutineContext.ensureActive()

            artworkBytes?.takeIf { it.isNotEmpty() }?.let { artwork ->
                AudioPlaybackService.storeCachedMediaArtworkBytes(
                    context.applicationContext,
                    song.id.toString(),
                    artwork
                )
            }

            // Explicit "Unknown" placeholders are missing metadata, not real tag values.
            val metadataTitle = firstUsableMetadataText(
                song.title, cachedMetadata?.title, sourceMetadata?.title, cleanTitleStr
            ) ?: cleanTitleStr
            val metadataArtist = firstUsableMetadataText(
                song.artist, cachedMetadata?.artist, sourceMetadata?.artist, cleanArtistStr
            ) ?: cleanArtistStr
            val metadataAlbum = firstUsableMetadataText(
                song.albumName, cachedMetadata?.albumName
            )

            val metadataEmbedded = embedMetadataSafely(
                outputFile = outputFile,
                title = metadataTitle,
                artist = metadataArtist,
                album = metadataAlbum,
                videoId = videoId,
                fromCache = cachedStream != null,
                artworkBytes = artworkBytes
            )
            // M4A metadata writing is best-effort after the audio bytes have been downloaded.
            // AudioFileManager writes title/artist/album into MediaStore on Android 10+ and
            // updates the scanned MediaStore row on Android 6-9, so a tagger limitation must
            // not delete an otherwise valid audio download.
            if (outputFile.extension.equals("m4a", ignoreCase = true) && !metadataEmbedded) {
                Log.w(TAG, "M4A download will continue with MediaStore metadata fallback because embedded tagging was incomplete")
            }
            // A failed WebM-to-Ogg remux must never publish WebM bytes with a .opus name.
            if (outputFile.extension.equals("opus", ignoreCase = true) && !metadataEmbedded) {
                throw IOException("Could not create a valid tagged Ogg Opus file")
            }
            // Retain the safety check for older Matroska download paths.
            if (outputFile.extension.equals("mka", ignoreCase = true) && !metadataEmbedded) {
                throw IOException("Could not embed metadata into the Opus Matroska file")
            }

            coroutineContext.ensureActive()
            if (!AudioFileManager.scanFile(context, outputFile, metadataTitle, metadataArtist, metadataAlbum)) {
                throw IOException("Could not publish the downloaded audio to the music library")
            }
            rememberDownloadedSong(context, videoId, outputFile)
            // The source is now a completed, published download. Cancellation during the
            // optional MP3 conversion must not delete this valid Opus original.
            task.outputFile = null

            // The selected MP3 format is fulfilled by converting the tagged Opus source locally.
            // Conversion failure does not discard the valid Opus download; the manual three-dot
            // action remains available to retry it.
            if (convertToMp3AfterDownload) {
                _downloadProgress.value += (videoId to 95)
                updateNotification(context, cleanTitleStr, 95, notificationId, videoId)
                automaticMp3Conversion = OpusToMp3Converter.convert(context, song)
                if (automaticMp3Conversion?.success != true) {
                    Log.w(
                        TAG,
                        "Opus download succeeded but automatic MP3 conversion failed for $videoId: " +
                            automaticMp3Conversion?.message
                    )
                }
            }

            val selectedFormatCompleted = !convertToMp3AfterDownload ||
                automaticMp3Conversion?.success == true
            _downloadedVideoIds.update { current ->
                if (selectedFormatCompleted) current + videoId else current - videoId
            }
            if (selectedFormatCompleted) {
                showFinishedNotification(context, metadataTitle, metadataArtist, notificationId)
            } else {
                cancelNotification(context, notificationId)
            }
            _downloadProgress.value += (videoId to 100)

            withContext(Dispatchers.Main) {
                val automaticConversionMessage = automaticMp3Conversion?.let { result ->
                    if (result.success) {
                        result.message
                    } else {
                        "Opus downloaded, but automatic MP3 conversion failed: ${result.message}. " +
                            "Use Convert Opus to MP3 in the three-dot menu to retry."
                    }
                }
                com.dsann.music.ui.SnackbarController.showMessage(
                    automaticConversionMessage ?: when {
                        cachedStream != null && metadataEmbedded -> "Saved cached audio offline: $cleanTitleStr"
                        cachedStream != null -> "Saved cached audio offline: $cleanTitleStr (metadata tags unavailable)"
                        metadataEmbedded -> "Downloaded with embedded metadata: $cleanTitleStr"
                        outputFile.extension.equals("m4a", ignoreCase = true) ->
                            "Downloaded: $cleanTitleStr (music-library metadata saved; some embedded tags were unavailable)"
                        else -> "Downloaded: $cleanTitleStr (this audio format could not store embedded tags)"
                    },
                    iconResId = R.drawable.ic_downloaded
                )
            }
        } catch (e: com.dsann.music.data.innertube.AudioFormatSelectionCancelledException) {
            task.outputFile?.delete()
            cancelNotification(context, notificationId)
            withContext(Dispatchers.Main) {
                com.dsann.music.ui.SnackbarController.showMessage(
                    "Download cancelled: no format selected"
                )
            }
        } catch (e: CancellationException) {
            task.cancelled = true
            task.outputFile?.delete()
            cancelNotification(context, notificationId)
            throw e
        } catch (e: Exception) {
            task.outputFile?.delete()
            Log.e(TAG, "Download failed for ${song.title}", e)
            cancelNotification(context, notificationId)
            if (!task.cancelled) {
                withContext(Dispatchers.Main) {
                    com.dsann.music.ui.SnackbarController.showMessage("Download failed: ${e.message}")
                }
            }
        } finally {
            task.connection?.disconnect()
            task.connection = null
            task.call?.cancel()
            task.call = null
            if (task.cancelled) task.outputFile?.delete()
            _downloadProgress.value -= videoId
        }
    }


    /**
     * Downloads with browser-like headers, buffered IO, exact response-length validation,
     * and bounded retries for transient read/network failures.
     */
    private suspend fun downloadNetworkStream(
        context: Context,
        title: String,
        streamUrl: String,
        expectedBytes: Long?,
        outputFile: File,
        task: DownloadTask,
        videoId: String,
        notificationId: Int,
        coroutineContext: CoroutineContext
    ) {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            var call: okhttp3.Call? = null
            var response: okhttp3.Response? = null
            try {
                coroutineContext.ensureActive()
                if (outputFile.exists() && !outputFile.delete()) {
                    throw IOException("Could not clear an incomplete audio download")
                }
                outputFile.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IOException("Could not create the download directory")
                    }
                }

                // Google video streams are fetched as 1 MiB range requests, pipelining the next
                // range while the current range is read. This avoids YouTube's playback-speed
                // throttling on a single long, open-ended GET without changing audio quality.
                call = YouTubeRangeDownloadClient.newCall(streamUrl)
                task.call = call
                response = call.execute()
                if (response.code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Download server returned HTTP ${response.code}")
                }

                val declaredLength = response.body.contentLength().takeIf { it > 0L }
                val progressLength = declaredLength ?: expectedBytes?.takeIf { it > 0L }
                var downloadedBytes = 0L
                var lastProgressUpdate = 0L
                response.body.byteStream().use { input ->
                    BufferedOutputStream(FileOutputStream(outputFile), 64 * 1024).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            if (bytesRead == 0) continue
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead.toLong()

                            if (progressLength != null && progressLength > 0L) {
                                val progress = ((downloadedBytes.toDouble() / progressLength.toDouble()) * 88.0)
                                    .toInt().coerceIn(1, 88)
                                if (_downloadProgress.value[videoId] != progress) {
                                    _downloadProgress.value += (videoId to progress)
                                }
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 400L) {
                                    lastProgressUpdate = now
                                    updateNotification(context, title, progress, notificationId, videoId)
                                }
                            }
                        }
                    }
                }

                if (downloadedBytes <= 0L) {
                    throw IOException("The server returned an empty audio stream")
                }
                if (declaredLength != null && downloadedBytes != declaredLength) {
                    throw IOException(
                        "Incomplete audio download: received $downloadedBytes of $declaredLength bytes"
                    )
                }
                Log.i(TAG, "Downloaded $downloadedBytes bytes using range-enabled mode on attempt $attempt")
                return
            } catch (cancelled: CancellationException) {
                outputFile.delete()
                throw cancelled
            } catch (error: Exception) {
                lastError = error
                outputFile.delete()
                coroutineContext.ensureActive()
                Log.w(TAG, "Download attempt $attempt/3 failed for $title", error)
                if (attempt < 3) {
                    _downloadProgress.value += (videoId to 0)
                    delay(attempt * 500L)
                }
            } finally {
                response?.close()
                call?.cancel()
                if (task.call === call) task.call = null
                if (task.connection != null) {
                    runCatching { task.connection?.disconnect() }
                    task.connection = null
                }
            }
        }
        throw IOException("Audio download failed after 3 attempts: ${lastError?.message ?: "unknown network error"}", lastError)
    }

    private fun isUsableMetadataText(value: String?): Boolean {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        return !normalized.equals("<unknown>", ignoreCase = true) &&
            !normalized.equals("unknown", ignoreCase = true) &&
            !normalized.equals("unknown title", ignoreCase = true) &&
            !normalized.equals("unknown artist", ignoreCase = true) &&
            !normalized.equals("unknown album", ignoreCase = true)
    }

    private fun firstUsableMetadataText(vararg values: String?): String? =
        values.firstOrNull { isUsableMetadataText(it) }?.trim()

    private fun fetchArtworkBytes(context: Context, url: String): ByteArray? {
        var target: com.bumptech.glide.request.FutureTarget<Bitmap>? = null
        var sourceBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        try {
            target = Glide.with(context)
                .asBitmap()
                .load(url)
                .override(768, 768)
                .submit()
            sourceBitmap = target.get() ?: return null
            if (sourceBitmap.width <= 0 || sourceBitmap.height <= 0) return null

            val size = sourceBitmap.width.coerceAtMost(sourceBitmap.height)
            val x = (sourceBitmap.width - size) / 2
            val y = (sourceBitmap.height - size) / 2
            croppedBitmap = Bitmap.createBitmap(sourceBitmap, x, y, size, size)
            val output = ByteArrayOutputStream()
            if (!croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) return null
            return output.toByteArray().takeIf { it.isNotEmpty() }
        } catch (error: Exception) {
            Log.w(TAG, "Could not retrieve artwork from $url", error)
            return null
        } finally {
            if (croppedBitmap != null && croppedBitmap !== sourceBitmap && !croppedBitmap.isRecycled) {
                croppedBitmap.recycle()
            }
            target?.let { Glide.with(context).clear(it) }
        }
    }

    /**
     * Tag a temporary copy and replace the original only after metadata commit succeeds.
     * Unsupported taggers therefore cannot corrupt the downloaded audio stream.
     */
    private fun embedMetadataSafely(
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        videoId: String,
        fromCache: Boolean,
        artworkBytes: ByteArray?
    ): Boolean {
        val extension = outputFile.extension
        if (extension.equals("opus", ignoreCase = true)) {
            return embedOggOpusMetadataSafely(outputFile, title, artist, album, videoId, fromCache, artworkBytes)
        }
        if (extension.equals("mka", ignoreCase = true)) {
            return embedMatroskaMetadataSafely(outputFile, title, artist, album, videoId, fromCache, artworkBytes)
        }
        if (extension.lowercase() in setOf("m4a", "mp4", "m4p", "m4b")) {
            return embedMp4MetadataSafely(outputFile, title, artist, album, videoId, fromCache, artworkBytes)
        }
        val taggedFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.tagging.$extension")
        var backupFile: File? = null
        try {
            if (taggedFile.exists() && !taggedFile.delete()) {
                throw IOException("Could not clear the metadata staging file")
            }
            outputFile.copyTo(taggedFile, overwrite = true)
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(taggedFile)
            val isMp4Container = extension.equals("m4a", ignoreCase = true) ||
                extension.equals("mp4", ignoreCase = true) ||
                extension.equals("m4p", ignoreCase = true) ||
                extension.equals("m4b", ignoreCase = true)

            val tag = if (isMp4Container) {
                (audioFile.tag as? org.jaudiotagger.tag.mp4.Mp4Tag)
                    ?: org.jaudiotagger.tag.mp4.Mp4Tag().also { audioFile.setTag(it) }
            } else {
                audioFile.tagOrCreateAndSetDefault
            }

            val sourceComment = if (fromCache) {
                "Exported from Quantune cached audio (video ID: $videoId)"
            } else {
                "Downloaded from YouTube Music (video ID: $videoId)"
            }

            if (tag is org.jaudiotagger.tag.mp4.Mp4Tag) {
                // Use native MP4 atoms for M4A/AAC. This is more reliable across Android
                // media scanners than relying only on generic cross-format field mappings.
                tag.setField(tag.createField(org.jaudiotagger.tag.mp4.Mp4FieldKey.TITLE, title))
                tag.setField(tag.createField(org.jaudiotagger.tag.mp4.Mp4FieldKey.ARTIST, artist))
                runCatching {
                    tag.setField(tag.createField(org.jaudiotagger.tag.mp4.Mp4FieldKey.ALBUM_ARTIST, artist))
                }.onFailure { Log.d(TAG, "MP4 album artist tag not supported: ${it.message}") }
                album?.takeIf { it.isNotBlank() }?.let { albumTitle ->
                    runCatching {
                        tag.setField(tag.createField(org.jaudiotagger.tag.mp4.Mp4FieldKey.ALBUM, albumTitle.trim()))
                    }.onFailure { Log.d(TAG, "MP4 album tag not supported: ${it.message}") }
                }
                runCatching {
                    tag.setField(tag.createField(org.jaudiotagger.tag.mp4.Mp4FieldKey.COMMENT, sourceComment))
                }.onFailure { Log.d(TAG, "MP4 comment tag not supported: ${it.message}") }
            } else {
                // TITLE and ARTIST are required. Optional fields are isolated because some formats
                // don't support every generic jaudiotagger key.
                tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
                tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
                runCatching { tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, artist) }
                    .onFailure { Log.d(TAG, "Album artist tag not supported by ${extension}: ${it.message}") }
                album?.takeIf { it.isNotBlank() }?.let { albumTitle ->
                    runCatching { tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, albumTitle.trim()) }
                        .onFailure { Log.d(TAG, "Album tag not supported by ${extension}: ${it.message}") }
                }
                runCatching { tag.setField(org.jaudiotagger.tag.FieldKey.COMMENT, sourceComment) }
                    .onFailure { Log.d(TAG, "Comment tag not supported by ${extension}: ${it.message}") }
            }

            var optionalMetadataComplete = true
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                // Cover art is an optional MP4 atom. Use Jaudiotagger's documented add-field
                // path for covr; setField can replace the in-memory field without reliably
                // preserving multiple MP4 binary atoms in every library version.
                val artworkWrite = runCatching {
                    if (tag.artworkList.isNotEmpty()) {
                        tag.deleteArtworkField()
                    }
                    if (tag is org.jaudiotagger.tag.mp4.Mp4Tag) {
                        tag.addField(tag.createArtworkField(artworkBytes))
                    } else {
                        val artwork = org.jaudiotagger.tag.images.StandardArtwork()
                        artwork.binaryData = artworkBytes
                        artwork.mimeType = "image/jpeg"
                        tag.addField(artwork)
                    }
                }
                if (artworkWrite.isFailure) {
                    optionalMetadataComplete = false
                    Log.w(TAG, "Could not add embedded cover art to ${extension}; retaining audio and text tags", artworkWrite.exceptionOrNull())
                }
            }

            audioFile.commit()
            if (!taggedFile.isFile || taggedFile.length() <= 0L) {
                throw IOException("Metadata write produced an empty file")
            }

            // Verify the written tags by reading the staged audio back.
            val writtenTag = org.jaudiotagger.audio.AudioFileIO.read(taggedFile).tag
            val writtenTitle = if (writtenTag is org.jaudiotagger.tag.mp4.Mp4Tag) {
                writtenTag.getFirst(org.jaudiotagger.tag.mp4.Mp4FieldKey.TITLE)
            } else {
                writtenTag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE)
            }
            val writtenArtist = if (writtenTag is org.jaudiotagger.tag.mp4.Mp4Tag) {
                writtenTag.getFirst(org.jaudiotagger.tag.mp4.Mp4FieldKey.ARTIST)
            } else {
                writtenTag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST)
            }
            if (writtenTitle.isNullOrBlank() || !writtenTitle.equals(title.trim(), ignoreCase = true) ||
                writtenArtist.isNullOrBlank() || !writtenArtist.equals(artist.trim(), ignoreCase = true)
            ) {
                throw IOException("Metadata verification failed: the exact M4A title or artist was not persisted")
            }
            // Album and artwork are best-effort fields. A failure to write either must
            // not discard an otherwise valid M4A download after title/artist were committed.
            if (!album.isNullOrBlank()) {
                val writtenAlbum = if (writtenTag is org.jaudiotagger.tag.mp4.Mp4Tag) {
                    writtenTag.getFirst(org.jaudiotagger.tag.mp4.Mp4FieldKey.ALBUM)
                } else {
                    writtenTag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM)
                }
                if (!writtenAlbum.equals(album.trim(), ignoreCase = true)) {
                    optionalMetadataComplete = false
                    Log.w(TAG, "M4A album atom was not persisted; title and artist tags remain usable")
                }
            }
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                val artworkPersisted = writtenTag.artworkList.any { artwork ->
                    runCatching { artwork.binaryData?.contentEquals(artworkBytes) == true }
                        .getOrDefault(false)
                }
                if (!artworkPersisted) {
                    optionalMetadataComplete = false
                    Log.w(TAG, "M4A cover atom was not persisted; keeping the audio and required text tags")
                }
            }
            if (isMp4Container) {
                // This is a diagnostic probe only. Some Android 6/older vendor extractors
                // fail to expose valid MP4 covr/ilst atoms even though jaudiotagger has
                // successfully written and reread them. Do not reject a valid tagged file
                // based on a framework metadata-extraction limitation.
                runCatching {
                    verifyMp4MetadataWithPlatform(
                        file = taggedFile,
                        title = title,
                        artist = artist,
                        album = album,
                        expectArtwork = artworkBytes != null && artworkBytes.isNotEmpty()
                    )
                }.onFailure {
                    Log.w(TAG, "Android framework could not independently expose M4A tags; MP4 atom verification passed", it)
                }
            }

            backupFile = File(outputFile.parentFile, "${outputFile.name}.raw-backup")
            if (backupFile.exists() && !backupFile.delete()) {
                throw IOException("Could not clear the audio backup file")
            }
            if (!outputFile.renameTo(backupFile)) {
                throw IOException("Could not preserve original audio before metadata writing")
            }
            if (!taggedFile.renameTo(outputFile)) {
                backupFile.renameTo(outputFile)
                throw IOException("Could not install the tagged audio file")
            }
            backupFile.delete()
            if (!optionalMetadataComplete) {
                Log.w(TAG, "M4A was tagged with required title/artist; one or more optional tags could not be verified")
            }
            return optionalMetadataComplete
        } catch (error: Exception) {
            Log.w(TAG, "Metadata embedding failed safely for ${outputFile.name}; original audio will be preserved", error)
            taggedFile.delete()
            if (backupFile?.exists() == true && !outputFile.exists()) {
                backupFile.renameTo(outputFile)
            }
            return false
        }
    }

    /**
     * Rebuild native MP4 ilst atoms like the Opus packet-remux path. No AAC decode,
     * re-encode, or generic tag-library commit is involved.
     */
    private fun embedMp4MetadataSafely(
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        videoId: String,
        fromCache: Boolean,
        artworkBytes: ByteArray?
    ): Boolean {
        val extension = outputFile.extension.ifBlank { "m4a" }
        val stagedFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.tagging.$extension")
        var backupFile: File? = null
        try {
            if (stagedFile.exists() && !stagedFile.delete()) {
                throw IOException("Could not clear the MP4 metadata staging file")
            }
            val sourceComment = if (fromCache) {
                "Exported from Quantune cached audio (video ID: $videoId)"
            } else {
                "Downloaded from YouTube Music (video ID: $videoId)"
            }
            Mp4MetadataRemuxer.writeTaggedMp4(
                inputFile = outputFile,
                outputFile = stagedFile,
                title = title.trim(),
                artist = artist.trim(),
                album = album?.trim()?.takeIf { it.isNotBlank() },
                comment = sourceComment,
                artworkBytes = artworkBytes
            )

            // Atom-level read-back is authoritative. Framework extraction is diagnostic
            // only because some Android 6 vendor extractors omit otherwise-valid ilst data.
            runCatching {
                verifyMp4MetadataWithPlatform(
                    file = stagedFile,
                    title = title,
                    artist = artist,
                    album = album,
                    expectArtwork = artworkBytes != null && artworkBytes.isNotEmpty()
                )
            }.onFailure {
                Log.w(TAG, "Android extractor did not expose tagged M4A metadata; atom read-back passed", it)
            }

            backupFile = File(outputFile.parentFile, "${outputFile.name}.raw-backup")
            if (backupFile.exists() && !backupFile.delete()) {
                throw IOException("Could not clear the original M4A backup file")
            }
            if (!outputFile.renameTo(backupFile)) {
                throw IOException("Could not preserve the original M4A audio before tagging")
            }
            if (!stagedFile.renameTo(outputFile)) {
                backupFile.renameTo(outputFile)
                throw IOException("Could not install the tagged M4A file")
            }
            backupFile.delete()
            return true
        } catch (error: Exception) {
            Log.w(TAG, "Native M4A metadata embedding failed safely for ${outputFile.name}", error)
            stagedFile.delete()
            if (backupFile?.exists() == true && !outputFile.exists()) {
                backupFile.renameTo(outputFile)
            }
            return false
        }
    }

    /**
     * Verify the actual MP4 atoms with Android's native extractor as well as jaudiotagger.
     * This catches files whose Java tag structure can be reread but whose metadata is not
     * visible to Android's own music library and artwork decoder.
     */
    private fun verifyMp4MetadataWithPlatform(
        file: File,
        title: String,
        artist: String,
        album: String?,
        expectArtwork: Boolean
    ) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val nativeTitle = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_TITLE
            )?.trim()
            val nativeArtist = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST
            )?.trim()
            val nativeAlbum = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM
            )?.trim()

            if (!nativeTitle.equals(title.trim(), ignoreCase = true)) {
                throw IOException("Android could not read the embedded M4A title")
            }
            if (!nativeArtist.equals(artist.trim(), ignoreCase = true)) {
                throw IOException("Android could not read the embedded M4A artist")
            }
            if (!album.isNullOrBlank() && !nativeAlbum.equals(album.trim(), ignoreCase = true)) {
                throw IOException("Android could not read the embedded M4A album")
            }
            if (expectArtwork) {
                val embeddedPicture = retriever.embeddedPicture
                if (embeddedPicture == null || embeddedPicture.isEmpty()) {
                    throw IOException("Android could not read the embedded M4A cover artwork")
                }
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Android could not verify the embedded M4A metadata", error)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Verification result is already determined.
            }
        }
    }

    /**
     * Publish a standards-compliant Ogg Opus file with Vorbis-comment metadata.
     * WebM input is demuxed and remuxed without decoding or re-encoding its Opus packets.
     * Existing Ogg Opus input is retagged while keeping its audio packets intact.
     */
    private fun embedOggOpusMetadataSafely(
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        videoId: String,
        fromCache: Boolean,
        artworkBytes: ByteArray?
    ): Boolean {
        val stagedFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.tagging.opus")
        var backupFile: File? = null
        try {
            if (stagedFile.exists() && !stagedFile.delete()) {
                throw IOException("Could not clear the Ogg Opus metadata staging file")
            }
            val sourceComment = if (fromCache) {
                "Exported from Quantune cached audio (video ID: $videoId)"
            } else {
                "Downloaded from YouTube Music (video ID: $videoId)"
            }
            OggOpusRemuxer.writeTaggedOpus(
                inputFile = outputFile,
                outputFile = stagedFile,
                title = title,
                artist = artist,
                album = album,
                comment = sourceComment,
                artworkBytes = artworkBytes
            )

            backupFile = File(outputFile.parentFile, "${outputFile.name}.raw-backup")
            if (backupFile.exists() && !backupFile.delete()) {
                throw IOException("Could not clear the original Opus backup file")
            }
            if (!outputFile.renameTo(backupFile)) {
                throw IOException("Could not preserve the original Opus stream before tagging")
            }
            if (!stagedFile.renameTo(outputFile)) {
                backupFile.renameTo(outputFile)
                throw IOException("Could not install the tagged Ogg Opus file")
            }
            backupFile.delete()
            return true
        } catch (error: Exception) {
            Log.w(TAG, "Ogg Opus metadata embedding failed safely for ${outputFile.name}", error)
            stagedFile.delete()
            if (backupFile?.exists() == true && !outputFile.exists()) {
                backupFile.renameTo(outputFile)
            }
            return false
        }
    }

    /**
     * YouTube supplies Opus inside WebM. Matroska attachments are not part of the WebM profile.
     * convert the staged DocType to Matroska, then write tags and the embedded cover into the media file,
     * rather than relying only on MediaStore display fields.
     */
    private fun embedMatroskaMetadataSafely(
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        videoId: String,
        fromCache: Boolean,
        artworkBytes: ByteArray?
    ): Boolean {
        val stagedFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.tagging.mka")
        var backupFile: File? = null
        try {
            if (stagedFile.exists() && !stagedFile.delete()) {
                throw IOException("Could not clear the Matroska metadata staging file")
            }
            outputFile.copyTo(stagedFile, overwrite = true)
            convertWebmHeaderToMatroska(stagedFile)
            val sourceComment = if (fromCache) {
                "Exported from Quantune cached audio (video ID: $videoId)"
            } else {
                "Downloaded from YouTube Music (video ID: $videoId)"
            }
            val tags = buildMatroskaMetadataTags(title, artist, album, sourceComment)
            val coverAttachment = artworkBytes
                ?.takeIf { it.isNotEmpty() }
                ?.let(::buildMatroskaCoverAttachment)
            appendMatroskaMetadataTags(stagedFile, tags, coverAttachment)
            val appendedElements = if (coverAttachment == null) tags else tags + coverAttachment
            verifyAppendedMatroskaMetadata(stagedFile, appendedElements)

            backupFile = File(outputFile.parentFile, "${outputFile.name}.raw-backup")
            if (backupFile.exists() && !backupFile.delete()) {
                throw IOException("Could not clear the Matroska audio backup file")
            }
            if (!outputFile.renameTo(backupFile)) {
                throw IOException("Could not preserve original Matroska audio before metadata writing")
            }
            if (!stagedFile.renameTo(outputFile)) {
                backupFile.renameTo(outputFile)
                throw IOException("Could not install the tagged Matroska audio file")
            }
            backupFile.delete()
            return true
        } catch (error: Exception) {
            Log.w(TAG, "Matroska metadata embedding failed safely for ${outputFile.name}", error)
            stagedFile.delete()
            if (backupFile?.exists() == true && !outputFile.exists()) {
                backupFile.renameTo(outputFile)
            }
            return false
        }
    }

    private fun buildMatroskaMetadataTags(
        title: String,
        artist: String,
        album: String?,
        comment: String
    ): ByteArray {
        val entries = ArrayList<ByteArray>()

        fun addTag(name: String, value: String?) {
            if (value.isNullOrBlank()) return
            val nameElement = matroskaElement(byteArrayOf(0x45, 0xA3.toByte()), name.toByteArray(Charsets.UTF_8))
            val valueElement = matroskaElement(byteArrayOf(0x44, 0x87.toByte()), value.toByteArray(Charsets.UTF_8))
            val simpleTag = matroskaElement(byteArrayOf(0x67, 0xC8.toByte()), nameElement + valueElement)
            // TargetTypeValue 50 identifies a track/song tag and applies to the audio track.
            val targets = matroskaElement(
                byteArrayOf(0x63, 0xC0.toByte()),
                matroskaElement(byteArrayOf(0x68, 0xCA.toByte()), byteArrayOf(50))
            )
            entries.add(matroskaElement(byteArrayOf(0x73, 0x73.toByte()), targets + simpleTag))
        }

        addTag("TITLE", title)
        addTag("ARTIST", artist)
        addTag("ALBUM", album)
        addTag("COMMENT", comment)
        return matroskaElement(
            byteArrayOf(0x12, 0x54, 0xC3.toByte(), 0x67),
            entries.fold(ByteArray(0)) { output, entry -> output + entry }
        )
    }

    private fun matroskaElement(id: ByteArray, payload: ByteArray): ByteArray =
        id + encodeEbmlSize(payload.size.toLong()) + payload
    /** Matroska cover art is stored as an AttachedFile child inside the Segment. */
    private fun buildMatroskaCoverAttachment(artworkBytes: ByteArray): ByteArray {
        val description = matroskaElement(byteArrayOf(0x46, 0x7E), "Front cover".toByteArray(Charsets.UTF_8))
        val fileName = matroskaElement(byteArrayOf(0x46, 0x6E), "cover.jpg".toByteArray(Charsets.UTF_8))
        val mimeType = matroskaElement(byteArrayOf(0x46, 0x60), "image/jpeg".toByteArray(Charsets.UTF_8))
        val fileData = matroskaElement(byteArrayOf(0x46, 0x5C), artworkBytes)
        val fileUid = matroskaElement(byteArrayOf(0x46, 0xAE.toByte()), byteArrayOf(1))
        val attachedFile = matroskaElement(
            byteArrayOf(0x61, 0xA7.toByte()),
            description + fileName + mimeType + fileData + fileUid
        )
        return matroskaElement(byteArrayOf(0x19, 0x41, 0xA4.toByte(), 0x69), attachedFile)
    }

    private fun encodeEbmlSize(value: Long, fixedWidth: Int? = null): ByteArray {
        if (value < 0L) throw IOException("Invalid EBML element size")
        val width = fixedWidth ?: (1..8).firstOrNull { candidate ->
            value <= (1L shl (7 * candidate)) - 2L
        } ?: throw IOException("EBML element is too large")
        if (width !in 1..8 || value > (1L shl (7 * width)) - 2L) {
            throw IOException("EBML size does not fit its existing field")
        }
        val encoded = ByteArray(width)
        var remaining = value
        for (index in width - 1 downTo 0) {
            encoded[index] = (remaining and 0xFFL).toByte()
            remaining = remaining ushr 8
        }
        encoded[0] = (encoded[0].toInt() or (1 shl (8 - width))).toByte()
        return encoded
    }

    /**
     * Convert the small EBML header to the Matroska DocType before writing an AttachedFile.
     * The Segment is copied byte-for-byte, preserving all Opus packets and timestamps.
     */
    private fun convertWebmHeaderToMatroska(file: File) {
        val convertedFile = File(file.parentFile, file.name + ".matroska-header")
        var backupFile: File? = null
        try {
            java.io.RandomAccessFile(file, "r").use { input ->
                if (input.length() < 8L ||
                    input.readUnsignedByte() != 0x1A ||
                    input.readUnsignedByte() != 0x45 ||
                    input.readUnsignedByte() != 0xDF ||
                    input.readUnsignedByte() != 0xA3
                ) {
                    throw IOException("The downloaded audio does not start with an EBML header")
                }

                val firstSizeByte = input.readUnsignedByte()
                var sizeWidth = 1
                var sizeMarker = 0x80
                while (sizeWidth <= 8 && (firstSizeByte and sizeMarker) == 0) {
                    sizeWidth++
                    sizeMarker = sizeMarker ushr 1
                }
                if (sizeWidth > 8) throw IOException("Invalid EBML header size")

                var headerPayloadSize = (firstSizeByte and (sizeMarker - 1)).toLong()
                repeat(sizeWidth - 1) {
                    headerPayloadSize = (headerPayloadSize shl 8) or input.readUnsignedByte().toLong()
                }
                if (headerPayloadSize <= 0L || headerPayloadSize > 64L * 1024L) {
                    throw IOException("Unexpected EBML header size: $headerPayloadSize")
                }

                val headerPayload = ByteArray(headerPayloadSize.toInt())
                input.readFully(headerPayload)
                val segmentStart = input.filePointer
                val rewrittenPayload = ByteArrayOutputStream(headerPayload.size + 16)
                var cursor = 0
                var docTypeFound = false

                while (cursor < headerPayload.size) {
                    val elementStart = cursor
                    val firstIdByte = headerPayload[cursor].toInt() and 0xFF
                    var idWidth = 1
                    var idMarker = 0x80
                    while (idWidth <= 4 && (firstIdByte and idMarker) == 0) {
                        idWidth++
                        idMarker = idMarker ushr 1
                    }
                    if (idWidth > 4 || cursor + idWidth >= headerPayload.size) {
                        throw IOException("Invalid EBML header element ID")
                    }

                    val elementId = headerPayload.copyOfRange(cursor, cursor + idWidth)
                    cursor += idWidth
                    val (elementSize, elementSizeWidth) = decodeEbmlSize(headerPayload, cursor)
                    cursor += elementSizeWidth
                    if (elementSize < 0L || elementSize > (headerPayload.size - cursor).toLong()) {
                        throw IOException("EBML header element extends beyond the header")
                    }
                    val elementEnd = cursor + elementSize.toInt()
                    val isDocType = elementId.contentEquals(byteArrayOf(0x42, 0x82.toByte()))
                    val isCrc32 = elementId.contentEquals(byteArrayOf(0xBF.toByte()))

                    if (isDocType) {
                        if (docTypeFound) throw IOException("EBML header contains more than one DocType")
                        docTypeFound = true
                        val oldDocType = String(headerPayload, cursor, elementSize.toInt(), Charsets.UTF_8)
                        if (oldDocType != "webm" && oldDocType != "matroska") {
                            throw IOException("Unsupported EBML DocType: $oldDocType")
                        }
                        val newDocType = "matroska".toByteArray(Charsets.UTF_8)
                        rewrittenPayload.write(elementId)
                        rewrittenPayload.write(encodeEbmlSize(newDocType.size.toLong()))
                        rewrittenPayload.write(newDocType)
                    } else if (!isCrc32) {
                        rewrittenPayload.write(headerPayload, elementStart, elementEnd - elementStart)
                    }
                    cursor = elementEnd
                }

                if (!docTypeFound) throw IOException("EBML header has no DocType element")
                val newHeaderPayload = rewrittenPayload.toByteArray()

                FileOutputStream(convertedFile).use { output ->
                    output.write(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
                    output.write(encodeEbmlSize(newHeaderPayload.size.toLong()))
                    output.write(newHeaderPayload)

                    input.seek(segmentStart)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        if (bytesRead > 0) output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (!convertedFile.isFile || convertedFile.length() <= 0L) {
                throw IOException("Matroska header conversion produced an empty file")
            }
            backupFile = File(file.parentFile, file.name + ".webm-backup")
            if (backupFile.exists() && !backupFile.delete()) {
                throw IOException("Could not clear the pre-conversion staging backup")
            }
            if (!file.renameTo(backupFile)) {
                throw IOException("Could not preserve the original WebM staging file")
            }
            if (!convertedFile.renameTo(file)) {
                backupFile.renameTo(file)
                throw IOException("Could not install the Matroska header")
            }
            backupFile.delete()
        } catch (error: Exception) {
            convertedFile.delete()
            if (backupFile?.exists() == true && !file.exists()) {
                backupFile.renameTo(file)
            }
            throw error
        }
    }

    private fun decodeEbmlSize(bytes: ByteArray, offset: Int): Pair<Long, Int> {
        if (offset !in bytes.indices) throw IOException("Missing EBML element size")
        val first = bytes[offset].toInt() and 0xFF
        var width = 1
        var marker = 0x80
        while (width <= 8 && (first and marker) == 0) {
            width++
            marker = marker ushr 1
        }
        if (width > 8 || offset + width > bytes.size) {
            throw IOException("Invalid EBML element size")
        }

        var value = (first and (marker - 1)).toLong()
        for (index in 1 until width) {
            value = (value shl 8) or (bytes[offset + index].toInt() and 0xFF).toLong()
        }
        if (value == (1L shl (7 * width)) - 1L) {
            throw IOException("Unknown-size elements are not valid inside the EBML header")
        }
        return value to width
    }

    private fun verifyAppendedMatroskaMetadata(file: File, expectedElements: ByteArray) {
        if (expectedElements.isEmpty()) throw IOException("No Matroska metadata was generated")
        java.io.RandomAccessFile(file, "r").use { randomFile ->
            val fileLength = randomFile.length()
            if (fileLength < expectedElements.size.toLong()) {
                throw IOException("Matroska metadata verification failed: tagged file is too small")
            }
            randomFile.seek(fileLength - expectedElements.size.toLong())
            val actualElements = ByteArray(expectedElements.size)
            randomFile.readFully(actualElements)
            if (!actualElements.contentEquals(expectedElements)) {
                throw IOException("WebM metadata verification failed: tags or cover attachment were not persisted")
            }
        }
    }

    private fun appendMatroskaMetadataTags(file: File, tags: ByteArray, coverAttachment: ByteArray?) {
        val segmentChildren = if (coverAttachment == null || coverAttachment.isEmpty()) tags else tags + coverAttachment
        java.io.RandomAccessFile(file, "rw").use { randomFile ->
            val fileLength = randomFile.length()
            if (fileLength < 8L) throw IOException("Downloaded Matroska file is too small to tag")

            // The EBML Segment header occurs near the beginning of the file. Do not scan the
            // audio clusters indefinitely or risk matching arbitrary payload bytes later on.
            val scanLimit = minOf(fileLength - 4L, 1024L * 1024L)
            var segmentOffset = -1L
            var offset = 0L
            while (offset <= scanLimit) {
                randomFile.seek(offset)
                if (randomFile.readUnsignedByte() == 0x18 &&
                    randomFile.readUnsignedByte() == 0x53 &&
                    randomFile.readUnsignedByte() == 0x80 &&
                    randomFile.readUnsignedByte() == 0x67
                ) {
                    segmentOffset = offset
                    break
                }
                offset++
            }
            if (segmentOffset < 0L) throw IOException("Matroska Segment header was not found")

            val sizeOffset = segmentOffset + 4L
            randomFile.seek(sizeOffset)
            val first = randomFile.readUnsignedByte()
            var width = 1
            var marker = 0x80
            while (width <= 8 && (first and marker) == 0) {
                width++
                marker = marker ushr 1
            }
            if (width > 8) throw IOException("Invalid Matroska Segment size")

            var segmentSize = (first and (marker - 1)).toLong()
            repeat(width - 1) {
                segmentSize = (segmentSize shl 8) or randomFile.readUnsignedByte().toLong()
            }
            val unknownSize = segmentSize == (1L shl (7 * width)) - 1L
            if (unknownSize) {
                // Unknown-size Segments extend to EOF, so adding another Segment child is safe.
                randomFile.seek(fileLength)
                randomFile.write(segmentChildren)
                return
            }

            val payloadStart = sizeOffset + width
            val expectedEnd = payloadStart + segmentSize
            if (expectedEnd != fileLength) {
                throw IOException("Unexpected trailing bytes after the WebM Segment")
            }
            val newSize = encodeEbmlSize(segmentSize + segmentChildren.size.toLong(), width)
            randomFile.seek(sizeOffset)
            randomFile.write(newSize)
            randomFile.seek(fileLength)
            randomFile.write(segmentChildren)
        }
    }

    private fun findCachedStream(context: Context, songId: String): CachedStreamCandidate? {
        val cache = try {
            AudioPlaybackService.getCache(context.applicationContext)
        } catch (e: Exception) {
            throw IOException("Unable to inspect the local audio cache; refusing to start a network download", e)
        }

        val candidates = cache.keys.mapNotNull { cacheKey ->
            val metadata = AudioPlaybackService.getCachedMediaMetadata(context, cacheKey)
                ?: return@mapNotNull null
            if (metadata.songId != songId) return@mapNotNull null

            val spans = cache.getCachedSpans(cacheKey).toList().sortedBy { it.position }
            if (spans.isEmpty()) return@mapNotNull null
            val contentMetadata = cache.getContentMetadata(cacheKey)
            val contentLength = ContentMetadata.getContentLength(contentMetadata)
            val cachedBytes = spans.sumOf { it.length }
            // Media3 does not preserve MIME in cache metadata. Use the MIME recorded after
            // playback, then the URL's mime parameter or a container signature for old entries.
            val mimeType = metadata.mimeType.takeIf { it.isNotBlank() }
                ?: inferCachedMimeType(cacheKey, spans)
            if (mimeType.isNullOrBlank()) return@mapNotNull null

            var coveredUntil = 0L
            var hasGap = false
            spans.forEach { span ->
                if (span.position > coveredUntil) hasGap = true
                coveredUntil = maxOf(coveredUntil, span.position + span.length)
            }
            val complete = contentLength > 0L &&
                !hasGap &&
                coveredUntil >= contentLength &&
                cache.isCached(cacheKey, 0L, contentLength)

            CachedStreamCandidate(
                cacheKey = cacheKey,
                spans = spans,
                contentLength = contentLength,
                mimeType = mimeType,
                cachedBytes = cachedBytes,
                isComplete = complete,
                metadata = metadata
            )
        }
        return candidates.firstOrNull { it.isComplete } ?: candidates.maxByOrNull { it.cachedBytes }
    }

    private fun inferCachedMimeType(cacheKey: String, spans: List<CacheSpan>): String? {
        val urlMime = runCatching {
            android.net.Uri.parse(cacheKey).getQueryParameter("mime")
        }.getOrNull()?.substringBefore(';')?.trim()
        if (!urlMime.isNullOrBlank()) return urlMime

        val firstSpan = spans.firstOrNull { it.position == 0L } ?: return null
        val spanFile = firstSpan.file ?: return null
        val header = ByteArray(16)
        val read = runCatching {
            FileInputStream(spanFile).use { input -> input.read(header) }
        }.getOrDefault(-1)
        if (read < 4) return null

        fun ascii(start: Int, length: Int): String =
            String(header, start, length, Charsets.US_ASCII)

        return when {
            (header[0].toInt() and 0xFF) == 0x1A &&
                (header[1].toInt() and 0xFF) == 0x45 &&
                (header[2].toInt() and 0xFF) == 0xDF &&
                (header[3].toInt() and 0xFF) == 0xA3 -> "audio/webm"
            read >= 8 && ascii(4, 4) == "ftyp" -> "audio/mp4"
            ascii(0, 4) == "OggS" -> "audio/ogg"
            ascii(0, 4) == "fLaC" -> "audio/flac"
            ascii(0, 4) == "RIFF" -> "audio/wav"
            ascii(0, 3) == "ID3" ||
                ((header[0].toInt() and 0xFF) == 0xFF &&
                    ((header[1].toInt() and 0xE0) == 0xE0)) -> "audio/mpeg"
            else -> null
        }
    }

    private fun copyCachedStream(
        cached: CachedStreamCandidate,
        outputFile: File,
        coroutineContext: CoroutineContext
    ) {
        outputFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        var expectedPosition = 0L
        FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(64 * 1024)
            for (span in cached.spans) {
                if (span.position != expectedPosition) {
                    throw IOException("Cached audio has a gap; refusing to use the network")
                }
                val spanFile = span.file ?: throw IOException("A cached audio segment is no longer available")
                FileInputStream(spanFile).use { input ->
                    var remaining = span.length
                    while (remaining > 0L) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) throw IOException("Cached audio segment ended unexpectedly")
                        output.write(buffer, 0, read)
                        remaining -= read.toLong()
                    }
                }
                expectedPosition += span.length
            }
            if (expectedPosition < cached.contentLength) {
                throw IOException("Cached audio is incomplete; refusing to use the network")
            }
        }
        if (outputFile.length() != cached.contentLength) {
            outputFile.delete()
            throw IOException("Cached audio size does not match its content length")
        }
    }

    private fun extensionForMimeType(mimeType: String?): String = when {
        mimeType?.contains("mp4", ignoreCase = true) == true -> "m4a"
        mimeType?.contains("mpeg", ignoreCase = true) == true -> "mp3"
        mimeType?.contains("webm", ignoreCase = true) == true -> "opus"
        mimeType?.contains("matroska", ignoreCase = true) == true -> "mka"
        mimeType?.contains("opus", ignoreCase = true) == true -> "opus"
        mimeType?.contains("ogg", ignoreCase = true) == true -> "ogg"
        mimeType?.contains("flac", ignoreCase = true) == true -> "flac"
        mimeType?.contains("aac", ignoreCase = true) == true -> "aac"
        mimeType?.contains("wav", ignoreCase = true) == true -> "wav"
        else -> "m4a"
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active song downloads"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notificationChannels
                .filter { it.id != CHANNEL_ID && it.id.endsWith("_downloads") }
                .forEach { manager.deleteNotificationChannel(it.id) }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createCancelDownloadPendingIntent(
        context: Context,
        videoId: String,
        notificationId: Int
    ): PendingIntent {
        val intent = Intent(context, SongDownloadCancelReceiver::class.java).apply {
            action = SongDownloadCancelReceiver.ACTION_CANCEL
            putExtra(SongDownloadCancelReceiver.EXTRA_VIDEO_ID, videoId)
            putExtra(SongDownloadCancelReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        )
    }

    private fun updateNotification(
        context: Context,
        title: String,
        progress: Int,
        notificationId: Int,
        videoId: String
    ) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.dsann.music.R.drawable.ic_music_note)
            .setContentTitle("Downloading Track")
            .setContentText("$title ($progress%)")
            .setSubText("Quantune · Cancel anytime")
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                createCancelDownloadPendingIntent(context, videoId, notificationId)
            )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun showFinishedNotification(context: Context, title: String, artist: String, notificationId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.dsann.music.R.drawable.ic_music_note)
            .setContentTitle("Download Complete")
            .setContentText("$title — $artist")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    fun isSongDownloaded(context: Context, song: SongItem): Boolean {
        if (song.videoId.isNullOrBlank()) return true // Local songs are already available offline
        val selectedFormat = NewPipeStreamExtractor.normalizeDownloadAudioFormat(
            context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .getString("download_audio_format", "m4a")
        )
        return findDownloadedFormatUri(context.applicationContext, song, selectedFormat) != null
    }
}
