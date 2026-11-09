@file:OptIn(DelicateCoroutinesApi::class)
@file:Suppress("DeferredResultUnused")

package com.dsann.music.data.innertube

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * High-level YouTube Music API facade.
 * Provides clean, type-safe methods for all YouTube Music operations.
 * This is the main entry point for online music features.
 */
object YouTubeMusic {

    private const val TAG = "YouTubeMusic"

    // Intelligent Streaming Performance Caches
    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private data class ActiveStreamRequest(
        val generation: Long,
        val deferred: Deferred<Result<String>>
    )
    private val activeStreamRequests = ConcurrentHashMap<String, ActiveStreamRequest>()
    private val streamSelectionGeneration = AtomicLong(0L)
    private const val CACHE_TTL = 6 * 60 * 60 * 1000L // Standard 6 Hour TTL

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun cleanExpiredCache() {
        val now = System.currentTimeMillis()
        // ConcurrentHashMap entry removeIf is API 24+ on Android. Conditional
        // remove(key, value) is available on API 23 and remains race-safe.
        for ((key, value) in streamUrlCache.entries) {
            if (value.second < now) {
                streamUrlCache.remove(key, value)
            }
        }
    }

    /**
     * Proactively resolve and cache the streaming URL in the background.
     * Eliminates click-to-play lag entirely for predicted next tracks.
     */
    @JvmStatic
    fun prefetch(videoId: String?) {
        if (videoId.isNullOrEmpty()) return
        // Fire-and-forget resolution call leveraging our deduplicated cache chain
        appScope.launch {
            try {
                getStreamUrl(videoId)
                Log.d(TAG, "Successfully prefetched stream cache for: $videoId")
            } catch (_: Exception) {
                // Prefetch failures are non-critical, silent ignore
            }
        }
    }

    // ========== Search ==========

    /**
     * Search YouTube Music for songs, albums, artists, playlists.
     * Returns mixed results (all types) when no filter is specified.
     */
    suspend fun search(query: String, filter: SearchFilter? = null): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            // Live broadcasts come from standard YouTube search; every other category comes from YouTube Music.
            kotlinx.coroutines.withTimeout(15000L.milliseconds) {
                val response = if (filter == SearchFilter.LIVE) {
                    InnerTubeClient.liveSearch(query.trim(), anonymous = true)
                } else {
                    InnerTubeClient.search(query.trim(), filter?.value, anonymous = true)
                }
                if (response.entrySet().isEmpty()) {
                    throw IllegalStateException(
                        "YouTube Music search returned an empty response; check InnerTubeClient HTTP/network logs"
                    )
                }
                val parsed = if (filter == SearchFilter.LIVE) {
                    InnerTubeParser.parseLiveSearchResponse(response)
                } else {
                    InnerTubeParser.parseSearchResponse(response)
                }
                val typedItems = when (filter) {
                    SearchFilter.VIDEOS -> parsed.items.map { item ->
                        if (item is SearchItem.Song) SearchItem.Song(item.song.copy(isVideo = true)) else item
                    }
                    SearchFilter.EPISODES -> parsed.items.map { item ->
                        if (item is SearchItem.Song) SearchItem.Song(item.song.copy(isEpisode = true)) else item
                    }
                    SearchFilter.PODCASTS -> parsed.items.map { item ->
                        if (item is SearchItem.Playlist) SearchItem.Playlist(item.playlist.copy(isPodcast = true)) else item
                    }
                    else -> parsed.items
                }
                if (typedItems.isEmpty()) {
                    Log.w(TAG, "Search returned a response but no parsed items for query: $query filter=$filter")
                }
                parsed.copy(items = typedItems)
            }
        }
    }

    /**
     * Top results: request each YouTube Music category independently, then keep
     * the result groups separate so the UI can show songs as rows and browseable collections
     * as horizontal shelves. The unfiltered response supplies an extra best-match candidate.
     */
    suspend fun searchTop(query: String): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(30000L.milliseconds) {
                coroutineScope {
                    val normalizedQuery = query.trim()
                    require(normalizedQuery.isNotEmpty()) { "Enter a search query." }

                    // Use independent searches for each result kind, plus
                    // YouTube Music's own unfiltered Top result card. Do not let a mixed,
                    // trend-heavy shelf decide what the query means.
                    val cardRequest = async(Dispatchers.IO) {
                        runCatching {
                            InnerTubeClient.search(normalizedQuery, anonymous = true)
                                .let(InnerTubeParser::parseTopSearchCard)
                        }.getOrNull()
                    }
                    val requests = SearchFilter.values().map { filter ->
                        filter to async(Dispatchers.IO) {
                            search(normalizedQuery, filter).getOrNull()?.items.orEmpty()
                        }
                    }

                    val results = requests.map { (filter, deferred) -> filter to deferred.await() }
                    val card = cardRequest.await()
                    if (results.all { it.second.isEmpty() } && card == null) {
                        throw IllegalStateException("No results were returned for this search.")
                    }

                    val ranked = SearchRank.rank(normalizedQuery, results, card)
                    ranked
                }
            }
        }
    }

    private fun searchItemKey(item: SearchItem): String = when (item) {
        is SearchItem.Song -> "song:" + item.song.videoId
        is SearchItem.Album -> "album:" + item.album.browseId
        is SearchItem.Artist -> "artist:" + item.artist.browseId
        is SearchItem.Playlist -> "playlist:" + item.playlist.playlistId
    }

    private fun normalizeSearchText(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /**
     * Load more search results using continuation token.
     */
    suspend fun searchContinuation(
        continuation: String,
        filter: SearchFilter? = null
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(9000L.milliseconds) {
                if (filter == SearchFilter.LIVE) {
                    InnerTubeParser.parseLiveSearchResponse(InnerTubeClient.liveSearch("", continuation))
                } else {
                    val response = InnerTubeClient.search("", filter?.value, continuation)
                    InnerTubeParser.parseSearchResponse(response)
                }
            }
        }
    }

    /**
     * Fetch both text completions and the songs, albums, artists or playlists YouTube attaches
     * to its suggestions response. Those entities are rendered as image rows while typing.
     */
    suspend fun searchSuggestionsWithItems(query: String): Result<SearchSuggestions> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(5000L.milliseconds) {
                InnerTubeParser.parseSearchSuggestions(InnerTubeClient.searchSuggestions(query))
            }
        }
    }

    /**
     * Get search suggestions as user types.
     */
    suspend fun searchSuggestions(query: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(2500L.milliseconds) {
                val response = InnerTubeClient.searchSuggestions(query)
                val suggestions = mutableListOf<String>()

                val contents = response.path("contents")?.asJsonArray
                contents?.forEach { section ->
                    val renderer = section.asJsonObject
                        .getAsJsonObject("searchSuggestionsSectionRenderer")
                    val sectionContents = renderer?.getAsJsonArray("contents")
                    sectionContents?.forEach { item ->
                        val suggestion = item.asJsonObject
                            .getAsJsonObject("searchSuggestionRenderer")
                            ?.path("suggestion.runs")?.asJsonArray
                            ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
                        suggestion?.let { suggestions.add(it) }
                    }
                }
                suggestions
            }
        }
    }

    // ========== Player & Streaming ==========

    suspend fun player(
        videoId: String?,
        metadataOnly: Boolean = false
    ): Result<PlayerResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (videoId.isNullOrEmpty()) throw IllegalArgumentException("Invalid videoId")

            cleanExpiredCache()

            coroutineScope {
                val newPipeDeferred = async(Dispatchers.IO) {
                    try {
                        NewPipeStreamExtractor.getPlayerResult(videoId, metadataOnly = metadataOnly)
                    } catch (e: Exception) {
                        Log.e("YouTubeTelemetry", "NewPipe stream extraction failed for $videoId: ${e.message}", e)
                        null
                    }
                }

                val trackingDeferred = async(Dispatchers.IO) {
                    var pbUrl: String? = null
                    var wtUrl: String? = null
                    if (!InnerTubeClient.cookie.isNullOrBlank()) {
                        try {
                            val playerJson = InnerTubeClient.playerWebRemix(videoId)
                            Log.d("YouTubeTelemetry", "playerWebRemix keys: ${playerJson.keySet()}")
                            if (playerJson.has("playabilityStatus")) {
                                Log.d("YouTubeTelemetry", "playabilityStatus: ${playerJson.getAsJsonObject("playabilityStatus")}")
                            }
                            val tracking = playerJson.getAsJsonObject("playbackTracking")
                            pbUrl = tracking?.getAsJsonObject("videostatsPlaybackUrl")?.get("baseUrl")?.asString
                            wtUrl = tracking?.getAsJsonObject("videostatsWatchtimeUrl")?.get("baseUrl")?.asString
                            Log.d("YouTubeTelemetry", "Fetched watch history URLs from WebRemix player: playbackUrl=$pbUrl, watchtimeUrl=$wtUrl")
                        } catch (e: Exception) {
                            Log.e("YouTubeTelemetry", "Failed to fetch watch history URLs from WebRemix player: ${e.message}", e)
                        }
                    }
                    Pair(pbUrl, wtUrl)
                }

                val newPipeResult = newPipeDeferred.await()
                val (playbackUrl, watchtimeUrl) = trackingDeferred.await()

                newPipeResult?.copy(
                    playbackUrl = playbackUrl,
                    watchtimeUrl = watchtimeUrl
                ) ?: PlayerResult(
                    videoId = videoId,
                    title = "Unknown",
                    artist = "Unknown Artist",
                    thumbnailUrl = null,
                    durationMs = 0,
                    streams = emptyList(),
                    likesCount = null,
                    playbackUrl = playbackUrl,
                    watchtimeUrl = watchtimeUrl
                )
            }
        }
    }

    /**
     * Atomically acquires the best audio stream URL for a given video ID.
     * Implements absolute deduped parallelism prevention and automatic caching.
     */
    suspend fun getStreamUrl(
        videoId: String?,
        promptForFormatChoice: Boolean = false
    ): Result<String> = coroutineScope {
        if (videoId.isNullOrEmpty()) return@coroutineScope Result.failure(IllegalArgumentException("Video ID null"))

        cleanExpiredCache()
        val requestGeneration = streamSelectionGeneration.get()

        // A cached URL is valid only for the current quality-preference generation.
        val explicitFormat = NewPipeStreamExtractor.getPlaybackAudioFormat() != "auto"
        val canUseCachedUrl = !promptForFormatChoice || !explicitFormat
        val cached = if (canUseCachedUrl) streamUrlCache[videoId] else null
        if (cached != null && cached.second > System.currentTimeMillis() &&
            streamSelectionGeneration.get() == requestGeneration
        ) {
            return@coroutineScope Result.success(cached.first)
        }

        // Separate interactive and background requests so prefetch cannot consume a foreground
        // format-choice request before its dialog has a chance to appear.
        val id = videoId
        val requestKey = "$id#format-choice=$promptForFormatChoice"
        val activeTask = synchronized(activeStreamRequests) {
            val currentRequest = activeStreamRequests[requestKey]
            if (currentRequest != null && currentRequest.generation == requestGeneration) {
                currentRequest.deferred
            } else {
                val newDeferred = appScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                var extractionFailure: Throwable? = null
                try {
                    var bestUrl: String? = try {
                        NewPipeStreamExtractor.getStreamUrl(id, allowFormatChoice = promptForFormatChoice)
                    } catch (extractorEx: Exception) {
                        extractionFailure = extractorEx
                        if (extractorEx is AudioFormatSelectionCancelledException) throw extractorEx
                        // A missing Opus/M4A NewPipe result should still try the native InnerTube
                        // client, which may expose the M4A fallback even when NewPipe does not.
                        if (extractorEx is RequestedAudioFormatUnavailableException) {
                            Log.w(TAG, "NewPipe has no Opus/M4A playback stream for $id; checking InnerTube")
                        } else {
                            Log.e(TAG, "NewPipe extraction failed for $id; trying InnerTube fallback", extractorEx)
                        }
                        null
                    }

                    if (bestUrl.isNullOrEmpty()) {
                        Log.d(TAG, "NewPipe extractor returned empty for $id. Falling back to native InnerTube player client...")
                        try {
                            val playerJson = InnerTubeClient.player(id)
                            val parsed = InnerTubeParser.parsePlayerResponse(playerJson)
                            bestUrl = chooseInnerTubeStream(parsed)
                            if (!bestUrl.isNullOrEmpty()) {
                                Log.d(TAG, "Successfully resolved fallback stream via InnerTube player for $id")
                            }
                        } catch (fallbackEx: Exception) {
                            extractionFailure = fallbackEx
                            if (fallbackEx is AudioFormatSelectionCancelledException ||
                                fallbackEx is RequestedAudioFormatUnavailableException
                            ) throw fallbackEx
                            Log.e(TAG, "InnerTube player fallback failed for $id: ${fallbackEx.message}")
                        }
                    }

                    if (!bestUrl.isNullOrEmpty()) {
                        // Commit successfully resolved URL to memory cache
                        if (streamSelectionGeneration.get() == requestGeneration) {
                            streamUrlCache[id] = bestUrl to (System.currentTimeMillis() + CACHE_TTL)
                        }
                        Result.success(bestUrl)
                    } else {
                        Result.failure(
                            extractionFailure
                                ?: IllegalStateException("Stream extraction total exhaustion")
                        )
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    // Remove only this request generation; retain newer work for the same song.
                    synchronized(activeStreamRequests) {
                        val active = activeStreamRequests[requestKey]
                        if (active != null && active.generation == requestGeneration) {
                            activeStreamRequests.remove(requestKey, active)
                        }
                    }
                }
                }
                val request = ActiveStreamRequest(requestGeneration, newDeferred)
                activeStreamRequests[requestKey] = request
                newDeferred.start()
                newDeferred
            }
        }

        return@coroutineScope activeTask.await()
    }


    private fun chooseInnerTubeStream(parsed: PlayerResult?): String? {
        val audio = parsed?.streams.orEmpty()
            .filter { it.mimeType.contains("audio", ignoreCase = true) && it.url.isNotBlank() }
        if (audio.isEmpty()) return null

        val requestedFormat = NewPipeStreamExtractor.getPlaybackAudioFormat()
        val targetKbps = NewPipeStreamExtractor.getTargetBitrateKbps()
        val matching = audio.filter {
            NewPipeStreamExtractor.matchesAudioFormat(
                it.mimeType,
                preferredFormat = requestedFormat
            )
        }
        val candidates = when {
            requestedFormat == "auto" -> audio.filter {
                it.mimeType.contains("audio/mp4", ignoreCase = true)
            }.ifEmpty { audio }
            matching.isNotEmpty() -> matching
            requestedFormat == "opus" -> audio.filter {
                NewPipeStreamExtractor.matchesAudioFormat(it.mimeType, preferredFormat = "m4a")
            }
            else -> emptyList()
        }

        // There is no interactive playback-format selector: Opus is preferred and M4A is
        // the only fallback. If neither is available, let stream resolution fail cleanly.
        if (candidates.isEmpty()) return null
        return candidates.minByOrNull { stream ->
            val kbps = if (stream.bitrate > 1000) stream.bitrate / 1000 else stream.bitrate
            if (kbps > 0) kotlin.math.abs(kbps - targetKbps) else Int.MAX_VALUE
        }?.url
    }

    /**
     * Resolve an alternate audio URL through InnerTube directly, bypassing NewPipe and both
     * URL caches. This is deliberately reserved for recovery after Media3 rejected an extracted
     * URL; repeating the same resolver can otherwise return another URL of the same failing type.
     */
    suspend fun getStreamUrlFromInnerTube(videoId: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = videoId?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Video ID is blank")
                val playerJson = InnerTubeClient.player(id)
                val parsed = InnerTubeParser.parsePlayerResponse(playerJson)
                val streams = parsed?.streams.orEmpty()
                    .filter { it.mimeType.contains("audio", ignoreCase = true) && !it.url.isNullOrBlank() }
                // The parser selects by configured format and bitrate, with a native-source
                // fallback when the preferred format is unavailable.
                streams.firstOrNull()
                    ?.url
                    ?.takeIf { it.startsWith("http", ignoreCase = true) }
                    ?: throw IllegalStateException("InnerTube returned no usable audio stream for $id")
            }
        }

    /**
     * Resolve a playback URL using YouTube Music's WEB_REMIX player client.
     * This is a separate recovery route, used when Media3 rejects the initial stream.
     */
    suspend fun getStreamUrlFromWebRemix(videoId: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = videoId?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Video ID is blank")
                val response = InnerTubeClient.playerWebRemix(id)
                val parsed = InnerTubeParser.parsePlayerResponse(response)
                val url = parsed?.streams.orEmpty()
                    .firstOrNull { it.mimeType.contains("audio", ignoreCase = true) }
                    ?.url
                    ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                url ?: throw IllegalStateException("WEB_REMIX returned no usable audio stream for $id")
            }
        }

    /** Resolve a decoder-compatible NewPipe audio stream for the final recovery attempt. */
    suspend fun getCompatibleFallbackStreamUrl(videoId: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = videoId?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Video ID is blank")
                NewPipeStreamExtractor.getFallbackAudioStreamUrl(id)
                    ?: throw IllegalStateException("No compatible fallback audio stream for $id")
            }
        }

    /**
     * Invalidate a possibly expired/broken stream URL so the next request re-runs extraction.
     */
    fun invalidateStreamUrl(videoId: String?) {
        if (videoId.isNullOrBlank()) return
        streamUrlCache.remove(videoId)
        NewPipeStreamExtractor.clearVideoCache(videoId)
    }

    /**
     * Invalidate all quality-dependent URLs and extracted formats after a bitrate preference change.
     */
    fun invalidateAllStreamUrls() {
        streamSelectionGeneration.incrementAndGet()
        streamUrlCache.clear()
        synchronized(activeStreamRequests) {
            activeStreamRequests.clear()
        }
        NewPipeStreamExtractor.clearCache()
    }

    /**
     * Extracts YouTube's native 'loudnessDb' parameter for a given video ID.
     * This metric indicates how much the track deviates from YouTube's target of -14 LUFS.
     */
    suspend fun getLoudnessDb(videoId: String): Result<Float> = withContext(Dispatchers.IO) {
        runCatching {
            android.util.Log.d("YouTubeMusic", "Fetching loudnessDb for videoId: $videoId via playerWebRemix")
            // Using playerWebRemix (Client 67) instead of player (Android/iOS) to bypass HTTP 400 blocks
            val playerJson = InnerTubeClient.playerWebRemix(videoId)
            val playerConfig = playerJson.getAsJsonObject("playerConfig")
            val audioConfig = playerConfig?.getAsJsonObject("audioConfig")
            val loudnessDb = audioConfig?.get("loudnessDb")?.asFloat
            
            if (loudnessDb == null) {
                android.util.Log.w("YouTubeMusic", "Loudness DB not found in playerWebRemix response for $videoId")
                throw Exception("Loudness DB not found in player response")
            } else {
                android.util.Log.d("YouTubeMusic", "Successfully fetched loudnessDb: $loudnessDb for $videoId")
                loudnessDb
            }
        }.onFailure { e ->
            android.util.Log.e("YouTubeMusic", "Failed to fetch loudnessDb for $videoId: ${e.message}", e)
        }
    }

    // ========== Home Feed ==========

    /**
     * Get the YouTube Music home page with personalized recommendations.
     */
    suspend fun home(): Result<HomePage> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(25000L.milliseconds) {
                val response = InnerTubeClient.browse(browseId = "FEmusic_home")
                InnerTubeParser.parseHomePage(response)
            }
        }
    }

    /**
     * Load more home page sections.
     */
    suspend fun homeContinuation(continuation: String): Result<HomePage> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(25000L.milliseconds) {
                val response = InnerTubeClient.browse(continuation = continuation)
                InnerTubeParser.parseHomePage(response)
            }
        }
    }

    /**
     * Get the Explore/Charts page.
     */
    suspend fun explore(): Result<HomePage> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(25000L.milliseconds) {
                val response = InnerTubeClient.browse(browseId = "FEmusic_explore")
                InnerTubeParser.parseHomePage(response)
            }
        }
    }

    /**
     * Load the dedicated YouTube Music moods-and-genres catalogue used by Search.
     * This browse surface is separate from Home's chips.
     */
    suspend fun moodsAndGenres(): Result<List<HomeCategory>> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(25000L.milliseconds) {
                val response = InnerTubeClient.browse(browseId = "FEmusic_moods_and_genres")
                val categories = InnerTubeParser.parseMoodsAndGenres(response)
                if (categories.isEmpty()) {
                    throw java.io.IOException("YouTube Music returned no moods or genres.")
                }
                categories
            }
        }
    }

    // ========== Albums ==========

    /**
     * Get an album's details and songs.
     */
    suspend fun album(browseId: String): Result<AlbumPage> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = browseId)
            InnerTubeParser.parseAlbumPage(response, browseId)
                ?: throw Exception("Failed to parse album $browseId")
        }
    }

    // ========== Artists ==========

    /**
     * Get an artist's page with songs, albums, etc.
     */
    suspend fun artist(browseId: String): Result<ArtistPage> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = browseId)

            // Parse artist header
            val header = response.path("header.musicImmersiveHeaderRenderer")?.asJsonObject
                ?: response.path("header.musicVisualHeaderRenderer")?.asJsonObject
                ?: response.path("header.musicHeaderRenderer")?.asJsonObject

            val title = header?.path("title.runs.0.text")?.asString ?: "Unknown Artist"
            val thumbnail = header?.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                ?: header?.path("foregroundThumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                ?: header?.path("avatar.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                ?: response.path("header.musicHeaderRenderer.thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                ?: response.path("header.musicHeaderRenderer.avatar.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
            val finalThumbnail = InnerTubeParser.getHighResThumbnailUrl(thumbnail)
            val description = header?.path("description.runs.0.text")?.asString

            val subCount = header?.path("subtitle.runs")?.asJsonArray
                ?.joinToString("") { run ->
                    run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                }?.takeIf { it.isNotBlank() }
                ?: header?.path("subscriptionButton.subscribeButtonRenderer.subscriberCountWithConfirm.runs.0.text")?.asString
                ?: header?.path("subscriptionButton.subscribeButtonRenderer.subscriberCountText.runs.0.text")?.asString

            val additionalInfo = header?.path("additionalInfo.runs")?.asJsonArray
                ?.joinToString("") { run ->
                    run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                }?.takeIf { it.isNotBlank() }

            var combinedSubCount = subCount ?: ""
            if (!additionalInfo.isNullOrEmpty()) {
                if (combinedSubCount.isNotEmpty() && !combinedSubCount.contains(additionalInfo)) {
                    combinedSubCount = "$combinedSubCount • $additionalInfo"
                } else if (combinedSubCount.isEmpty()) {
                    combinedSubCount = additionalInfo
                }
            }

            // Regex extraction from description as a solid fallback
            if (description != null && !combinedSubCount.lowercase().contains("monthly")) {
                val regex = Regex("([0-9a-zA-Z.,]+)\\s*(monthly listeners|monthly audience|listeners|audience)", RegexOption.IGNORE_CASE)
                val match = regex.find(description)
                if (match != null) {
                    val count = match.groupValues[1]
                    val label = match.groupValues[2]
                    val suffix = if (label.lowercase().contains("listener")) "Monthly Listeners" else "Monthly Audience"
                    combinedSubCount = if (combinedSubCount.isNotEmpty()) {
                        "$combinedSubCount • $count $suffix"
                    } else {
                        "$count $suffix"
                    }
                }
            }

            // Extract subscription status
            val isSubscribed = header?.path("subscriptionButton.subscribeButtonRenderer.subscribed")?.asBoolean ?: false

            // Parse content sections (reuse home section parser)
            val homePage = InnerTubeParser.parseHomePage(response)

            ArtistPage(
                artist = OnlineArtist(
                    browseId = browseId,
                    title = title,
                    thumbnailUrl = finalThumbnail,
                    subscriberCount = combinedSubCount.takeIf { it.isNotBlank() },
                    isSubscribed = isSubscribed
                ),
                sections = homePage.sections,
                description = description
            )
        }
    }

    // ========== Playlists ==========

    /**
     * Get a playlist's details and songs.
     */
    suspend fun playlist(playlistId: String): Result<PlaylistPage> = withContext(Dispatchers.IO) {
        runCatching {
            val isPodcast = playlistId.startsWith("MPSP")
            val browseId = if (isPodcast) playlistId else "VL$playlistId"
            val response = InnerTubeClient.browse(browseId = browseId)

            val editableHeader = InnerTubeParser.findFirstObject(response, "musicEditablePlaylistDetailHeaderRenderer")
            val editHeader = editableHeader?.let {
                InnerTubeParser.findFirstObject(it, "musicPlaylistEditHeaderRenderer")
            }
            val header = InnerTubeParser.findFirstObject(response, "musicResponsiveHeaderRenderer")
                ?: response.path(
                    "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicEditablePlaylistDetailHeaderRenderer.header.musicResponsiveHeaderRenderer"
                )?.asJsonObject
                ?: response.path(
                    "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicEditablePlaylistDetailHeaderRenderer.header.musicResponsiveHeaderRenderer"
                )?.asJsonObject

            val title = header?.path("title.runs.0.text")?.asString ?: "Playlist"
            val authorRun = header?.path("straplineTextOne.runs.0")?.asJsonObject
            val author = authorRun?.let {
                OnlineArtistRef(
                    name = it.get("text")?.asString ?: "",
                    id = it.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                )
            }
            val thumbnail = InnerTubeParser.getHighResThumbnailUrl(
                header?.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
            )
            val songCount = header?.path("secondSubtitle.runs.0.text")?.asString

            // Parse songs
            var songContents = response.path(
                "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
            )?.asJsonArray
            if (songContents == null) {
                songContents = response.path(
                    "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
                )?.asJsonArray
            }

            val songs = mutableListOf<OnlineSong>()
            if (isPodcast) {
                songs.addAll(InnerTubeParser.parsePodcastEpisodes(response))
                if (songs.isEmpty()) {
                    songs.addAll(
                        InnerTubeParser.parseSearchResponse(response).items
                            .filterIsInstance<SearchItem.Song>()
                            .map { it.song }
                            .filter { it.isEpisode }
                    )
                }
            } else songContents?.forEach { item ->
                val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val renderer = obj.getAsJsonObject("musicResponsiveListItemRenderer") ?: return@forEach
                val flexColumns = renderer.getAsJsonArray("flexColumns")
                val songTitle = flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return@forEach
                val videoId = renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint.videoId")?.asString
                    ?: renderer.path("navigationEndpoint.watchEndpoint.videoId")?.asString
                    ?: flexColumns.getOrNull(0)?.asJsonObject
                        ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.watchEndpoint.videoId")?.asString
                    ?: return@forEach
                val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
                val artistText = subtitleRuns?.joinToString("") { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                } ?: ""
                val fixedColumns = renderer.getAsJsonArray("fixedColumns")
                val duration = fixedColumns?.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFixedColumnRenderer.text.runs.0.text")?.asString
                val songThumbnail = InnerTubeParser.getHighResThumbnailUrl(
                    renderer.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                )

                songs.add(OnlineSong(
                    videoId = videoId,
                    title = songTitle,
                    artist = artistText.trim(),
                    duration = duration,
                    durationMs = parseDuration(duration),
                    thumbnailUrl = songThumbnail
                ))
            }

            var currentContinuation = response.path(
                "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.continuations.0.nextContinuationData.continuation"
            )?.asString
            if (currentContinuation == null) {
                currentContinuation = response.path(
                    "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.continuations.0.nextContinuationData.continuation"
                )?.asString
            }
            if (currentContinuation == null && isPodcast) {
                currentContinuation = InnerTubeParser.findFirstObject(response, "musicShelfRenderer")
                    ?.path("continuations.0.nextContinuationData.continuation")?.asString
            }

            val descriptionText = header?.path("description.runs")?.asJsonArray
                ?.joinToString("") { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString.orEmpty()
                }?.takeIf { it.isNotBlank() }
                ?: header?.path("description.simpleText")?.asString
                ?: header?.path("description.text")?.asString
            val editable = editableHeader != null && !InnerTubeClient.cookie.isNullOrBlank()
            val privacy = editHeader?.get("privacy")?.asString
                ?: editableHeader?.path("privacy")?.asString

            PlaylistPage(
                playlist = OnlinePlaylist(
                    playlistId = playlistId.removePrefix("VL"),
                    title = title,
                    author = author,
                    songCount = songCount ?: "${songs.size} songs",
                    thumbnailUrl = thumbnail,
                    isPodcast = isPodcast
                ),
                songs = songs.distinctBy { it.videoId },
                continuation = currentContinuation,
                description = descriptionText,
                isOwned = editable,
                privacy = privacy
            )
        }
    }

    /**
     * Fetch the next page of a playlist's tracks.
     */
    suspend fun playlistContinuation(
        continuation: String
    ): Result<Pair<List<OnlineSong>, String?>> = withContext(Dispatchers.IO) {
        runCatching {
            require(continuation.isNotBlank()) { "Missing playlist continuation token" }
            val response = InnerTubeClient.browse(continuation = continuation)
            InnerTubeParser.parsePlaylistContinuationSongs(response)
        }
    }

    // ========== Lyrics ==========

    /**
     * Fetch lyrics for a song by first getting the lyrics browseId from the next endpoint,
     * then fetching the actual lyrics via browse.
     */
    suspend fun lyrics(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val nextResponse = InnerTubeClient.next(videoId)
            val tabs = nextResponse.path("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs")?.asJsonArray

            // Lyrics tab is typically the second tab (index 1)
            var lyricsBrowseId: String? = null
            tabs?.forEach { tab ->
                val tabRenderer = tab.asJsonObject?.getAsJsonObject("tabRenderer")
                val endpoint = tabRenderer?.path("endpoint.browseEndpoint")?.asJsonObject
                val browseId = endpoint?.get("browseId")?.asString
                if (browseId != null && browseId.startsWith("MPLYt")) {
                    lyricsBrowseId = browseId
                }
            }

            if (lyricsBrowseId == null) throw IllegalStateException("No lyrics available for $videoId")

            val lyricsResponse = InnerTubeClient.browse(lyricsBrowseId)
            InnerTubeParser.parseLyrics(lyricsResponse)
        }
    }

    // ========== Start Radio ==========

    /**
     * Start a radio (mix) based on a song, returning the full auto-generated playlist.
     */
    suspend fun startRadio(videoId: String): Result<List<OnlineSong>> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = "RDAMVM$videoId"
            val response = InnerTubeClient.next(videoId, playlistId = playlistId)
            val songs = mutableListOf<OnlineSong>()

            val playlistContents = response.path("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.0.tabRenderer.content.musicQueueRenderer.content.playlistPanelRenderer.contents")?.asJsonArray

            playlistContents?.forEach { content ->
                val renderer = content.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer") ?: return@forEach
                val id = renderer.get("videoId")?.asString ?: return@forEach
                val titleText = renderer.path("title.runs.0.text")?.asString ?: return@forEach
                val shortByline = renderer.path("shortBylineText.runs")?.asJsonArray
                    ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
                val lengthText = renderer.path("lengthText.runs.0.text")?.asString
                val thumb = InnerTubeParser.getHighResThumbnailUrl(
                    renderer.path("thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                )

                songs.add(OnlineSong(
                    videoId = id,
                    title = titleText,
                    artist = shortByline.trim(),
                    duration = lengthText,
                    durationMs = parseDuration(lengthText),
                    thumbnailUrl = thumb
                ))
            }
            songs
        }
    }

    // ========== Playlist Management ==========

    /**
     * Create a new playlist on the user's YouTube Music account.
     */
    suspend fun createPlaylist(
        title: String,
        description: String = "",
        privacyStatus: String = "PRIVATE",
        videoIds: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.createPlaylist(title, description, privacyStatus, videoIds)
            response.get("playlistId")?.asString ?: throw IllegalStateException("Failed to create playlist")
        }
    }

    /**
     * Edit title, description and visibility of a playlist owned by the connected account.
     */
    suspend fun editPlaylist(
        playlistId: String,
        title: String? = null,
        description: String? = null,
        privacyStatus: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(!InnerTubeClient.cookie.isNullOrBlank()) { "Connect your YouTube Music account first." }
            InnerTubeClient.editPlaylist(playlistId, title, description, privacyStatus)
            Unit
        }
    }

    /**
     * Delete a playlist from the user's YouTube Music library.
     */
    suspend fun deletePlaylist(playlistId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            require(playlistId.isNotBlank()) { "Playlist ID is missing." }
            check(!InnerTubeClient.cookie.isNullOrBlank()) {
                "Connect your YouTube Music account first."
            }
            val response = InnerTubeClient.deletePlaylist(playlistId)
            response.getAsJsonObject("error")?.let { error ->
                throw IllegalStateException(
                    error.get("message")?.asString ?: "YouTube Music could not delete this playlist."
                )
            }
            if (response.entrySet().isEmpty()) {
                throw IllegalStateException("YouTube Music returned an empty delete response.")
            }
            true
        }
    }

    /**
     * Add songs to an existing YouTube Music playlist.
     */
    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.addToPlaylist(playlistId, videoIds)
            response.has("status") || response.has("playlistEditResults") || response.has("responseContext")
        }
    }

    // ========== Subscriptions ==========

    suspend fun subscribeArtist(channelId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.subscribe(channelId)
            if (response.has("error")) {
                val errorMsg = response.getAsJsonObject("error")?.get("message")?.asString ?: "Subscription failed"
                throw Exception(errorMsg)
            }
            if (response.entrySet().isEmpty()) {
                throw Exception("Empty response from subscribe API")
            }
            true
        }
    }

    /**
     * Unsubscribe from an artist on YouTube Music.
     */
    suspend fun unsubscribeArtist(channelId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.unsubscribe(channelId)
            if (response.has("error")) {
                val errorMsg = response.getAsJsonObject("error")?.get("message")?.asString ?: "Unsubscription failed"
                throw Exception(errorMsg)
            }
            if (response.entrySet().isEmpty()) {
                throw Exception("Empty response from unsubscribe API")
            }
            true
        }
    }

    // ========== Related / Next ==========

    /**
     * Get related songs for a given video.
     */
    suspend fun relatedSongs(videoId: String): Result<List<OnlineSong>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.next(videoId)
            val songs = mutableListOf<OnlineSong>()

            val tabs = response.path("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs")?.asJsonArray
            val upNextTab = tabs?.getOrNull(0)?.asJsonObject
            val playlistContents = upNextTab?.path("tabRenderer.content.musicQueueRenderer.content.playlistPanelRenderer.contents")?.asJsonArray

            playlistContents?.forEach { content ->
                val renderer = content.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer") ?: return@forEach
                val id = renderer.get("videoId")?.asString ?: return@forEach
                val titleText = renderer.path("title.runs.0.text")?.asString ?: return@forEach
                val shortByline = renderer.path("shortBylineText.runs")?.asJsonArray
                    ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
                val lengthText = renderer.path("lengthText.runs.0.text")?.asString
                val thumb = InnerTubeParser.getHighResThumbnailUrl(
                    renderer.path("thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                )

                songs.add(OnlineSong(
                    videoId = id,
                    title = titleText,
                    artist = shortByline.trim(),
                    duration = lengthText,
                    durationMs = parseDuration(lengthText),
                    thumbnailUrl = thumb
                ))
            }
            songs
        }
    }

    // ========== Library & Account ==========

    /**
     * Browse a specific section using browseId and params.
     * Often returns a list of items similar to a playlist or artist page.
     * For simplicity, we can parse it as a PlaylistPage if it contains items.
     */
    suspend fun section(browseId: String, params: String?): Result<HomeSection> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = browseId, params = params)
            // A section often has the same structure as a playlist (a list of items)
            InnerTubeParser.parseSectionPage(response) ?: throw Exception("Failed to parse section")
        }
    }

    /**
     * Fetch the current user's profile details (name, avatar)
     */
    suspend fun accountProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.accountMenu()
            InnerTubeParser.parseAccountProfile(response)
                ?: throw Exception("Failed to parse user profile")
        }
    }

    /**
     * Fetch user's library playlists
     */
    suspend fun libraryPlaylists(): Result<List<OnlinePlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "FEmusic_liked_playlists")
            val list = InnerTubeParser.parseLibraryPlaylists(response).toMutableList()
            var continuation = InnerTubeParser.extractLibraryContinuation(response)
            var iterations = 0
            while (continuation != null && iterations < 25) {
                iterations++
                try {
                    val nextResponse = InnerTubeClient.browse(continuation = continuation)
                    val (items, nextToken) = InnerTubeParser.extractContinuationItemsAndNextToken(nextResponse)
                    if (items != null && items.size() > 0) {
                        list.addAll(InnerTubeParser.parseLibraryPlaylistsList(items))
                    }
                    continuation = nextToken
                } catch (e: Exception) {
                    Log.e("YouTubeMusic", "Error fetching playlist continuation", e)
                    break
                }
            }
            list
        }
    }

    /**
     * Fetch user's library artists (followed/subscribed artists)
     */
    suspend fun libraryArtists(): Result<List<OnlineArtist>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "FEmusic_library_corpus_artists")
            val list = InnerTubeParser.parseLibraryArtists(response).toMutableList()
            var continuation = InnerTubeParser.extractLibraryContinuation(response)
            var iterations = 0
            while (continuation != null && iterations < 25) {
                iterations++
                try {
                    val nextResponse = InnerTubeClient.browse(continuation = continuation)
                    val (items, nextToken) = InnerTubeParser.extractContinuationItemsAndNextToken(nextResponse)
                    if (items != null && items.size() > 0) {
                        list.addAll(InnerTubeParser.parseLibraryArtistsList(items))
                    }
                    continuation = nextToken
                } catch (e: Exception) {
                    Log.e("YouTubeMusic", "Error fetching artist continuation", e)
                    break
                }
            }
            list
        }
    }

    suspend fun libraryAlbums(): Result<List<OnlineAlbum>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "FEmusic_library_corpus_albums")
            val list = InnerTubeParser.parseLibraryAlbums(response).toMutableList()
            var continuation = InnerTubeParser.extractLibraryContinuation(response)
            var iterations = 0
            while (continuation != null && iterations < 25) {
                iterations++
                try {
                    val nextResponse = InnerTubeClient.browse(continuation = continuation)
                    val (items, nextToken) = InnerTubeParser.extractContinuationItemsAndNextToken(nextResponse)
                    if (items != null && items.size() > 0) {
                        list.addAll(InnerTubeParser.parseLibraryAlbumsList(items))
                    }
                    continuation = nextToken
                } catch (e: Exception) {
                    Log.e("YouTubeMusic", "Error fetching album continuation", e)
                    break
                }
            }
            list
        }
    }

    /**
     * Fetch user's library podcasts (subscribed podcasts)
     */
    suspend fun libraryPodcasts(): Result<List<OnlinePlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "FEmusic_library_corpus_podcasts")
            val list = InnerTubeParser.parseLibraryPlaylists(response).toMutableList()
            var continuation = InnerTubeParser.extractLibraryContinuation(response)
            var iterations = 0
            while (continuation != null && iterations < 25) {
                iterations++
                try {
                    val nextResponse = InnerTubeClient.browse(continuation = continuation)
                    val (items, nextToken) = InnerTubeParser.extractContinuationItemsAndNextToken(nextResponse)
                    if (items != null && items.size() > 0) {
                        list.addAll(InnerTubeParser.parseLibraryPlaylistsList(items))
                    }
                    continuation = nextToken
                } catch (e: Exception) {
                    Log.e("YouTubeMusic", "Error fetching podcast continuation", e)
                    break
                }
            }
            list
        }
    }

    /**
     * Prefetch a stream in background to speed up playback switches
     */
    fun prefetchStream(videoId: String?) {
        if (videoId.isNullOrEmpty()) return
        com.dsann.music.data.innertube.NewPipeStreamExtractor.prefetchBatch(
            listOf(videoId), 
            com.dsann.music.data.innertube.NewPipeStreamExtractor.PrefetchPriority.LOW
        )
    }

    /**
     * High level convenience to kick off pre-caching for upcoming track
     */
    fun preCacheNextSong(song: com.dsann.music.model.SongItem) {
        prefetchStream(song.videoId)
    }

    // ========== Auth / Account ==========

    /**
     * Fetch the authenticated user's YouTube Music listening history.
     */
    suspend fun history(): Result<List<OnlineSong>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse("FEmusic_history")
            InnerTubeParser.parseHistory(response)
        }
    }

    /**
     * Set authentication cookie for logged-in features.
     */
    fun setCookie(cookie: String?) {
        InnerTubeClient.cookie = cookie
    }

    // ========== Utility ==========

    private fun parseDuration(duration: String?): Long {
        if (duration == null) return 0
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                else -> 0
            }
        } catch (_: Exception) { 0 }
    }

    @JvmStatic
    fun fetchAndAppendRelatedSongs(viewModel: com.dsann.music.viewmodel.PlayerSharedViewModel, videoId: String) {
        if (!viewModel.isAutoplayEnabled()) return
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = relatedSongs(videoId)
                result.onSuccess { related ->
                    related.forEach { onlineSong ->
                        val songItem = com.dsann.music.model.SongItem.createOnlineSong(
                            onlineSong.videoId,
                            onlineSong.title,
                            onlineSong.artist,
                            "", // streamUrl placeholder
                            onlineSong.durationMs,
                            onlineSong.thumbnailUrl,
                            onlineSong.artistId
                        ).apply {
                            isAutoplayRecommendation = true
                        }
                        viewModel.addToQueue(songItem)
                    }
                    Log.d(TAG, "Successfully auto-appended ${related.size} related songs to queue.")
                    viewModel.playNextSong(true)
                }
                result.onFailure { e ->
                    Log.e(TAG, "Failed to fetch related songs for auto-queue", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching related songs", e)
            }
        }
    }

    enum class LikeStatus {
        LIKE, DISLIKE, INDIFFERENT
    }

    /**
     * Update the like status of a song on YouTube Music.
     */
    suspend fun updateLikeStatus(videoId: String, status: LikeStatus): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = when (status) {
                LikeStatus.LIKE -> InnerTubeClient.like(videoId)
                LikeStatus.DISLIKE -> InnerTubeClient.dislike(videoId)
                LikeStatus.INDIFFERENT -> InnerTubeClient.removeLike(videoId)
            }
            response.has("actions") || response.has("responseContext")
        }
    }

    /**
     * Fetch the user's authentic last watched position for a track to sync cross-device resumes.
     */
    suspend fun getPlaybackPosition(videoId: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.playerWebRemix(videoId)
            val startSeconds = response.path("playerConfig.playbackStartConfig.startSeconds")?.asLong ?: 0L
            startSeconds * 1000L
        }
    }
}
