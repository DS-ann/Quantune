package com.dsann.music.data.lyrics

/**
 * Unified result from any lyrics' provider.
 * Carries the raw lyrics content plus match quality metadata.
 */
data class LyricsResult(
    var syncedLyrics: String? = null,
    var plainLyrics: String? = null,
    var providerName: String? = null,
    var confidence: Float = 0f,
    var isSynced: Boolean = false,
    var isWordByWord: Boolean = false,
    var isInstrumental: Boolean = false,
    var matchedTitle: String? = null,
    var matchedArtist: String? = null,
    var matchedAlbum: String? = null,
    var matchedDuration: Float = 0f,
    var fetchTimestamp: Long = System.currentTimeMillis(),
    /** True when the lyric payload was dropped to save memory; refetch before using again. */
    var payloadClearedForMemory: Boolean = false
) {
    /**
     * Returns true if this result has any usable lyrics content.
     */
    fun hasLyrics(): Boolean {
        return !syncedLyrics.isNullOrEmpty() || !plainLyrics.isNullOrEmpty()
    }

    fun setMatchedAlbum(matchedAlbum: String?): LyricsResult {
        this.matchedAlbum = matchedAlbum
        return this
    }

    // ===== Builder Setters for backward compatibility =====

    fun setSyncedLyrics(syncedLyrics: String?): LyricsResult {
        this.syncedLyrics = syncedLyrics
        val raw = syncedLyrics.orEmpty().trimStart()
        this.isSynced = looksSynchronized(raw)
        if (this.isSynced) {
            this.isWordByWord = LrcParser.parse(raw).any { it.isWordByWord && it.words.isNotEmpty() }
        }
        return this
    }

    companion object {
        @JvmStatic
        fun looksSynchronized(content: String): Boolean {
            val raw = content.trimStart()
            if (raw.isEmpty()) return false
            return AdvancedLyricsParser.recognizes(raw) ||
                raw.contains("<tt", ignoreCase = true) ||
                raw.contains("<?xml", ignoreCase = true) ||
                raw.contains("<p begin=", ignoreCase = true) ||
                raw.contains("ttm:begin", ignoreCase = true) ||
                Regex("""(?m)^\s*\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""").containsMatchIn(raw) ||
                Regex("""<\d{1,3}:\d{2}[.:]\d{1,3}>""").containsMatchIn(raw) ||
                Regex("""(?m)^\s*\[\d+,\d+].*<\d+,\d+,-?\d+>""").containsMatchIn(raw) ||
                Regex("""(?m)^\s*\[\d+,\d+]\(\d+,\d+,-?\d+\)""").containsMatchIn(raw) ||
                Regex("""(?m)^\s*\[\d+,\d+].*\(\d+,\d+\)""").containsMatchIn(raw)
        }
    }

    fun setPlainLyrics(plainLyrics: String?): LyricsResult {
        this.plainLyrics = plainLyrics
        return this
    }

    fun setProviderName(providerName: String?): LyricsResult {
        this.providerName = providerName
        return this
    }

    fun setWordByWord(wordByWord: Boolean): LyricsResult {
        this.isWordByWord = wordByWord
        return this
    }

    fun setMatchedTitle(matchedTitle: String?): LyricsResult {
        this.matchedTitle = matchedTitle
        return this
    }

    fun setMatchedArtist(matchedArtist: String?): LyricsResult {
        this.matchedArtist = matchedArtist
        return this
    }

    fun setMatchedDuration(matchedDuration: Float): LyricsResult {
        this.matchedDuration = matchedDuration
        return this
    }

    /**
     * Converts this result to a legacy LrcLibResponse for backward compatibility.
     */
    fun toLrcLibResponse(): LrcLibResponse {
        return LrcLibResponse(
            id = 0,
            name = this.matchedTitle,
            artistName = this.matchedArtist,
            albumName = null,
            duration = this.matchedDuration,
            instrumental = this.isInstrumental,
            plainLyrics = this.plainLyrics,
            syncedLyrics = this.syncedLyrics
        )
    }

    override fun toString(): String {
        return "LyricsResult(provider='$providerName', confidence=$confidence, synced=$isSynced, wordByWord=$isWordByWord, instrumental=$isInstrumental, hasLyrics=${hasLyrics()})"
    }
}
