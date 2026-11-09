package com.dsann.music.viewmodel.lyrics

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import com.dsann.music.data.lyrics.LrcParser
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LyricsRepository
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.LyricsState
import com.dsann.music.model.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Handles lyrics state, fetch, and retry flows for player view models.
 * Kotlin + StateFlow implementation for modern reactive architecture.
 */
class PlayerLyricsStateController(private val logTag: String) {
    private companion object {
        const val DEFAULT_PROVIDER = "KuGou"

        // Parsing stays off the UI thread. A single daemon worker avoids spawning
        // competing CPU-heavy parser threads on memory-constrained devices.
        val LYRICS_PARSE_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "DSMusicLyricsParser").apply { isDaemon = true }
        }
    }

    private var activeLyricsSongId: Long? = null
    private var providerSelectionGeneration: Long = 0L
    private var lyricsRequestGeneration: Long = 0L
    private val pendingParseCallbacks =
        HashMap<String, MutableList<(List<LyricLine>?) -> Unit>>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val parsedLyricsCache = object : LruCache<String, List<LyricLine>>(512 * 1024) {
        override fun sizeOf(key: String, value: List<LyricLine>): Int {
            val lineBytes = value.sumOf { line ->
                96L + line.content.length * 2L + line.words.size * 48L
            }
            return (key.length * 2L + lineBytes)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
                .coerceAtLeast(1)
        }
    }

    private fun beginLyricsRequest(): Long {
        lyricsRequestGeneration += 1L
        return lyricsRequestGeneration
    }

    private val _syncedLyrics = MutableStateFlow<List<LyricLine>?>(null)
    val syncedLyrics: StateFlow<List<LyricLine>?> = _syncedLyrics.asStateFlow()

    private val _plainLyrics = MutableStateFlow<String?>(null)
    val plainLyrics: StateFlow<String?> = _plainLyrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    private val _isLyricsModeEnabled = MutableStateFlow(false)
    val isLyricsModeEnabled: StateFlow<Boolean> = _isLyricsModeEnabled.asStateFlow()

    private val _lyricsState = MutableStateFlow(LyricsState.IDLE)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)

    private val _providerResults = MutableStateFlow<Map<String, LyricsResult>>(emptyMap())
    val providerResults: StateFlow<Map<String, LyricsResult>> = _providerResults.asStateFlow()

    private val _selectedProvider = MutableStateFlow<String?>(null)
    val selectedProvider: StateFlow<String?> = _selectedProvider.asStateFlow()

    val shouldShowLyrics: Boolean get() = _isLyricsModeEnabled.value

    fun setLyricsModeEnabled(enabled: Boolean) {
        _isLyricsModeEnabled.value = enabled
    }

    fun clearForSongChange(song: SongItem? = null) {
        beginLyricsRequest()
        activeLyricsSongId = song?.id
        providerSelectionGeneration += 1L
        _syncedLyrics.value = null
        _plainLyrics.value = null
        _error.value = null
        _isLoading.value = false
        _lyricsState.value = LyricsState.IDLE
        _statusMessage.value = null
        _providerResults.value = emptyMap()
        _selectedProvider.value = null
    }

    fun fetchLyrics(context: Context, song: SongItem?) {
        if (song == null) return

        val repository = LyricsRepository.getInstance(context)
        val requestGeneration = beginLyricsRequest()
        activeLyricsSongId = song.id
        providerSelectionGeneration += 1L
        repository.cancelCurrent()

        // Reset state
        _lyricsState.value = LyricsState.IDLE
        _syncedLyrics.value = null
        _plainLyrics.value = null
        _error.value = null
        _providerResults.value = emptyMap()
        _selectedProvider.value = null

        setLyricsState(LyricsState.FETCHING)
        _statusMessage.value = "Searching for lyrics..."

        repository.fetchLyrics(
            song.title,
            song.artist,
            song.albumName,
            song.duration,
            song.path,
            createCallback(
                requestSongId = song.id,
                keepExistingLyricsOnNotFound = true,
                instrumentalMessage = "Instrumental track - no vocals",
                logUpgrades = true,
                requestGeneration = requestGeneration
            ),
            song.videoId
        )
    }

    fun retryLyrics(context: Context, song: SongItem?) {
        if (song == null) return

        val requestGeneration = beginLyricsRequest()
        activeLyricsSongId = song.id
        providerSelectionGeneration += 1L
        LyricsRepository.getInstance(context).cancelProviderFetch()

        // Reset state
        _syncedLyrics.value = null
        _plainLyrics.value = null
        _error.value = null
        _lyricsState.value = LyricsState.IDLE
        _providerResults.value = emptyMap()
        _selectedProvider.value = null

        setLyricsState(LyricsState.FETCHING)
        _statusMessage.value = "Retrying all sources..."

        LyricsRepository.getInstance(context).retryLyrics(
            song.title,
            song.artist,
            song.albumName,
            song.duration,
            song.path,
            createCallback(
                requestSongId = song.id,
                keepExistingLyricsOnNotFound = false,
                instrumentalMessage = "Instrumental track",
                logUpgrades = false,
                requestGeneration = requestGeneration
            ),
            song.videoId
        )
    }

    fun selectProvider(context: Context, song: SongItem?, providerName: String) {
        if (song == null || providerName.isBlank()) return
        val repository = LyricsRepository.getInstance(context)
        val result = _providerResults.value[providerName]

        // Reuse an in-memory result only when its payload is still present. Results marked as
        // payloadClearedForMemory deliberately go through the single-provider fetch below.
        if (result != null && result.hasLyrics() && !result.payloadClearedForMemory) {
            val requestGeneration = beginLyricsRequest()
            activeLyricsSongId = song.id
            providerSelectionGeneration += 1L
            val selectionRequestGeneration = providerSelectionGeneration
            repository.cancelProviderFetch()
            val compacted = _providerResults.value.toMutableMap()
            clearInactiveProviderPayloads(compacted, providerName)
            _providerResults.value = compacted

            _syncedLyrics.value = null
            _plainLyrics.value = null
            _selectedProvider.value = providerName
            applyLyricsResult(
                result = result,
                requestSongId = song.id,
                selectionRequestGeneration = selectionRequestGeneration,
                requestGeneration = requestGeneration
            ) {
                _lyricsState.value = LyricsState.SUCCESS
                _isLoading.value = false
                _error.value = null
                _statusMessage.value = "Lyrics from ${result.providerName ?: providerName}"
            }
            return
        }

        // A null result is selectable too: a row may not have completed during the initial sweep.
        // Fetch only this source and invalidate any older selection request.
        if (_selectedProvider.value == providerName && _isLoading.value) return
        val requestGeneration = beginLyricsRequest()
        activeLyricsSongId = song.id
        providerSelectionGeneration += 1L
        val selectionRequestGeneration = providerSelectionGeneration
        repository.cancelProviderFetch()

        _selectedProvider.value = providerName
        _syncedLyrics.value = null
        _plainLyrics.value = null
        _error.value = null
        _isLoading.value = true
        setLyricsState(LyricsState.FETCHING)
        _statusMessage.value = "Fetching lyrics from $providerName…"

        repository.fetchProviderLyrics(
            song.title,
            song.artist,
            song.albumName,
            song.duration,
            song.path,
            providerName,
            createCallback(
                requestSongId = song.id,
                keepExistingLyricsOnNotFound = false,
                instrumentalMessage = "Instrumental track",
                logUpgrades = false,
                selectionRequestGeneration = selectionRequestGeneration,
                requestGeneration = requestGeneration
            ),
            song.videoId
        )
    }

    /**
     * The selector needs only provider identity/status for inactive sources. Removing their large
     * raw lyric strings keeps one source payload resident instead of retaining every catalogue's
     * full result. The UI can refetch if the user wants to switch back to a cleared source.
     */
    private fun clearInactiveProviderPayloads(
        results: MutableMap<String, LyricsResult>,
        activeProviderName: String
    ) {
        results.toMap().forEach { (name, item) ->
            if (name != activeProviderName && item.hasLyrics()) {
                results[name] = item.copy(
                    syncedLyrics = null,
                    plainLyrics = null,
                    // Preserve the source's lyric type after dropping its raw text.
                    payloadClearedForMemory = true
                )
            }
        }
    }

    fun determineBestResult(results: Map<String, LyricsResult>): LyricsResult? {
        // Failed providers are represented by empty confidence=-1 sentinels. Ignore them.
        val candidates = results.values.filter { it.hasLyrics() && it.confidence >= 0f }

        fun preferKugouWithinTier(items: List<LyricsResult>): LyricsResult? =
            items.maxWithOrNull(
                compareBy<LyricsResult> {
                    it.providerName.equals(DEFAULT_PROVIDER, ignoreCase = true)
                }.thenBy { it.confidence }
            )

        // Karaoke source priority is fixed: LyricsPlus first, KuGou second, Bini third.
        // Other karaoke sources remain eligible after these three, with confidence as the tie-break.
        fun preferKaraokeSource(items: List<LyricsResult>): LyricsResult? =
            items.minWithOrNull(
                compareBy<LyricsResult> {
                    when {
                        it.providerName.equals("LyricsPlus", ignoreCase = true) -> 0
                        it.providerName.equals("KuGou", ignoreCase = true) -> 1
                        it.providerName.equals("Bini", ignoreCase = true) -> 2
                        else -> 3
                    }
                }.thenByDescending { it.confidence }
            )

        // Preserve the quality hierarchy: karaoke, then line-synchronized, then plain text.
        val karaokeCandidates = candidates.filter { it.isWordByWord }
        if (karaokeCandidates.isNotEmpty()) return preferKaraokeSource(karaokeCandidates)

        val syncedCandidates = candidates.filter { it.isSynced }
        if (syncedCandidates.isNotEmpty()) return preferKugouWithinTier(syncedCandidates)

        val plainCandidates = candidates.filter { !it.isSynced && !it.isWordByWord && !it.isInstrumental }
        if (plainCandidates.isNotEmpty()) return preferKugouWithinTier(plainCandidates)

        return candidates.firstOrNull { it.isInstrumental }
    }

    private fun createCallback(
        requestSongId: Long,
        keepExistingLyricsOnNotFound: Boolean,
        instrumentalMessage: String,
        logUpgrades: Boolean,
        selectionRequestGeneration: Long? = null,
        requestGeneration: Long = lyricsRequestGeneration
    ): LyricsRepository.ExtendedLyricsCallback {
        return object : LyricsRepository.ExtendedLyricsCallback {
            override fun onLyricsFound(result: LyricsResult) {
                if (!isActiveCallback(requestSongId, selectionRequestGeneration, requestGeneration)) return

                if (_selectedProvider.value == null) {
                    applyLyricsResult(
                        result = result,
                        requestSongId = requestSongId,
                        selectionRequestGeneration = selectionRequestGeneration,
                        requestGeneration = requestGeneration
                    ) {
                        _lyricsState.value = LyricsState.SUCCESS
                        _isLoading.value = false
                        _error.value = null
                        _statusMessage.value = "Lyrics from ${result.providerName}"
                    }
                }
            }

            override fun onLyricsUpgraded(betterResult: LyricsResult) {
                if (!isActiveCallback(requestSongId, selectionRequestGeneration, requestGeneration)) return

                val syncedPayload = betterResult.syncedLyrics
                if (_selectedProvider.value == null && !syncedPayload.isNullOrEmpty()) {
                    parseLyricsCandidatesAsync(
                        candidates = listOf(syncedPayload),
                        requestSongId = requestSongId,
                        selectionRequestGeneration = selectionRequestGeneration,
                        requestGeneration = requestGeneration
                    ) { lines ->
                        if (!lines.isNullOrEmpty()) {
                            _syncedLyrics.value = lines
                            _plainLyrics.value = null
                            _lyricsState.value = LyricsState.SUCCESS
                            if (logUpgrades) {
                                _statusMessage.value = "Upgraded to synced lyrics from ${betterResult.providerName}"
                                Log.d(logTag, "Lyrics upgraded to synced from ${betterResult.providerName}")
                            }
                        } else {
                            Log.w(logTag, "Ignoring unparseable synced lyrics from ${betterResult.providerName}")
                        }
                    }
                }
            }

            override fun onLyricsNotFound(reason: String) {
                if (!isActiveCallback(requestSongId, selectionRequestGeneration, requestGeneration)) return
                if (selectionRequestGeneration == null && _selectedProvider.value != null && _isLoading.value) return

                if (keepExistingLyricsOnNotFound && hasLyricsData()) return

                if (_syncedLyrics.value.isNullOrEmpty() && _plainLyrics.value.isNullOrEmpty()) {
                    _lyricsState.value = LyricsState.FAILED
                    _isLoading.value = false
                    _error.value = Exception(reason)
                    _statusMessage.value = null
                }
            }

            override fun onInstrumental() {
                if (!isActiveCallback(requestSongId, selectionRequestGeneration, requestGeneration)) return
                if (selectionRequestGeneration == null && _selectedProvider.value != null && _isLoading.value) return

                if (_syncedLyrics.value.isNullOrEmpty() && _plainLyrics.value.isNullOrEmpty()) {
                    _lyricsState.value = LyricsState.FAILED
                    _isLoading.value = false
                    _error.value = Exception(instrumentalMessage)
                    _statusMessage.value = null
                }
            }

            override fun onSearchStatus(message: String) {
                if (!isActiveCallback(requestSongId, selectionRequestGeneration, requestGeneration)) return
                if (selectionRequestGeneration == null && _selectedProvider.value != null && _isLoading.value) return

                _statusMessage.value = message
            }

            override fun onProviderResult(providerName: String, result: LyricsResult?) {
                if (!isActiveCallback(requestSongId, selectionRequestGeneration, requestGeneration)) return

                val selectedBeforeCallback = _selectedProvider.value
                val currentMap = _providerResults.value.toMutableMap()
                currentMap[providerName] = when {
                    result == null -> LyricsResult(providerName = providerName, confidence = -1f)
                    selectedBeforeCallback != null && selectedBeforeCallback != providerName && result.hasLyrics() ->
                        result.copy(
                            syncedLyrics = null,
                            plainLyrics = null,
                            // Keep type metadata even when this inactive provider's payload is released.
                            payloadClearedForMemory = true
                        )
                    else -> result
                }
                _providerResults.value = currentMap

                // Continue recording initial provider statuses, but never let a late callback
                // from that sweep stop an explicit single-source request.
                if (selectionRequestGeneration == null &&
                    _selectedProvider.value != null && _isLoading.value
                ) return

                val selected = _selectedProvider.value
                if (selected != null) {
                    // Re-apply only updates from the active source; discard raw payloads from other sources.
                    if (selected == providerName && result != null && result.hasLyrics()) {
                        currentMap[providerName] = result
                        clearInactiveProviderPayloads(currentMap, selected)
                        _providerResults.value = currentMap
                        _syncedLyrics.value = null
                        _plainLyrics.value = null
                        applyLyricsResult(
                            result = result,
                            requestSongId = requestSongId,
                            selectionRequestGeneration = selectionRequestGeneration,
                            requestGeneration = requestGeneration
                        ) {
                            _lyricsState.value = LyricsState.SUCCESS
                            _isLoading.value = false
                            _error.value = null
                        }
                    }
                } else {
                    val best = determineBestResult(currentMap)
                    if (best != null && best.hasLyrics()) {
                        // Do not lock KuGou while LyricsPlus is still being checked.
                        // Once the highest available karaoke source is confirmed, release
                        // the inactive payloads to keep lyrics memory usage bounded.
                        val confirmedKaraokeSource = when {
                            best.isWordByWord &&
                                best.providerName.equals("LyricsPlus", ignoreCase = true) ->
                                "LyricsPlus"
                            best.isWordByWord &&
                                best.providerName.equals("KuGou", ignoreCase = true) &&
                                currentMap.containsKey("LyricsPlus") ->
                                "KuGou"
                            best.isWordByWord &&
                                best.providerName.equals("Bini", ignoreCase = true) &&
                                currentMap.containsKey("LyricsPlus") &&
                                currentMap.containsKey("KuGou") ->
                                "Bini"
                            else -> null
                        }
                        if (confirmedKaraokeSource != null) {
                            _selectedProvider.value = confirmedKaraokeSource
                            clearInactiveProviderPayloads(currentMap, confirmedKaraokeSource)
                            _providerResults.value = currentMap
                        }
                        _syncedLyrics.value = null
                        _plainLyrics.value = null
                        applyLyricsResult(
                            result = best,
                            requestSongId = requestSongId,
                            selectionRequestGeneration = selectionRequestGeneration,
                            requestGeneration = requestGeneration
                        ) {
                            _lyricsState.value = LyricsState.SUCCESS
                            _isLoading.value = false
                            _error.value = null
                        }
                    } else if (best != null && best.isInstrumental) {
                        _syncedLyrics.value = null
                        _plainLyrics.value = null
                        _lyricsState.value = LyricsState.FAILED
                        _isLoading.value = false
                        _error.value = Exception(instrumentalMessage)
                    }
                }
            }
        }
    }

    private fun isActiveCallback(
        requestSongId: Long,
        selectionRequestGeneration: Long?,
        requestGeneration: Long? = null
    ): Boolean {
        return activeLyricsSongId == requestSongId &&
            (selectionRequestGeneration == null || selectionRequestGeneration == providerSelectionGeneration) &&
            (requestGeneration == null || requestGeneration == lyricsRequestGeneration)
    }

    private fun applyLyricsResult(
        result: LyricsResult,
        requestSongId: Long,
        selectionRequestGeneration: Long?,
        requestGeneration: Long,
        onApplied: () -> Unit
    ) {
        val synced = result.syncedLyrics?.takeIf { it.isNotBlank() }
        val plain = result.plainLyrics?.takeIf { it.isNotBlank() }
        val rawPlain = plain?.trim()
        val plainIsTtml = rawPlain != null && (
            rawPlain.contains("<tt", ignoreCase = true) ||
                rawPlain.contains("<?xml", ignoreCase = true) ||
                rawPlain.contains("<p begin=", ignoreCase = true)
            )

        val parseCandidates = buildList {
            synced?.let(::add)
            if (plainIsTtml && rawPlain != synced) rawPlain?.let(::add)
        }

        if (parseCandidates.isEmpty()) {
            applyPlainLyrics(plain)
            onApplied()
            return
        }

        _isLoading.value = true
        parseLyricsCandidatesAsync(
            candidates = parseCandidates,
            requestSongId = requestSongId,
            selectionRequestGeneration = selectionRequestGeneration,
            requestGeneration = requestGeneration
        ) { lines ->
            if (!lines.isNullOrEmpty()) {
                _syncedLyrics.value = lines
                _plainLyrics.value = null
            } else {
                if (synced != null) {
                    Log.w(logTag, "Synced lyrics could not be parsed; trying plain lyrics fallback")
                }
                applyPlainLyrics(plain)
            }
            onApplied()
        }
    }

    private fun applyPlainLyrics(plain: String?) {
        if (!plain.isNullOrBlank()) {
            val normalized = sanitizePlainLyrics(plain.trim())
            _syncedLyrics.value = null
            _plainLyrics.value = normalized.takeIf { it.isNotBlank() }
        } else {
            _syncedLyrics.value = null
            _plainLyrics.value = null
        }
    }

    /**
     * Parse provider payloads off the main thread. A size-bounded LRU retains recent
     * parsed results, and identical in-flight payloads share one parse operation.
     */
    private fun parseLyricsCandidatesAsync(
        candidates: List<String>,
        requestSongId: Long,
        selectionRequestGeneration: Long?,
        requestGeneration: Long,
        onParsed: (List<LyricLine>?) -> Unit
    ) {
        val guardedCallback: (List<LyricLine>?) -> Unit = { parsed ->
            if (isActiveCallback(
                    requestSongId,
                    selectionRequestGeneration,
                    requestGeneration
                )
            ) {
                onParsed(parsed)
            }
        }

        val uncached = ArrayList<String>(candidates.size)
        for (candidate in candidates) {
            val cached = parsedLyricsCache.get(candidate)
            if (cached != null) {
                if (cached.isNotEmpty()) {
                    guardedCallback(cached)
                    return
                }
            } else {
                uncached.add(candidate)
            }
        }

        if (uncached.isEmpty()) {
            guardedCallback(null)
            return
        }

        val workKey = uncached.joinToString("|") { "${it.length}:$it" }
        val waitingCallbacks = pendingParseCallbacks[workKey]
        if (waitingCallbacks != null) {
            waitingCallbacks.add(guardedCallback)
            return
        }
        pendingParseCallbacks[workKey] = mutableListOf(guardedCallback)

        LYRICS_PARSE_EXECUTOR.execute {
            val parsedCandidates = ArrayList<Pair<String, List<LyricLine>>>(uncached.size)
            var bestLines: List<LyricLine>? = null
            for (candidate in uncached) {
                val parsed = runCatching { LrcParser.parse(candidate) }
                    .getOrDefault(emptyList())
                parsedCandidates.add(candidate to parsed)
                if (bestLines.isNullOrEmpty() && parsed.isNotEmpty()) {
                    bestLines = parsed
                }
                if (!bestLines.isNullOrEmpty()) break
            }

            mainHandler.post {
                parsedCandidates.forEach { (raw, lines) ->
                    parsedLyricsCache.put(raw, lines)
                }
                val callbacks = pendingParseCallbacks.remove(workKey).orEmpty()
                callbacks.forEach { callback -> callback(bestLines) }
            }
        }
    }

    /**
     * Strip provider wrappers and XML tags so malformed provider payloads never appear
     * as raw markup in the lyrics view.
     */
    private fun sanitizePlainLyrics(plain: String): String {
        var text = plain.trim()
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                val json = com.google.gson.JsonParser.parseString(text).asJsonObject
                for (key in listOf("lyrics", "plainLyrics", "content", "data", "text", "ttml", "syncedLyrics")) {
                    val value = json.get(key)
                    if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        text = value.asString.trim()
                        break
                    }
                }
            } catch (_: Exception) {
                // Leave non-JSON text untouched for the sanitizer below.
            }
        }
        if (text.contains("<tt", ignoreCase = true) ||
            text.contains("<?xml", ignoreCase = true) ||
            text.contains("<p", ignoreCase = true)) {
            val paragraphText = Regex(
                """<p\b[^>]*>(.*?)</p>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).findAll(text)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            if (paragraphText.isNotBlank()) text = paragraphText
            else text = text.replace(Regex("<[^>]+>"), " ").trim()
        } else {
            text = text.replace(Regex("<[^>]+>"), " ").trim()
        }
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
    }

    private fun hasLyricsData(): Boolean {
        val synced = _syncedLyrics.value
        val plain = _plainLyrics.value
        return !synced.isNullOrEmpty() || !plain.isNullOrEmpty()
    }

    private fun setLyricsState(newState: LyricsState) {
        val current = _lyricsState.value
        if (!current.canTransitionTo(newState)) {
            Log.w(logTag, "Invalid lyrics state transition: $current -> $newState. Blocked.")
            return
        }
        _lyricsState.value = newState
        _isLoading.value = newState.isLoading
    }
}
