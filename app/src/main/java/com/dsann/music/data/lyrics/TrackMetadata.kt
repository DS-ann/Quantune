package com.dsann.music.data.lyrics

/**
 * Normalized track metadata used across all lyrics providers and the decision
 * engine.
 * Created by MetadataRepair from raw SongItem fields.
 */
data class TrackMetadata @JvmOverloads constructor(
    val rawTitle: String = "",
    val rawArtist: String = "",
    val cleanedTitle: String = "",
    val cleanedArtist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val filePath: String = "",
    val videoId: String? = null, // YouTube video ID for InnerTube lyrics
    val version: String = "original", // "original", "remix", "slowed", "live", "cover", "extended", "edit"
    val detectedLanguage: String = "unknown" // "en", "hi", "mr", "ta", "te", "pa", "unknown"
) {
    /**
     * Generates a normalized cache key for this track.
     * Uses cleaned artist + title + duration bucket (10-second granularity).
     */
    fun getCacheKey(): String {
        val raw = normalizeCachePart(cleanedArtist) + "|" +
            normalizeCachePart(cleanedTitle) + "|" + (durationMs / 10000L)
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            out.append(hex[value ushr 4]).append(hex[value and 0x0f])
        }
        return "lyr_v2_" + out.toString()
    }

    fun getLegacyCacheKey(): String? {
        val title = cleanedTitle.lowercase().trim()
        val artist = cleanedArtist.lowercase().trim()

        // The previous ASCII-only key removed non-Latin text. Do not migrate an old entry
        // if either side contains Unicode, because different songs could share that old key.
        if (cleanedTitle.any { it.code > 127 } || cleanedArtist.any { it.code > 127 }) return null
        if (title.replace(Regex("[^a-z0-9]"), "").isBlank()) return null
        if (artist.replace(Regex("[^a-z0-9]"), "").isBlank()) return null

        val raw = artist + "|" + title + "|" + (durationMs / 10000L)
        return "lyr_" + raw.replace(Regex("[^a-z0-9|]"), "")
    }

    private fun normalizeCachePart(value: String): String =
        java.text.Normalizer.normalize(
            value.lowercase(java.util.Locale.ROOT).trim(),
            java.text.Normalizer.Form.NFC
        ).replace(Regex("\\s+"), " ")

    override fun toString(): String {
        return "TrackMetadata(title='$cleanedTitle', artist='$cleanedArtist', version='$version', lang='$detectedLanguage', duration=$durationMs)"
    }

    class Builder {
        private var rawTitle = ""
        private var rawArtist = ""
        private var cleanedTitle = ""
        private var cleanedArtist = ""
        private var album = ""
        private var durationMs = 0L
        private var filePath = ""
        private var videoId: String? = null
        private var version = "original"
        private var detectedLanguage = "unknown"

        fun build(): TrackMetadata {
            return TrackMetadata(
                rawTitle = rawTitle,
                rawArtist = rawArtist,
                cleanedTitle = cleanedTitle,
                cleanedArtist = cleanedArtist,
                album = album,
                durationMs = durationMs,
                filePath = filePath,
                videoId = videoId,
                version = version,
                detectedLanguage = detectedLanguage
            )
        }
    }
}
