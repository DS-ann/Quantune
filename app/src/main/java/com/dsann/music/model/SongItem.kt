package com.dsann.music.model

import com.dsann.music.data.innertube.OnlineArtistRef
import android.net.Uri
import java.io.File
import java.io.Serializable
import androidx.core.net.toUri

data class SongItem(
    @JvmField val id: Long,
    @JvmField val title: String,
    @JvmField val artist: String,
    @JvmField val albumId: Long,
    @JvmField val path: String?,
    @JvmField val duration: Long,
    @JvmField val dateAdded: Long,
    @JvmField val data: String?
) : Serializable {

    @JvmField var thumbnailUrl: String? = null
    @JvmField var mediaStoreRelativePath: String? = null
    @JvmField var albumName: String? = null
    @JvmField var playbackContextName: String? = null
    @JvmField var videoId: String? = null
    @JvmField var artistId: String? = null
    @JvmField var artistRefs: ArrayList<OnlineArtistRef> = arrayListOf()
    @JvmField var lufs: Float? = null
    /** True when this queue entry resolves to a fully cached Media3 stream, not a live URL. */
    @JvmField var isCachedTrack: Boolean = false
    /** True when an online track is currently being served by a downloaded MediaStore/file URI. */
    @JvmField var isDownloadedOffline: Boolean = false
    /** True for queue entries inserted automatically by radio/autoplay, not by the listener. */
    @JvmField var isAutoplayRecommendation: Boolean = false

    private var uriString: String? = null

    var contentUri: Uri
        get() = uriString?.toUri() ?: "content://media/external/audio/media/$id".toUri()
        set(value) {
            uriString = value.toString()
        }

    // Main constructor with automatic cleaning fallbacks
    constructor(
        id: Long,
        rawTitle: String?,
        rawArtist: String?,
        albumId: Long,
        path: String?,
        duration: Long,
        dateAdded: Long
    ) : this(
        id,
        cleanTitle(rawTitle, path),
        cleanArtist(rawArtist, path),
        albumId,
        path,
        duration,
        dateAdded,
        path
    )

    // Secondary constructor without duration for backwards compatibility
    constructor(id: Long, title: String?, artist: String?, albumId: Long, path: String?, dateAdded: Long) : 
        this(id, title, artist, albumId, path, 0, dateAdded)

    fun getAlbumArtUri(): Uri? {
        if (thumbnailUrl != null) return enhanceThumbnailUrl(thumbnailUrl!!).toUri()
        if (albumId > 0) {
            return "content://media/external/audio/albumart/$albumId".toUri()
        }
        // Do not call File.exists() here: this method is used while composing
        // visible song rows and artwork pages. A synchronous storage stat for every
        // row stalls scrolling on slower Android 6 devices. Coil handles missing files.
        val localPath = path?.takeIf {
            it.isNotBlank() &&
                !it.startsWith("http://", ignoreCase = true) &&
                !it.startsWith("https://", ignoreCase = true) &&
                !it.contains("://")
        }
        if (localPath != null) {
            return Uri.fromFile(File(localPath))
        }
        return contentUri
    }

    /**
     * Reads embedded artwork using APIs available since Android 6.
     * MediaMetadataRetriever.release() is used instead of AutoCloseable/use,
     * which is not available on all supported Android versions.
     */
    fun getEmbeddedPicture(context: android.content.Context): ByteArray? {
        // A downloaded track can keep its original remote/null path while its playback
        // source changes to a local file:// URI. Resolve the active source first rather
        // than requiring path to be populated before attempting to read embedded art.
        val sourceUri = runCatching { contentUri }.getOrNull()
        val localPath = sequenceOf(path, data)
            .mapNotNull { raw ->
                raw?.takeIf { it.isNotBlank() }?.let { value ->
                    when {
                        value.startsWith("file://", ignoreCase = true) ->
                            runCatching { android.net.Uri.parse(value).path }.getOrNull()
                        value.startsWith("http://", ignoreCase = true) ||
                            value.startsWith("https://", ignoreCase = true) ||
                            value.contains("://") -> null
                        else -> value
                    }
                }
            }
            .firstOrNull()

        return try {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                // Prefer the granted MediaStore/document descriptor when the active
                // source is content://. This also works on Android 6.
                val contentArtwork = if (sourceUri?.scheme == "content") {
                    runCatching {
                        context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                            retriever.embeddedPicture
                        }
                    }.getOrNull()
                } else {
                    null
                }

                if (contentArtwork != null) {
                    contentArtwork
                } else {
                    // The downloaded source is normally file:// on Android 6. Use its
                    // actual filesystem path; openFileDescriptor(file://) is not reliable
                    // across legacy ContentResolver implementations.
                    val sourcePath = sourceUri
                        ?.takeIf { it.scheme == "file" }
                        ?.path
                        ?.takeIf { it.isNotBlank() }
                        ?: localPath
                        ?: return null
                    retriever.setDataSource(sourcePath)
                    retriever.embeddedPicture
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                    // Preserve the artwork read result if release fails.
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Song ID remains the stable identity, but playback-source differences must be observable by
     * StateFlow. The player can switch the same track ID between an online URL, a Media3 cache
     * URL, and a downloaded content URI; treating these as equal leaves stale source details/UI.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val songItem = other as SongItem
        return id == songItem.id &&
            contentUri == songItem.contentUri &&
            videoId == songItem.videoId &&
            isCachedTrack == songItem.isCachedTrack &&
            isDownloadedOffline == songItem.isDownloadedOffline
    }

    override fun hashCode(): Int {
        // Equal objects necessarily share the same stable song ID. Source variants may
        // intentionally collide, but keeping the ID hash stable avoids mutable-hash hazards.
        return id.hashCode()
    }

    /** Create a replacement instead of mutating the object already published through StateFlow. */
    fun withPlaybackSource(uri: Uri, cached: Boolean, downloaded: Boolean): SongItem {
        return copy().apply {
            thumbnailUrl = this@SongItem.thumbnailUrl
            mediaStoreRelativePath = this@SongItem.mediaStoreRelativePath
            albumName = this@SongItem.albumName
            playbackContextName = this@SongItem.playbackContextName
            videoId = this@SongItem.videoId
            artistId = this@SongItem.artistId
            artistRefs = ArrayList(this@SongItem.artistRefs)
            lufs = this@SongItem.lufs
            contentUri = uri
            isCachedTrack = cached
            isDownloadedOffline = downloaded
            isAutoplayRecommendation = this@SongItem.isAutoplayRecommendation
        }
    }

    companion object {
        private fun cleanTitle(rawTitle: String?, path: String?): String {
            var cleanTitle = rawTitle ?: "Unknown Title"
            if (cleanTitle.trim().isEmpty() || cleanTitle.equals("<unknown>", ignoreCase = true)) {
                cleanTitle = "Unknown Title"
            }
            if (path != null && cleanTitle == "Unknown Title") {
                try {
                    var fileName = File(path).name
                    if (fileName.lowercase().endsWith(".mp3")) {
                        fileName = fileName.substring(0, fileName.length - 4)
                    } else if (fileName.lowercase().endsWith(".m4a")) {
                        fileName = fileName.substring(0, fileName.length - 4)
                    }
                    if (fileName.contains(" - ")) {
                        val parts = fileName.split(" - ", limit = 2)
                        cleanTitle = parts[0].trim()
                    } else {
                        cleanTitle = fileName.trim()
                    }
                } catch (_: Exception) {}
            }
            return cleanTitle
        }

        private fun cleanArtist(rawArtist: String?, path: String?): String {
            var cleanArtist = rawArtist ?: "Unknown Artist"
            if (cleanArtist.trim().isEmpty() || cleanArtist.equals("<unknown>", ignoreCase = true)) {
                cleanArtist = "Unknown Artist"
            }
            if (path != null && cleanArtist == "Unknown Artist") {
                try {
                    var fileName = File(path).name
                    if (fileName.lowercase().endsWith(".mp3")) {
                        fileName = fileName.substring(0, fileName.length - 4)
                    } else if (fileName.lowercase().endsWith(".m4a")) {
                        fileName = fileName.substring(0, fileName.length - 4)
                    }
                    if (fileName.contains(" - ")) {
                        val parts = fileName.split(" - ", limit = 2)
                        cleanArtist = parts[1].trim()
                    }
                } catch (_: Exception) {}
            }
            return cleanArtist
        }

        @JvmStatic
        @JvmOverloads
        fun createOnlineSong(
            videoId: String?,
            title: String?,
            artist: String?,
            streamUrl: String?,
            durationMs: Long,
            thumbnailUrl: String?,
            artistId: String? = null,
            albumName: String? = null,
            artistRefs: List<OnlineArtistRef>? = null
        ): SongItem {
            val id = videoId?.hashCode()?.toLong() ?: (streamUrl?.hashCode()?.toLong() ?: System.currentTimeMillis())
            val song = SongItem(id, title ?: "Unknown Title", artist ?: "Unknown Artist", -1, streamUrl, durationMs, System.currentTimeMillis())
            song.contentUri = (streamUrl ?: "").toUri()
            song.thumbnailUrl = thumbnailUrl?.let { enhanceThumbnailUrl(it) }
            song.videoId = videoId
            song.artistId = artistId
            song.artistRefs = ArrayList(
                artistRefs.orEmpty().mapNotNull { ref ->
                    val name = ref?.name?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    OnlineArtistRef(name, ref.id?.takeIf { it.isNotBlank() })
                }
            )
            song.albumName = albumName?.takeIf { it.isNotBlank() }
            return song
        }

        @JvmStatic
        fun enhanceThumbnailUrl(url: String): String {
            if (url.isEmpty()) return url
            
            // Handle googleusercontent/ggpht dynamic image sizing parameters
            if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
                val regex = "(=[ws]\\d+.*)$".toRegex()
                return if (regex.containsMatchIn(url)) {
                    url.replace(regex, "=w1000-h1000-l90-rj")
                } else {
                    "$url=w1000-h1000-l90-rj"
                }
            }
            
            // Handle standard YouTube video thumbnails (fall back safely to hqdefault)
            if (url.contains("ytimg.com") || url.contains("youtube.com/vi/")) {
                if (url.contains("/default.jpg") || url.contains("/mqdefault.jpg") || url.contains("/hqdefault.jpg")) {
                    return url.replace(Regex("/(default|mqdefault)\\.jpg$"), "/hqdefault.jpg")
                }
            }
            
            return url
        }
    }
}

fun SongItem.toPlaylistSongEntity(playlistId: Long, position: Int = 0): com.dsann.music.data.db.PlaylistSongEntity {
    return com.dsann.music.data.db.PlaylistSongEntity(
        playlistId = playlistId,
        songId = this.videoId ?: this.id.toString(),
        title = this.title,
        artist = this.artist,
        album = "",
        duration = this.duration,
        thumbnailUrl = this.thumbnailUrl ?: this.path,
        addedAt = System.currentTimeMillis(),
        position = position,
        lufs = this.lufs
    )
}

fun com.dsann.music.data.db.PlaylistSongEntity.toSongItem(): SongItem {
    return if (this.songId.toLongOrNull() != null && (this.thumbnailUrl == null || !this.thumbnailUrl.startsWith("http"))) {
        // Local device song
        val localId = this.songId.toLong()
        SongItem(
            localId,
            this.title,
            this.artist,
            -1L, // albumId
            this.thumbnailUrl, // path is stored in thumbnailUrl
            this.duration,
            this.addedAt
        ).apply {
            this.lufs = this@toSongItem.lufs
        }
    } else {
        // Online YouTube Music song
        SongItem.createOnlineSong(
            videoId = this.songId,
            title = this.title,
            artist = this.artist,
            streamUrl = null,
            durationMs = this.duration,
            thumbnailUrl = this.thumbnailUrl
        ).apply {
            this.lufs = this@toSongItem.lufs
        }
    }
}
