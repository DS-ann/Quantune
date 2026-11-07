package com.dsann.music.ui.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.dsann.music.model.SongItem
import com.dsann.music.service.AudioPlaybackService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * File facts read from the local media source. Bitrate from embedded metadata is kept
 * separate from an estimate calculated from file bytes and duration.
 */
internal data class OnlineStreamMetadata(
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val bitrateBps: Int? = null,
    val contentLengthBytes: Long? = null,
    val audioQuality: String? = null
)

internal object OnlineStreamMetadataReader {
    suspend fun read(videoId: String): OnlineStreamMetadata? {
        if (videoId.isBlank()) return null
        return try {
            // Metadata lookup must not fail simply because the user's selected playback codec is
            // unavailable for this track. This mode may inspect a usable source stream for details,
            // but it never seeds a playback URL cache.
            val player = com.dsann.music.data.innertube.YouTubeMusic
                .player(videoId, metadataOnly = true).getOrNull()
                ?: return null
            val audio = player.streams
                .filter { it.mimeType.startsWith("audio/", ignoreCase = true) }
                .maxByOrNull { it.bitrate }
            OnlineStreamMetadata(
                title = player.title.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) },
                artist = player.artist.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) },
                durationMs = player.durationMs.takeIf { it > 0L },
                mimeType = audio?.mimeType?.takeIf { it.isNotBlank() },
                bitrateBps = audio?.bitrate?.takeIf { it > 0 },
                contentLengthBytes = audio?.contentLength?.takeIf { it > 0L }
                    ?: queryContentLength(audio?.url),
                audioQuality = audio?.audioQuality?.takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Probe the selected stream with a one-byte range request to read the true total size.
     * No audio body is downloaded; servers that ignore ranges may still supply Content-Length.
     */
    private fun queryContentLength(streamUrl: String?): Long? {
        if (streamUrl.isNullOrBlank()) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_500
                readTimeout = 4_500
                instanceFollowRedirects = true
                setRequestProperty("Range", "bytes=0-0")
            }
            connection.connect()
            val status = connection.responseCode
            val rangeTotal = connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/')
                ?.takeIf { it != "*" }
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            rangeTotal ?: if (status == HttpURLConnection.HTTP_OK) {
                connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

internal data class SongFileMetadata(
    val isRemote: Boolean,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val bitrateBps: Long? = null,
    val estimatedBitrateBps: Long? = null,
    val mimeType: String? = null,
    val album: String? = null,
    val fileName: String? = null,
    val filePath: String? = null
)

internal object SongFileMetadataReader {

    fun isRemoteSong(song: SongItem): Boolean {
        if (song.isCachedTrack || song.isDownloadedOffline) return false
        val path = song.path.orEmpty()
        val uri = runCatching { song.contentUri }.getOrNull()
        return path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true) ||
            uri?.scheme.equals("http", ignoreCase = true) ||
            uri?.scheme.equals("https", ignoreCase = true) ||
            uri?.scheme.equals("innertube", ignoreCase = true)
    }

    fun read(context: Context, song: SongItem): SongFileMetadata {
        if (song.isCachedTrack) {
            val facts = runCatching {
                AudioPlaybackService.getCachedAudioFacts(
                    context.applicationContext,
                    song.id.toString(),
                    song.videoId,
                    song.contentUri.toString()
                )
            }.getOrNull()
            if (facts != null && facts.isComplete && facts.cachedBytes > 0L) {
                val duration = song.duration.takeIf { it > 0L }
                val bytes = facts.totalBytes ?: facts.cachedBytes
                val estimatedBitrate = if (duration != null && duration > 0L && bytes > 0L) {
                    (bytes.toDouble() * 8000.0 / duration.toDouble())
                        .takeIf { it.isFinite() && it > 0.0 && it < Long.MAX_VALUE.toDouble() }
                        ?.toLong()
                } else null
                return SongFileMetadata(
                    isRemote = false,
                    sizeBytes = bytes,
                    durationMs = duration,
                    estimatedBitrateBps = estimatedBitrate,
                    mimeType = facts.mimeType,
                    fileName = runCatching { song.contentUri.lastPathSegment }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                )
            }
            // The cached flag is only a queue hint. If its complete backing resource is gone,
            // report the real source as remote so Song Info and its metadata lookup cannot claim
            // that a deleted cache file is still playing.
            return SongFileMetadata(
                isRemote = true,
                durationMs = song.duration.takeIf { it > 0L },
                mimeType = null
            )
        }
        if (isRemoteSong(song)) return SongFileMetadata(isRemote = true)

        val localFile = sequenceOf(song.path, song.data)
            .filterNotNull()
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    !it.startsWith("content://", true) &&
                    !it.startsWith("http://", true) &&
                    !it.startsWith("https://", true)
            }
            .map { File(it) }
            .firstOrNull { it.isFile }

        val uri = runCatching { song.contentUri }.getOrNull()
        val sizeBytes = localFile?.length()?.takeIf { it > 0L }
            ?: uri?.let { querySize(context, it) }
        var durationMs: Long? = null
        var bitrateBps: Long? = null
        var mimeType: String? = null
        var album: String? = song.albumName?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }

        val retriever = MediaMetadataRetriever()
        try {
            when {
                localFile != null -> retriever.setDataSource(localFile.absolutePath)
                uri?.scheme == "content" || uri?.scheme == "file" -> retriever.setDataSource(context, uri)
            }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.takeIf { it.isNotBlank() }
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        } catch (_: Exception) {
            // Some MediaStore providers expose a readable URI but no retrievable file metadata.
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Metadata already collected remains valid if release fails.
            }
        }

        if (mimeType == null && uri != null) {
            mimeType = try {
                context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

        val fileName = localFile?.name ?: uri?.let { queryDisplayName(context, it) }
        val estimate = if (bitrateBps == null && sizeBytes != null && sizeBytes > 0L && song.duration > 0L) {
            // bits/sec = bytes * 8 / (durationMillis / 1000). Tags make this approximate.
            (sizeBytes.toDouble() * 8000.0 / song.duration.toDouble())
                .takeIf { it.isFinite() && it > 0.0 && it < Long.MAX_VALUE.toDouble() }
                ?.toLong()
        } else {
            null
        }

        return SongFileMetadata(
            isRemote = false,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            bitrateBps = bitrateBps,
            estimatedBitrateBps = estimate,
            mimeType = mimeType,
            album = album,
            fileName = fileName?.takeIf { it.isNotBlank() },
            filePath = localFile?.absolutePath
        )
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        for (column in listOf(android.provider.MediaStore.MediaColumns.SIZE, OpenableColumns.SIZE).distinct()) {
            val fromProvider = try {
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(column)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getLong(index).takeIf { it > 0L }
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (fromProvider != null) return fromProvider
        }

        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it > 0L }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        for (column in listOf(
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            OpenableColumns.DISPLAY_NAME
        ).distinct()) {
            val displayName = try {
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(column)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (displayName != null) return displayName
        }
        return null
    }
}
