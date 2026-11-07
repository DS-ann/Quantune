package com.dsann.music.data.lyrics.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dsann.music.data.lyrics.ConfidenceScorer
import com.dsann.music.data.lyrics.LrcLibApi
import com.dsann.music.data.lyrics.LyricsNormalizer
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.MetadataRepair
import com.dsann.music.data.lyrics.TrackMetadata
import com.dsann.music.data.lyrics.providers.EmbeddedLyricsProvider
import com.dsann.music.data.lyrics.providers.BetterLyricsAppleProvider
import com.dsann.music.data.lyrics.providers.QqMusicLyricsProvider
import com.dsann.music.data.lyrics.providers.NetEaseLyricsProvider
import com.dsann.music.data.lyrics.providers.KuGouLyricsProvider
import com.dsann.music.data.lyrics.providers.LyricsPlusProvider
import com.dsann.music.data.lyrics.providers.BiniLyricsProvider
import com.dsann.music.data.lyrics.providers.UnisonLyricsProvider
import com.dsann.music.data.lyrics.providers.GeniusLyricsProvider
import com.dsann.music.data.lyrics.providers.LrcLibProvider
import com.dsann.music.data.lyrics.providers.LyricsProvider
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Top-level orchestrator for the lyrics fetch pipeline.
 * Coordinates: MetadataRepair → Cache → ProviderRouter → ConfidenceScorer →
 * DecisionEngine → Cache.
 * Thread-safe. All callbacks delivered on main thread.
 */
class LyricsFetchManager private constructor(context: Context) {

    private val metadataRepair: MetadataRepair = MetadataRepair()
    private val normalizer: LyricsNormalizer = LyricsNormalizer()
    private val decisionEngine: LyricsDecisionEngine
    private val cacheManager: LyricsCacheManager = LyricsCacheManager(context)
    private val providerStats: ProviderStats = ProviderStats(context)
    private val telemetry: LyricsTelemetry = LyricsTelemetry()
    private val router: ProviderRouter
    private val backgroundExecutor: ExecutorService
    private val providerFetchExecutor: ExecutorService
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    // Track current search to allow cancellation
    @Volatile
    private var currentFetch: Future<*>? = null

    @Volatile
    private var selectedProviderFetch: Future<*>? = null
    private val selectedProviderGeneration = AtomicLong(0L)

    @Volatile
    private var currentTrackKey: String? = null
    private val cancelled = AtomicBoolean(false)

    /**
     * Callback interface for lyrics results.
     * All methods called on main thread.
     */
    interface LyricsCallback {
        /** Called when lyrics are ready to display */
        fun onLyricsFound(result: LyricsResult)

        /** Called when a better version is found during background search */
        fun onLyricsUpgraded(betterResult: LyricsResult)

        /** Called when all providers exhausted with no result */
        fun onLyricsNotFound(reason: String)

        /** Called when track is detected as instrumental */
        fun onInstrumental()

        /** Called when a provider completes execution */
        fun onProviderResult(providerName: String, result: LyricsResult?) {}

        /** Optional status update during search */
        fun onSearchStatus(message: String) {}
    }

    init {
        val scorer = ConfidenceScorer()
        this.decisionEngine = LyricsDecisionEngine(scorer)
        this.backgroundExecutor = Executors.newSingleThreadExecutor { r ->
            val t = Thread(r, "LyricsFetchManager")
            t.isDaemon = true
            t
        }
        this.providerFetchExecutor = Executors.newCachedThreadPool { r ->
            val t = Thread(r, "LyricsSelectedProvider")
            t.isDaemon = true
            t
        }

        // Build providers
        val client = buildHttpClient()
        val providers = createProviders(client)
        this.router = ProviderRouter(providers, scorer, providerStats)
    }

    companion object {
        private const val TAG = "LyricsFetchManager"

        @Volatile
        private var instance: LyricsFetchManager? = null

        @JvmStatic
        fun getInstance(context: Context): LyricsFetchManager {
            return instance ?: synchronized(this) {
                instance ?: LyricsFetchManager(context.applicationContext).also { instance = it }
            }
        }

        private fun buildHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    /**
     * Fetch lyrics for a song. This is the main entry point.
     * Automatically checks cache, repairs metadata, searches providers, scores
     * results.
     */
    fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        filePath: String?,
        callback: LyricsCallback,
        videoId: String? = null
    ) {
        // Cancel any ongoing fetch
        cancelCurrent()
        cancelled.set(false)

        // Launch pipeline on background thread to prevent UI freeze during cache I/O
        currentFetch = backgroundExecutor.submit {
            if (cancelled.get()) return@submit

            // Repair metadata
            val track = metadataRepair.repair(title, artist, album, durationMs, filePath, videoId)
            currentTrackKey = track.getCacheKey()

            telemetry.logSearchStart(track)

            // Check cache first (fully safe on background thread)
            val cached = cacheManager.get(track)
            if (cached != null) {
                val decision = decisionEngine.decideFetch(cached, false)

                when (decision) {
                    LyricsDecisionEngine.FetchDecision.USE_CACHE -> {
                        telemetry.logCacheStatus("HIT", "confidence=${cached.confidence}")
                        telemetry.logResult(cached, "CACHE_HIT")
                        mainHandler.post {
                            callback.onProviderResult(cached.providerName.orEmpty(), cached)
                            callback.onLyricsFound(cached)
                        }
                        // Search all providers to populate the selector list in the UI
                        router.searchAll(track, track.detectedLanguage, cancelOnEarlyWin = false) { provider, res ->
                            mainHandler.post { callback.onProviderResult(provider, res) }
                        }
                        return@submit
                    }

                    LyricsDecisionEngine.FetchDecision.USE_CACHE_AND_SEARCH_BACKGROUND -> {
                        telemetry.logCacheStatus("HIT_UNSYNCED", "showing cached, searching for synced")
                        mainHandler.post {
                            callback.onProviderResult(cached.providerName.orEmpty(), cached)
                            callback.onLyricsFound(cached)
                        }
                        // Continue to background search for synced upgrade
                        launchBackgroundUpgrade(track, cached, callback)
                        return@submit
                    }

                    else -> {
                        // For FETCH or SKIP_NEGATIVE_CACHE, continue pipeline execution
                    }
                }
            }

            // Check negative cache
            if (cacheManager.isNegativeCacheActive(track)) {
                telemetry.logCacheStatus("NEGATIVE_ACTIVE", "skipping search")
                telemetry.logFailure("Negative cache active")
                mainHandler.post { callback.onLyricsNotFound("Recently searched, no lyrics available") }
                return@submit
            }

            telemetry.logCacheStatus("MISS", null)

            if (cancelled.get()) return@submit

            mainHandler.post { callback.onSearchStatus("Searching multiple sources…") }

            // Search all providers
            val result = router.searchAll(track, track.detectedLanguage, cancelOnEarlyWin = false) { provider, res ->
                mainHandler.post { callback.onProviderResult(provider, res) }
            }

            if (cancelled.get()) return@submit

            if (result != null && result.hasLyrics()) {
                // Decision engine evaluates the result
                when (val decision = decisionEngine.decide(result, null)) {
                    LyricsDecisionEngine.Decision.ACCEPT -> {
                        cacheManager.put(track, result)
                        telemetry.logResult(result, "ACCEPTED")
                        mainHandler.post { callback.onLyricsFound(result) }
                    }

                    LyricsDecisionEngine.Decision.SHOW_AND_CONTINUE -> {
                        cacheManager.put(track, result)
                        telemetry.logResult(result, "SHOW_AND_CONTINUE")
                        mainHandler.post { callback.onLyricsFound(result) }

                        // The initial full sweep already collected every provider result.
                        // Do not repeat the sweep; selecting a source fetches only that source.
                    }

                    LyricsDecisionEngine.Decision.MARK_INSTRUMENTAL -> {
                        cacheManager.putNegative(track)
                        telemetry.logResult(result, "INSTRUMENTAL")
                        mainHandler.post { callback.onInstrumental() }
                    }

                    LyricsDecisionEngine.Decision.REJECT -> {
                        // Try deep search with alternate queries
                        val deepResult = deepSearch(track)
                        if (deepResult != null && !cancelled.get()) {
                            cacheManager.put(track, deepResult)
                            telemetry.logResult(deepResult, "DEEP_SEARCH_ACCEPT")
                            mainHandler.post { callback.onLyricsFound(deepResult) }
                        } else if (!cancelled.get()) {
                            cacheManager.putNegative(track)
                            telemetry.logFailure("All providers returned low confidence results")
                            mainHandler.post { callback.onLyricsNotFound("No matching lyrics found") }
                        }
                    }

                    else -> {
                        cacheManager.putNegative(track)
                        telemetry.logFailure("Decision: $decision")
                        mainHandler.post { callback.onLyricsNotFound("No lyrics available") }
                    }
                }
            } else if (!cancelled.get()) {
                // Deep search as last resort
                val deepResult = deepSearch(track)
                if (deepResult != null && !cancelled.get()) {
                    cacheManager.put(track, deepResult)
                    telemetry.logResult(deepResult, "DEEP_SEARCH_ACCEPT")
                    mainHandler.post { callback.onLyricsFound(deepResult) }
                } else if (!cancelled.get()) {
                    cacheManager.putNegative(track)
                    telemetry.logFailure("No results from any provider")
                    mainHandler.post { callback.onLyricsNotFound("No lyrics available") }
                }
            }
        }
    }

    /**
     * Fetch lyrics from only the provider selected in the lyrics source selector.
     */
    fun fetchProviderLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        filePath: String?,
        providerName: String,
        callback: LyricsCallback,
        videoId: String? = null
    ) {
        val sourceName = providerName.trim()
        if (sourceName.isEmpty()) {
            mainHandler.post { callback.onLyricsNotFound("No lyrics source was selected") }
            return
        }

        val track = metadataRepair.repair(title, artist, album, durationMs, filePath, videoId)
        val requestToken = selectedProviderGeneration.incrementAndGet()
        selectedProviderFetch?.cancel(true)
        selectedProviderFetch = providerFetchExecutor.submit {
            if (requestToken != selectedProviderGeneration.get()) return@submit
            mainHandler.post {
                if (requestToken == selectedProviderGeneration.get()) {
                    callback.onSearchStatus("Fetching lyrics from $sourceName…")
                }
            }

            val result = router.searchProvider(track, sourceName, track.detectedLanguage)
            if (Thread.currentThread().isInterrupted ||
                requestToken != selectedProviderGeneration.get()
            ) return@submit

            mainHandler.post {
                if (requestToken != selectedProviderGeneration.get()) return@post
                callback.onProviderResult(result?.providerName ?: sourceName, result)
                if (result != null && result.hasLyrics()) {
                    callback.onLyricsFound(result)
                } else {
                    callback.onLyricsNotFound("No lyrics found from $sourceName")
                }
            }
        }
    }

    /** Cancel a selected-source fetch without starting a full provider sweep. */
    fun cancelProviderFetch() {
        selectedProviderGeneration.incrementAndGet()
        selectedProviderFetch?.cancel(true)
        selectedProviderFetch = null
    }

    /**
     * Retry lyrics search — clears negative cache and forces full refetch.
     */
    fun retryLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        filePath: String?,
        callback: LyricsCallback,
        videoId: String? = null
    ) {
        val track = metadataRepair.repair(title, artist, album, durationMs, filePath, videoId)
        cacheManager.evict(track) // Clear ALL cache for this track
        providerStats.resetAll() // Reset provider failure state
        Log.d(TAG, "Retry: evicted cache and reset stats for ${track.getCacheKey()}")

        // Now do a full fetch
        fetchLyrics(title, artist, album, durationMs, filePath, callback, videoId)
    }

    /**
     * Cancel the current fetch operation (e.g., when song changes).
     */
    fun cancelCurrent() {
        cancelled.set(true)
        cancelProviderFetch()
        currentFetch?.let {
            if (!it.isDone) {
                it.cancel(true)
                Log.d(TAG, "Cancelled current fetch")
            }
        }
    }

    /**
     * Deep search: retry with alternate query formulations.
     */
    private fun deepSearch(track: TrackMetadata): LyricsResult? {
        if (cancelled.get()) return null

        val queries = normalizer.generateQueries(track)
        telemetry.logQueries(queries)

        // Skip first query (already tried) and try remaining
        for (i in 1 until queries.size) {
            if (cancelled.get()) break
            val q = queries[i]
            val altTrack = TrackMetadata(
                rawTitle = q[1],
                rawArtist = q[0],
                cleanedTitle = q[1],
                cleanedArtist = q[0],
                durationMs = track.durationMs,
                filePath = track.filePath,
                version = track.version,
                detectedLanguage = track.detectedLanguage
            )

            val result = router.searchAll(altTrack, track.detectedLanguage)
            if (result != null && result.hasLyrics()) {
                val confidence = result.confidence
                if (confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
                    return result
                }
            }
        }

        return null
    }

    /**
     * Background search for better lyrics while currently showing lower-quality.
     * Handles: unsynced → synced, synced → word-by-word.
     */
    private fun launchBackgroundUpgrade(
        track: TrackMetadata,
        current: LyricsResult,
        callback: LyricsCallback
    ) {
        backgroundExecutor.submit {
            if (cancelled.get()) return@submit

            Log.d(TAG, "Background upgrade search (current: synced=${current.isSynced}, wordByWord=${current.isWordByWord})...")

            // Search all providers for a better result
            val result = router.searchAll(track, track.detectedLanguage, cancelOnEarlyWin = false) { provider, res ->
                mainHandler.post { callback.onProviderResult(provider, res) }
            }

            if (cancelled.get()) return@submit

            if (result != null && result.hasLyrics() && result.confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
                val decision = decisionEngine.decide(result, current)
                if (decision == LyricsDecisionEngine.Decision.REPLACE_UNSYNCED ||
                    decision == LyricsDecisionEngine.Decision.REPLACE_WITH_WORD_BY_WORD ||
                    decision == LyricsDecisionEngine.Decision.ACCEPT
                ) {
                    cacheManager.put(track, result)
                    telemetry.logResult(result, "BACKGROUND_UPGRADE")
                    mainHandler.post { callback.onLyricsUpgraded(result) }
                }
            }
        }
    }

    private fun createProviders(client: OkHttpClient): List<LyricsProvider> {
        val providers = ArrayList<LyricsProvider>()

        // ═══════════════════════════════════════════
        // LOCAL PROVIDERS (no network, highest priority)
        // ═══════════════════════════════════════════
        providers.add(EmbeddedLyricsProvider())

        // ═══════════════════════════════════════════
        // YOUTUBE MUSIC PROVIDER (official source, highest network priority)
        // ═══════════════════════════════════════════
        providers.add(com.dsann.music.data.lyrics.providers.YouTubeMusicLyricsProvider())



        // ═══════════════════════════════════════════
        // LINE-BY-LINE PROVIDERS (standard sync)
        // ═══════════════════════════════════════════

        // LRCLIB — best open-source baseline
        val lrcLibApi = Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibApi::class.java)
        providers.add(LrcLibProvider(lrcLibApi))

        // SyncLRC — alternative high-fidelity provider
        val syncLrcApi = Retrofit.Builder()
            .baseUrl("https://api.synclrc.dev/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.dsann.music.data.lyrics.SyncLrcApi::class.java)
        providers.add(com.dsann.music.data.lyrics.providers.SyncLrcProvider(syncLrcApi))

        // Paxsenix Multi-Provider API
        val paxsenixApi = Retrofit.Builder()
            .baseUrl("https://lyrics.paxsenix.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.dsann.music.data.lyrics.PaxsenixApi::class.java)

        providers.add(com.dsann.music.data.lyrics.providers.paxsenix.PaxsenixAppleMusicProvider(paxsenixApi))
        providers.add(com.dsann.music.data.lyrics.providers.paxsenix.PaxsenixSpotifyProvider(paxsenixApi))
        providers.add(com.dsann.music.data.lyrics.providers.paxsenix.PaxsenixMusixmatchProvider(paxsenixApi))
        providers.add(com.dsann.music.data.lyrics.providers.paxsenix.PaxsenixYoutubeProvider(paxsenixApi))

        // Additional catalogues remain separate entries so results stay selectable.
        providers.add(BetterLyricsAppleProvider(client))
        providers.add(QqMusicLyricsProvider(client))
        providers.add(NetEaseLyricsProvider(client))
        providers.add(KuGouLyricsProvider(client))
        providers.add(LyricsPlusProvider(client))
        providers.add(BiniLyricsProvider(client))
        providers.add(UnisonLyricsProvider(client))
        providers.add(GeniusLyricsProvider(client))

        Log.d(TAG, "Initialized ${providers.size} lyrics providers")
        return providers
    }
}
